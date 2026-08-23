package com.xuzhihuanjing.rpgcore.config;

import com.xuzhihuanjing.rpgcore.combat.WeaponArchetype;
import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;

public record CombatCoreSettings(
        boolean enabled,
        Map<WeaponArchetype, AttackProfile> attacks,
        EffectSettings effects,
        VanillaContentSettings vanillaContent) {

    public CombatCoreSettings {
        attacks = Map.copyOf(attacks);
    }

    public static CombatCoreSettings from(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getInt("schema-version", -1) != 1) {
            throw new IllegalArgumentException("Unsupported combat-core.yml schema-version");
        }

        EnumMap<WeaponArchetype, AttackProfile> attacks = new EnumMap<>(WeaponArchetype.class);
        for (WeaponArchetype archetype : WeaponArchetype.values()) {
            String path = "weapon-combat.profiles." + archetype.configKey();
            ConfigurationSection section = requiredSection(yaml, path);
            AttackProfile profile = new AttackProfile(
                    bounded(section.getDouble("range"), 1.0, 32.0, path + ".range"),
                    bounded(section.getDouble("hit-radius"), 0.1, 2.0, path + ".hit-radius"),
                    boundedLong(section.getLong("cooldown-ms"), 100L, 3000L, path + ".cooldown-ms"),
                    bounded(section.getDouble("damage-multiplier"), 0.1, 3.0, path + ".damage-multiplier"),
                    boundedInt(section.getInt("maximum-targets"), 1, 8, path + ".maximum-targets"),
                    bounded(section.getDouble("arc-degrees"), 5.0, 180.0, path + ".arc-degrees"));
            attacks.put(archetype, profile);
        }

        EffectSettings effects = new EffectSettings(
                yaml.getBoolean("effects.enabled", true),
                boundedInt(yaml.getInt("effects.maximum-trail-points", 48), 4, 96, "effects.maximum-trail-points"),
                boundedInt(yaml.getInt("effects.maximum-impact-particles", 18), 4, 40, "effects.maximum-impact-particles"),
                bounded(yaml.getDouble("effects.sound-volume", 0.72), 0.0, 2.0, "effects.sound-volume"));

        Set<CreatureSpawnEvent.SpawnReason> spawnReasons = new LinkedHashSet<>();
        for (String value : yaml.getStringList("vanilla-content.allowed-spawn-reasons")) {
            try {
                spawnReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown vanilla-content spawn reason: " + value, exception);
            }
        }
        if (spawnReasons.isEmpty()) {
            throw new IllegalArgumentException("vanilla-content.allowed-spawn-reasons must not be empty");
        }

        Set<String> worlds = normalizedSet(yaml.getStringList("vanilla-content.worlds"));
        if (worlds.isEmpty()) {
            throw new IllegalArgumentException("vanilla-content.worlds must not be empty");
        }
        Set<String> namespaces = normalizedSet(yaml.getStringList("vanilla-content.allowed-item-namespaces"));
        if (namespaces.isEmpty()) {
            throw new IllegalArgumentException("vanilla-content.allowed-item-namespaces must not be empty");
        }

        VanillaContentSettings vanilla = new VanillaContentSettings(
                yaml.getBoolean("vanilla-content.enabled", true),
                worlds,
                namespaces,
                Set.copyOf(spawnReasons),
                yaml.getString("vanilla-content.bypass-permission", "rpgcore.vanilla.bypass"),
                yaml.getBoolean("vanilla-content.block-natural-creatures", true),
                yaml.getBoolean("vanilla-content.block-world-loot", true),
                yaml.getBoolean("vanilla-content.block-block-drops", true),
                yaml.getBoolean("vanilla-content.block-vanilla-recipes", true),
                yaml.getBoolean("vanilla-content.block-fishing-and-bartering", true),
                yaml.getBoolean("vanilla-content.strip-player-inventories", true),
                boundedLong(yaml.getLong("vanilla-content.inventory-scan-ticks", 20L), 5L, 1200L,
                        "vanilla-content.inventory-scan-ticks"));

        return new CombatCoreSettings(yaml.getBoolean("weapon-combat.enabled", true), attacks, effects, vanilla);
    }

    public AttackProfile attack(WeaponArchetype archetype) {
        AttackProfile profile = attacks.get(archetype);
        if (profile == null) {
            throw new IllegalArgumentException("Missing attack profile for " + archetype);
        }
        return profile;
    }

    private static ConfigurationSection requiredSection(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("Missing combat-core.yml section: " + path);
        }
        return section;
    }

    private static Set<String> normalizedSet(Iterable<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private static double bounded(double value, double minimum, double maximum, String path) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static int boundedInt(int value, int minimum, int maximum, String path) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static long boundedLong(long value, long minimum, long maximum, String path) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    public record AttackProfile(
            double range,
            double hitRadius,
            long cooldownMillis,
            double damageMultiplier,
            int maximumTargets,
            double arcDegrees) {
    }

    public record EffectSettings(
            boolean enabled,
            int maximumTrailPoints,
            int maximumImpactParticles,
            double soundVolume) {
    }

    public record VanillaContentSettings(
            boolean enabled,
            Set<String> worlds,
            Set<String> allowedItemNamespaces,
            Set<CreatureSpawnEvent.SpawnReason> allowedSpawnReasons,
            String bypassPermission,
            boolean blockNaturalCreatures,
            boolean blockWorldLoot,
            boolean blockBlockDrops,
            boolean blockVanillaRecipes,
            boolean blockFishingAndBartering,
            boolean stripPlayerInventories,
            long inventoryScanTicks) {

        public boolean appliesTo(String worldName) {
            String normalized = worldName.toLowerCase(Locale.ROOT);
            return worlds.contains("*") || worlds.contains(normalized);
        }
    }
}
