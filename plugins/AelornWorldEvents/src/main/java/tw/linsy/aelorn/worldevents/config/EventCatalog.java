package tw.linsy.aelorn.worldevents.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import tw.linsy.aelorn.lib.config.ConfigParse;
import tw.linsy.aelorn.worldevents.model.EventNode;

/**
 * Immutable index of configured event nodes, built once per (re)load.
 *
 * Carries a coarse spatial grid so the proximity check — which runs on player
 * movement — only ever looks at the nodes in the 3x3 buckets around the player
 * instead of scanning every node on the server.
 *
 * <p>A malformed node is logged with its id and skipped; the rest still load.
 * The previous version aborted the whole catalog on the first bad entry, which
 * meant one typo disabled every event on the server.
 */
public final class EventCatalog {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern WORLD_NAME = Pattern.compile("[A-Za-z0-9_.-]+");

    private static final EventCatalog EMPTY =
        new EventCatalog(EventSettings.from(new org.bukkit.configuration.MemoryConfiguration(),
            Logger.getLogger("AelornWorldEvents")), Map.of(), Map.of());

    private final EventSettings settings;
    private final Map<String, EventNode> byId;
    private final Map<GridKey, List<EventNode>> spatialIndex;

    private EventCatalog(EventSettings settings, Map<String, EventNode> byId,
                         Map<GridKey, List<EventNode>> spatialIndex) {
        this.settings = settings;
        this.byId = byId;
        this.spatialIndex = spatialIndex;
    }

    public static EventCatalog empty() {
        return EMPTY;
    }

    /** Pure parsing — no server state touched, so a reload can do this off the main thread. */
    public static EventCatalog load(ConfigurationSection config, Logger logger) {
        EventSettings settings = EventSettings.from(config, logger);
        ConfigurationSection root = config.getConfigurationSection("events");
        if (root == null || root.getKeys(false).isEmpty()) {
            logger.warning("config.yml 沒有定義任何 events;世界事件不會觸發。");
            return new EventCatalog(settings, Map.of(), Map.of());
        }

        Map<String, EventNode> byId = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                logger.warning("events." + id + " 不是有效的區段，已略過。");
                continue;
            }
            EventNode node = readNode(id, section, settings, logger);
            if (node == null) {
                continue;
            }
            if (byId.putIfAbsent(node.id(), node) != null) {
                logger.warning("重複的事件 id（忽略大小寫）: " + id + "，已略過後者。");
            }
        }

        Map<GridKey, List<EventNode>> index = new HashMap<>();
        for (EventNode node : byId.values()) {
            index.computeIfAbsent(keyOf(node.world(), node.x(), node.z(), settings.spatialGridSize()),
                ignored -> new ArrayList<>()).add(node);
        }
        Map<GridKey, List<EventNode>> frozen = new HashMap<>();
        index.forEach((key, value) -> frozen.put(key, List.copyOf(value)));

        return new EventCatalog(settings, Map.copyOf(byId), Map.copyOf(frozen));
    }

    private static EventNode readNode(String rawId, ConfigurationSection section,
                                      EventSettings settings, Logger logger) {
        String id = rawId.toLowerCase(Locale.ROOT);
        String path = "events." + rawId;
        List<String> problems = new ArrayList<>();

        if (!ID.matcher(id).matches()) {
            problems.add("id 只能用小寫英數、底線與連字號");
        }
        String region = ConfigParse.trimmedOrEmpty(section.getString("region"));
        String encounter = ConfigParse.trimmedOrEmpty(section.getString("encounter"));
        if (!ID.matcher(region).matches()) {
            problems.add("region 無效: " + region);
        }
        if (!ID.matcher(encounter).matches()) {
            problems.add("encounter 無效: " + encounter);
        }
        String world = ConfigParse.trimmedOrEmpty(section.getString("location.world"));
        if (!WORLD_NAME.matcher(world).matches()) {
            problems.add("location.world 無效: " + world);
        }

        double x = section.getDouble("location.x", Double.NaN);
        double y = section.getDouble("location.y", Double.NaN);
        double z = section.getDouble("location.z", Double.NaN);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            problems.add("location 座標必須是有限數值");
        }

        int level = section.getInt("level", -1);
        if (level < 1) {
            problems.add("level 必須是 1 以上的整數");
        }
        long cooldownMinutes = section.getLong("cooldown-minutes", -1L);
        if (cooldownMinutes < 1L) {
            problems.add("cooldown-minutes 必須是 1 以上");
        }

        if (!problems.isEmpty()) {
            logger.warning("略過事件 " + path + "：" + String.join("；", problems));
            return null;
        }
        return new EventNode(id,
            section.getString("display-name", id),
            region,
            encounter,
            world,
            x, y, z,
            level,
            cooldownMinutes * 60_000L,
            ConfigParse.bounded(section.getDouble("activation-radius", settings.defaultActivationRadius()),
                8.0D, 256.0D));
    }

    public EventSettings settings() {
        return settings;
    }

    public Collection<EventNode> nodes() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }

    /**
     * Nodes in the 3x3 grid neighbourhood of a position. The neighbourhood is
     * what makes a node near a bucket edge still reachable.
     */
    public List<EventNode> nearby(String world, double x, double z) {
        if (spatialIndex.isEmpty()) {
            return List.of();
        }
        int grid = settings.spatialGridSize();
        GridKey centre = keyOf(world, x, z, grid);
        List<EventNode> matches = new ArrayList<>();
        for (int deltaX = -1; deltaX <= 1; deltaX++) {
            for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
                matches.addAll(spatialIndex.getOrDefault(
                    new GridKey(world, centre.x() + deltaX, centre.z() + deltaZ), List.of()));
            }
        }
        return matches;
    }

    private static GridKey keyOf(String world, double x, double z, int gridSize) {
        return new GridKey(world,
            Math.floorDiv((int) Math.floor(x), gridSize),
            Math.floorDiv((int) Math.floor(z), gridSize));
    }

    private record GridKey(String world, int x, int z) {
    }
}
