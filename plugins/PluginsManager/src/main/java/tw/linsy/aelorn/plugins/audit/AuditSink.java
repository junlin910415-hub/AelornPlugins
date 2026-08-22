package tw.linsy.aelorn.plugins.audit;

import java.util.List;

/**
 * Where audit records end up.
 *
 * Two implementations with genuinely different tradeoffs, which is why this is an
 * interface rather than a flag on one class:
 *
 * <ul>
 *   <li>{@link FileAuditSink} writes JSON lines and flushes every record. Nothing to
 *       configure, nothing to break, and the last entry before a crash is on disk.</li>
 *   <li>{@link SqlAuditSink} batches inserts into a table. Cheaper per record at
 *       volume and actually queryable, at the cost of a window in which buffered
 *       records would be lost.</li>
 * </ul>
 *
 * <p>The file sink is the default deliberately. An audit trail whose availability
 * depends on a database being up is not one you can rely on during the incident you
 * are auditing — the same reasoning that made AelornLib a soft dependency.
 */
public interface AuditSink {

    /**
     * Accepts a record. May buffer it.
     *
     * Must not throw: a failure to audit is reported through the logger and must
     * never turn a successful plugin operation into a failed one.
     */
    void write(AuditRecord record);

    /**
     * The most recent entries, oldest first, rendered one per line.
     *
     * Reads whatever is durable plus whatever is buffered, so an admin who runs the
     * audit command immediately after an operation sees it.
     */
    List<String> tail(int count);

    /** Persists anything buffered. Called on a timer and before shutdown. */
    void flush();

    /**
     * Whether records can sit unwritten, which is what makes a flush timer worth
     * scheduling. False for the file sink, which writes each record as it arrives.
     */
    default boolean buffers() {
        return false;
    }

    /** Flushes and releases resources. */
    void close();

    /** Where records are going, for the status report. */
    String describe();
}
