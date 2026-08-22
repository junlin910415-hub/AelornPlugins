package tw.linsy.aelorn.plugins.audit;

import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Logger;
import tw.linsy.aelorn.plugins.config.ManagerSettings;

/**
 * Builds the audit sink the configuration asks for, falling back to the file.
 *
 * <p>Falling back rather than failing is the same judgement made for AelornLib and for
 * the NMS adapter: a plugin manager has to work when the rest of the server does not.
 * A misconfigured database should cost the admin a queryable trail, not the ability to
 * disable the plugin that is breaking their server.
 */
public final class AuditSinks {

    private AuditSinks() {
    }

    /**
     * @param dataFolder the plugin's data folder; the file sink and a relative SQLite
     *                   path both resolve inside it
     */
    public static AuditSink open(ManagerSettings.Audit config, Path dataFolder, Logger logger) {
        Path logFile = dataFolder.resolve("audit.log");
        if (config.storage() != ManagerSettings.Audit.Storage.SQL) {
            return new FileAuditSink(logFile, logger);
        }
        ManagerSettings.Audit.Sql sql = config.sql();
        if (sql.url().isBlank()) {
            logger.warning("audit.storage 設為 sql 但 audit.sql.url 是空的，改用檔案稽核。");
            return new FileAuditSink(logFile, logger);
        }
        try {
            Properties properties = new Properties();
            if (!sql.username().isBlank()) {
                properties.setProperty("user", sql.username());
            }
            if (!sql.password().isBlank()) {
                properties.setProperty("password", sql.password());
            }
            ConnectionSource connections = ConnectionSources.open(
                sql.url(), properties, sql.driverClass().isBlank() ? null : sql.driverClass(),
                "PluginsManager-audit", sql.connectionTimeoutMillis(), logger);
            AuditSink sink = new SqlAuditSink(connections, sql.table(), sql.batchSize(), logger);
            logger.info("稽核儲存：" + sink.describe()
                + "（批次 " + sql.batchSize() + " 筆 / 每 " + sql.flushSeconds() + " 秒寫出）。");
            return sink;
        } catch (RuntimeException misconfigured) {
            logger.warning("無法建立 SQL 稽核儲存（"
                + misconfigured.getClass().getSimpleName()
                + (misconfigured.getMessage() == null ? "" : ": " + misconfigured.getMessage())
                + "），改用檔案稽核。");
            return new FileAuditSink(logFile, logger);
        }
    }
}
