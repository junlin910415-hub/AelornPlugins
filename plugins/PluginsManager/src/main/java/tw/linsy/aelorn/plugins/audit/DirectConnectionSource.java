package tw.linsy.aelorn.plugins.audit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.jetbrains.annotations.Nullable;

/**
 * One {@link DriverManager} connection, revalidated and reopened when it goes stale.
 *
 * The fallback when HikariCP is not on the class path. Deliberately not a hand-rolled
 * pool: this sink has a single writer, so what is needed is a live connection rather
 * than several, and re-implementing validation and eviction badly is worse than asking
 * for one connection and checking it.
 *
 * <p>The driver is loaded with {@link Class#forName} rather than being a compile-time
 * dependency, which is the convention across this server's plugins: the JDBC drivers
 * ship with the server, and binding to one at compile time would mean a plugin that
 * cannot start when the admin picks the other database.
 *
 * <p>Serialized on this object. That is not a bottleneck for one writer, and it is what
 * makes "check, maybe reconnect, then use" safe against the admin who runs the audit
 * command while a flush is in progress.
 */
final class DirectConnectionSource implements ConnectionSource {

    /** Long enough for a local file or a LAN database, short enough not to stall a flush. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final String url;
    private final Properties properties;
    private final @Nullable String driverClass;

    private @Nullable Connection held;

    DirectConnectionSource(String url, Properties properties, @Nullable String driverClass) {
        this.url = url;
        this.properties = properties;
        this.driverClass = driverClass;
    }

    @Override
    public synchronized <T> T execute(SqlWork<T> work) throws SQLException {
        return work.apply(liveConnection());
    }

    private Connection liveConnection() throws SQLException {
        Connection current = held;
        if (current != null && isUsable(current)) {
            return current;
        }
        closeQuietly();
        loadDriver();
        current = DriverManager.getConnection(url, properties);
        held = current;
        return current;
    }

    private void loadDriver() throws SQLException {
        if (driverClass == null || driverClass.isBlank()) {
            return;
        }
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException missing) {
            throw new SQLException("找不到 JDBC 驅動類別 " + driverClass
                + "；請確認伺服器已內含該驅動。", missing);
        }
    }

    private static boolean isUsable(Connection connection) {
        try {
            return !connection.isClosed() && connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException broken) {
            return false;
        }
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        Connection current = held;
        held = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (SQLException ignored) {
            // Shutting down; rows were committed as they were batched.
        }
    }

    @Override
    public String describe() {
        return "direct";
    }
}
