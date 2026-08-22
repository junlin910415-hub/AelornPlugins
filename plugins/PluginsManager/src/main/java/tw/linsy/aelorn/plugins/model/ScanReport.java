package tw.linsy.aelorn.plugins.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The result of walking the plugins folder once.
 *
 * Formatting lives in the command layer, not here: the previous version had this
 * type build its own coloured, paginated output, which meant a data structure
 * knew about page sizes and colour codes and could not be reused by anything that
 * did not want them.
 *
 * @param entries       every readable jar, sorted by file name
 * @param errors        one entry per jar that could not be read at all
 * @param duplicateNames plugin name (lower-cased) to the jars declaring it —
 *                       the failure mode worth surfacing, because the server
 *                       silently loads one of them and admins chase ghosts
 */
public record ScanReport(List<ScanEntry> entries, List<String> errors, Map<String, List<String>> duplicateNames) {

    public ScanReport(List<ScanEntry> entries, List<String> errors, Map<String, List<String>> duplicateNames) {
        this.entries = List.copyOf(entries);
        this.errors = List.copyOf(errors);
        this.duplicateNames = Map.copyOf(duplicateNames);
    }

    public static ScanReport failed(String error) {
        return new ScanReport(List.of(), List.of(error), Map.of());
    }

    /** Entries whose file name or declared plugin name contains {@code filter}. */
    public List<ScanEntry> matching(String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return entries;
        }
        return entries.stream()
            .filter(entry -> (entry.fileName() + " " + entry.descriptor().name())
                .toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    }
}
