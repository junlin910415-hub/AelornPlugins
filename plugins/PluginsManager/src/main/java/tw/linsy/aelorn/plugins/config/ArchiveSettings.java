package tw.linsy.aelorn.plugins.config;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * {@code version-control.yml}, parsed once.
 *
 * The timestamp pattern is compiled here rather than at every archive, which also
 * means a malformed pattern is caught at load with a named fallback instead of
 * throwing from inside a file copy.
 */
public record ArchiveSettings(boolean enabled,
                              String folderName,
                              int maxPerPlugin,
                              boolean archiveBeforeLoad,
                              boolean restoreRequiresUnloaded,
                              DateTimeFormatter timestamp,
                              String timestampPattern) {

    private static final String DEFAULT_PATTERN = "yyyyMMdd-HHmmss";

    static ArchiveSettings from(FileConfiguration file, java.util.logging.Logger logger) {
        String pattern = file.getString("version-control.timestamp-pattern", DEFAULT_PATTERN);
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        } catch (IllegalArgumentException malformed) {
            logger.warning("version-control.timestamp-pattern 無法解析（" + pattern
                + "），改用 " + DEFAULT_PATTERN + "。");
            pattern = DEFAULT_PATTERN;
            formatter = DateTimeFormatter.ofPattern(DEFAULT_PATTERN, Locale.ROOT);
        }
        return new ArchiveSettings(
            file.getBoolean("version-control.enabled", true),
            // Sanitised the same way archive names are: this becomes a directory
            // under the data folder, and a path separator here would escape it.
            safeFolder(file.getString("version-control.archive-folder", "VC")),
            Math.max(1, Math.min(200, file.getInt("version-control.max-archives-per-plugin", 8))),
            file.getBoolean("version-control.archive-before-manual-load", false),
            file.getBoolean("version-control.restore-requires-plugin-unloaded", true),
            formatter,
            pattern);
    }

    private static String safeFolder(String raw) {
        String trimmed = raw == null || raw.isBlank() ? "VC" : raw.trim();
        String cleaned = trimmed.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..") ? "VC" : cleaned;
    }
}
