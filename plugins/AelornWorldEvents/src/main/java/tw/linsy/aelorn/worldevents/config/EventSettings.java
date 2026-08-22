package tw.linsy.aelorn.worldevents.config;

import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import tw.linsy.aelorn.lib.config.ConfigParse;

/**
 * Immutable snapshot of the {@code settings} section.
 *
 * Bounds are applied rather than rejected: a value outside the sane range is
 * clamped and logged, so one bad number in config.yml cannot stop the plugin
 * from enabling. The previous version threw, which took the whole plugin down
 * over a typo.
 *
 * @param activeWindowMillis how long a node counts as "running" for the limits
 * @param startCommandTokens placeholders the start command must contain, from
 *        config; an empty list disables the check entirely
 */
public record EventSettings(
    boolean enabled,
    long checkIntervalTicks,
    double defaultActivationRadius,
    int maximumActiveGlobal,
    int maximumActivePerRegion,
    int minimumOnlinePlayers,
    double announceRadius,
    long activeWindowMillis,
    long dispatchFailureCooldownMillis,
    int spatialGridSize,
    String startCommand,
    List<String> startCommandTokens) {

    public static EventSettings from(ConfigurationSection config, Logger logger) {
        ConfigurationSection settings = ConfigParse.sectionOrEmpty(config, "settings");
        double radius = ConfigParse.bounded(settings.getDouble("activation-radius", 96.0D), 8.0D, 256.0D);
        return new EventSettings(
            settings.getBoolean("enabled", true),
            ConfigParse.bounded(settings.getLong("check-interval-ticks", 20L), 1L, 1200L),
            radius,
            ConfigParse.bounded(settings.getInt("maximum-active-global", 3), 1, 64),
            ConfigParse.bounded(settings.getInt("maximum-active-per-region", 1), 1, 64),
            ConfigParse.bounded(settings.getInt("minimum-online-players", 1), 1, 1000),
            // An announce radius smaller than the activation radius would mean the
            // player who triggered it never hears about it.
            Math.max(radius, ConfigParse.bounded(settings.getDouble("announce-radius", 1200.0D), 8.0D, 8192.0D)),
            ConfigParse.bounded(settings.getLong("active-window-seconds", 600L), 30L, 3600L) * 1000L,
            ConfigParse.bounded(settings.getLong("dispatch-failure-cooldown-seconds", 60L), 5L, 3600L) * 1000L,
            // Spatial bucket edge in blocks. Should stay comfortably above the
            // largest activation radius: lookup only scans the 3x3 neighbourhood.
            ConfigParse.bounded(settings.getInt("spatial-grid-size", 256), 64, 4096),
            settings.getString("start-command", ""),
            // Absent key keeps the original six-token check rather than silently
            // dropping it; an explicit empty list in config.yml turns it off.
            settings.contains("start-command-tokens")
                ? ConfigParse.stringList(settings, "start-command-tokens")
                : DEFAULT_START_COMMAND_TOKENS);
    }

    /** Mirrors the shipped config.yml; overridable there like every other default. */
    private static final List<String> DEFAULT_START_COMMAND_TOKENS =
        List.of("{encounter}", "{world}", "{x}", "{y}", "{z}", "{level}");

    /** Movement throttle in wall-clock terms, which is what the listener compares against. */
    public long checkIntervalMillis() {
        return Math.max(50L, checkIntervalTicks * 50L);
    }
}
