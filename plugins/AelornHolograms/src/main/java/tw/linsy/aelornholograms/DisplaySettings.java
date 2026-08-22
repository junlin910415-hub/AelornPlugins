package tw.linsy.aelornholograms;

import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;

/**
 * Immutable snapshot of config.yml display defaults.
 *
 * Rebuilt on enable and /vh reload so the per-tick render path never touches
 * the YAML configuration (MemorySection path lookups) again.
 */
record DisplaySettings(
    int displayRange,
    int updateRange,
    int updateInterval,
    int minUpdateInterval,
    double lineHeight,
    String defaultText,
    int lineWidth,
    boolean shadowed,
    boolean seeThrough,
    byte textOpacity,
    Color backgroundColor,
    Display.Billboard billboard,
    boolean miniMessageEnabled,
    String messagePrefix) {

    static DisplaySettings from(FileConfiguration config) {
        int minUpdateInterval = Math.max(5, config.getInt("performance.min-update-interval", 10));
        return new DisplaySettings(
            Math.max(8, config.getInt("defaults.display-range", 48)),
            Math.max(8, config.getInt("defaults.update-range", 48)),
            Math.max(minUpdateInterval, config.getInt("defaults.update-interval", 20)),
            minUpdateInterval,
            Math.max(0.05, config.getDouble("defaults.line-height", 0.3)),
            config.getString("defaults.text", "Blank Line"),
            config.getInt("defaults.line-width", 240),
            config.getBoolean("defaults.shadowed", true),
            config.getBoolean("defaults.see-through", false),
            (byte) clamp(config.getInt("defaults.text-opacity", 255), 0, 255),
            Color.fromARGB(clamp(config.getInt("defaults.background-alpha", 64), 0, 255), 0, 0, 0),
            readBillboard(config.getString("defaults.billboard", "CENTER")),
            config.getBoolean("compatibility.minimessage", true),
            config.getString("messages.prefix", ""));
    }

    private static Display.Billboard readBillboard(String raw) {
        try {
            return Display.Billboard.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return Display.Billboard.CENTER;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
