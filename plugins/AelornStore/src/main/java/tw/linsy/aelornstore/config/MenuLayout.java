package tw.linsy.aelornstore.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.config.ConfigParse;

/**
 * One screen's layout, read from the {@code menu:} block of shop.yml.
 *
 * Slots are validated against the configured row count at load time, so a typo
 * produces a warning and a hidden button rather than an
 * {@code ArrayIndexOutOfBoundsException} in front of a paying customer.
 */
public record MenuLayout(
    int rows,
    @Nullable Material filler,
    List<Integer> contentSlots,
    Map<String, Integer> buttons
) {

    public MenuLayout {
        contentSlots = List.copyOf(contentSlots);
        buttons = Map.copyOf(buttons);
    }

    public int size() {
        return rows * 9;
    }

    /** The slot for a named button, or {@code -1} when the admin removed it. */
    public int button(String name) {
        Integer slot = buttons.get(name);
        return slot == null ? -1 : slot;
    }

    public static MenuLayout load(@Nullable ConfigurationSection section, int defaultRows,
                                  String path, Logger logger) {
        if (section == null) {
            logger.warning("設定 " + path + " 不存在，該介面將使用空白版面。");
            return new MenuLayout(defaultRows, null, List.of(), Map.of());
        }
        int rows = ConfigParse.rows(section, "rows", defaultRows, logger);
        int size = rows * 9;
        Material filler = ConfigParse.materialOrNull(section.getString("filler"), path + ".filler", logger);

        List<Integer> content = section.contains("content-slots")
            ? ConfigParse.slotList(section, "content-slots", size, logger)
            : List.of();

        Map<String, Integer> buttons = new LinkedHashMap<>();
        ConfigurationSection buttonSection = section.getConfigurationSection("buttons");
        if (buttonSection != null) {
            for (String key : buttonSection.getKeys(false)) {
                int slot = ConfigParse.slot(buttonSection, key, size, logger);
                if (slot >= 0) {
                    buttons.put(key, slot);
                }
            }
        }
        // Single-purpose slots on the confirm screen are named rather than listed.
        for (String key : List.of("item-slot", "yes-slot", "no-slot")) {
            if (section.contains(key)) {
                int slot = ConfigParse.slot(section, key, size, logger);
                if (slot >= 0) {
                    buttons.put(key, slot);
                }
            }
        }
        return new MenuLayout(rows, filler, content, buttons);
    }
}
