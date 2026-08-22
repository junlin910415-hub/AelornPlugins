package tw.linsy.aelorn.plugins.audit;

import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The one entry point for recording what happened.
 *
 * A thin front over an {@link AuditSink} so every caller writes the same six fields and
 * none of them knows whether the trail is a file or a table. The indirection pays for
 * itself the moment a server switches storage: nothing in the services changes.
 *
 * <p>{@code enabled} is read per record rather than captured, so toggling
 * {@code audit.enabled} takes effect on the next reload without rebuilding the sink and
 * losing whatever it had buffered.
 */
public final class AuditLog {

    private final AuditSink sink;
    private final BooleanSupplier enabled;

    public AuditLog(AuditSink sink, BooleanSupplier enabled) {
        this.sink = sink;
        this.enabled = enabled;
    }

    /**
     * Records one operation.
     *
     * Never throws and never blocks on IO for longer than an append: called from the
     * global region, where a stalled write would be a stalled tick.
     *
     * @param actor  a player name, {@code console}, {@code watcher} or {@code system}
     * @param action the operation in kebab-case, matching the command name
     * @param target what it acted on
     * @param status {@code SUCCESS}, {@code FAIL}, {@code WARN}, {@code SKIP},
     *               {@code WAIT} or {@code NOTICE}
     * @param detail free text, already stripped of markup by the caller
     */
    public void record(String actor, String action, String target, String status, String detail) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        sink.write(new AuditRecord(Instant.now(), actor, action, target, status, detail));
    }

    /** The most recent entries, oldest first. Reads a file or a table; call off-tick. */
    public List<String> tail(int count) {
        return sink.tail(count);
    }

    /** Persists anything buffered; the owner calls this on a timer and at shutdown. */
    public void flush() {
        sink.flush();
    }

    public void close() {
        sink.close();
    }

    /** Where records go, for the status report. */
    public String describe() {
        return sink.describe();
    }

    /** True when a periodic {@link #flush()} is worth scheduling. */
    public boolean buffers() {
        return sink.buffers();
    }
}
