package com.xuzhihuanjing.rpgbridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;

/**
 * Reads MythicCore/AelornItems data from item stacks.
 *
 * Two tiers: {@link #hasMythicData(ItemStack)} touches only the item id and is
 * cheap enough for per-hit event paths; {@link #inspect(ItemStack)} reads the
 * full stat map and should stay off hot paths (commands, throttled sampling).
 */
final class MythicItemInspector {

    private final MythicCoreApi api;
    private final Logger logger;

    MythicItemInspector(MythicCoreApi api, Logger logger) {
        this.api = api;
        this.logger = logger;
    }

    /** Cheap presence probe for hot event paths: a single id/type read, no stat map. */
    boolean hasMythicData(@Nullable ItemStack item) {
        if (!inspectable(item)) {
            return false;
        }
        try {
            return blankToNull(api.readItemId(item)) != null || blankToNull(api.readItemType(item)) != null;
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to probe MythicCore item data.", exception);
            return false;
        }
    }

    ItemInspection inspect(@Nullable ItemStack item) {
        if (!inspectable(item)) {
            return ItemInspection.empty();
        }
        try {
            String id = blankToNull(api.readItemId(item));
            String type = blankToNull(api.readItemType(item));
            String tier = blankToNull(api.readItemTag(item, "tier"));
            int level = Math.max(0, api.readItemLevel(item));
            Map<String, Double> stats = sanitizeStats(api.readItemStats(item));
            return id == null && type == null && stats.isEmpty()
                ? ItemInspection.empty()
                : new ItemInspection(id, type, tier, level, stats);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to inspect MythicCore item data.", exception);
            return ItemInspection.empty();
        }
    }

    private static boolean inspectable(@Nullable ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta();
    }

    private static Map<String, Double> sanitizeStats(@Nullable Map<String, Double> stats) {
        if (stats == null || stats.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> clean = new LinkedHashMap<>();
        stats.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && Double.isFinite(value)) {
                clean.put(key.toLowerCase(Locale.ROOT), value);
            }
        });
        return Map.copyOf(clean);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    record ItemInspection(@Nullable String id, @Nullable String type, @Nullable String tier,
                          int level, Map<String, Double> stats) {

        private static final ItemInspection EMPTY = new ItemInspection(null, null, null, 0, Map.of());

        static ItemInspection empty() {
            return EMPTY;
        }

        boolean present() {
            return id != null || type != null || !stats.isEmpty();
        }

        String summary() {
            List<String> parts = new ArrayList<>(5);
            if (id != null) {
                parts.add(id);
            }
            if (type != null) {
                parts.add("type=" + type);
            }
            if (tier != null) {
                parts.add("tier=" + tier);
            }
            if (level > 0) {
                parts.add("level=" + level);
            }
            parts.add("stats=" + stats.size());
            return String.join(", ", parts);
        }
    }
}
