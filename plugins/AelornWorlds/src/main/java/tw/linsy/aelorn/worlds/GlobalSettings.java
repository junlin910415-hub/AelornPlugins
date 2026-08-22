package tw.linsy.aelorn.worlds;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.config.ConfigParse;
import tw.linsy.aelorn.lib.text.Text;

/**
 * Immutable snapshot of everything outside the {@code worlds} section.
 *
 * Values are read once per (re)load into nested option records, so the hot paths
 * — chunk events, movement checks, menu clicks — never touch YAML. Every section
 * is optional; the fallbacks below mirror the shipped config.yml exactly, so a
 * trimmed-down file behaves identically to the documented one.
 */
record GlobalSettings(
    int configVersion,
    Text.Format textFormat,
    boolean attemptDynamicLoad,
    Apply apply,
    ChunkOptions chunk,
    TransferOptions transfer,
    MenuOptions menu,
    RuleOptions rules,
    PurgeOptions purge,
    HealthOptions health,
    Map<World.Environment, Material> defaultIcons) {

    /** Config schema the code expects; a lower value in the file triggers a warning. */
    static final int EXPECTED_CONFIG_VERSION = 5;

    private static final int INVENTORY_ROW = 9;

    /** Which declared world properties are actually pushed onto the live world. */
    record Apply(boolean difficulty, boolean pvp, boolean spawnPoint, boolean visual,
                 boolean border, boolean spawnChunkRadius) {
    }

    /**
     * @param churnWarnThreshold redundant loads per window — every load of a chunk
     *                           after its first in the same window. Loading a chunk
     *                           once costs what it should; loading it five times is
     *                           the symptom worth a warning.
     * @param churnStartupGraceSeconds quiet period after boot or a world load, during
     *                           which the one-off replay of an area is not reported
     * @param itemCapRescanSeconds minimum gap between full entity scans of one chunk
     */
    record ChunkOptions(boolean metricsEnabled, int maxTrackedChunks,
                        int churnWindowSeconds, int churnWarnThreshold, int churnWarnCooldownSeconds,
                        int churnStartupGraceSeconds,
                        boolean itemCapEnabled, int itemCapMaxPerChunk, int itemCapRescanSeconds,
                        boolean protectNamed, boolean protectCustomData) {

        long churnWindowMillis() {
            return churnWindowSeconds * 1000L;
        }

        long churnWarnCooldownMillis() {
            return churnWarnCooldownSeconds * 1000L;
        }

        long churnStartupGraceMillis() {
            return churnStartupGraceSeconds * 1000L;
        }

        long itemCapRescanCooldownMillis() {
            return itemCapRescanSeconds * 1000L;
        }
    }

    record TransferOptions(boolean enabled, boolean preloadChunks, int cooldownSeconds,
                           boolean requirePerWorldPermission, String permissionPrefix,
                           SafeLandingOptions safeLanding) {

        long cooldownMillis() {
            return cooldownSeconds * 1000L;
        }
    }

    /**
     * Which blocks make a landing spot unusable. Kept as data rather than a code
     * constant so a server with custom hazards can extend the list.
     */
    record SafeLandingOptions(boolean enabled, int horizontalRadius, int verticalRadius,
                              Set<Material> unsafeBody, Set<Material> unsafeFloor,
                              boolean deepWaterUnsafe) {
    }

    record MenuOptions(String title, int rows, boolean hideUnloaded, Sort sort,
                       Material unloadedIcon, List<Integer> contentSlots,
                       boolean fillerEnabled, Material fillerMaterial,
                       ButtonOptions previousPage, ButtonOptions close, ButtonOptions nextPage) {

        int size() {
            return rows * INVENTORY_ROW;
        }
    }

    /** A navigation button; {@code slot < 0} removes it from the menu entirely. */
    record ButtonOptions(int slot, Material material) {

        boolean enabled() {
            return slot >= 0;
        }
    }

    /** Ordering of worlds inside the transfer menu. */
    enum Sort {
        /** Keep the order the worlds appear in config.yml. */
        CONFIG,
        ALIAS,
        NAME,
        /** Busiest world first. */
        PLAYERS
    }

    /**
     * Per-world player rules (game mode, flight, void handling, respawn routing).
     *
     * @param defaultGameMode applied when a player enters a world that declares no
     *                        game mode, or {@code null} to leave their mode alone
     */
    record RuleOptions(boolean enabled, String bypassPermission, @Nullable GameMode defaultGameMode,
                       boolean voidProtectionEnabled, boolean enforceEntryOnTeleport,
                       boolean enforceEntryOnJoin, String entryFallbackWorld,
                       Set<PlayerTeleportEvent.TeleportCause> entryExemptCauses,
                       String commandBypassPermission) {

        boolean exempt(PlayerTeleportEvent.TeleportCause cause) {
            return entryExemptCauses.contains(cause);
        }
    }

    record PurgeOptions(Set<SpawnCategory> defaultCategories, ProtectOptions protect,
                        boolean autoEnabled, int autoCheckSeconds, int autoDefaultIntervalSeconds,
                        boolean autoAnnounce) {
    }

    /** What an entity purge must never touch. */
    record ProtectOptions(boolean named, boolean persistentData, boolean scoreboardTags,
                          boolean tamed, boolean leashed, Set<EntityType> entityTypes) {
    }

    /**
     * Thresholds above which {@code /aw health} flags a world.
     *
     * @param censusCooldownSeconds minimum gap between censuses from one sender; the
     *                              scan costs one task per loaded chunk, so back-to-back
     *                              runs queue up behind each other on the region threads
     */
    record HealthOptions(int warnEntities, int warnTileEntities, int warnChunks, int warnItems,
                         int censusCooldownSeconds) {
    }

    /** All-defaults snapshot, used before config.yml has been read. */
    static GlobalSettings defaults() {
        return from(new MemoryConfiguration(), Logger.getLogger("WorldLoaderX"));
    }

    static GlobalSettings from(ConfigurationSection config, Logger logger) {
        int rows = ConfigParse.bounded(config.getInt("transfer.menu.rows", 6), 1, 6);
        return new GlobalSettings(
            config.getInt("config-version", 0),
            Text.Format.parse(config.getString("text-format")),
            config.getBoolean("attempt-dynamic-load", false),
            readApply(config),
            readChunk(config),
            readTransfer(config, logger),
            readMenu(section(config, "transfer.menu"), rows, logger),
            readRules(config, logger),
            readPurge(section(config, "purge"), logger),
            readHealth(config),
            readDefaultIcons(section(config, "default-icons"), logger));
    }

    private static Apply readApply(ConfigurationSection config) {
        return new Apply(
            config.getBoolean("apply-world-settings.difficulty", true),
            config.getBoolean("apply-world-settings.pvp", true),
            // Writing the world spawn mutates level.dat, so it stays opt-in.
            config.getBoolean("apply-world-settings.spawn-point", false),
            config.getBoolean("visual.enabled", true),
            config.getBoolean("apply-world-settings.border", true),
            config.getBoolean("apply-world-settings.spawn-chunk-radius", true));
    }

    private static ChunkOptions readChunk(ConfigurationSection config) {
        return new ChunkOptions(
            config.getBoolean("chunk.metrics-enabled", true),
            ConfigParse.bounded(config.getInt("chunk.max-tracked-chunks", 20_000), 256, 1_000_000),
            ConfigParse.bounded(config.getInt("chunk.churn.window-seconds", 10), 1, 600),
            // Counts redundant loads, not loads. See ChunkOptions for why the old
            // load-count threshold fired on ordinary joins and pre-generation.
            ConfigParse.bounded(config.getInt("chunk.churn.warn-threshold", 200), 10, 1_000_000),
            ConfigParse.bounded(config.getInt("chunk.churn.warn-cooldown-seconds", 300), 10, 3600),
            ConfigParse.bounded(config.getInt("chunk.churn.startup-grace-seconds", 90), 0, 3600),
            config.getBoolean("chunk.item-cap.enabled", false),
            ConfigParse.bounded(config.getInt("chunk.item-cap.max-per-chunk", 96), 8, 4096),
            ConfigParse.bounded(config.getInt("chunk.item-cap.rescan-cooldown-seconds", 5), 0, 300),
            config.getBoolean("chunk.item-cap.protect-named-items", true),
            config.getBoolean("chunk.item-cap.protect-custom-data-items", true));
    }

    private static TransferOptions readTransfer(ConfigurationSection config, Logger logger) {
        ConfigurationSection landing = section(config, "transfer.safe-landing");
        return new TransferOptions(
            config.getBoolean("transfer.enabled", true),
            config.getBoolean("transfer.preload-chunks", true),
            ConfigParse.bounded(config.getInt("transfer.cooldown-seconds", 3), 0, 3600),
            config.getBoolean("transfer.require-per-world-permission", false),
            config.getString("transfer.permission-prefix", Permissions.WORLD_PREFIX),
            new SafeLandingOptions(
                landing.getBoolean("enabled", true),
                // A wide search means many block reads on the destination region.
                ConfigParse.bounded(landing.getInt("horizontal-radius", 8), 0, 32),
                ConfigParse.bounded(landing.getInt("vertical-radius", 4), 0, 32),
                ConfigParse.materialSet(landing, "unsafe-body-blocks", DEFAULT_UNSAFE_BODY, logger),
                ConfigParse.materialSet(landing, "unsafe-floor-blocks", DEFAULT_UNSAFE_FLOOR, logger),
                landing.getBoolean("treat-deep-water-as-unsafe", true)));
    }

    private static MenuOptions readMenu(ConfigurationSection menu, int rows, Logger logger) {
        int size = rows * INVENTORY_ROW;
        int navRow = (rows - 1) * INVENTORY_ROW;
        ConfigurationSection buttons = section(menu, "buttons");
        return new MenuOptions(
            menu.getString("title", "&5世界傳送 &8({page}/{pages})"),
            rows,
            menu.getBoolean("hide-unloaded", false),
            ConfigParse.enumValue(Sort.class, menu.getString("sort"), Sort.CONFIG, "transfer.menu.sort", logger),
            ConfigParse.material(menu.getString("unloaded-icon"), Material.GRAY_DYE,
                "transfer.menu.unloaded-icon", logger),
            ConfigParse.slotList(menu, "content-slots", size, logger),
            menu.getBoolean("filler.enabled", false),
            ConfigParse.material(menu.getString("filler.material"), Material.GRAY_STAINED_GLASS_PANE,
                "transfer.menu.filler.material", logger),
            readButton(section(buttons, "previous-page"), navRow, size, Material.ARROW, logger),
            readButton(section(buttons, "close"), navRow + 4, size, Material.BARRIER, logger),
            readButton(section(buttons, "next-page"), navRow + 8, size, Material.ARROW, logger));
    }

    /**
     * Button slots default to the last row so a smaller {@code rows} value still
     * produces a usable menu without the admin recomputing every slot number.
     */
    private static ButtonOptions readButton(ConfigurationSection button, int defaultSlot, int size,
                                            Material defaultMaterial, Logger logger) {
        int slot = button.getInt("slot", defaultSlot);
        if (slot >= size) {
            logger.warning("設定 " + button.getCurrentPath() + ".slot 的槽位 " + slot
                + " 超出介面範圍，已改用 " + defaultSlot + "。");
            slot = defaultSlot;
        }
        return new ButtonOptions(slot,
            ConfigParse.material(button.getString("material"), defaultMaterial,
                button.getCurrentPath() + ".material", logger));
    }

    private static RuleOptions readRules(ConfigurationSection config, Logger logger) {
        ConfigurationSection rules = section(config, "rules");
        String bypass = rules.getString("bypass-permission", Permissions.RULES_BYPASS);
        return new RuleOptions(
            rules.getBoolean("enabled", true),
            bypass,
            ConfigParse.enumOrNull(GameMode.class, rules.getString("default-game-mode"),
                "rules.default-game-mode", logger),
            rules.getBoolean("void-protection", true),
            rules.getBoolean("enforce-entry-on-teleport", true),
            // A player who logs out inside a restricted world otherwise walks back in
            // on their next login without any rule being consulted.
            rules.getBoolean("enforce-entry-on-join", true),
            ConfigParse.trimmedOrEmpty(rules.getString("entry-fallback-world")),
            ConfigParse.enumSet(PlayerTeleportEvent.TeleportCause.class, rules, "entry-exempt-causes",
                Set.of(), logger),
            // Blank falls back to the general rules bypass rather than to "nobody".
            firstNonBlank(rules.getString("command-bypass-permission"), bypass));
    }

    private static String firstNonBlank(@Nullable String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    private static PurgeOptions readPurge(ConfigurationSection purge, Logger logger) {
        ConfigurationSection protect = section(purge, "protect");
        return new PurgeOptions(
            ConfigParse.enumSet(SpawnCategory.class, purge, "default-categories",
                DEFAULT_PURGE_CATEGORIES, logger),
            new ProtectOptions(
                protect.getBoolean("named", true),
                protect.getBoolean("persistent-data", true),
                protect.getBoolean("scoreboard-tags", true),
                protect.getBoolean("tamed", true),
                protect.getBoolean("leashed", true),
                ConfigParse.enumSet(EntityType.class, protect, "entity-types",
                    DEFAULT_PROTECTED_TYPES, logger)),
            purge.getBoolean("auto.enabled", false),
            ConfigParse.bounded(purge.getInt("auto.check-seconds", 30), 5, 3600),
            ConfigParse.bounded(purge.getInt("auto.default-interval-seconds", 600), 30, 86_400),
            purge.getBoolean("auto.announce", false));
    }

    private static HealthOptions readHealth(ConfigurationSection config) {
        return new HealthOptions(
            Math.max(0, config.getInt("health.warn-entities", 3000)),
            Math.max(0, config.getInt("health.warn-tile-entities", 4000)),
            Math.max(0, config.getInt("health.warn-chunks", 2000)),
            Math.max(0, config.getInt("health.warn-items", 400)),
            ConfigParse.bounded(config.getInt("health.census-cooldown-seconds", 10), 0, 3600));
    }

    /**
     * Fallback transfer icon per environment. Previously a hardcoded switch; now a
     * map so a server that renames or reskins dimensions can pick its own.
     */
    private static Map<World.Environment, Material> readDefaultIcons(ConfigurationSection icons, Logger logger) {
        Map<World.Environment, Material> resolved = new EnumMap<>(World.Environment.class);
        resolved.putAll(BUILTIN_ICONS);
        for (String key : icons.getKeys(false)) {
            World.Environment environment =
                ConfigParse.enumOrNull(World.Environment.class, key, "default-icons." + key, logger);
            if (environment == null) {
                continue;
            }
            Material material = ConfigParse.materialOrNull(icons.getString(key), "default-icons." + key, logger);
            if (material != null) {
                resolved.put(environment, material);
            }
        }
        return Map.copyOf(resolved);
    }

    Material iconFor(World.Environment environment) {
        return defaultIcons.getOrDefault(environment, Material.GRASS_BLOCK);
    }

    /** Never returns {@code null}, so readers can treat a missing section as "all defaults". */
    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection existing = parent.getConfigurationSection(path);
        if (existing != null) {
            return existing;
        }
        // A detached section keeps getCurrentPath() usable in warnings.
        return new MemoryConfiguration().createSection(path);
    }

    private static final Map<World.Environment, Material> BUILTIN_ICONS = Map.of(
        World.Environment.NORMAL, Material.GRASS_BLOCK,
        World.Environment.NETHER, Material.NETHERRACK,
        World.Environment.THE_END, Material.END_STONE,
        World.Environment.CUSTOM, Material.STONE);

    private static final Set<Material> DEFAULT_UNSAFE_BODY =
        Set.of(Material.FIRE, Material.SOUL_FIRE, Material.LAVA, Material.POWDER_SNOW);

    private static final Set<Material> DEFAULT_UNSAFE_FLOOR =
        Set.of(Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE);

    private static final Set<SpawnCategory> DEFAULT_PURGE_CATEGORIES = Set.of(
        SpawnCategory.MONSTER, SpawnCategory.ANIMAL, SpawnCategory.WATER_ANIMAL,
        SpawnCategory.WATER_AMBIENT, SpawnCategory.WATER_UNDERGROUND_CREATURE,
        SpawnCategory.AMBIENT, SpawnCategory.AXOLOTL);

    private static final Set<EntityType> DEFAULT_PROTECTED_TYPES = Set.of(
        EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.IRON_GOLEM,
        EntityType.SNOW_GOLEM, EntityType.ARMOR_STAND);
}
