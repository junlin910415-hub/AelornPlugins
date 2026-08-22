package tw.linsy.aelornstore.db;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import tw.linsy.aelornstore.config.StoreSettings;

/**
 * JDBC access with a small pool of its own.
 *
 * The driver is instantiated by reflection and used directly rather than through
 * {@link java.sql.DriverManager}: Paper hands library jars to the plugin's own
 * class loader, which DriverManager refuses to look inside. That also keeps the
 * plugin free of any compile-time dependency on a driver, so it builds offline
 * against nothing but the JDK's {@code java.sql}.
 *
 * <p>A store does single-digit queries per minute, so the pool is deliberately
 * plain: a semaphore for the ceiling, a deque of idle connections, and a validity
 * check on anything that has been sitting long enough for MySQL to have hung up.
 */
public final class Database implements AutoCloseable {

    /** A unit of work that needs a connection. */
    @FunctionalInterface
    public interface SqlTask<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Dialect dialect;
    private final String url;
    private final Properties properties;
    private final String tablePrefix;
    private final int connectionTimeoutSeconds;
    private final long validateAfterIdleMillis;
    private final Logger logger;

    private final Deque<Pooled> idle = new ArrayDeque<>();
    private final Semaphore permits;
    private volatile Driver driver;
    private volatile boolean closed;

    private static final class Pooled {
        private final Connection connection;
        private long releasedAt;

        private Pooled(Connection connection, long releasedAt) {
            this.connection = connection;
            this.releasedAt = releasedAt;
        }
    }

    public Database(StoreSettings.Storage settings, File dataFolder, Logger logger) {
        this.logger = logger;
        this.tablePrefix = settings.tablePrefix();
        this.connectionTimeoutSeconds = settings.connectionTimeoutSeconds();
        this.validateAfterIdleMillis = settings.validateAfterIdleSeconds() * 1000L;
        this.permits = new Semaphore(settings.maxConnections(), true);
        this.properties = new Properties();

        if (settings.type() == StoreSettings.StorageType.MYSQL) {
            this.dialect = Dialect.MYSQL;
            StringBuilder target = new StringBuilder("jdbc:mysql://")
                .append(settings.mysqlHost()).append(':').append(settings.mysqlPort())
                .append('/').append(settings.mysqlDatabase());
            if (!settings.mysqlProperties().isEmpty()) {
                target.append('?').append(settings.mysqlProperties());
            }
            this.url = target.toString();
            this.properties.setProperty("user", settings.mysqlUser());
            this.properties.setProperty("password", settings.mysqlPassword());
            this.properties.setProperty("connectTimeout", String.valueOf(connectionTimeoutSeconds * 1000));
        } else {
            this.dialect = Dialect.SQLITE;
            File target = new File(settings.sqliteFile());
            if (!target.isAbsolute()) {
                target = new File(dataFolder, settings.sqliteFile());
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                logger.warning("無法建立資料庫目錄: " + parent.getAbsolutePath());
            }
            this.url = "jdbc:sqlite:" + target.getAbsolutePath();
        }
    }

    public Dialect dialect() {
        return dialect;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    /** Loads the driver, opens one connection to prove the settings work, and builds the schema. */
    public void open() throws SQLException {
        try {
            Class<?> type = Class.forName(dialect.driverClass(), true, getClass().getClassLoader());
            driver = (Driver) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException missing) {
            throw new SQLException("找不到 JDBC 驅動 " + dialect.driverClass()
                + "。請確認 plugin.yml 的 libraries 已解析（伺服器首次啟動需要網路）。", missing);
        }
        execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String ddl : dialect.schema(tablePrefix)) {
                    statement.execute(ddl);
                }
            }
            return null;
        });
    }

    /** Runs a task on a pooled connection with auto-commit left on. */
    public <T> T execute(SqlTask<T> task) throws SQLException {
        Pooled pooled = borrow();
        try {
            return task.run(pooled.connection);
        } finally {
            release(pooled);
        }
    }

    /**
     * Runs a task inside a transaction, rolling back on any failure.
     *
     * Every balance change and every order transition goes through here — a
     * ledger row without its matching balance update (or the reverse) would make
     * the books unreconcilable, which is the one failure mode a payment system
     * cannot recover from.
     */
    public <T> T transaction(SqlTask<T> task) throws SQLException {
        Pooled pooled = borrow();
        Connection connection = pooled.connection;
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            T result;
            try {
                result = task.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    logger.log(Level.SEVERE, "交易回滾失敗，資料可能不一致。", rollbackFailure);
                }
                throw failure;
            }
            return result;
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // The connection is about to be validated or discarded anyway.
            }
            release(pooled);
        }
    }

    private Pooled borrow() throws SQLException {
        if (closed) {
            throw new SQLException("資料庫連線已關閉。");
        }
        try {
            if (!permits.tryAcquire(connectionTimeoutSeconds, TimeUnit.SECONDS)) {
                throw new SQLException("等待資料庫連線逾時（" + connectionTimeoutSeconds + " 秒）。");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SQLException("等待資料庫連線時被中斷。", interrupted);
        }
        try {
            Pooled candidate;
            synchronized (idle) {
                candidate = idle.pollLast();
            }
            if (candidate != null && usable(candidate)) {
                return candidate;
            }
            if (candidate != null) {
                closeQuietly(candidate.connection);
            }
            return new Pooled(openConnection(), System.currentTimeMillis());
        } catch (SQLException | RuntimeException failure) {
            permits.release();
            throw failure;
        }
    }

    private boolean usable(Pooled pooled) {
        try {
            if (pooled.connection.isClosed()) {
                return false;
            }
            long idleMillis = System.currentTimeMillis() - pooled.releasedAt;
            if (validateAfterIdleMillis > 0 && idleMillis < validateAfterIdleMillis) {
                return true;
            }
            return pooled.connection.isValid(connectionTimeoutSeconds);
        } catch (SQLException unusable) {
            return false;
        }
    }

    private void release(Pooled pooled) {
        pooled.releasedAt = System.currentTimeMillis();
        boolean keep = !closed;
        if (keep) {
            synchronized (idle) {
                idle.addLast(pooled);
            }
        } else {
            closeQuietly(pooled.connection);
        }
        permits.release();
    }

    private Connection openConnection() throws SQLException {
        Connection connection = driver.connect(url, properties);
        if (connection == null) {
            throw new SQLException("JDBC 驅動不接受連線字串: " + url);
        }
        List<String> setup = dialect.sessionSetup(connectionTimeoutSeconds * 1000);
        if (!setup.isEmpty()) {
            try (Statement statement = connection.createStatement()) {
                for (String pragma : setup) {
                    statement.execute(pragma);
                }
            }
        }
        return connection;
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do with a failure to close a dead connection.
        }
    }

    @Override
    public void close() {
        closed = true;
        synchronized (idle) {
            Pooled pooled;
            while ((pooled = idle.pollFirst()) != null) {
                closeQuietly(pooled.connection);
            }
        }
    }
}
