package tw.linsy.aelorn.worlds;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.text.Messages;

/**
 * Folia-safe world transfer.
 *
 * The destination chunk is generated and loaded asynchronously *before* the
 * teleport is issued, so the player's region thread never blocks on a synchronous
 * chunk load — the main stall in a naive cross-world teleport. Once the chunk is
 * resident, the landing spot is validated on that chunk's own region thread and
 * nudged to a safe position if the configured coordinate has become unusable.
 */
final class WorldTransferService {

    private final AelornWorldsPlugin plugin;
    private final Map<UUID, Long> lastTransferAt = new ConcurrentHashMap<>();

    WorldTransferService(AelornWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Validates and starts a transfer. Returns immediately; the player is told
     * the outcome asynchronously. Call from the player's region thread.
     */
    Result transfer(Player player, WorldProfile profile) {
        return start(player, profile, false);
    }

    /**
     * Same pipeline as {@link #transfer}, but a player already in the world is
     * sent to its configured spawn instead of being told they are there — this is
     * what {@code /wx spawn} needs.
     */
    Result sendToSpawn(Player player, WorldProfile profile) {
        return start(player, profile, true);
    }

    private Result start(Player player, WorldProfile profile, boolean allowSameWorld) {
        GlobalSettings globals = plugin.globals();
        Result gate = checkEntry(player, profile, globals, allowSameWorld);
        if (gate != Result.STARTED) {
            return gate;
        }

        World world = Bukkit.getWorld(profile.name());
        if (world == null) {
            return Result.WORLD_NOT_LOADED;
        }

        long now = System.currentTimeMillis();
        if (!claimCooldown(player.getUniqueId(), now, globals.transfer().cooldownMillis())) {
            return Result.COOLDOWN;
        }

        Location destination = profile.destination(world);
        if (globals.transfer().preloadChunks()) {
            preloadThenTeleport(player, profile, world, destination, now);
        } else {
            runTeleport(player, profile, destination, now);
        }
        return Result.STARTED;
    }

    /**
     * Permission, transferability and per-world entry rules. Ordered cheapest
     * first so a denied transfer costs almost nothing.
     */
    private Result checkEntry(Player player, WorldProfile profile, GlobalSettings globals,
                              boolean allowSameWorld) {
        GlobalSettings.TransferOptions transfer = globals.transfer();
        if (!transfer.enabled()) {
            return Result.DISABLED;
        }
        if (!profile.transferable()) {
            return Result.NOT_TRANSFERABLE;
        }
        if (!Permissions.has(player, Permissions.TRANSFER)) {
            return Result.NO_PERMISSION;
        }
        // The per-world node is generated from config, so it cannot be declared a
        // child of the admin node in plugin.yml. Without an explicit bypass, turning
        // require-per-world-permission on locks administrators out of their own
        // worlds until someone grants every aelorn.worlds.world.<name> by hand.
        if (transfer.requirePerWorldPermission()
            && !Permissions.has(player, Permissions.ENTRY_BYPASS)
            && !Permissions.hasOrAdmin(player, worldPermission(transfer, profile))) {
            return Result.NO_PERMISSION;
        }

        World world = Bukkit.getWorld(profile.name());
        if (world == null) {
            return Result.WORLD_NOT_LOADED;
        }
        if (!allowSameWorld && world.equals(player.getWorld())) {
            return Result.ALREADY_THERE;
        }

        Result denial = checkArrival(player, profile, world, player.getWorld());
        return denial == null ? Result.STARTED : denial;
    }

    /**
     * The per-world entry gate on its own, shared by {@code /wx tp}, the menu and
     * the teleport/portal listeners.
     *
     * Applying the same gate to arrivals the plugin did not initiate is what makes
     * {@code player-limit} and {@code blocked-origins} mean anything: otherwise any
     * other plugin's teleport walks straight past them.
     *
     * @return the reason to refuse, or {@code null} when the player may enter
     */
    @Nullable Result checkArrival(Player player, WorldProfile profile, World destination,
                                  @Nullable World origin) {
        return checkArrival(player, profile, destination, origin, false);
    }

    /**
     * @param alreadyInside {@code true} when the player is already counted in the
     *                      destination's player list — a login, rather than an arrival
     *                      still to come. Without the adjustment the last player
     *                      allowed into a capped world is refused on their next relog,
     *                      because they are counted as both the occupant and the
     *                      arrival.
     */
    @Nullable Result checkArrival(Player player, WorldProfile profile, World destination,
                                  @Nullable World origin, boolean alreadyInside) {
        // Staff with the bypass node ignore capacity, origin and entry gating.
        if (Permissions.has(player, Permissions.ENTRY_BYPASS)) {
            return null;
        }
        WorldProfile.EntryRules entry = profile.entry();
        if (entry.isOpen()) {
            return null;
        }
        if (entry.hasPermission() && !Permissions.has(player, entry.permission())) {
            return Result.NO_PERMISSION;
        }
        if (entry.blocksOrigin(origin)) {
            return Result.ORIGIN_BLOCKED;
        }
        if (entry.hasLimit()) {
            int occupancy = destination.getPlayers().size() - (alreadyInside ? 1 : 0);
            if (occupancy >= entry.playerLimit()) {
                return Result.WORLD_FULL;
            }
        }
        return null;
    }

    private void preloadThenTeleport(Player player, WorldProfile profile, World world,
                                     Location destination, long startedAt) {
        world.getChunkAtAsync(destination, true)
            .thenAccept(chunk -> resolveLandingThenTeleport(player, profile, destination, startedAt))
            .exceptionally(failure -> {
                plugin.getLogger().log(Level.WARNING,
                    "預載入 " + profile.name() + " 目標區塊失敗，改為直接傳送。", failure);
                runTeleport(player, profile, destination, startedAt);
                return null;
            });
    }

    /**
     * Block reads must happen on the region owning the destination, which the
     * preload has just made resident.
     */
    private void resolveLandingThenTeleport(Player player, WorldProfile profile,
                                            Location destination, long startedAt) {
        GlobalSettings.SafeLandingOptions landing = plugin.globals().transfer().safeLanding();
        if (!landing.enabled()) {
            runTeleport(player, profile, destination, startedAt);
            return;
        }
        SafeSpawnLocator locator = new SafeSpawnLocator(landing);
        plugin.schedulers().region(destination, () -> {
            Location target = destination;
            if (!locator.isSafe(destination)) {
                Location safe = locator.findSafeLanding(destination);
                if (safe != null) {
                    target = safe;
                    plugin.getLogger().fine(() -> "世界 " + profile.name()
                        + " 的設定落點不安全，已改用鄰近安全位置。");
                } else {
                    plugin.getLogger().warning("世界 " + profile.name()
                        + " 的設定落點不安全，且附近找不到安全位置；仍以原座標傳送。");
                }
            }
            runTeleport(player, profile, target, startedAt);
        });
    }

    /** Hops onto the player's entity scheduler before touching the player. */
    private void runTeleport(Player player, WorldProfile profile, Location destination, long startedAt) {
        World destinationWorld = destination.getWorld();
        if (destinationWorld == null) {
            plugin.getLogger().warning("世界 " + profile.name() + " 的傳送目的地沒有世界物件，已取消傳送。");
            return;
        }
        plugin.schedulers().entity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            // Already gated above; tell the listener not to re-run the entry check,
            // which would otherwise report a denial the player has no way to act on.
            // The ticket names this destination, so it cannot be spent by anything else.
            plugin.ruleService().markInternalTeleport(player.getUniqueId(), destinationWorld);
            player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .thenAccept(success -> announce(player, profile, startedAt, success));
        }, null);
    }

    private void announce(Player player, WorldProfile profile, long startedAt, boolean success) {
        long elapsed = System.currentTimeMillis() - startedAt;
        Messages messages = plugin.messages();
        String key = success ? "transfer.success" : "transfer.failed";
        plugin.schedulers().entity(player, () -> messages.send(player, key,
            "world", profile.alias(), "ms", elapsed), null);
    }

    /**
     * True when the player may start a transfer now, recording the attempt if so.
     *
     * <p>Atomic on purpose. A separate get-then-put loses the race between a menu
     * click and a {@code /aw tp} arriving on two different threads, and two transfers
     * starting for one player means two teleports chasing each other into the same
     * destination.
     */
    private boolean claimCooldown(UUID playerId, long now, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return true;
        }
        Long kept = lastTransferAt.merge(playerId, now,
            (previous, candidate) -> candidate - previous < cooldownMillis ? previous : candidate);
        return kept != null && kept == now;
    }

    /** Drops the cooldown entry for a player who left. */
    void forget(UUID playerId) {
        lastTransferAt.remove(playerId);
    }

    void reset() {
        lastTransferAt.clear();
    }

    /** Per-world permission node; the prefix is configurable. */
    static String worldPermission(GlobalSettings.TransferOptions transfer, WorldProfile profile) {
        return transfer.permissionPrefix() + profile.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Outcome of the validation phase; {@link #STARTED} means the transfer is
     * underway. Each constant names a key in messages.yml rather than carrying
     * the text, so the wording is the admin's to change.
     */
    enum Result {
        STARTED("transfer.started"),
        DISABLED("transfer.disabled"),
        NOT_TRANSFERABLE("transfer.not-transferable"),
        NO_PERMISSION("transfer.no-permission"),
        WORLD_NOT_LOADED("world.not-loaded"),
        ALREADY_THERE("transfer.already-there"),
        COOLDOWN("transfer.cooldown"),
        ORIGIN_BLOCKED("transfer.origin-blocked"),
        WORLD_FULL("transfer.world-full");

        private final String messageKey;

        Result(String messageKey) {
            this.messageKey = messageKey;
        }

        String messageKey() {
            return messageKey;
        }

        boolean started() {
            return this == STARTED;
        }
    }
}
