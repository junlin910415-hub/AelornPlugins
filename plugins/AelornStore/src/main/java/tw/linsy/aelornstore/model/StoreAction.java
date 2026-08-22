package tw.linsy.aelornstore.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * One line of a {@code actions:} / {@code revoke-actions:} / {@code on-grant:} list.
 *
 * Lines are parsed once at reload and kept as immutable values, so delivery never
 * re-parses YAML on the hot path. An unparseable line is logged with its config
 * path and dropped rather than aborting the whole product.
 *
 * <p>Syntax is {@code <kind>: <argument>}. For {@link Kind#VIP} the argument is
 * {@code key=value} pairs, pre-split into {@link #options()}.
 */
public record StoreAction(Kind kind, String argument, Map<String, String> options) {

    public enum Kind {
        /** Run as console. The usual way to reach LuckPerms, AelornItems, Nexo, CMI. */
        CONSOLE,
        /** Run as the buying player. */
        PLAYER,
        /** Grant store credit. */
        CREDIT,
        /** Grant Vault money. */
        VAULT,
        /** Grant or extend a VIP tier: {@code tier=<id> days=<n>}. */
        VIP,
        /** Give a vanilla item: {@code <MATERIAL> <amount>}. */
        ITEM,
        /** Private message to the buyer. */
        MESSAGE,
        /** Server-wide announcement. */
        BROADCAST,
        /** Play a sound to the buyer: {@code <SOUND> <volume> <pitch>}. */
        SOUND
    }

    public StoreAction {
        options = Map.copyOf(options);
    }

    /** Parses one line, or returns {@code null} after logging why it was rejected. */
    public static @Nullable StoreAction parse(@Nullable String line, String path, Logger logger) {
        if (line == null || line.isBlank()) {
            return null;
        }
        int separator = line.indexOf(':');
        if (separator <= 0) {
            logger.warning("設定 " + path + " 的動作缺少「型別:」前綴，已略過: " + line);
            return null;
        }
        String rawKind = line.substring(0, separator).trim().toUpperCase(Locale.ROOT);
        String argument = line.substring(separator + 1).trim();
        Kind kind;
        try {
            kind = Kind.valueOf(rawKind);
        } catch (IllegalArgumentException unknown) {
            logger.warning("設定 " + path + " 的動作型別無效「" + rawKind + "」，已略過: " + line);
            return null;
        }
        if (argument.isEmpty()) {
            logger.warning("設定 " + path + " 的動作沒有內容，已略過: " + line);
            return null;
        }
        Map<String, String> options = kind == Kind.VIP ? splitOptions(argument) : Map.of();
        if (kind == Kind.VIP && !options.containsKey("tier")) {
            logger.warning("設定 " + path + " 的 vip 動作缺少 tier=，已略過: " + line);
            return null;
        }
        return new StoreAction(kind, argument, options);
    }

    private static Map<String, String> splitOptions(String argument) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String token : argument.split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals > 0 && equals < token.length() - 1) {
                options.put(token.substring(0, equals).toLowerCase(Locale.ROOT), token.substring(equals + 1));
            }
        }
        return options;
    }

    /** Reads a numeric option, falling back when it is missing or not a number. */
    public long optionLong(String key, long fallback) {
        String value = options.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    public String option(String key, String fallback) {
        String value = options.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
