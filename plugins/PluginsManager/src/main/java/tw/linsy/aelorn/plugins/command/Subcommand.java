package tw.linsy.aelorn.plugins.command;

import java.util.List;
import tw.linsy.aelorn.plugins.model.Reply;

/**
 * One subcommand, declared rather than coded into a switch.
 *
 * <p>The point of making these data is {@link #where}. Every subcommand either
 * touches plugin state (global region) or touches disk (async), and getting that
 * wrong is the class of bug that shows up as a stalled tick or a
 * {@code WrongThreadException} under load rather than as a test failure. Declaring
 * it next to the handler means the dispatcher applies it uniformly and a new
 * subcommand cannot forget to hop.
 *
 * <p>The previous version dispatched from a 55-line switch in which each arm
 * repeated its own {@code runGlobal(...)} wrapper — and two of the read-only arms
 * did disk IO on the region thread because the wrapper was copied from the arm
 * above.
 *
 * @param name      the word an admin types
 * @param permission required node; checked before the thread hop, so a refusal is
 *                   immediate and never schedules work
 * @param where     which thread the handler needs
 * @param sensitive whether a <em>refused</em> attempt is worth an audit record. True
 *                  for anything that changes state: "someone tried to unload LuckPerms
 *                  and was blocked" is exactly the entry an audit trail exists for.
 *                  False for read-only commands, where a mistyped plugin name would
 *                  otherwise fill the log with noise
 * @param handler   what to run
 * @param completer tab completion for this subcommand's arguments
 */
public record Subcommand(String name,
                         String permission,
                         Where where,
                         boolean sensitive,
                         Handler handler,
                         Completer completer) {

    /** Which thread a handler must run on. */
    public enum Where {
        /** Reads or mutates plugin registry state. */
        GLOBAL,
        /** Reads or writes files and never touches server state. */
        ASYNC
    }

    @FunctionalInterface
    public interface Handler {
        /**
         * @param actor who to record in the audit trail
         * @return the reply to send; never {@code null}
         */
        Reply run(String actor, Args args);
    }

    @FunctionalInterface
    public interface Completer {
        List<String> complete(Args args);
    }

    /** A read-only subcommand with no argument completion of its own. */
    public static Subcommand of(String name, String permission, Where where, Handler handler) {
        return new Subcommand(name, permission, where, false, handler, args -> List.of());
    }

    /** A read-only subcommand: refusals are not audited. */
    public static Subcommand reading(String name, String permission, Where where,
                                     Handler handler, Completer completer) {
        return new Subcommand(name, permission, where, false, handler, completer);
    }

    /** A state-changing subcommand: refusals are audited. */
    public static Subcommand changing(String name, String permission, Where where,
                                      Handler handler, Completer completer) {
        return new Subcommand(name, permission, where, true, handler, completer);
    }
}
