package tw.linsy.aelorn.worlds;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.text.Text;

/**
 * Immutable per-world configuration. Parsed once by {@link WorldRegistry} and
 * never re-read from YAML afterwards, so lookups on the transfer, movement and
 * render paths cost a single map probe.
 *
 * <p>Every optional block has an {@code INHERIT}/{@code OPEN}/{@code DISABLED}
 * singleton, so a world that declares nothing allocates nothing and the
 * "unconfigured" case is a reference comparison.
 */
record WorldProfile(
    String name,
    String alias,
    World.Environment environment,
    @Nullable Difficulty difficulty,
    @Nullable Boolean pvp,
    @Nullable SpawnPoint spawn,
    VisualSettings visual,
    BorderSettings border,
    ChunkSettings chunk,
    SpawnSettings spawnSettings,
    EntryRules entry,
    PlayerRules playerRules,
    PortalRules portals,
    PurgeRules purge,
    Arrival arrival,
    Material icon,
    boolean transferable,
    int menuSlot) {

    /** Config-declared spawn point; used as the transfer destination. */
    record SpawnPoint(double x, double y, double z, float yaw, float pitch) {

        Location toLocation(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    /**
     * Per-world visual tuning. {@code -1} means "leave the server default
     * alone"; a null boxed value means the same for booleans.
     */
    record VisualSettings(
        int viewDistance,
        int simulationDistance,
        int sendViewDistance,
        long lockedTime,
        WeatherLock weather,
        @Nullable Boolean autoSave,
        int spawnChunkRadius,
        Map<String, String> gameRules) {

        static final VisualSettings INHERIT =
            new VisualSettings(-1, -1, -1, -1L, WeatherLock.NONE, null, -1, Map.of());

        boolean hasAnyOverride() {
            return viewDistance > 0 || simulationDistance > 0 || sendViewDistance > 0
                || lockedTime >= 0 || weather != WeatherLock.NONE || autoSave != null
                || spawnChunkRadius >= 0 || !gameRules.isEmpty();
        }
    }

    /**
     * Per-world border. The vanilla border is the cheapest way to stop players
     * generating terrain forever, which is the single largest driver of disk and
     * chunk-load cost on a multi-world server.
     *
     * @param centerX {@code null} keeps the world's current border centre
     * @param transitionSeconds seconds to animate towards {@code size}; 0 snaps
     */
    record BorderSettings(
        boolean enabled,
        double size,
        @Nullable Double centerX,
        @Nullable Double centerZ,
        long transitionSeconds,
        int warningDistance,
        int warningTimeSeconds,
        double damageAmount,
        double damageBuffer) {

        static final BorderSettings INHERIT =
            new BorderSettings(false, -1.0D, null, null, 0L, -1, -1, -1.0D, -1.0D);

        boolean hasAnyOverride() {
            return enabled && (size > 0.0D || centerX != null || centerZ != null
                || warningDistance >= 0 || warningTimeSeconds >= 0
                || damageAmount >= 0.0D || damageBuffer >= 0.0D);
        }
    }

    /** Per-world chunk tuning; see {@link ChunkOptimizer}. */
    record ChunkSettings(boolean itemCapEnabled, int maxItemsPerChunk) {

        static final ChunkSettings DISABLED = new ChunkSettings(false, -1);
    }

    /**
     * Per-world mob spawn budget. {@code limits} caps how many of a category may
     * exist per player; {@code tickRates} sets how often the category is spawned
     * (higher = rarer). Empty maps leave the server-wide bukkit.yml values alone.
     */
    record SpawnSettings(Map<SpawnCategory, Integer> limits, Map<SpawnCategory, Integer> tickRates) {

        static final SpawnSettings INHERIT = new SpawnSettings(Map.of(), Map.of());

        boolean isEmpty() {
            return limits.isEmpty() && tickRates.isEmpty();
        }
    }

    /**
     * Gate applied before a transfer completes.
     *
     * @param playerLimit max players allowed in the world, or -1 for unlimited
     * @param blockedOrigins lowercase world names a player may not arrive from
     * @param allowedOrigins when non-empty, the only worlds a player may arrive from
     * @param permission extra permission node required to enter, or {@code ""}
     */
    record EntryRules(int playerLimit, Set<String> blockedOrigins, Set<String> allowedOrigins,
                      String permission) {

        static final EntryRules OPEN = new EntryRules(-1, Set.of(), Set.of(), "");

        boolean blocksOrigin(@Nullable World origin) {
            if (origin == null) {
                return false;
            }
            String name = origin.getName().toLowerCase(Locale.ROOT);
            if (blockedOrigins.contains(name)) {
                return true;
            }
            return !allowedOrigins.isEmpty() && !allowedOrigins.contains(name);
        }

        boolean hasLimit() {
            return playerLimit >= 0;
        }

        boolean hasPermission() {
            return !permission.isEmpty();
        }

        boolean isOpen() {
            return !hasLimit() && blockedOrigins.isEmpty() && allowedOrigins.isEmpty() && !hasPermission();
        }
    }

    /**
     * Rules enforced on players while they are in this world.
     *
     * @param gameMode forced on entry and join, or {@code null} to leave it alone
     * @param allowFlight forced flight permission, or {@code null} to leave it alone
     */
    record PlayerRules(
        @Nullable GameMode gameMode,
        @Nullable Boolean allowFlight,
        VoidRule voidRule,
        RespawnRule respawn,
        CommandRules commands) {

        static final PlayerRules INHERIT =
            new PlayerRules(null, null, VoidRule.DISABLED, RespawnRule.VANILLA, CommandRules.OPEN);

        boolean isEmpty() {
            return gameMode == null && allowFlight == null
                && !voidRule.enabled() && !respawn.overrides() && commands.isOpen();
        }
    }

    /**
     * Commands refused while the player is in this world.
     *
     * Names are matched on the first token with any namespace stripped, so both
     * {@code /home} and {@code /essentials:home} are caught by {@code home}.
     */
    record CommandRules(Set<String> blocked, String deniedMessage) {

        static final CommandRules OPEN = new CommandRules(Set.of(), "");

        boolean isOpen() {
            return blocked.isEmpty();
        }

        boolean blocks(String normalisedCommand) {
            return blocked.contains(normalisedCommand);
        }
    }

    /**
     * Portal behaviour for this world.
     *
     * Vanilla sends every nether portal to the one nether world, which is wrong as
     * soon as a server has more than one overworld. {@code targets} re-points the
     * destination by environment while keeping vanilla's coordinate scaling.
     *
     * @param targets destination environment to the world that should receive it
     */
    record PortalRules(boolean disabled, Map<World.Environment, String> targets, String deniedMessage) {

        static final PortalRules INHERIT = new PortalRules(false, Map.of(), "");

        boolean isOpen() {
            return !disabled && targets.isEmpty();
        }

        @Nullable String targetFor(World.Environment environment) {
            return targets.get(environment);
        }
    }

    /**
     * What happens when a player falls out of the world.
     *
     * @param belowY trigger height; {@code Double.NaN} means "world minimum minus a margin"
     * @param targetWorld world to send the player to for {@link VoidAction#TELEPORT_WORLD}
     * @param message inline text sent on rescue, or {@code ""} for silence
     */
    record VoidRule(boolean enabled, double belowY, VoidAction action, String targetWorld, String message) {

        static final VoidRule DISABLED =
            new VoidRule(false, Double.NaN, VoidAction.TELEPORT_SPAWN, "", "");

        boolean usesWorldMinimum() {
            return Double.isNaN(belowY);
        }
    }

    enum VoidAction {
        /** Back to this world's configured spawn. */
        TELEPORT_SPAWN,
        /** To another configured world's spawn. */
        TELEPORT_WORLD,
        KILL
    }

    /**
     * Where a player who dies in this world reappears.
     *
     * @param world name of the world to respawn in, or {@code ""} to keep vanilla
     * @param useConfiguredSpawn use the WorldLoaderX spawn instead of the world spawn
     * @param overrideBed also redirect players with a valid bed or anchor
     */
    record RespawnRule(String world, boolean useConfiguredSpawn, boolean overrideBed) {

        static final RespawnRule VANILLA = new RespawnRule("", false, false);

        boolean overrides() {
            return !world.isEmpty() || useConfiguredSpawn;
        }
    }

    /**
     * Scheduled entity cleanup for this world.
     *
     * @param keepPerChunk entities of the target categories kept per chunk; 0 removes all
     */
    record PurgeRules(boolean autoEnabled, int intervalSeconds, Set<SpawnCategory> categories,
                      int keepPerChunk, boolean announce) {

        static final PurgeRules INHERIT = new PurgeRules(false, -1, Set.of(), 0, false);
    }

    /**
     * Feedback shown when a player arrives in this world. All of it is optional;
     * blank strings are skipped rather than sent as empty lines.
     */
    record Arrival(String message, String title, String subtitle, String actionBar,
                   int fadeInTicks, int stayTicks, int fadeOutTicks,
                   String sound, float soundVolume, float soundPitch,
                   List<String> commands, boolean commandsAsPlayer, int commandCooldownSeconds) {

        static final Arrival NONE =
            new Arrival("", "", "", "", 10, 40, 10, "", 1.0F, 1.0F, List.of(), false, 0);

        boolean hasTitle() {
            return !title.isEmpty() || !subtitle.isEmpty();
        }

        boolean isEmpty() {
            return message.isEmpty() && !hasTitle() && actionBar.isEmpty()
                && sound.isEmpty() && commands.isEmpty();
        }
    }

    enum WeatherLock {
        /** Leave weather under vanilla control. */
        NONE,
        CLEAR,
        RAIN,
        THUNDER
    }

    /** Transfer destination in the given world, falling back to its own spawn. */
    Location destination(World world) {
        return spawn == null ? world.getSpawnLocation() : spawn.toLocation(world);
    }

    /** Alias with formatting codes intact; callers render it through {@link Messages}. */
    String displayName() {
        return alias;
    }

    /** Convenience for permission checks that quote the world name. */
    String lowerName() {
        return name.toLowerCase(Locale.ROOT);
    }

    boolean bypasses(Player player, String bypassPermission) {
        return !bypassPermission.isEmpty() && Permissions.has(player, bypassPermission);
    }
}
