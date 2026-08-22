package tw.linsy.aelorn.plugins.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The finished player-facing answer to one command.
 *
 * Services return this instead of a {@code String}. The difference matters: a
 * service names a key in {@code messages.yml} and hands over the resolved
 * markup, so no service contains a sentence anybody reads. Changing the wording
 * of an operation is a YAML edit, and the compiler cannot be the reason a
 * translation is stale.
 *
 * <p>Multi-line by construction. Nearly every report here is a header plus rows,
 * and joining them with {@code \n} only to split them again in the sender was
 * how the previous version ended up with formatting decisions in three places.
 *
 * @param success whether the operation did what was asked; drives the audit
 *                status and whether the command layer logs a failure
 * @param lines   resolved markup, one line per element, never {@code null}
 */
public record Reply(boolean success, List<String> lines) {

    public Reply(boolean success, List<String> lines) {
        this.success = success;
        this.lines = List.copyOf(lines);
    }

    public static Reply ok(String line) {
        return new Reply(true, List.of(line));
    }

    public static Reply ok(List<String> lines) {
        return new Reply(true, lines);
    }

    public static Reply fail(String line) {
        return new Reply(false, List.of(line));
    }

    /** The first line, for audit details and log lines that want one sentence. */
    public String first() {
        return lines.isEmpty() ? "" : lines.get(0);
    }

    /** Appends lines to a successful reply, e.g. warnings gathered along the way. */
    public Reply with(List<String> extra) {
        if (extra.isEmpty()) {
            return this;
        }
        List<String> merged = new ArrayList<>(lines);
        merged.addAll(extra);
        return new Reply(success, merged);
    }
}
