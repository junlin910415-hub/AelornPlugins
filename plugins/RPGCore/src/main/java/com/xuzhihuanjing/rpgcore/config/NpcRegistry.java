package com.xuzhihuanjing.rpgcore.config;

import com.xuzhihuanjing.rpgcore.domain.npc.NpcDefinition;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** 讀取 npcs.yml;與其他 registry 一樣「全部驗證通過才套用」。 */
public final class NpcRegistry {

    private static final int SCHEMA_VERSION = 1;

    private volatile Map<String, NpcDefinition> npcs = Map.of();

    public void load(File file, QuestRegistry quests) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getInt("schema-version", -1) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported npcs.yml schema-version (expected "
                + SCHEMA_VERSION + ")");
        }

        ConfigurationSection root = yaml.getConfigurationSection("npcs");
        if (root == null || root.getKeys(false).isEmpty()) {
            this.npcs = Map.of();
            return;
        }

        Map<String, NpcDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                errors.add("Invalid npc section: " + id);
                continue;
            }
            NpcDefinition definition = readDefinition(id, section, quests, errors);
            if (definition == null) {
                continue;
            }
            if (loaded.putIfAbsent(definition.key(), definition) != null) {
                errors.add("Duplicate npc name: " + definition.key());
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
        }
        this.npcs = Collections.unmodifiableMap(loaded);
    }

    private NpcDefinition readDefinition(String id, ConfigurationSection section,
                                         QuestRegistry quests, List<String> errors) {
        String citizensName = section.getString("citizens-name", "");
        if (citizensName == null || citizensName.isBlank()) {
            errors.add("NPC " + id + " is missing citizens-name");
            return null;
        }

        NpcDefinition.NpcRole role;
        try {
            role = NpcDefinition.NpcRole.valueOf(section.getString("role", "AMBIENT").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            errors.add("NPC " + id + " has an invalid role");
            return null;
        }

        List<String> questIds = section.getStringList("quests");
        for (String questId : questIds) {
            if (quests.find(questId).isEmpty()) {
                errors.add("NPC " + id + " references unknown quest " + questId);
            }
        }

        return new NpcDefinition(
            citizensName.trim().toLowerCase(Locale.ROOT),
            section.getString("display-name", citizensName),
            role,
            questIds,
            readBehavior(id, section.getConfigurationSection("behavior"), errors));
    }

    private NpcDefinition.NpcBehavior readBehavior(String id, ConfigurationSection section, List<String> errors) {
        if (section == null) {
            return NpcDefinition.NpcBehavior.stationary();
        }
        NpcDefinition.BehaviorType type;
        try {
            type = NpcDefinition.BehaviorType.valueOf(
                section.getString("type", "STATIONARY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            errors.add("NPC " + id + " has an invalid behavior type");
            return NpcDefinition.NpcBehavior.stationary();
        }

        List<NpcDefinition.Waypoint> waypoints = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("waypoints")) {
            NpcDefinition.Waypoint waypoint = readWaypoint(raw);
            if (waypoint == null) {
                errors.add("NPC " + id + " has an invalid waypoint");
            } else {
                waypoints.add(waypoint);
            }
        }
        if (type == NpcDefinition.BehaviorType.PATROL && waypoints.size() < 2) {
            errors.add("NPC " + id + " uses PATROL but has fewer than two waypoints");
        }

        NpcDefinition.Waypoint escortTarget = null;
        ConfigurationSection escortSection = section.getConfigurationSection("escort-target");
        if (escortSection != null) {
            escortTarget = new NpcDefinition.Waypoint(
                escortSection.getString("world", ""),
                escortSection.getDouble("x"),
                escortSection.getDouble("y"),
                escortSection.getDouble("z"));
        }
        if (type == NpcDefinition.BehaviorType.ESCORT && escortTarget == null) {
            errors.add("NPC " + id + " uses ESCORT but has no escort-target");
        }

        return new NpcDefinition.NpcBehavior(
            type,
            section.getDouble("speed", 1.0D),
            waypoints,
            section.getDouble("follow-range", 6.0D),
            section.getDouble("target-range", 10.0D),
            escortTarget,
            section.getDouble("wander-radius", 8.0D),
            section.getInt("idle-ticks", 100));
    }

    private NpcDefinition.Waypoint readWaypoint(Map<?, ?> raw) {
        Object world = raw.get("world");
        if (world == null) {
            return null;
        }
        if (!(raw.get("x") instanceof Number x)
            || !(raw.get("y") instanceof Number y)
            || !(raw.get("z") instanceof Number z)) {
            return null;
        }
        return new NpcDefinition.Waypoint(String.valueOf(world), x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    /** key 為正規化後的 NPC 名稱。 */
    public Optional<NpcDefinition> find(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(npcs.get(key.toLowerCase(Locale.ROOT)));
    }

    public Collection<NpcDefinition> all() {
        return npcs.values();
    }

    public int size() {
        return npcs.size();
    }
}
