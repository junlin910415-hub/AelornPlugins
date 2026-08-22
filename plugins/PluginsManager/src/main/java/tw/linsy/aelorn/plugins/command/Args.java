package tw.linsy.aelorn.plugins.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One command invocation, with flags separated from positional arguments.
 *
 * Parsed once at the top of {@code onCommand} instead of by each subcommand. The
 * previous version had four static helpers — {@code targetFrom}, {@code argument},
 * {@code trailingInt}, {@code stripTrailingPage} — each re-scanning the raw array
 * and each with its own idea of what counts as a flag, which is why
 * {@code list foo 2} worked and {@code list foo --confirm 2} did not.
 *
 * <p>Flags may appear anywhere. An admin who types {@code disable --force Foo} means
 * the same as {@code disable Foo --force}, and refusing one of them teaches nothing.
 */
public final class Args {

    private static final Set<String> CONFIRM_FLAGS = Set.of("--confirm", "-y");
    private static final Set<String> FORCE_FLAGS = Set.of("--force", "-f");

    /** Offered wherever a flag is accepted; the short forms are intentionally not. */
    static final List<String> FLAG_SUGGESTIONS = List.of("--confirm", "--force");

    private final String label;
    private final List<String> words;
    private final boolean confirmed;
    private final boolean forced;
    private final String lastToken;

    private Args(String label, List<String> words, boolean confirmed, boolean forced, String lastToken) {
        this.label = label;
        this.words = List.copyOf(words);
        this.confirmed = confirmed;
        this.forced = forced;
        this.lastToken = lastToken;
    }

    /**
     * @param label the alias the sender actually typed, so usage messages echo the
     *              command they used rather than a canonical name they may not know
     */
    public static Args parse(String label, String[] raw) {
        List<String> words = new ArrayList<>(raw.length);
        boolean confirmed = false;
        boolean forced = false;
        for (String token : raw) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (CONFIRM_FLAGS.contains(lower)) {
                confirmed = true;
            } else if (FORCE_FLAGS.contains(lower)) {
                forced = true;
            } else if (lower.startsWith("--")) {
                // An unknown long flag is dropped rather than treated as a target:
                // a typo'd --confrim must not become the name of a plugin to disable.
                continue;
            } else {
                words.add(token);
            }
        }
        String lastToken = raw.length == 0 ? "" : raw[raw.length - 1];
        return new Args(label, words, confirmed, forced, lastToken);
    }

    /** The alias typed, e.g. {@code zpm} or {@code pm}. */
    public String label() {
        return label;
    }

    /** {@code true} when {@code --confirm} or {@code -y} was given. */
    public boolean confirmed() {
        return confirmed;
    }

    /** {@code true} when {@code --force} or {@code -f} was given. */
    public boolean forced() {
        return forced;
    }

    /** The subcommand name, lower-cased, or {@code ""} when none was typed. */
    public String subcommand() {
        return at(0).toLowerCase(Locale.ROOT);
    }

    /** Positional word at {@code index}, flags excluded, or {@code ""}. */
    public String at(int index) {
        return index >= 0 && index < words.size() ? words.get(index) : "";
    }

    /** Positional count, including the subcommand name. */
    public int size() {
        return words.size();
    }

    /**
     * Everything from {@code index} onwards joined with spaces.
     *
     * Plugin names do not contain spaces, but admins type them anyway when they mean
     * a filter; joining keeps {@code find world edit} working as one query.
     */
    public String joinedFrom(int index) {
        if (index >= words.size()) {
            return "";
        }
        return String.join(" ", words.subList(index, words.size())).trim();
    }

    /**
     * A trailing number, for paginated listings.
     *
     * @return the last positional parsed as an int, or {@code fallback}
     */
    public int trailingInt(int fallback) {
        if (words.size() < 2) {
            return fallback;
        }
        try {
            return Integer.parseInt(words.get(words.size() - 1));
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /**
     * Everything from {@code index} onwards, minus a trailing page number.
     *
     * So {@code list world 2} filters on {@code world} rather than on
     * {@code "world 2"}, which matched nothing and looked like the filter was broken.
     */
    public String filterFrom(int index) {
        if (index >= words.size()) {
            return "";
        }
        List<String> parts = new ArrayList<>(words.subList(index, words.size()));
        if (parts.size() > 1 || (parts.size() == 1 && isInteger(parts.get(0)))) {
            if (isInteger(parts.get(parts.size() - 1))) {
                parts.remove(parts.size() - 1);
            }
        }
        return String.join(" ", parts).trim();
    }

    /** The raw last token, including a partial flag, for tab completion. */
    public String lastToken() {
        return lastToken;
    }

    /** Raw token count, flags included; tab completion needs the typed position. */
    public int rawSize() {
        return words.size() + (confirmed ? 1 : 0) + (forced ? 1 : 0);
    }

    private static boolean isInteger(String text) {
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** Entries of {@code options} starting with {@code partial}, case-insensitively. */
    public static List<String> filter(List<String> options, String partial) {
        String needle = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
