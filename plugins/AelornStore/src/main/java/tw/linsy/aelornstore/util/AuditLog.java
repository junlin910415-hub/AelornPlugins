package tw.linsy.aelornstore.util;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.db.StoreDao;

/**
 * The trail that answers "who got what, when, and on whose authority".
 *
 * Two sinks on purpose. The database copy is queryable and is what
 * {@code /astore audit} reads. The plain-text copy is append-only and survives a
 * dropped table or a botched migration — when a chargeback arrives months later,
 * the file is the copy that is still there.
 *
 * <p>Audit failures never abort the operation being audited: a store that
 * refuses to deliver a paid order because it could not write a log line has
 * turned a bookkeeping problem into a customer-facing one.
 */
public final class AuditLog {

    private final StoreDao dao;
    private final Supplier<StoreSettings.Audit> settings;
    private final Clock clock;
    private final File dataFolder;
    private final Logger logger;
    private final Object fileLock = new Object();

    public AuditLog(StoreDao dao, Supplier<StoreSettings.Audit> settings, Clock clock,
                    File dataFolder, Logger logger) {
        this.dao = dao;
        this.settings = settings;
        this.clock = clock;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    /** Records inside the caller's transaction, so the trail commits with the change. */
    public void record(Connection connection, String actor, String action,
                       @Nullable String target, @Nullable String detail, long now) throws SQLException {
        StoreSettings.Audit current = settings.get();
        if (!current.enabled()) {
            return;
        }
        dao.audit(connection, actor, action, target, detail, now);
        appendToFile(current, actor, action, target, detail, now);
    }

    /** Records on its own connection. Never throws — see the class note. */
    public void record(String actor, String action, @Nullable String target, @Nullable String detail) {
        StoreSettings.Audit current = settings.get();
        if (!current.enabled()) {
            return;
        }
        long now = clock.now();
        try {
            dao.database().execute(connection -> {
                dao.audit(connection, actor, action, target, detail, now);
                return null;
            });
        } catch (SQLException failure) {
            logger.log(Level.WARNING, "寫入稽核紀錄失敗: " + action, failure);
        }
        appendToFile(current, actor, action, target, detail, now);
    }

    /** Drops rows past the retention window. A keep-days of 0 means keep everything. */
    public int purge() {
        StoreSettings.Audit current = settings.get();
        if (!current.enabled() || current.keepDays() <= 0) {
            return 0;
        }
        try {
            return dao.purgeAudit(clock.now() - Clock.daysToMillis(current.keepDays()));
        } catch (SQLException failure) {
            logger.log(Level.WARNING, "清理稽核紀錄失敗。", failure);
            return 0;
        }
    }

    private void appendToFile(StoreSettings.Audit current, String actor, String action,
                              @Nullable String target, @Nullable String detail, long now) {
        if (current.file().isEmpty()) {
            return;
        }
        String line = clock.format(now) + "\t" + actor + "\t" + action
            + "\t" + (target == null ? "-" : target)
            + "\t" + (detail == null ? "-" : detail.replace('\n', ' ').replace('\r', ' '))
            + System.lineSeparator();
        File target_ = new File(dataFolder, current.file());
        synchronized (fileLock) {
            try {
                File parent = target_.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    return;
                }
                try (Writer writer = Files.newBufferedWriter(target_.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    writer.write(line);
                }
            } catch (IOException failure) {
                logger.log(Level.WARNING, "寫入稽核檔失敗: " + target_.getName(), failure);
            }
        }
    }
}
