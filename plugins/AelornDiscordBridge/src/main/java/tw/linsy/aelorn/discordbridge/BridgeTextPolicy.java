package tw.linsy.aelorn.discordbridge;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BridgeTextPolicy {

    private static final Pattern SHARE_PLACEHOLDER = Pattern.compile("(?i)\\[(?:item|i|inv|inventory|ender|e)\\]");

    private BridgeTextPolicy() {
    }

    /** Single pass over the code points: length, ISO controls, and RTL/zero-width abuse. */
    static Inspection inspect(String text, int maxCodePoints, boolean blockDirectionalControls) {
        if (text == null || text.isBlank()) {
            return new Inspection(false, Violation.EMPTY);
        }
        int codePoints = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (++codePoints > maxCodePoints) {
                return new Inspection(false, Violation.TOO_LONG);
            }
            if (Character.isISOControl(codePoint)
                || (blockDirectionalControls && isDangerousFormatControl(codePoint))) {
                return new Inspection(false, Violation.UNSAFE_CONTROL);
            }
            index += Character.charCount(codePoint);
        }
        return new Inspection(true, Violation.NONE);
    }

    static int countInteractiveShares(String text) {
        if (text == null || text.isEmpty() || text.indexOf('[') < 0) {
            return 0;
        }
        int count = 0;
        for (Matcher matcher = SHARE_PLACEHOLDER.matcher(text); matcher.find(); ) {
            count++;
        }
        return count;
    }

    static boolean containsMassMentionText(String text) {
        if (text == null || text.indexOf('@') < 0) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("@everyone") || lower.contains("@here");
    }

    private static boolean isDangerousFormatControl(int codePoint) {
        return codePoint == 0x200B                        // zero-width space
            || codePoint == 0x200E || codePoint == 0x200F // LRM / RLM
            || (codePoint >= 0x202A && codePoint <= 0x202E) // directional embedding/override
            || codePoint == 0x2060                        // word joiner
            || (codePoint >= 0x2066 && codePoint <= 0x2069) // directional isolates
            || codePoint == 0xFEFF;                       // BOM / zero-width no-break
    }

    enum Violation {
        NONE,
        EMPTY,
        TOO_LONG,
        UNSAFE_CONTROL
    }

    record Inspection(boolean allowed, Violation violation) {
    }
}
