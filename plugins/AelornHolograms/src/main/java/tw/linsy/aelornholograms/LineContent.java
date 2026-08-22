package tw.linsy.aelornholograms;

import java.util.Locale;

/**
 * A hologram line parsed once: its display kind and the content after prefix
 * stripping. Parsing happens at rebuild time, never in the per-tick loop.
 */
record LineContent(DisplayEntitySpawner.DisplayKind kind, String content) {

    static LineContent parse(String rawLine) {
        String trimmed = rawLine == null ? "" : rawLine.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("#ICON:") || upper.startsWith("#ITEM:")) {
            return new LineContent(DisplayEntitySpawner.DisplayKind.ITEM, trimmed.substring(6).trim());
        }
        if (upper.startsWith("#BLOCK:")) {
            return new LineContent(DisplayEntitySpawner.DisplayKind.BLOCK, trimmed.substring(7).trim());
        }
        if (upper.startsWith("{ITEM:") && trimmed.endsWith("}")) {
            return new LineContent(DisplayEntitySpawner.DisplayKind.ITEM,
                trimmed.substring(6, trimmed.length() - 1).trim());
        }
        if (upper.startsWith("{BLOCK:") && trimmed.endsWith("}")) {
            return new LineContent(DisplayEntitySpawner.DisplayKind.BLOCK,
                trimmed.substring(7, trimmed.length() - 1).trim());
        }
        return new LineContent(DisplayEntitySpawner.DisplayKind.TEXT,
            rawLine != null && !rawLine.isBlank() ? rawLine : " ");
    }
}
