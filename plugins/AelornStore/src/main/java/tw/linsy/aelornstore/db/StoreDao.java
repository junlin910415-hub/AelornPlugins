package tw.linsy.aelornstore.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.model.AuditEntry;
import tw.linsy.aelornstore.model.OrderStatus;
import tw.linsy.aelornstore.model.OrderType;
import tw.linsy.aelornstore.model.PriceCurrency;
import tw.linsy.aelornstore.model.StoreOrder;
import tw.linsy.aelornstore.model.VipRecord;

/**
 * Every SQL statement in the plugin.
 *
 * Two rules hold throughout. First, no state ever moves forward except through a
 * conditional {@code UPDATE ... WHERE status = ?}: a replayed gateway callback, a
 * double-clicked button and two servers polling the same database all collapse to
 * one winner, because only one of them sees a row count of 1. Second, anything
 * that touches a balance takes a {@link Connection} from the caller so it can be
 * enrolled in the caller's transaction alongside its ledger row.
 */
public final class StoreDao {

    /** Result of a balance change: whether the guard passed, and the balance now. */
    public record AdjustResult(boolean applied, long balance) { }

    private static final String ORDER_COLUMNS =
        "order_no, uuid, player_name, type, product_id, quantity, amount_minor, credit_amount, "
        + "currency, provider, provider_trade_no, pay_method, status, created_at, paid_at, "
        + "delivered_at, expires_at, attempts, next_attempt_at, fail_reason";

    private final Database database;
    private final String wallet;
    private final String walletTx;
    private final String orders;
    private final String vip;
    private final String purchases;
    private final String audit;
    private final String meta;

    public StoreDao(Database database) {
        this.database = database;
        String prefix = database.tablePrefix();
        this.wallet = prefix + "wallet";
        this.walletTx = prefix + "wallet_tx";
        this.orders = prefix + "orders";
        this.vip = prefix + "vip";
        this.purchases = prefix + "purchases";
        this.audit = prefix + "audit";
        this.meta = prefix + "meta";
    }

    public Database database() {
        return database;
    }

    // ── 錢包 ────────────────────────────────────────────────────────────────

    public long balance(UUID playerId) throws SQLException {
        return database.execute(connection -> balance(connection, playerId));
    }

    private long balance(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement =
                 connection.prepareStatement("SELECT balance FROM " + wallet + " WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }

    /**
     * Adds credit, refusing to exceed {@code maxBalance}, and writes the matching
     * ledger row. The cap is enforced in the {@code WHERE} clause rather than by a
     * read-then-write, so two simultaneous grants cannot both pass the check.
     */
    public AdjustResult credit(Connection connection, UUID playerId, long amount, long maxBalance,
                               String kind, @Nullable String ref, @Nullable String note, long now)
            throws SQLException {
        if (amount <= 0) {
            return new AdjustResult(false, balance(connection, playerId));
        }
        ensureWalletRow(connection, playerId, now);
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + wallet + " SET balance = balance + ?, updated_at = ? "
                + "WHERE uuid = ? AND balance + ? <= ?")) {
            statement.setLong(1, amount);
            statement.setLong(2, now);
            statement.setString(3, playerId.toString());
            statement.setLong(4, amount);
            statement.setLong(5, maxBalance);
            if (statement.executeUpdate() == 0) {
                return new AdjustResult(false, balance(connection, playerId));
            }
        }
        long after = balance(connection, playerId);
        writeLedger(connection, playerId, amount, after, kind, ref, note, now);
        return new AdjustResult(true, after);
    }

    /** Removes credit only if the balance covers it. Never lets a balance go negative. */
    public AdjustResult debit(Connection connection, UUID playerId, long amount,
                              String kind, @Nullable String ref, @Nullable String note, long now)
            throws SQLException {
        if (amount <= 0) {
            return new AdjustResult(false, balance(connection, playerId));
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + wallet + " SET balance = balance - ?, updated_at = ? "
                + "WHERE uuid = ? AND balance >= ?")) {
            statement.setLong(1, amount);
            statement.setLong(2, now);
            statement.setString(3, playerId.toString());
            statement.setLong(4, amount);
            if (statement.executeUpdate() == 0) {
                return new AdjustResult(false, balance(connection, playerId));
            }
        }
        long after = balance(connection, playerId);
        writeLedger(connection, playerId, -amount, after, kind, ref, note, now);
        return new AdjustResult(true, after);
    }

    /** Overwrites a balance outright. Admin-only; still produces a ledger row. */
    public AdjustResult setBalance(Connection connection, UUID playerId, long target,
                                   String kind, @Nullable String ref, @Nullable String note, long now)
            throws SQLException {
        ensureWalletRow(connection, playerId, now);
        long before = balance(connection, playerId);
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + wallet + " SET balance = ?, updated_at = ? WHERE uuid = ?")) {
            statement.setLong(1, Math.max(0L, target));
            statement.setLong(2, now);
            statement.setString(3, playerId.toString());
            statement.executeUpdate();
        }
        long after = balance(connection, playerId);
        writeLedger(connection, playerId, after - before, after, kind, ref, note, now);
        return new AdjustResult(true, after);
    }

    private void ensureWalletRow(Connection connection, UUID playerId, long now) throws SQLException {
        try (PreparedStatement probe =
                 connection.prepareStatement("SELECT 1 FROM " + wallet + " WHERE uuid = ?")) {
            probe.setString(1, playerId.toString());
            try (ResultSet rows = probe.executeQuery()) {
                if (rows.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO " + wallet + " (uuid, balance, updated_at) VALUES (?, 0, ?)")) {
            insert.setString(1, playerId.toString());
            insert.setLong(2, now);
            insert.executeUpdate();
        } catch (SQLException raced) {
            // Another transaction inserted the same row first; that is the outcome we wanted.
            if (balanceRowMissing(connection, playerId)) {
                throw raced;
            }
        }
    }

    private boolean balanceRowMissing(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement probe =
                 connection.prepareStatement("SELECT 1 FROM " + wallet + " WHERE uuid = ?")) {
            probe.setString(1, playerId.toString());
            try (ResultSet rows = probe.executeQuery()) {
                return !rows.next();
            }
        }
    }

    private void writeLedger(Connection connection, UUID playerId, long delta, long after,
                             String kind, @Nullable String ref, @Nullable String note, long now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + walletTx + " (uuid, delta, balance_after, kind, ref, note, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, delta);
            statement.setLong(3, after);
            statement.setString(4, kind);
            statement.setString(5, ref);
            statement.setString(6, truncate(note, 255));
            statement.setLong(7, now);
            statement.executeUpdate();
        }
    }

    // ── 訂單 ────────────────────────────────────────────────────────────────

    public void insertOrder(Connection connection, StoreOrder order) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + orders + " (" + ORDER_COLUMNS + ") "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, order.orderNo());
            statement.setString(2, order.playerId().toString());
            statement.setString(3, order.playerName());
            statement.setString(4, order.type().name());
            statement.setString(5, order.productId());
            statement.setInt(6, order.quantity());
            statement.setLong(7, order.amountMinor());
            statement.setLong(8, order.creditAmount());
            statement.setString(9, order.currency() == null ? null : order.currency().name());
            statement.setString(10, order.provider());
            statement.setString(11, order.providerTradeNo());
            statement.setString(12, order.payMethod());
            statement.setString(13, order.status().name());
            statement.setLong(14, order.createdAt());
            statement.setLong(15, order.paidAt());
            statement.setLong(16, order.deliveredAt());
            statement.setLong(17, order.expiresAt());
            statement.setInt(18, order.attempts());
            statement.setLong(19, 0L);
            statement.setString(20, truncate(order.failReason(), 255));
            statement.executeUpdate();
        }
    }

    public Optional<StoreOrder> findOrder(String orderNo) throws SQLException {
        return database.execute(connection -> findOrder(connection, orderNo));
    }

    public Optional<StoreOrder> findOrder(Connection connection, String orderNo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT " + ORDER_COLUMNS + " FROM " + orders + " WHERE order_no = ?")) {
            statement.setString(1, orderNo);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readOrder(rows)) : Optional.empty();
            }
        }
    }

    public List<StoreOrder> recentOrders(UUID playerId, int limit) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + ORDER_COLUMNS + " FROM " + orders
                    + " WHERE uuid = ? ORDER BY created_at DESC LIMIT ?")) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, limit);
                return readOrders(statement);
            }
        });
    }

    public int countOpenOrders(UUID playerId) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + orders + " WHERE uuid = ? AND status IN ('CREATED','PENDING')")) {
                statement.setString(1, playerId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            }
        });
    }

    /** Real money already taken from this player since {@code since}; drives the daily cap. */
    public long paidAmountSince(UUID playerId, long since) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(SUM(amount_minor), 0) FROM " + orders
                    + " WHERE uuid = ? AND paid_at >= ? AND status IN "
                    + "('PAID','DELIVERING','DELIVERED','NEEDS_ATTENTION')")) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, since);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? rows.getLong(1) : 0L;
                }
            }
        });
    }

    public int countByStatus(OrderStatus status) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + orders + " WHERE status = ?")) {
                statement.setString(1, status.name());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            }
        });
    }

    /** Moves an order from one exact status to another. Returns false if it had already moved. */
    public boolean transition(Connection connection, String orderNo, OrderStatus from, OrderStatus to)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + orders + " SET status = ? WHERE order_no = ? AND status = ?")) {
            statement.setString(1, to.name());
            statement.setString(2, orderNo);
            statement.setString(3, from.name());
            return statement.executeUpdate() == 1;
        }
    }

    /** Attaches the chosen channel and moves CREATED → PENDING. */
    public boolean markPending(Connection connection, String orderNo, String provider, long expiresAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + orders + " SET status = 'PENDING', provider = ?, expires_at = ? "
                + "WHERE order_no = ? AND status = 'CREATED'")) {
            statement.setString(1, provider);
            statement.setLong(2, expiresAt);
            statement.setString(3, orderNo);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Records payment. Only an unpaid order can be marked paid, so a gateway that
     * sends its callback three times still results in exactly one delivery.
     */
    public boolean markPaid(Connection connection, String orderNo, @Nullable String tradeNo,
                            @Nullable String payMethod, long paidAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + orders + " SET status = 'PAID', provider_trade_no = ?, pay_method = ?, "
                + "paid_at = ?, next_attempt_at = 0 "
                + "WHERE order_no = ? AND status IN ('CREATED','PENDING')")) {
            statement.setString(1, tradeNo);
            statement.setString(2, payMethod);
            statement.setLong(3, paidAt);
            statement.setString(4, orderNo);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Takes ownership of up to {@code limit} paid orders whose backoff has elapsed.
     *
     * The select and the claim are separate statements on purpose: the claim is
     * the conditional update, so if a second server (or a second poll tick) reads
     * the same candidate list, only one of them wins each row.
     */
    public List<StoreOrder> claimForDelivery(int limit, long now) throws SQLException {
        List<StoreOrder> candidates = database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + ORDER_COLUMNS + " FROM " + orders
                    + " WHERE status = 'PAID' AND next_attempt_at <= ? ORDER BY paid_at ASC LIMIT ?")) {
                statement.setLong(1, now);
                statement.setInt(2, limit);
                return readOrders(statement);
            }
        });
        List<StoreOrder> claimed = new ArrayList<>(candidates.size());
        for (StoreOrder candidate : candidates) {
            boolean won = database.execute(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + orders + " SET status = 'DELIVERING', attempts = attempts + 1 "
                        + "WHERE order_no = ? AND status = 'PAID'")) {
                    statement.setString(1, candidate.orderNo());
                    return statement.executeUpdate() == 1;
                }
            });
            if (won) {
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    public boolean markDelivered(Connection connection, String orderNo, long deliveredAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + orders + " SET status = 'DELIVERED', delivered_at = ?, fail_reason = NULL "
                + "WHERE order_no = ? AND status = 'DELIVERING'")) {
            statement.setLong(1, deliveredAt);
            statement.setString(2, orderNo);
            return statement.executeUpdate() == 1;
        }
    }

    /** Returns a failed delivery to the queue with a backoff, or parks it for a human. */
    public void markDeliveryFailed(Connection connection, String orderNo, String reason,
                                   boolean giveUp, long nextAttemptAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + orders + " SET status = ?, fail_reason = ?, next_attempt_at = ? "
                + "WHERE order_no = ? AND status = 'DELIVERING'")) {
            statement.setString(1, giveUp ? OrderStatus.NEEDS_ATTENTION.name() : OrderStatus.PAID.name());
            statement.setString(2, truncate(reason, 255));
            statement.setLong(3, giveUp ? 0L : nextAttemptAt);
            statement.setString(4, orderNo);
            statement.executeUpdate();
        }
    }

    /**
     * Returns a claimed order to the queue without counting the attempt.
     *
     * Used when the only thing missing is the player: waiting for someone to log
     * in is not a failure, and burning retry attempts on it would push a perfectly
     * good order into NEEDS_ATTENTION while they were asleep.
     */
    public void deferDelivery(Connection connection, String orderNo, long nextAttemptAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + orders + " SET status = 'PAID', next_attempt_at = ?, "
                + "attempts = CASE WHEN attempts > 0 THEN attempts - 1 ELSE 0 END "
                + "WHERE order_no = ? AND status = 'DELIVERING'")) {
            statement.setLong(1, nextAttemptAt);
            statement.setString(2, orderNo);
            statement.executeUpdate();
        }
    }

    /**
     * Orders claimed for delivery before {@code cutoff} and never finished.
     *
     * Only a crash produces these: the claim is a single statement, so a live
     * server either completes the delivery or records a failure.
     */
    public List<StoreOrder> staleClaims(long cutoff, int limit) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + ORDER_COLUMNS + " FROM " + orders
                    + " WHERE status = 'DELIVERING' AND paid_at < ? ORDER BY paid_at ASC LIMIT ?")) {
                statement.setLong(1, cutoff);
                statement.setInt(2, limit);
                return readOrders(statement);
            }
        });
    }

    /** How many settled orders are still waiting to reach this player. */
    public int awaitingDeliveryFor(UUID playerId) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + orders + " WHERE uuid = ? AND status IN "
                    + "('PAID','DELIVERING','NEEDS_ATTENTION')")) {
                statement.setString(1, playerId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            }
        });
    }

    /** Clears the backoff on a player's queued orders so the next poll picks them up. */
    public int releaseBackoffFor(UUID playerId) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + orders + " SET next_attempt_at = 0 "
                    + "WHERE uuid = ? AND status = 'PAID' AND next_attempt_at > 0")) {
                statement.setString(1, playerId.toString());
                return statement.executeUpdate();
            }
        });
    }

    /** Puts a stuck order back in the queue. Used by {@code /astore redeliver}. */
    public boolean requeue(Connection connection, String orderNo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + orders + " SET status = 'PAID', next_attempt_at = 0, attempts = 0 "
                + "WHERE order_no = ? AND status IN ('NEEDS_ATTENTION','DELIVERING')")) {
            statement.setString(1, orderNo);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean markRefunded(Connection connection, String orderNo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + orders + " SET status = 'REFUNDED' "
                + "WHERE order_no = ? AND status IN ('PAID','DELIVERED','NEEDS_ATTENTION')")) {
            statement.setString(1, orderNo);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean markCancelled(Connection connection, String orderNo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + orders + " SET status = 'CANCELLED' "
                + "WHERE order_no = ? AND status IN ('CREATED','PENDING')")) {
            statement.setString(1, orderNo);
            return statement.executeUpdate() == 1;
        }
    }

    /** Expires unpaid orders past their window and reports which ones moved. */
    public List<StoreOrder> reapExpired(long now, int limit) throws SQLException {
        List<StoreOrder> candidates = database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + ORDER_COLUMNS + " FROM " + orders
                    + " WHERE status IN ('CREATED','PENDING') AND expires_at > 0 AND expires_at <= ? "
                    + "ORDER BY expires_at ASC LIMIT ?")) {
                statement.setLong(1, now);
                statement.setInt(2, limit);
                return readOrders(statement);
            }
        });
        List<StoreOrder> expired = new ArrayList<>(candidates.size());
        for (StoreOrder candidate : candidates) {
            boolean won = database.execute(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + orders + " SET status = 'EXPIRED' "
                        + "WHERE order_no = ? AND status IN ('CREATED','PENDING')")) {
                    statement.setString(1, candidate.orderNo());
                    return statement.executeUpdate() == 1;
                }
            });
            if (won) {
                expired.add(candidate);
            }
        }
        return expired;
    }

    public boolean orderExists(Connection connection, String orderNo) throws SQLException {
        try (PreparedStatement statement =
                 connection.prepareStatement("SELECT 1 FROM " + orders + " WHERE order_no = ?")) {
            statement.setString(1, orderNo);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    // ── 購買紀錄（庫存與限購） ───────────────────────────────────────────────

    public void recordPurchase(Connection connection, UUID playerId, String productId, int quantity,
                               String orderNo, String dayKey, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + purchases + " (uuid, product_id, quantity, order_no, day_key, created_at) "
                + "VALUES (?,?,?,?,?,?)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, productId);
            statement.setInt(3, quantity);
            statement.setString(4, orderNo);
            statement.setString(5, dayKey);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
    }

    public int purchasedByPlayer(Connection connection, UUID playerId, String productId) throws SQLException {
        return count(connection,
            "SELECT COALESCE(SUM(quantity), 0) FROM " + purchases + " WHERE uuid = ? AND product_id = ?",
            playerId.toString(), productId);
    }

    public int purchasedByPlayerOn(Connection connection, UUID playerId, String productId, String dayKey)
            throws SQLException {
        return count(connection,
            "SELECT COALESCE(SUM(quantity), 0) FROM " + purchases
                + " WHERE uuid = ? AND product_id = ? AND day_key = ?",
            playerId.toString(), productId, dayKey);
    }

    public int purchasedTotal(Connection connection, String productId) throws SQLException {
        return count(connection,
            "SELECT COALESCE(SUM(quantity), 0) FROM " + purchases + " WHERE product_id = ?",
            productId);
    }

    /** Deleting the rows for a refunded order restores both stock and the player's allowance. */
    public int deletePurchases(Connection connection, String orderNo) throws SQLException {
        try (PreparedStatement statement =
                 connection.prepareStatement("DELETE FROM " + purchases + " WHERE order_no = ?")) {
            statement.setString(1, orderNo);
            return statement.executeUpdate();
        }
    }

    // ── VIP ─────────────────────────────────────────────────────────────────

    public Optional<VipRecord> findVip(UUID playerId) throws SQLException {
        return database.execute(connection -> findVip(connection, playerId));
    }

    public Optional<VipRecord> findVip(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT uuid, tier, expires_at, updated_at, source_order FROM " + vip + " WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readVip(rows)) : Optional.empty();
            }
        }
    }

    /** Portable upsert: update first, insert only when nothing was there to update. */
    public void saveVip(Connection connection, VipRecord record) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
            "UPDATE " + vip + " SET tier = ?, expires_at = ?, updated_at = ?, source_order = ? "
                + "WHERE uuid = ?")) {
            update.setString(1, record.tierId());
            update.setLong(2, record.expiresAt());
            update.setLong(3, record.updatedAt());
            update.setString(4, record.sourceOrder());
            update.setString(5, record.playerId().toString());
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO " + vip + " (uuid, tier, expires_at, updated_at, source_order) VALUES (?,?,?,?,?)")) {
            insert.setString(1, record.playerId().toString());
            insert.setString(2, record.tierId());
            insert.setLong(3, record.expiresAt());
            insert.setLong(4, record.updatedAt());
            insert.setString(5, record.sourceOrder());
            insert.executeUpdate();
        }
    }

    public void deleteVip(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement =
                 connection.prepareStatement("DELETE FROM " + vip + " WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    /** Memberships whose time has run out. Permanent rows ({@code expires_at = 0}) are excluded. */
    public List<VipRecord> expiredVips(long now, int limit) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid, tier, expires_at, updated_at, source_order FROM " + vip
                    + " WHERE expires_at > 0 AND expires_at <= ? ORDER BY expires_at ASC LIMIT ?")) {
                statement.setLong(1, now);
                statement.setInt(2, limit);
                List<VipRecord> found = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        found.add(readVip(rows));
                    }
                }
                return found;
            }
        });
    }

    // ── 稽核 ────────────────────────────────────────────────────────────────

    public void audit(Connection connection, String actor, String action,
                      @Nullable String target, @Nullable String detail, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + audit + " (actor, action, target, detail, created_at) VALUES (?,?,?,?,?)")) {
            statement.setString(1, truncate(actor, 64));
            statement.setString(2, truncate(action, 64));
            statement.setString(3, truncate(target, 64));
            statement.setString(4, truncate(detail, 512));
            statement.setLong(5, now);
            statement.executeUpdate();
        }
    }

    public List<AuditEntry> auditFor(String target, int limit) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, actor, action, target, detail, created_at FROM " + audit
                    + " WHERE target = ? ORDER BY created_at DESC LIMIT ?")) {
                statement.setString(1, target);
                statement.setInt(2, limit);
                List<AuditEntry> found = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        found.add(new AuditEntry(rows.getLong(1), rows.getString(2), rows.getString(3),
                            rows.getString(4), rows.getString(5), rows.getLong(6)));
                    }
                }
                return found;
            }
        });
    }

    public int purgeAudit(long before) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement =
                     connection.prepareStatement("DELETE FROM " + audit + " WHERE created_at < ?")) {
                statement.setLong(1, before);
                return statement.executeUpdate();
            }
        });
    }

    // ── meta ────────────────────────────────────────────────────────────────

    public void setMeta(String key, String value) throws SQLException {
        database.execute(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + meta + " SET meta_value = ? WHERE meta_key = ?")) {
                update.setString(1, value);
                update.setString(2, key);
                if (update.executeUpdate() > 0) {
                    return null;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + meta + " (meta_key, meta_value) VALUES (?, ?)")) {
                insert.setString(1, key);
                insert.setString(2, value);
                insert.executeUpdate();
            }
            return null;
        });
    }

    public Optional<String> meta(String key) throws SQLException {
        return database.execute(connection -> {
            try (PreparedStatement statement =
                     connection.prepareStatement("SELECT meta_value FROM " + meta + " WHERE meta_key = ?")) {
                statement.setString(1, key);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.ofNullable(rows.getString(1)) : Optional.<String>empty();
                }
            }
        });
    }

    // ── 讀取輔助 ────────────────────────────────────────────────────────────

    private int count(Connection connection, String sql, String... arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setString(index + 1, arguments[index]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    private List<StoreOrder> readOrders(PreparedStatement statement) throws SQLException {
        List<StoreOrder> found = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                found.add(readOrder(rows));
            }
        }
        return found;
    }

    private StoreOrder readOrder(ResultSet rows) throws SQLException {
        OrderStatus status = OrderStatus.parse(rows.getString("status"));
        OrderType type = OrderType.parse(rows.getString("type"));
        String rawCurrency = rows.getString("currency");
        PriceCurrency currency = null;
        if (rawCurrency != null) {
            try {
                currency = PriceCurrency.valueOf(rawCurrency);
            } catch (IllegalArgumentException unknown) {
                currency = null;
            }
        }
        return new StoreOrder(
            rows.getString("order_no"),
            uuid(rows.getString("uuid")),
            rows.getString("player_name"),
            type == null ? OrderType.PURCHASE : type,
            rows.getString("product_id"),
            rows.getInt("quantity"),
            rows.getLong("amount_minor"),
            rows.getLong("credit_amount"),
            currency,
            rows.getString("provider"),
            rows.getString("provider_trade_no"),
            rows.getString("pay_method"),
            // An unparseable status is treated as needing a human rather than as
            // deliverable — the safe direction when the row is already suspect.
            status == null ? OrderStatus.NEEDS_ATTENTION : status,
            rows.getLong("created_at"),
            rows.getLong("paid_at"),
            rows.getLong("delivered_at"),
            rows.getLong("expires_at"),
            rows.getInt("attempts"),
            rows.getString("fail_reason")
        );
    }

    private VipRecord readVip(ResultSet rows) throws SQLException {
        return new VipRecord(
            uuid(rows.getString("uuid")),
            rows.getString("tier"),
            rows.getLong("expires_at"),
            rows.getLong("updated_at"),
            rows.getString("source_order")
        );
    }

    private static UUID uuid(String raw) throws SQLException {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            throw new SQLException("資料庫中的 UUID 格式無效: " + raw, malformed);
        }
    }

    private static @Nullable String truncate(@Nullable String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
