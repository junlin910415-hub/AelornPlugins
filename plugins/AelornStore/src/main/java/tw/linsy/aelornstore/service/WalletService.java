package tw.linsy.aelornstore.service;

import java.sql.SQLException;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.AelornStorePlugin;
import tw.linsy.aelornstore.db.StoreDao;
import tw.linsy.aelornstore.util.AuditLog;
import tw.linsy.aelornstore.util.Clock;

/**
 * The store-credit balance, and the only path that may change one.
 *
 * Every mutation is a transaction that writes both the balance and its ledger
 * row, and every mutation is audited. Nothing else in the plugin calls
 * {@link StoreDao#credit} or {@link StoreDao#debit} directly — routing them all
 * through here is what guarantees that a balance can always be explained by the
 * rows behind it.
 *
 * <p>All methods block on the database and must be called off the main thread.
 */
public final class WalletService {

    private final AelornStorePlugin plugin;
    private final StoreDao dao;
    private final AuditLog audit;
    private final Clock clock;

    public WalletService(AelornStorePlugin plugin, StoreDao dao, AuditLog audit, Clock clock) {
        this.plugin = plugin;
        this.dao = dao;
        this.audit = audit;
        this.clock = clock;
    }

    public long balance(UUID playerId) throws SQLException {
        return dao.balance(playerId);
    }

    /** Adds credit. Fails (without throwing) when the balance cap would be exceeded. */
    public StoreDao.AdjustResult grant(UUID playerId, long amount, String kind,
                                       @Nullable String ref, @Nullable String note) throws SQLException {
        long max = plugin.settings().credit().maxBalance();
        long now = clock.now();
        return dao.database().transaction(connection -> {
            StoreDao.AdjustResult result = dao.credit(connection, playerId, amount, max, kind, ref, note, now);
            audit.record(connection, kind, result.applied() ? "CREDIT_GRANT" : "CREDIT_GRANT_REJECTED",
                playerId.toString(),
                "amount=" + amount + " balance=" + result.balance() + " ref=" + ref, now);
            return result;
        });
    }

    /** Removes credit. Fails (without throwing) when the balance does not cover it. */
    public StoreDao.AdjustResult take(UUID playerId, long amount, String kind,
                                      @Nullable String ref, @Nullable String note) throws SQLException {
        long now = clock.now();
        return dao.database().transaction(connection -> {
            StoreDao.AdjustResult result = dao.debit(connection, playerId, amount, kind, ref, note, now);
            audit.record(connection, kind, result.applied() ? "CREDIT_TAKE" : "CREDIT_TAKE_REJECTED",
                playerId.toString(),
                "amount=" + amount + " balance=" + result.balance() + " ref=" + ref, now);
            return result;
        });
    }

    /** Sets a balance outright. Admin action; the delta still lands in the ledger. */
    public StoreDao.AdjustResult set(UUID playerId, long target, String actor,
                                     @Nullable String note) throws SQLException {
        long max = plugin.settings().credit().maxBalance();
        long clamped = Math.max(0L, Math.min(target, max));
        long now = clock.now();
        return dao.database().transaction(connection -> {
            StoreDao.AdjustResult result =
                dao.setBalance(connection, playerId, clamped, actor, null, note, now);
            audit.record(connection, actor, "CREDIT_SET", playerId.toString(),
                "balance=" + result.balance() + " note=" + note, now);
            return result;
        });
    }
}
