package tw.linsy.aelorn.plugins.audit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import org.jetbrains.annotations.Nullable;

/**
 * Connections from a HikariCP pool.
 *
 * <p><b>Load-order rule, as with the AelornLib classes:</b> this is the only class in
 * the plugin that names {@code com.zaxxer.hikari}. HikariCP ships in the server's
 * {@code libraries/} tree but is resolved per plugin, so it is not guaranteed to be on
 * <em>this</em> plugin's class path at runtime. {@link ConnectionSources} therefore
 * reaches it through {@link #create} behind a class-presence probe and a
 * {@code catch (Throwable)}; a field of a Hikari type anywhere else, or an
 * {@code instanceof} against one, would resolve the class on a server without it and
 * turn the fallback into a {@code NoClassDefFoundError}.
 *
 * <p>The pool is sized for what this actually is: one writer and the occasional
 * reader. Two connections, not ten — a pool larger than the concurrency it serves adds
 * idle sockets the database has to keep, and the audit trail is not a hot path.
 */
final class PooledConnectionSource implements ConnectionSource {

    /** One writer plus one concurrent reader; anything more would sit idle. */
    private static final int MAX_POOL_SIZE = 2;
    private static final int MIN_IDLE = 1;

    private final HikariDataSource dataSource;

    private PooledConnectionSource(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @throws Throwable when HikariCP's classes are missing or the pool refuses to
     *                   start; the caller treats any failure as "no pool"
     */
    static ConnectionSource create(String url, Properties properties, @Nullable String driverClass,
                                   String poolName, long connectionTimeoutMillis) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setPoolName(poolName);
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setConnectionTimeout(connectionTimeoutMillis);
        // A connection idle for half an hour on a MySQL with an eight-hour timeout is
        // fine; one idle for a week is not. Retiring them keeps the pool from handing
        // out a socket the server closed.
        config.setMaxLifetime(30 * 60 * 1000L);
        config.setDataSourceProperties(properties);
        if (driverClass != null && !driverClass.isBlank()) {
            config.setDriverClassName(driverClass);
        }
        return new PooledConnectionSource(new HikariDataSource(config));
    }

    @Override
    public <T> T execute(SqlWork<T> work) throws SQLException {
        // try-with-resources returns the connection to the pool rather than ending it.
        try (Connection connection = dataSource.getConnection()) {
            return work.apply(connection);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    @Override
    public String describe() {
        return "hikari(" + MAX_POOL_SIZE + ")";
    }
}
