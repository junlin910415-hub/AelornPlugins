package tw.linsy.aelorn.worlds;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.text.Messages;

/**
 * Enforces the per-world player rules: game mode, flight, void rescue, respawn
 * routing, and the feedback shown on arrival.
 *
 * These are the levers that make a multi-world server behave like separate
 * servers — a creative build world, a survival dungeon, a lobby nobody can fall
 * out of — without a second Bukkit instance. Everything is opt-in per world, and
 * the whole listener short-circuits on a single field read when no world uses it.
 *
 * <p>All player mutations hop onto the player's entity scheduler first, as Folia
 * requires.
 */
final class WorldRuleService implements Listener {

    /**
     * How long a pre-validated teleport stays claimable.
     *
     * <p>Not configurable: this is the width of an internal race window between
     * issuing a teleport and seeing its event, not a policy an admin has an opinion
     * about. Generous enough to survive a stalled region thread, short enough that a
     * teleport which never happened cannot help a later one.
     */
    private static final long TELEPORT_TICKET_MILLIS = 5_000L;

    private final AelornWorldsPlugin plugin;

    /** Players mid-rescue, so a fall does not queue one teleport per movement packet. */
    private final Set<UUID> rescuing = ConcurrentHashMap.newKeySet();

    /**
     * Teleports this plugin issued after already running the entry gate. Consumed
     * by the teleport listener so a validated transfer is never denied twice.
     *
     * <p>A ticket names its destination and expires, rather than being a bare "the
     * next teleport is fine" flag. A bare flag is consumed by whichever teleport
     * arrives first, which cuts both ways: our own validated transfer gets gated a
     * second time, and — the half that matters — an unrelated teleport walks through
     * the entry gate untested. Any player able to trigger a second teleport while a
     * transfer is in flight could use that to enter a world their permissions,
     * origin restrictions or capacity limit should have refused.
     */
    private final Map<UUID, Ticket> internalTeleports = new ConcurrentHashMap<>();

    /** Last console-side arrival command run, keyed by player and world. */
    private final Map<String, Long> lastArrivalCommandsAt = new ConcurrentHashMap<>();

    WorldRuleService(AelornWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    /** A cross-world teleport this plugin has already gated. */
    private record Ticket(UUID destinationWorldId, long expiresAt) {
    }

    /** Marks a specific pending teleport as already validated. */
    void markInternalTeleport(UUID playerId, World destination) {
        internalTeleports.put(playerId,
            new Ticket(destination.getUID(), System.currentTimeMillis() + TELEPORT_TICKET_MILLIS));
    }

    /**
     * Consumes the ticket for this exact arrival, if there is one.
     *
     * <p>A ticket that does not match is deliberately left in place: it belongs to a
     * teleport that has not arrived yet, and stealing it would send that one back
     * through the gate. Expired tickets are dropped on sight, so a teleport another
     * plugin cancelled cannot leave one behind.
     */
    private boolean consumeTicket(Player player, World destination,
                                  PlayerTeleportEvent.TeleportCause cause) {
        Ticket ticket = internalTeleports.get(player.getUniqueId());
        if (ticket == null) {
            return false;
        }
        if (System.currentTimeMillis() > ticket.expiresAt()) {
            internalTeleports.remove(player.getUniqueId(), ticket);
            return false;
        }
        // Everything this plugin issues is a PLUGIN teleport into a known world.
        if (cause != PlayerTeleportEvent.TeleportCause.PLUGIN
            || !ticket.destinationWorldId().equals(destination.getUID())) {
            return false;
        }
        return internalTeleports.remove(player.getUniqueId(), ticket);
    }

    /**
     * Applies the per-world entry gate to arrivals this plugin did not start —
     * other plugins' teleports, commands, and portals. Without this, a
     * {@code player-limit} or {@code blocked-origins} rule only holds for
     * {@code /aw tp}, which is not what an admin who set it expects.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        World destination = event.getTo().getWorld();
        if (destination == null || destination.equals(event.getFrom().getWorld())) {
            return;
        }
        // Ours, and already gated. Matching on destination and cause means an
        // unrelated teleport cannot claim the ticket and slip past the gate.
        if (consumeTicket(player, destination, event.getCause())) {
            return;
        }
        GlobalSettings.RuleOptions options = plugin.globals().rules();
        if (!options.enforceEntryOnTeleport() || options.exempt(event.getCause())
            || !plugin.registry().anyEntryRule()) {
            return;
        }
        WorldProfile profile = plugin.registry().byWorld(destination);
        if (profile == null) {
            return;
        }
        WorldTransferService.Result denial = plugin.transferService()
            .checkArrival(player, profile, destination, event.getFrom().getWorld());
        if (denial != null) {
            event.setCancelled(true);
            plugin.messages().send(player, denial.messageKey(), "world", profile.alias());
        }
    }

    /**
     * Portal control: block portal use per world, and re-point the destination
     * world. Vanilla sends every nether portal to the single nether world, which
     * stops being right the moment a server has more than one overworld.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!plugin.registry().anyPortalRule() && !plugin.registry().anyEntryRule()) {
            return;
        }
        Player player = event.getPlayer();
        GlobalSettings.RuleOptions options = plugin.globals().rules();
        boolean bypass = !options.bypassPermission().isEmpty()
            && Permissions.has(player, options.bypassPermission());

        WorldProfile origin = plugin.registry().byWorld(event.getFrom().getWorld());
        if (origin != null && origin.portals().disabled() && !bypass) {
            event.setCancelled(true);
            sendPortalDenial(player, origin);
            return;
        }

        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (origin != null) {
            retargetPortal(origin, to, event);
        }
        // Re-read: retargetPortal may have moved the destination to another world.
        World destination = to.getWorld();
        if (destination.equals(event.getFrom().getWorld())) {
            return;
        }
        WorldProfile profile = plugin.registry().byWorld(destination);
        if (profile == null || !options.enforceEntryOnTeleport()) {
            return;
        }
        WorldTransferService.Result denial = plugin.transferService()
            .checkArrival(player, profile, destination, event.getFrom().getWorld());
        if (denial != null) {
            event.setCancelled(true);
            plugin.messages().send(player, denial.messageKey(), "world", profile.alias());
        }
    }

    /** Keeps vanilla's coordinate scaling, only swapping which world receives it. */
    private void retargetPortal(WorldProfile origin, Location to, PlayerPortalEvent event) {
        String targetName = origin.portals().targetFor(to.getWorld().getEnvironment());
        if (targetName == null) {
            return;
        }
        WorldProfile target = plugin.registry().byName(targetName).orElse(null);
        World targetWorld = Bukkit.getWorld(target == null ? targetName : target.name());
        if (targetWorld == null) {
            plugin.getLogger().warning("世界 " + origin.name() + " 的 portals.targets 指向未載入的世界: "
                + targetName + "，維持原版目的地。");
            return;
        }
        if (targetWorld.equals(to.getWorld())) {
            return;
        }
        to.setWorld(targetWorld);
        event.setTo(to);
    }

    private void sendPortalDenial(Player player, WorldProfile origin) {
        if (origin.portals().deniedMessage().isEmpty()) {
            plugin.messages().send(player, "portal.disabled", "world", origin.alias());
        } else {
            plugin.messages().sendInline(player, origin.portals().deniedMessage(),
                "player", player.getName(), "world", origin.alias());
        }
    }

    /**
     * Per-world command blocking. Matches the first token with any {@code plugin:}
     * namespace stripped, so {@code /home} and {@code /essentials:home} both hit
     * the same rule.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.registry().anyCommandRule()) {
            return;
        }
        Player player = event.getPlayer();
        WorldProfile profile = plugin.registry().byWorld(player.getWorld());
        if (profile == null) {
            return;
        }
        WorldProfile.CommandRules rules = profile.playerRules().commands();
        if (rules.isOpen()) {
            return;
        }
        String bypass = plugin.globals().rules().commandBypassPermission();
        if (!bypass.isEmpty() && Permissions.has(player, bypass)) {
            return;
        }
        String message = event.getMessage();
        int space = message.indexOf(' ');
        String command = WorldRegistry.normaliseCommand(space < 0 ? message : message.substring(0, space));
        if (!rules.blocks(command)) {
            return;
        }
        event.setCancelled(true);
        if (rules.deniedMessage().isEmpty()) {
            plugin.messages().send(player, "command.blocked-in-world",
                "command", command, "world", profile.alias());
        } else {
            plugin.messages().sendInline(player, rules.deniedMessage(),
                "player", player.getName(), "command", command, "world", profile.alias());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Rules only; arrival feedback is for actually crossing into a world.
        applyRules(event.getPlayer(), event.getPlayer().getWorld());
        enforceEntryOnJoin(event.getPlayer());
    }

    /**
     * Applies the entry gate to a player who logged in already inside a managed world.
     *
     * <p>Without this, {@code player-limit} and {@code entry.permission} hold only
     * while a player is moving around: log out inside a capped event world, log back
     * in, and the server puts you exactly where you left off without consulting any
     * rule. That makes every entry restriction advisory, since the way past it is to
     * reconnect.
     *
     * <p>Origin rules cannot apply — a login has no origin world — so only capacity
     * and permission are checked, and the player is moved out rather than refused,
     * because there is no login left to cancel by this point.
     */
    private void enforceEntryOnJoin(Player player) {
        GlobalSettings.RuleOptions options = plugin.globals().rules();
        if (!options.enforceEntryOnJoin() || !plugin.registry().anyEntryRule()) {
            return;
        }
        World world = player.getWorld();
        WorldProfile profile = plugin.registry().byWorld(world);
        if (profile == null) {
            return;
        }
        WorldTransferService.Result denial =
            plugin.transferService().checkArrival(player, profile, world, null, true);
        if (denial == null) {
            return;
        }
        Location fallback = joinFallback(options, world);
        if (fallback == null) {
            plugin.getLogger().warning("玩家 " + player.getName() + " 登入時不符合世界 " + profile.name()
                + " 的進入條件（" + denial.name() + "），但找不到可用的退避世界，因此維持原地。"
                + "請設定 rules.entry-fallback-world。");
            return;
        }
        // One tick later: teleporting inside the join event itself races the client
        // still being told where it is, and lands players in an unloaded view.
        plugin.schedulers().entityDelayed(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.messages().send(player, denial.messageKey(), "world", profile.alias());
            markInternalTeleport(player.getUniqueId(), fallback.getWorld());
            player.teleportAsync(fallback, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }, null, 1L);
    }

    /** Where a refused login goes: the configured world, else the server's first world. */
    private @Nullable Location joinFallback(GlobalSettings.RuleOptions options, World refused) {
        WorldProfile target = plugin.registry().byName(options.entryFallbackWorld()).orElse(null);
        if (target != null) {
            World world = Bukkit.getWorld(target.name());
            if (world != null && !world.equals(refused)) {
                return target.destination(world);
            }
        }
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return null;
        }
        World primary = worlds.get(0);
        return primary.equals(refused) ? null : primary.getSpawnLocation();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        applyRules(player, world);

        WorldProfile profile = plugin.registry().byWorld(world);
        if (profile != null && profile.arrival() != WorldProfile.Arrival.NONE) {
            announceArrival(player, profile, world);
        }
    }

    /**
     * Void rescue. Gated on a volatile flag so the common case — no world uses
     * the feature — costs one field read per movement packet.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.registry().anyVoidRule() || !plugin.globals().rules().voidProtectionEnabled()) {
            return;
        }
        Location to = event.getTo();
        Player player = event.getPlayer();
        World world = to.getWorld();
        WorldProfile profile = plugin.registry().byWorld(world);
        if (profile == null) {
            return;
        }
        WorldProfile.VoidRule rule = profile.playerRules().voidRule();
        if (!rule.enabled() || to.getY() >= triggerHeight(rule, world)) {
            return;
        }
        if (!rescuing.add(player.getUniqueId())) {
            return;
        }
        rescue(player, profile, rule, world);
    }

    private static double triggerHeight(WorldProfile.VoidRule rule, World world) {
        // Five blocks below bedrock: past the point where a player can recover, but
        // still above the vanilla despawn depth, so the rescue is not a surprise.
        return rule.usesWorldMinimum() ? world.getMinHeight() - 5.0D : rule.belowY();
    }

    private void rescue(Player player, WorldProfile profile, WorldProfile.VoidRule rule, World world) {
        Messages messages = plugin.messages();
        plugin.schedulers().entity(player, () -> {
            if (!player.isOnline()) {
                rescuing.remove(player.getUniqueId());
                return;
            }
            messages.sendInline(player, rule.message(),
                "player", player.getName(), "world", profile.alias());
            if (rule.action() == WorldProfile.VoidAction.KILL) {
                player.setHealth(0.0D);
                rescuing.remove(player.getUniqueId());
                return;
            }
            Location destination = rescueDestination(profile, rule, world);
            // A rescue must never be refused by an entry rule; the player is falling.
            markInternalTeleport(player.getUniqueId(), destination.getWorld());
            player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenComplete((success, failure) -> rescuing.remove(player.getUniqueId()));
        }, () -> rescuing.remove(player.getUniqueId()));
    }

    private Location rescueDestination(WorldProfile profile, WorldProfile.VoidRule rule, World fallbackWorld) {
        if (rule.action() == WorldProfile.VoidAction.TELEPORT_WORLD && !rule.targetWorld().isBlank()) {
            WorldProfile target = plugin.registry().byName(rule.targetWorld()).orElse(null);
            World targetWorld = target == null ? null : Bukkit.getWorld(target.name());
            if (targetWorld != null) {
                return target.destination(targetWorld);
            }
            plugin.getLogger().warning("世界 " + profile.name() + " 的 rules.void.target-world 指向未載入的世界: "
                + rule.targetWorld() + "，改用本世界出生點。");
        }
        return profile.destination(fallbackWorld);
    }

    /** Sends a player who dies in a configured world to that world's chosen respawn. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.registry().anyPlayerRules()) {
            return;
        }
        WorldProfile diedIn = plugin.registry().byWorld(event.getPlayer().getWorld());
        if (diedIn == null) {
            return;
        }
        WorldProfile.RespawnRule rule = diedIn.playerRules().respawn();
        if (!rule.overrides()) {
            return;
        }
        // A bed or anchor is the player's own choice; only override it on request.
        if ((event.isBedSpawn() || event.isAnchorSpawn()) && !rule.overrideBed()) {
            return;
        }
        WorldProfile target = rule.world().isEmpty() ? diedIn
            : plugin.registry().byName(rule.world()).orElse(null);
        if (target == null) {
            plugin.getLogger().warning("世界 " + diedIn.name() + " 的 rules.respawn.world 指向未設定的世界: "
                + rule.world());
            return;
        }
        World targetWorld = Bukkit.getWorld(target.name());
        if (targetWorld == null) {
            return;
        }
        // A respawn crosses into the target world without a teleport event, so the
        // entry gate would otherwise never see it — hand it a ticket rather than
        // leaving the arrival to be second-guessed.
        markInternalTeleport(event.getPlayer().getUniqueId(), targetWorld);
        event.setRespawnLocation(rule.useConfiguredSpawn()
            ? target.destination(targetWorld)
            : targetWorld.getSpawnLocation());
    }

    /**
     * Applies game mode and flight for the world the player is now in. A world
     * without its own game mode falls back to {@code rules.default-game-mode},
     * which is what stops a creative builder carrying creative into survival.
     */
    void applyRules(Player player, World world) {
        GlobalSettings.RuleOptions options = plugin.globals().rules();
        // default-game-mode is a server-wide rule: it has to work even when no
        // individual world declares a rules section of its own.
        if (!options.enabled()
            || (!plugin.registry().anyPlayerRules() && options.defaultGameMode() == null)) {
            return;
        }
        if (!options.bypassPermission().isEmpty() && Permissions.has(player, options.bypassPermission())) {
            return;
        }
        WorldProfile profile = plugin.registry().byWorld(world);
        WorldProfile.PlayerRules rules = profile == null
            ? WorldProfile.PlayerRules.INHERIT : profile.playerRules();

        GameMode gameMode = rules.gameMode() != null ? rules.gameMode() : options.defaultGameMode();
        Boolean allowFlight = rules.allowFlight();
        if (gameMode == null && allowFlight == null) {
            return;
        }
        plugin.schedulers().entity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (gameMode != null && player.getGameMode() != gameMode) {
                player.setGameMode(gameMode);
            }
            if (allowFlight != null && player.getAllowFlight() != allowFlight) {
                player.setAllowFlight(allowFlight);
                if (!allowFlight) {
                    player.setFlying(false);
                }
            }
        }, null);
    }

    /** Chat line, title, action bar, sound and commands — each one optional. */
    private void announceArrival(Player player, WorldProfile profile, World world) {
        WorldProfile.Arrival arrival = profile.arrival();
        Messages messages = plugin.messages();
        Object[] placeholders = {
            "player", player.getName(),
            "world", profile.name(),
            "alias", profile.alias(),
            "players", world.getPlayers().size()
        };

        plugin.schedulers().entity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            messages.sendInline(player, arrival.message(), placeholders);
            if (!arrival.actionBar().isEmpty()) {
                player.sendActionBar(messages.inline(arrival.actionBar(), placeholders));
            }
            if (arrival.hasTitle()) {
                player.showTitle(Title.title(
                    messages.inline(arrival.title(), placeholders),
                    messages.inline(arrival.subtitle(), placeholders),
                    Title.Times.times(ticks(arrival.fadeInTicks()), ticks(arrival.stayTicks()),
                        ticks(arrival.fadeOutTicks()))));
            }
            playArrivalSound(player, arrival);
            runArrivalCommands(player, profile, arrival);
        }, null);
    }

    private static Duration ticks(int count) {
        return Duration.ofMillis(count * 50L);
    }

    private void playArrivalSound(Player player, WorldProfile.Arrival arrival) {
        if (arrival.sound().isEmpty()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(arrival.sound()), Sound.Source.MASTER,
                arrival.soundVolume(), arrival.soundPitch()));
        } catch (RuntimeException invalid) {
            plugin.getLogger().warning("arrival.sound 不是有效的音效 key: " + arrival.sound());
        }
    }

    /**
     * Console commands run on the global region scheduler, player commands on the
     * player's own. Only {@code {player}} and the world placeholders are
     * substituted; the command text itself comes from config.yml.
     *
     * <p>Two guards, both about the console path. Commands there run with full
     * operator authority, and the trigger is "a player entered a world" — something a
     * player controls and can repeat as fast as they can walk through a portal. A
     * {@code give} or {@code eco add} in this list is an item duplicator without the
     * cooldown, so it is applied per player, per world.
     *
     * <p>The name is also checked rather than pasted in blind. Bukkit account names
     * are safe, but Bedrock players arriving through a proxy carry prefixes and
     * spaces, which silently turn one command into a different one with different
     * arguments. A name that is not a plain identifier is reported and skipped.
     */
    private void runArrivalCommands(Player player, WorldProfile profile, WorldProfile.Arrival arrival) {
        if (arrival.commands().isEmpty()) {
            return;
        }
        String name = player.getName();
        if (!isPlainName(name)) {
            plugin.getLogger().warning("玩家名稱 " + name + " 含有指令參數分隔字元，"
                + "世界 " + profile.name() + " 的 arrival.commands 已略過，避免組出非預期的指令。");
            return;
        }
        if (!arrival.commandsAsPlayer()
            && !claimArrivalCommands(player.getUniqueId(), profile, arrival)) {
            return;
        }
        for (String template : arrival.commands()) {
            String command = template
                .replace("{player}", name)
                .replace("{world}", profile.name());
            if (arrival.commandsAsPlayer()) {
                dispatchAsPlayer(player, command);
            } else {
                plugin.schedulers().global(() -> dispatchAsConsole(profile, command));
            }
        }
    }

    /** Rate limits the console path; the player path already runs with the player's own rights. */
    private boolean claimArrivalCommands(UUID playerId, WorldProfile profile,
                                         WorldProfile.Arrival arrival) {
        long cooldownMillis = arrival.commandCooldownSeconds() * 1000L;
        if (cooldownMillis <= 0L) {
            return true;
        }
        long now = System.currentTimeMillis();
        // Per player and per world: entering two different worlds is two arrivals, and
        // bouncing between the same two should still only pay out once for each.
        String key = playerId + "|" + profile.name();
        Long kept = lastArrivalCommandsAt.merge(key, now,
            (previous, candidate) -> candidate - previous < cooldownMillis ? previous : candidate);
        return kept != null && kept == now;
    }

    /** Letters, digits and underscore only — what a command argument can carry unquoted. */
    private static boolean isPlainName(String name) {
        if (name.isEmpty() || name.length() > 32) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            boolean plain = (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
            if (!plain) {
                return false;
            }
        }
        return true;
    }

    private void dispatchAsPlayer(Player player, String command) {
        try {
            player.performCommand(command);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "arrival.commands 執行失敗: " + command, failure);
        }
    }

    private void dispatchAsConsole(WorldProfile profile, String command) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING,
                "世界 " + profile.name() + " 的 arrival.commands 執行失敗: " + command, failure);
        }
    }

    void forget(UUID playerId) {
        rescuing.remove(playerId);
        internalTeleports.remove(playerId);
        // Arrival-command keys carry the world name, so they are pruned by prefix.
        String prefix = playerId + "|";
        lastArrivalCommandsAt.keySet().removeIf(key -> key.startsWith(prefix));
    }

    void reset() {
        rescuing.clear();
        internalTeleports.clear();
        lastArrivalCommandsAt.clear();
    }

    /** Re-applies rules to everyone online; used after {@code /aw reload}. */
    void applyToOnlinePlayers() {
        if (!plugin.registry().anyPlayerRules() && plugin.globals().rules().defaultGameMode() == null) {
            return;
        }
        plugin.schedulers().global(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyRules(player, player.getWorld());
            }
        });
    }

    /** Exposed for {@code /aw rules}. */
    static String describeGameMode(@Nullable GameMode gameMode, String unsetLabel) {
        return gameMode == null ? unsetLabel : gameMode.name();
    }
}
