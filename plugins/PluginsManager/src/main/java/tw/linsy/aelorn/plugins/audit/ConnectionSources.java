package tw.linsy.aelorn.plugins.audit;

import java.util.Properties;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Picks a pooled connection source when HikariCP is available, a direct one otherwise.
 *
 * The probe is by class presence rather than by configuration: whether HikariCP is on
 * this plugin's class path depends on what the server resolved for it, which an admin
 * cannot reasonably be asked to declare in a YAML file.
 */
public final class ConnectionSources {

    private static final String HIKARI_MARKER = "com.zaxxer.hikari.HikariDataSource";

    private ConnectionSources() {
    }

    /**
     * @param driverClass JDBC driver to load, or {@code null} to let the driver
     *                    register itself through the service loader
     */
    public static ConnectionSource open(String url, Properties properties,
                                        @Nullable String driverClass, String poolName,
                                        long connectionTimeoutMillis, Logger logger) {
        if (classPresent(HIKARI_MARKER)) {
            try {
                ConnectionSource pooled = PooledConnectionSource.create(
                    url, properties, driverClass, poolName, connectionTimeoutMillis);
                logger.info("稽核資料庫連線池：HikariCP。");
                return pooled;
            } catch (Throwable unavailable) {
                logger.warning("HikariCP 在 classpath 上但無法建立連線池（"
                    + unavailable.getClass().getSimpleName()
                    + (unavailable.getMessage() == null ? "" : ": " + unavailable.getMessage())
                    + "），改用單一連線。");
            }
        }
        return new DirectConnectionSource(url, properties, driverClass);
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, ConnectionSources.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }
}
