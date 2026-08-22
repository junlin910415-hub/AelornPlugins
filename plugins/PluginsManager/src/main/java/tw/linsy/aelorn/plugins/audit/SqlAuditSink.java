package tw.linsy.aelorn.plugins.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Audit records batched into a SQL table.
 *
 * <h2>Why batching, and what it costs</h2>
 * A single {@code INSERT} per record means a round trip per record, and a batch group
 * operation across thirty plugins produces thirty of them while an admin waits. Batched
 * inserts collapse that into one statement execution and one commit.
 *
 * <p>The cost is a window: records sit in {@link #pending} until the batch fills or the
 * flush timer fires, and a hard crash inside that window loses them. That is a real
 * tradeoff and the reason the file sink remains the default — this sink is for servers
 * that want the trail queryable and have accepted the window. It is bounded on both
 * axes ({@code batch-size}, {@code flush-seconds}) so the exposure is something an admin
 * chooses rather than discovers.
 *
 * <h2>Threads</h2>
 * {@link #write} is called from the global region and from the watcher thread; it only
 * appends to a synchronized deque, so it never blocks a tick on the database. Every
 * statement runs from {@link #flush}, which the owner schedules on an async thread.
 */
public final class SqlAuditSink implements AuditSink {

    /** Kept in one place because the read path has to match the write path's columns. */
    private static final String COLUMNS = "at_time, actor, action, target, status, detail";

    private final ConnectionSource connections;
    private final String table;
    private final int batchSize;
    private final Logger logger;

    /** Buffered records. Also the source of truth for {@link #tail}'s newest entries. */
    private final Deque<AuditRecord> pending = new ArrayDeque<>();

    private volatile boolean schemaReady;
    private volatile boolean degraded;

    public SqlAuditSink(ConnectionSource connections, String table, int batchSize, Logger logger) {
        this.connections = connections;
        // Validated by the caller; asserted here because it is interpolated into SQL.
        this.table = requireSafeIdentifier(table);
        this.batchSize = Math.max(1, batchSize);
        this.logger = logger;
    }

    /**
     * Table and column names cannot be bound as parameters, so the one identifier that
     * comes from configuration is restricted to characters that cannot end a statement.
     *
     * Every <em>value</em> is bound; this is the only interpolation in the class.
     */
    private static String requireSafeIdentifier(String candidate) {
        if (candidate == null || !candidate.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException(
                "稽核資料表名稱只允許英數與底線，且需以字母或底線開頭：" + candidate);
        }
        return candidate;
    }

    @Override
    public void write(AuditRecord record) {
        int size;
        synchronized (pending) {
            pending.addLast(record);
            size = pending.size();
        }
        if (size >= batchSize) {
            flush();
        }
    }

    /**
     * Writes everything buffered as one batch.
     *
     * On failure the records are put back at the front of the deque, so a database that
     * is briefly unreachable delays the trail rather than losing it. If the buffer then
     * grows past ten batches the oldest are dropped with a warning — an unbounded queue
     * on a permanently broken database would take the server's heap with it.
     */
    @Override
    public void flush() {
        List<AuditRecord> batch;
        synchronized (pending) {
            if (pending.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(pending);
            pending.clear();
        }
        try {
            ensureSchema();
            connections.execute(connection -> {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + table + " (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (AuditRecord record : batch) {
                        statement.setTimestamp(1, Timestamp.from(record.time()));
                        statement.setString(2, record.actor());
                        statement.setString(3, record.action());
                        statement.setString(4, record.target());
                        statement.setString(5, record.status());
                        statement.setString(6, record.detail());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                    connection.commit();
                } catch (SQLException failed) {
                    connection.rollback();
                    throw failed;
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
                return null;
            });
            if (degraded) {
                degraded = false;
                logger.info("稽核資料庫已恢復，緩衝的紀錄已寫入。");
            }
        } catch (SQLException | RuntimeException failure) {
            requeue(batch);
            if (!degraded) {
                degraded = true;
                logger.log(Level.WARNING,
                    "無法寫入稽核資料庫，紀錄暫存於記憶體並會重試。", failure);
            }
        }
    }

    private void requeue(List<AuditRecord> batch) {
        int limit = batchSize * 10;
        synchronized (pending) {
            for (int index = batch.size() - 1; index >= 0; index--) {
                pending.addFirst(batch.get(index));
            }
            int dropped = 0;
            while (pending.size() > limit) {
                pending.removeFirst();
                dropped++;
            }
            if (dropped > 0) {
                logger.warning("稽核緩衝已滿，丟棄最舊的 " + dropped + " 筆紀錄。");
            }
        }
    }

    /**
     * Creates the table if it is absent.
     *
     * <p>The DDL is dialect-specific because the two things this table needs are exactly
     * the two the dialects spell differently: an auto-incrementing key is
     * {@code INTEGER PRIMARY KEY AUTOINCREMENT} in SQLite and
     * {@code BIGINT AUTO_INCREMENT PRIMARY KEY} in MySQL, and MySQL has no
     * {@code CREATE INDEX IF NOT EXISTS} — so the index is declared inside the
     * {@code CREATE TABLE}, where {@code IF NOT EXISTS} already covers it.
     *
     * <p>The dialect comes from the connection's own metadata rather than from
     * configuration: an admin who has already given a JDBC URL should not have to also
     * name the database they just named in it.
     */
    private void ensureSchema() throws SQLException {
        if (schemaReady) {
            return;
        }
        connections.execute(connection -> {
            String product = connection.getMetaData().getDatabaseProductName();
            boolean sqlite = product != null
                && product.toLowerCase(java.util.Locale.ROOT).contains("sqlite");
            try (Statement statement = connection.createStatement()) {
                if (sqlite) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "at_time TIMESTAMP NOT NULL, "
                        + "actor VARCHAR(64) NOT NULL, "
                        + "action VARCHAR(64) NOT NULL, "
                        + "target VARCHAR(191) NOT NULL, "
                        + "status VARCHAR(16) NOT NULL, "
                        + "detail TEXT)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS "
                        + table + "_time_idx ON " + table + " (at_time)");
                } else {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "at_time TIMESTAMP(3) NOT NULL, "
                        + "actor VARCHAR(64) NOT NULL, "
                        + "action VARCHAR(64) NOT NULL, "
                        + "target VARCHAR(191) NOT NULL, "
                        + "status VARCHAR(16) NOT NULL, "
                        + "detail TEXT, "
                        + "INDEX " + table + "_time_idx (at_time))");
                }
            }
            return null;
        });
        schemaReady = true;
    }

    /**
     * The newest entries, oldest first, buffered records included.
     *
     * Buffered ones are appended after the persisted ones because they are by
     * definition newer. Without that an admin who disables a plugin and immediately
     * checks the trail would not see the entry they just caused, and would reasonably
     * conclude auditing is broken.
     */
    @Override
    public List<String> tail(int count) {
        int wanted = Math.max(1, count);
        List<AuditRecord> buffered;
        synchronized (pending) {
            buffered = new ArrayList<>(pending);
        }
        List<String> lines = new ArrayList<>(wanted);
        int fromDatabase = Math.max(0, wanted - buffered.size());
        if (fromDatabase > 0) {
            lines.addAll(readTail(fromDatabase));
        }
        int bufferedFrom = Math.max(0, buffered.size() - wanted);
        for (AuditRecord record : buffered.subList(bufferedFrom, buffered.size())) {
            lines.add(format(record));
        }
        return lines;
    }

    private List<String> readTail(int count) {
        try {
            ensureSchema();
            return connections.execute(connection -> {
                List<String> newestFirst = new ArrayList<>(count);
                try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM " + table + " ORDER BY at_time DESC, id DESC LIMIT ?")) {
                    statement.setInt(1, count);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            newestFirst.add(format(new AuditRecord(
                                rows.getTimestamp(1).toInstant(),
                                rows.getString(2), rows.getString(3), rows.getString(4),
                                rows.getString(5), rows.getString(6))));
                        }
                    }
                }
                // Queried newest-first so LIMIT takes the right end; reversed here so
                // the caller always receives oldest-first.
                java.util.Collections.reverse(newestFirst);
                return newestFirst;
            });
        } catch (SQLException | RuntimeException failure) {
            logger.log(Level.WARNING, "無法讀取稽核資料庫。", failure);
            return List.of();
        }
    }

    /** Same JSON-line shape as the file sink, so the audit command renders one way. */
    private static String format(AuditRecord record) {
        return "{\"time\":\"" + FileAuditSink.escape(record.time().toString())
            + "\",\"actor\":\"" + FileAuditSink.escape(record.actor())
            + "\",\"action\":\"" + FileAuditSink.escape(record.action())
            + "\",\"target\":\"" + FileAuditSink.escape(record.target())
            + "\",\"status\":\"" + FileAuditSink.escape(record.status())
            + "\",\"detail\":\"" + FileAuditSink.escape(record.detail()) + "\"}";
    }

    @Override
    public boolean buffers() {
        return true;
    }

    @Override
    public void close() {
        flush();
        connections.close();
    }

    @Override
    public String describe() {
        return "sql:" + table + "/" + connections.describe() + (degraded ? " (degraded)" : "");
    }
}
