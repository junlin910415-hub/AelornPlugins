package tw.linsy.aelorn.plugins.config;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * {@code config.yml}, parsed once into an immutable snapshot.
 *
 * Nothing below this package reads YAML at runtime. That is not tidiness: the
 * previous version called {@code getConfig().getInt(...)} from inside the folder
 * watcher and from per-plugin loops, so a mid-operation reload could change the
 * rules halfway through a batch, and every list command paid for a map lookup per
 * row.
 *
 * <p>Grouped into nested records by <em>who asks</em>, not by YAML nesting: the
 * scanner never needs a guard setting, so it takes {@link Scanner} and cannot
 * accidentally depend on one.
 */
public record ManagerSettings(Guards guards, Scanner scanner, Audit audit, Display display) {

    /** What the manager refuses to do without being asked twice. */
    public record Guards(boolean requireConfirmation,
                         boolean allowUnload,
                         boolean protectSelf,
                         boolean blockHardDependents,
                         boolean warnSoftDependents,
                         boolean useTransitiveDependents,
                         boolean syncCommandTree,
                         Set<String> protectedPlugins) {

        public Guards {
            protectedPlugins = Set.copyOf(protectedPlugins);
        }
    }

    /** How the plugins folder is watched and what may be auto-loaded. */
    public record Scanner(boolean watchEnabled,
                          boolean useFileEvents,
                          long watchIntervalSeconds,
                          boolean autoLoadNewJars,
                          boolean autoLoadRegionSafeOnly,
                          long autoLoadStableSeconds,
                          int maxScanLines) {
    }

    /**
     * Where the audit trail goes and, for SQL, how it is batched.
     *
     * {@link Storage#FILE} is the default. See
     * {@link tw.linsy.aelorn.plugins.audit.AuditSink} for why: a trail whose
     * availability depends on a database being up is not one to rely on during the
     * incident being audited.
     */
    public record Audit(boolean enabled, int maxTailLines, Storage storage, Sql sql) {

        public enum Storage {
            FILE,
            SQL;

            static Storage parse(@Nullable String raw) {
                if (raw == null || raw.isBlank()) {
                    return FILE;
                }
                try {
                    return valueOf(raw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException unknown) {
                    return FILE;
                }
            }
        }

        /**
         * @param batchSize   records buffered before an insert is executed; also the
         *                    bound on how many a crash can lose
         * @param flushSeconds how often a partial batch is written anyway, so a quiet
         *                     server does not hold one record indefinitely
         */
        public record Sql(String url,
                          String driverClass,
                          String table,
                          String username,
                          String password,
                          int batchSize,
                          long flushSeconds,
                          long connectionTimeoutMillis) {
        }
    }

    public record Display(int pageSize, boolean prefixEveryLine) {
    }

    static ManagerSettings from(FileConfiguration config) {
        return new ManagerSettings(
            new Guards(
                config.getBoolean("require-confirmation", true),
                // Renamed: unloading no longer works by reflecting into Bukkit, so
                // the old key name described the implementation rather than the
                // permission. The old key is still honoured as the default so an
                // admin who turned it off stays turned off across the upgrade.
                config.getBoolean("allow-unload",
                    config.getBoolean("allow-reflective-unload", true)),
                config.getBoolean("protect-self", true),
                config.getBoolean("danger.block-disable-when-hard-dependents-enabled", true),
                config.getBoolean("danger.warn-soft-dependents", true),
                config.getBoolean("danger.use-transitive-dependents", true),
                config.getBoolean("danger.sync-command-tree", true),
                lowerCaseSet(config, "protected-plugins")),
            new Scanner(
                config.getBoolean("scanner.watch-enabled", true),
                config.getBoolean("scanner.use-file-events", true),
                bounded(config.getLong("scanner.watch-interval-seconds", 10L), 5L, 3600L),
                config.getBoolean("scanner.auto-load-new-jars", true),
                // Renamed from auto-load-folia-only: the check is "does the jar
                // declare it can run on a regionised server", which is the same
                // question on Luminol as on Folia and moot on Purpur.
                config.getBoolean("scanner.auto-load-region-safe-only",
                    config.getBoolean("scanner.auto-load-folia-only", true)),
                bounded(config.getLong("scanner.auto-load-stable-seconds", 3L), 1L, 600L),
                (int) bounded(config.getInt("scanner.max-scan-lines", 12), 1L, 200L)),
            new Audit(
                config.getBoolean("audit.enabled", true),
                (int) bounded(config.getInt("audit.max-tail-lines", 20), 1L, 500L),
                Audit.Storage.parse(config.getString("audit.storage")),
                new Audit.Sql(
                    trimmed(config.getString("audit.sql.url", "")),
                    trimmed(config.getString("audit.sql.driver-class", "")),
                    trimmed(config.getString("audit.sql.table", "pluginsmanager_audit")),
                    trimmed(config.getString("audit.sql.username", "")),
                    config.getString("audit.sql.password", ""),
                    (int) bounded(config.getInt("audit.sql.batch-size", 50), 1L, 5000L),
                    bounded(config.getLong("audit.sql.flush-seconds", 15L), 1L, 3600L),
                    bounded(config.getLong("audit.sql.connection-timeout-millis", 5000L),
                        250L, 60_000L))),
            new Display(
                (int) bounded(config.getInt("display.page-size", config.getInt("page-size", 12)), 5L, 100L),
                config.getBoolean("display.prefix-every-line", true)));
    }

    /**
     * Clamped rather than validated-and-rejected: a page size of zero is a typo,
     * and refusing to start over it helps nobody. The clamp is silent because the
     * bounds are documented in the shipped config.yml next to each key.
     */
    private static long bounded(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trimmed(@Nullable String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static Set<String> lowerCaseSet(ConfigurationSection section, String key) {
        Set<String> values = new LinkedHashSet<>();
        for (String entry : section.getStringList(key)) {
            if (entry != null && !entry.isBlank()) {
                values.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }
}
