package tw.linsy.aelorn.worlds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.SpawnCategory;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.config.ConfigParse;

/**
 * Immutable index of configured worlds, built once per (re)load.
 *
 * Holds prebuilt views so no caller ever scans the world list or hits the YAML
 * config again: lookup by lowercase name, lookup by live world UUID, the
 * live/pending split used by the status command and transfer menu, and the
 * feature flags that let hot event handlers bail out after one field read.
 */
final class WorldRegistry {

    private static final WorldRegistry EMPTY = new WorldRegistry(
        List.of(), Map.of(), Map.of(), List.of(), List.of(), new Flags());

    private final List<WorldProfile> profiles;
    private final Map<String, WorldProfile> byName;
    private final Map<UUID, WorldProfile> byWorldId;
    private final List<WorldProfile> live;
    private final List<WorldProfile> pending;

    /** True when at least one world uses the feature; lets listeners exit immediately. */
    private final boolean anyItemCap;
    private final boolean anyPlayerRules;
    private final boolean anyVoidRule;
    private final boolean anyCommandRule;
    private final boolean anyPortalRule;
    private final boolean anyEntryRule;

    private WorldRegistry(List<WorldProfile> profiles, Map<String, WorldProfile> byName,
                          Map<UUID, WorldProfile> byWorldId, List<WorldProfile> live,
                          List<WorldProfile> pending, Flags flags) {
        this.profiles = profiles;
        this.byName = byName;
        this.byWorldId = byWorldId;
        this.live = live;
        this.pending = pending;
        this.anyItemCap = flags.itemCap;
        this.anyPlayerRules = flags.playerRules;
        this.anyVoidRule = flags.voidRule;
        this.anyCommandRule = flags.commandRule;
        this.anyPortalRule = flags.portalRule;
        this.anyEntryRule = flags.entryRule;
    }

    /** Mutable during parsing, then frozen into the registry's final fields. */
    private static final class Flags {
        private boolean itemCap;
        private boolean playerRules;
        private boolean voidRule;
        private boolean commandRule;
        private boolean portalRule;
        private boolean entryRule;

        private void observe(WorldProfile profile) {
            itemCap |= profile.chunk().itemCapEnabled() && profile.chunk().maxItemsPerChunk() > 0;
            playerRules |= !profile.playerRules().isEmpty();
            voidRule |= profile.playerRules().voidRule().enabled();
            commandRule |= !profile.playerRules().commands().isOpen();
            portalRule |= !profile.portals().isOpen();
            entryRule |= !profile.entry().isOpen();
        }
    }

    private Flags flags() {
        Flags flags = new Flags();
        flags.itemCap = anyItemCap;
        flags.playerRules = anyPlayerRules;
        flags.voidRule = anyVoidRule;
        flags.commandRule = anyCommandRule;
        flags.portalRule = anyPortalRule;
        flags.entryRule = anyEntryRule;
        return flags;
    }

    static WorldRegistry empty() {
        return EMPTY;
    }

    /**
     * Parses the {@code worlds} section into profiles. Touches no server state, so
     * an async reload can do the YAML work off the main thread; call
     * {@link #refreshed()} afterwards to classify live vs pending worlds.
     */
    static WorldRegistry parse(@Nullable ConfigurationSection worlds, GlobalSettings globals, Logger logger) {
        if (worlds == null) {
            logger.warning("config.yml 缺少 worlds 區段；沒有任何世界會被管理。");
            return EMPTY;
        }

        List<WorldProfile> parsed = new ArrayList<>();
        Map<String, WorldProfile> byName = new LinkedHashMap<>();
        Flags flags = new Flags();
        for (String worldName : worlds.getKeys(false)) {
            ConfigurationSection section = worlds.getConfigurationSection(worldName);
            if (section == null) {
                continue;
            }
            WorldProfile profile = readProfile(worldName, section, globals, logger);
            if (byName.putIfAbsent(worldName.toLowerCase(Locale.ROOT), profile) != null) {
                logger.warning("worlds 區段中有重複的世界名稱（忽略大小寫）: " + worldName + "，已略過後者。");
                continue;
            }
            parsed.add(profile);
            flags.observe(profile);
        }

        return new WorldRegistry(List.copyOf(parsed), Map.copyOf(byName), Map.of(), List.of(), List.of(), flags);
    }

    /** Re-classifies live/pending against the current server state, reusing parsed profiles. */
    WorldRegistry refreshed() {
        if (profiles.isEmpty()) {
            return this;
        }
        Map<UUID, WorldProfile> byWorldId = new LinkedHashMap<>();
        List<WorldProfile> live = new ArrayList<>();
        List<WorldProfile> pending = new ArrayList<>();
        for (WorldProfile profile : profiles) {
            World world = Bukkit.getWorld(profile.name());
            if (world == null) {
                pending.add(profile);
            } else {
                live.add(profile);
                byWorldId.put(world.getUID(), profile);
            }
        }
        return new WorldRegistry(profiles, byName, Map.copyOf(byWorldId), List.copyOf(live),
            List.copyOf(pending), flags());
    }

    private static WorldProfile readProfile(String name, ConfigurationSection section,
                                            GlobalSettings globals, Logger logger) {
        World.Environment environment = ConfigParse.enumValue(World.Environment.class,
            section.getString("environment"), World.Environment.NORMAL, name + ".environment", logger);
        Difficulty difficulty = section.contains("difficulty")
            ? ConfigParse.enumValue(Difficulty.class, section.getString("difficulty"), Difficulty.EASY,
                name + ".difficulty", logger)
            : null;

        WorldProfile.SpawnPoint spawn = null;
        ConfigurationSection spawnSection = section.getConfigurationSection("spawn");
        if (spawnSection != null) {
            spawn = new WorldProfile.SpawnPoint(
                spawnSection.getDouble("x"), spawnSection.getDouble("y"), spawnSection.getDouble("z"),
                (float) spawnSection.getDouble("yaw", 0.0D), (float) spawnSection.getDouble("pitch", 0.0D));
        }

        return new WorldProfile(
            name,
            section.getString("alias", name),
            environment,
            difficulty,
            ConfigParse.booleanOrNull(section, "pvp"),
            spawn,
            readVisual(section.getConfigurationSection("visual"), name, logger),
            readBorder(section.getConfigurationSection("border"), name, logger),
            readChunk(section.getConfigurationSection("chunk"), globals),
            readSpawnSettings(section.getConfigurationSection("spawn-control"), name, logger),
            readEntryRules(section.getConfigurationSection("entry")),
            readPlayerRules(section.getConfigurationSection("rules"), name, logger),
            readPortalRules(section.getConfigurationSection("portals"), name, logger),
            readPurgeRules(section.getConfigurationSection("purge"), globals, logger),
            readArrival(section.getConfigurationSection("arrival")),
            readIcon(section.getString("icon"), environment, globals, name, logger),
            section.getBoolean("transferable", true),
            section.getInt("menu-slot", -1));
    }

    private static WorldProfile.SpawnSettings readSpawnSettings(@Nullable ConfigurationSection section,
                                                                String worldName, Logger logger) {
        if (section == null) {
            return WorldProfile.SpawnSettings.INHERIT;
        }
        Map<SpawnCategory, Integer> limits =
            readCategoryMap(section.getConfigurationSection("limits"), worldName, "limits", logger);
        Map<SpawnCategory, Integer> tickRates =
            readCategoryMap(section.getConfigurationSection("tick-rates"), worldName, "tick-rates", logger);
        return limits.isEmpty() && tickRates.isEmpty()
            ? WorldProfile.SpawnSettings.INHERIT
            : new WorldProfile.SpawnSettings(Map.copyOf(limits), Map.copyOf(tickRates));
    }

    private static Map<SpawnCategory, Integer> readCategoryMap(@Nullable ConfigurationSection section,
                                                               String worldName, String label, Logger logger) {
        if (section == null) {
            return Map.of();
        }
        Map<SpawnCategory, Integer> values = new EnumMap<>(SpawnCategory.class);
        for (String rawCategory : section.getKeys(false)) {
            SpawnCategory category = ConfigParse.enumOrNull(SpawnCategory.class, rawCategory,
                "worlds." + worldName + ".spawn-control." + label, logger);
            if (category == null) {
                continue;
            }
            int value = section.getInt(rawCategory, -1);
            if (value < 0) {
                logger.warning("世界 " + worldName + " 的 spawn-control." + label + "." + rawCategory
                    + " 需為 0 以上的整數，已略過。");
                continue;
            }
            values.put(category, value);
        }
        return values;
    }

    private static WorldProfile.EntryRules readEntryRules(@Nullable ConfigurationSection section) {
        if (section == null) {
            return WorldProfile.EntryRules.OPEN;
        }
        WorldProfile.EntryRules rules = new WorldProfile.EntryRules(
            section.getInt("player-limit", -1),
            ConfigParse.lowerCaseSet(section, "blocked-origins"),
            ConfigParse.lowerCaseSet(section, "allowed-origins"),
            ConfigParse.trimmedOrEmpty(section.getString("permission")));
        return rules.isOpen() ? WorldProfile.EntryRules.OPEN : rules;
    }

    private static WorldProfile.PlayerRules readPlayerRules(@Nullable ConfigurationSection section,
                                                            String worldName, Logger logger) {
        if (section == null) {
            return WorldProfile.PlayerRules.INHERIT;
        }
        String path = "worlds." + worldName + ".rules";
        WorldProfile.PlayerRules rules = new WorldProfile.PlayerRules(
            ConfigParse.enumOrNull(GameMode.class, section.getString("game-mode"), path + ".game-mode", logger),
            ConfigParse.booleanOrNull(section, "allow-flight"),
            readVoidRule(section.getConfigurationSection("void"), path, logger),
            readRespawnRule(section.getConfigurationSection("respawn")),
            readCommandRules(section.getConfigurationSection("commands")));
        return rules.isEmpty() ? WorldProfile.PlayerRules.INHERIT : rules;
    }

    private static WorldProfile.CommandRules readCommandRules(@Nullable ConfigurationSection section) {
        if (section == null) {
            return WorldProfile.CommandRules.OPEN;
        }
        Set<String> blocked = new LinkedHashSet<>();
        for (String raw : section.getStringList("blocked")) {
            if (raw != null && !raw.isBlank()) {
                blocked.add(normaliseCommand(raw));
            }
        }
        return blocked.isEmpty()
            ? WorldProfile.CommandRules.OPEN
            : new WorldProfile.CommandRules(Set.copyOf(blocked),
                ConfigParse.trimmedOrEmpty(section.getString("denied-message")));
    }

    /** Strips a leading slash, any {@code plugin:} namespace and case, both here and at match time. */
    static String normaliseCommand(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int namespace = trimmed.indexOf(':');
        return namespace >= 0 ? trimmed.substring(namespace + 1) : trimmed;
    }

    private static WorldProfile.PortalRules readPortalRules(@Nullable ConfigurationSection section,
                                                            String worldName, Logger logger) {
        if (section == null) {
            return WorldProfile.PortalRules.INHERIT;
        }
        Map<World.Environment, String> targets = new EnumMap<>(World.Environment.class);
        ConfigurationSection targetSection = section.getConfigurationSection("targets");
        if (targetSection != null) {
            for (String key : targetSection.getKeys(false)) {
                World.Environment environment = ConfigParse.enumOrNull(World.Environment.class, key,
                    "worlds." + worldName + ".portals.targets." + key, logger);
                String target = ConfigParse.trimmedOrEmpty(targetSection.getString(key));
                if (environment != null && !target.isEmpty()) {
                    targets.put(environment, target);
                }
            }
        }
        WorldProfile.PortalRules rules = new WorldProfile.PortalRules(
            section.getBoolean("disabled", false),
            Map.copyOf(targets),
            ConfigParse.trimmedOrEmpty(section.getString("denied-message")));
        return rules.isOpen() ? WorldProfile.PortalRules.INHERIT : rules;
    }

    private static WorldProfile.VoidRule readVoidRule(@Nullable ConfigurationSection section,
                                                      String path, Logger logger) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return WorldProfile.VoidRule.DISABLED;
        }
        Double belowY = ConfigParse.doubleOrNull(section, "below-y");
        return new WorldProfile.VoidRule(
            true,
            belowY == null ? Double.NaN : belowY,
            ConfigParse.enumValue(WorldProfile.VoidAction.class, section.getString("action"),
                WorldProfile.VoidAction.TELEPORT_SPAWN, path + ".void.action", logger),
            ConfigParse.trimmedOrEmpty(section.getString("target-world")),
            ConfigParse.trimmedOrEmpty(section.getString("message")));
    }

    private static WorldProfile.RespawnRule readRespawnRule(@Nullable ConfigurationSection section) {
        if (section == null) {
            return WorldProfile.RespawnRule.VANILLA;
        }
        WorldProfile.RespawnRule rule = new WorldProfile.RespawnRule(
            ConfigParse.trimmedOrEmpty(section.getString("world")),
            section.getBoolean("use-configured-spawn", false),
            section.getBoolean("override-bed", false));
        return rule.overrides() ? rule : WorldProfile.RespawnRule.VANILLA;
    }

    /**
     * A world with no {@code purge} section still follows {@code purge.auto} in
     * config.yml — the global switch is what an admin reaches for first, and it
     * would be surprising if it only applied to worlds that opted in.
     */
    private static WorldProfile.PurgeRules readPurgeRules(@Nullable ConfigurationSection section,
                                                          GlobalSettings globals, Logger logger) {
        GlobalSettings.PurgeOptions defaults = globals.purge();
        if (section == null) {
            return defaults.autoEnabled()
                ? new WorldProfile.PurgeRules(true, defaults.autoDefaultIntervalSeconds(),
                    defaults.defaultCategories(), 0, defaults.autoAnnounce())
                : WorldProfile.PurgeRules.INHERIT;
        }
        return new WorldProfile.PurgeRules(
            section.getBoolean("auto-enabled", defaults.autoEnabled()),
            ConfigParse.bounded(section.getInt("interval-seconds", defaults.autoDefaultIntervalSeconds()),
                30, 86_400),
            ConfigParse.enumSet(SpawnCategory.class, section, "categories",
                defaults.defaultCategories(), logger),
            Math.max(0, section.getInt("keep-per-chunk", 0)),
            section.getBoolean("announce", defaults.autoAnnounce()));
    }

    private static WorldProfile.Arrival readArrival(@Nullable ConfigurationSection section) {
        if (section == null) {
            return WorldProfile.Arrival.NONE;
        }
        WorldProfile.Arrival arrival = new WorldProfile.Arrival(
            ConfigParse.trimmedOrEmpty(section.getString("message")),
            ConfigParse.trimmedOrEmpty(section.getString("title")),
            ConfigParse.trimmedOrEmpty(section.getString("subtitle")),
            ConfigParse.trimmedOrEmpty(section.getString("actionbar")),
            ConfigParse.bounded(section.getInt("fade-in-ticks", 10), 0, 200),
            ConfigParse.bounded(section.getInt("stay-ticks", 40), 0, 1200),
            ConfigParse.bounded(section.getInt("fade-out-ticks", 10), 0, 200),
            ConfigParse.trimmedOrEmpty(section.getString("sound")),
            (float) ConfigParse.bounded(section.getDouble("sound-volume", 1.0D), 0.0D, 10.0D),
            (float) ConfigParse.bounded(section.getDouble("sound-pitch", 1.0D), 0.5D, 2.0D),
            ConfigParse.stringList(section, "commands"),
            section.getBoolean("commands-as-player", false),
            // Console-side arrival commands run as operator on a player-triggered
            // event; without a floor between runs, walking a portal is a payout loop.
            ConfigParse.bounded(section.getInt("command-cooldown-seconds", 5), 0, 86_400));
        return arrival.isEmpty() ? WorldProfile.Arrival.NONE : arrival;
    }

    private static WorldProfile.VisualSettings readVisual(@Nullable ConfigurationSection section,
                                                          String worldName, Logger logger) {
        if (section == null) {
            return WorldProfile.VisualSettings.INHERIT;
        }
        Map<String, String> gameRules = new LinkedHashMap<>();
        ConfigurationSection ruleSection = section.getConfigurationSection("game-rules");
        if (ruleSection != null) {
            for (String rule : ruleSection.getKeys(false)) {
                Object value = ruleSection.get(rule);
                if (value != null) {
                    gameRules.put(rule, String.valueOf(value));
                }
            }
        }
        WorldProfile.VisualSettings visual = new WorldProfile.VisualSettings(
            section.getInt("view-distance", -1),
            section.getInt("simulation-distance", -1),
            section.getInt("send-view-distance", -1),
            section.getLong("locked-time", -1L),
            ConfigParse.enumValue(WorldProfile.WeatherLock.class, section.getString("weather"),
                WorldProfile.WeatherLock.NONE, "worlds." + worldName + ".visual.weather", logger),
            ConfigParse.booleanOrNull(section, "auto-save"),
            section.getInt("spawn-chunk-radius", -1),
            Map.copyOf(gameRules));
        return visual.hasAnyOverride() ? visual : WorldProfile.VisualSettings.INHERIT;
    }

    private static WorldProfile.BorderSettings readBorder(@Nullable ConfigurationSection section,
                                                          String worldName, Logger logger) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return WorldProfile.BorderSettings.INHERIT;
        }
        double size = section.getDouble("size", -1.0D);
        if (size == 0.0D) {
            logger.warning("世界 " + worldName + " 的 border.size 不可為 0，已忽略此邊界設定。");
            return WorldProfile.BorderSettings.INHERIT;
        }
        WorldProfile.BorderSettings border = new WorldProfile.BorderSettings(
            true,
            size,
            ConfigParse.doubleOrNull(section, "center-x"),
            ConfigParse.doubleOrNull(section, "center-z"),
            Math.max(0L, section.getLong("transition-seconds", 0L)),
            section.getInt("warning-distance", -1),
            section.getInt("warning-time-seconds", -1),
            section.getDouble("damage-amount", -1.0D),
            section.getDouble("damage-buffer", -1.0D));
        return border.hasAnyOverride() ? border : WorldProfile.BorderSettings.INHERIT;
    }

    private static WorldProfile.ChunkSettings readChunk(@Nullable ConfigurationSection section,
                                                        GlobalSettings globals) {
        GlobalSettings.ChunkOptions defaults = globals.chunk();
        if (section == null) {
            return defaults.itemCapEnabled()
                ? new WorldProfile.ChunkSettings(true, defaults.itemCapMaxPerChunk())
                : WorldProfile.ChunkSettings.DISABLED;
        }
        boolean enabled = section.getBoolean("item-cap-enabled", defaults.itemCapEnabled());
        return enabled
            ? new WorldProfile.ChunkSettings(true,
                section.getInt("max-items-per-chunk", defaults.itemCapMaxPerChunk()))
            : WorldProfile.ChunkSettings.DISABLED;
    }

    private static Material readIcon(@Nullable String raw, World.Environment environment,
                                     GlobalSettings globals, String worldName, Logger logger) {
        Material material = ConfigParse.materialOrNull(raw, "worlds." + worldName + ".icon", logger);
        if (material != null && material.isItem()) {
            return material;
        }
        if (material != null) {
            logger.warning("世界 " + worldName + " 的 icon 不是可放進物品欄的物品: " + raw + "（改用預設圖示）");
        }
        return globals.iconFor(environment);
    }

    List<WorldProfile> profiles() {
        return profiles;
    }

    List<WorldProfile> live() {
        return live;
    }

    List<WorldProfile> pending() {
        return pending;
    }

    boolean anyItemCap() {
        return anyItemCap;
    }

    boolean anyPlayerRules() {
        return anyPlayerRules;
    }

    boolean anyVoidRule() {
        return anyVoidRule;
    }

    boolean anyCommandRule() {
        return anyCommandRule;
    }

    boolean anyPortalRule() {
        return anyPortalRule;
    }

    boolean anyEntryRule() {
        return anyEntryRule;
    }

    Optional<WorldProfile> byName(@Nullable String name) {
        return name == null ? Optional.empty()
            : Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
    }

    /** O(1) lookup used on the chunk and movement paths, which already hold a World. */
    @Nullable WorldProfile byWorld(@Nullable World world) {
        return world == null ? null : byWorldId.get(world.getUID());
    }

    List<String> names() {
        return profiles.stream().map(WorldProfile::name).toList();
    }

    /** Menu ordering; {@link GlobalSettings.Sort#CONFIG} preserves the config order. */
    List<WorldProfile> sorted(List<WorldProfile> input, GlobalSettings.Sort sort) {
        if (sort == GlobalSettings.Sort.CONFIG || input.size() < 2) {
            return input;
        }
        List<WorldProfile> copy = new ArrayList<>(input);
        copy.sort(comparator(sort));
        return copy;
    }

    private static Comparator<WorldProfile> comparator(GlobalSettings.Sort sort) {
        return switch (sort) {
            case ALIAS -> Comparator.comparing(WorldProfile::alias, String.CASE_INSENSITIVE_ORDER);
            case NAME -> Comparator.comparing(WorldProfile::name, String.CASE_INSENSITIVE_ORDER);
            case PLAYERS -> Comparator.comparingInt(WorldRegistry::onlineCount).reversed()
                .thenComparing(WorldProfile::name, String.CASE_INSENSITIVE_ORDER);
            case CONFIG -> Comparator.comparing(WorldProfile::name, String.CASE_INSENSITIVE_ORDER);
        };
    }

    private static int onlineCount(WorldProfile profile) {
        World world = Bukkit.getWorld(profile.name());
        return world == null ? -1 : world.getPlayers().size();
    }

    /** Profiles whose auto purge is switched on; empty means the scheduler stays idle. */
    List<WorldProfile> autoPurgeTargets() {
        List<WorldProfile> targets = new ArrayList<>();
        for (WorldProfile profile : live) {
            if (profile.purge().autoEnabled()) {
                targets.add(profile);
            }
        }
        return List.copyOf(targets);
    }

    Set<String> nameKeys() {
        return byName.keySet();
    }
}
