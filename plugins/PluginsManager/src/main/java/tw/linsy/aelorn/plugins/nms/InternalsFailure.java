package tw.linsy.aelorn.plugins.nms;

/**
 * One server-internals operation did not work.
 *
 * Unchecked because the callers are teardown sequences that already accumulate
 * per-step outcomes: a failed step is recorded and the sequence continues, since
 * abandoning an unload halfway leaves the server in a worse state than finishing
 * it with a warning. A checked exception would only add a {@code catch} to every
 * step that already has one.
 *
 * <p>The message is written for an admin reading an audit line, not for a
 * developer reading a stack trace — it says which capability was unavailable and
 * on what server, because that is what decides whether an adapter needs adding.
 */
public final class InternalsFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InternalsFailure(String message) {
        super(message);
    }

    public InternalsFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
