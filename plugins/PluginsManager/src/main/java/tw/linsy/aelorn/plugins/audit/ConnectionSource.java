package tw.linsy.aelorn.plugins.audit;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Where the SQL audit sink gets a connection.
 *
 * <p>The contract is {@link #execute}, not "hand me a connection", on purpose. The two
 * implementations have opposite lifetime rules — a pooled connection must be closed to
 * be returned, a directly held one must not be closed or the next write reopens it —
 * and letting that difference reach the caller is exactly the kind of detail that
 * produces a leak in one configuration and a reconnect storm in the other. Inverting
 * it means the source owns the lifetime and the sink only describes the work.
 *
 * <p><b>An honest note on pooling.</b> A connection pool earns its keep under
 * concurrent load, and this sink has one writer plus the occasional read from an admin
 * running the audit command. The pool is not a throughput win here. What it buys is
 * connection <em>liveness</em>: validation, timeouts, and replacing a connection the
 * database closed underneath us — which, for a server that stays up for weeks and
 * writes a few rows a day, is the failure that actually happens. So the pooled path is
 * preferred when available, the direct path revalidates and reconnects, and neither is
 * presented as faster than the other.
 */
public interface ConnectionSource {

    /** Work to run against a connection the source owns. */
    @FunctionalInterface
    interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    /**
     * Runs {@code work} on a live connection.
     *
     * @throws SQLException when the database cannot be reached, or the work failed
     */
    <T> T execute(SqlWork<T> work) throws SQLException;

    void close();

    /** Which implementation this is, for the status report. */
    String describe();
}
