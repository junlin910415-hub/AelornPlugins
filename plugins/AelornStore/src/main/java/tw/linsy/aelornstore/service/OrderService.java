package tw.linsy.aelornstore.service;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.AelornStorePlugin;
import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.db.StoreDao;
import tw.linsy.aelornstore.model.Money;
import tw.linsy.aelornstore.model.OrderStatus;
import tw.linsy.aelornstore.model.OrderType;
import tw.linsy.aelornstore.model.PriceCurrency;
import tw.linsy.aelornstore.model.Product;
import tw.linsy.aelornstore.model.StoreOrder;
import tw.linsy.aelornstore.model.VipRecord;
import tw.linsy.aelornstore.util.AuditLog;
import tw.linsy.aelornstore.util.Clock;
import tw.linsy.aelorn.lib.text.Messages;

/**
 * Creating, settling, cancelling and refunding orders.
 *
 * A top-up and an in-game purchase are the same object here, differing only in
 * where the money came from: a top-up waits for a payment channel and is settled
 * by the web backend, a purchase settles against the player's own balance in one
 * transaction. Both end up in the delivery queue, so there is exactly one code
 * path that grants anything to a player.
 *
 * <p>All methods block on the database and must be called off the main thread.
 */
public final class OrderService {

    /** Why an operation did or did not go through. The command layer maps these to messages. */
    public enum Status {
        OK,
        DISABLED,
        AMOUNT_TOO_LOW,
        AMOUNT_TOO_HIGH,
        NOT_WHOLE_UNITS,
        DAILY_LIMIT,
        TOO_MANY_PENDING,
        ACCOUNT_TOO_NEW,
        NO_PROVIDER,
        NO_PERMISSION,
        INSUFFICIENT_CREDIT,
        INSUFFICIENT_VAULT,
        VAULT_UNAVAILABLE,
        OUT_OF_STOCK,
        LIMIT_REACHED,
        DAILY_LIMIT_REACHED,
        NOT_FOUND,
        WRONG_STATE,
        ERROR
    }

    /** {@code detail} carries whatever number the matching message needs to show. */
    public record Result(Status status, @Nullable StoreOrder order, String detail) {

        public boolean ok() {
            return status == Status.OK;
        }

        static Result fail(Status status) {
            return new Result(status, null, "");
        }

        static Result fail(Status status, Object detail) {
            return new Result(status, null, String.valueOf(detail));
        }

        static Result of(StoreOrder order) {
            return new Result(Status.OK, order, "");
        }
    }

    private static final String ORDER_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int RANDOM_SUFFIX_LENGTH = 6;
    private static final int ORDER_NO_MAX_LENGTH = 20;
    private static final long VAULT_TIMEOUT_MILLIS = 5_000L;

    private final AelornStorePlugin plugin;
    private final StoreDao dao;
    private final WalletService wallet;
    private final VipService vip;
    private final ActionRunner actions;
    private final AuditLog audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderService(AelornStorePlugin plugin, StoreDao dao, WalletService wallet, VipService vip,
                        ActionRunner actions, AuditLog audit, Clock clock) {
        this.plugin = plugin;
        this.dao = dao;
        this.wallet = wallet;
        this.vip = vip;
        this.actions = actions;
        this.audit = audit;
        this.clock = clock;
    }

    // ── 儲值 ────────────────────────────────────────────────────────────────

    /**
     * Creates a top-up order and hands it to a payment channel.
     *
     * Every risk control lives here rather than in the UI, because the same
     * checks have to hold whether the order came from the menu, from
     * {@code /store topup}, or from an admin acting on a player's behalf.
     */
    public Result createTopup(Player player, long amountMinor, String providerId) {
        StoreSettings settings = plugin.settings();
        StoreSettings.Topup topup = settings.topup();
        if (!topup.enabled()) {
            return Result.fail(Status.DISABLED);
        }
        if (amountMinor < topup.minAmountMinor()) {
            return Result.fail(Status.AMOUNT_TOO_LOW, settings.money().formatMinor(topup.minAmountMinor()));
        }
        if (amountMinor > topup.maxAmountMinor()) {
            return Result.fail(Status.AMOUNT_TOO_HIGH, settings.money().formatMinor(topup.maxAmountMinor()));
        }
        if (!settings.money().acceptable(amountMinor)) {
            return Result.fail(Status.NOT_WHOLE_UNITS);
        }
        if (topup.accountAgeHours() > 0) {
            long ageMillis = clock.now() - player.getFirstPlayed();
            if (ageMillis < topup.accountAgeHours() * 3_600_000L) {
                return Result.fail(Status.ACCOUNT_TOO_NEW, topup.accountAgeHours());
            }
        }
        StoreSettings.ProviderSettings provider = settings.resolveProvider(providerId);
        if (provider == null) {
            return Result.fail(Status.NO_PROVIDER, providerId);
        }
        UUID playerId = player.getUniqueId();
        try {
            if (dao.countOpenOrders(playerId) >= topup.maxPendingOrders()) {
                return Result.fail(Status.TOO_MANY_PENDING, dao.countOpenOrders(playerId));
            }
            if (topup.dailyLimitMinor() > 0) {
                long used = dao.paidAmountSince(playerId, clock.startOfDay(clock.now()));
                if (used + amountMinor > topup.dailyLimitMinor()) {
                    return new Result(Status.DAILY_LIMIT, null, settings.money().formatMinor(used));
                }
            }

            long now = clock.now();
            long expiresAt = now + topup.expireMinutes() * 60_000L;
            long credit = Money.creditFor(amountMinor, settings.credit().minorUnitsPerCredit());
            String playerName = player.getName();

            StoreOrder created = dao.database().transaction(connection -> {
                String orderNo = allocateOrderNo(connection, topup.orderPrefix());
                StoreOrder order = new StoreOrder(orderNo, playerId, playerName, OrderType.TOPUP,
                    null, 1, amountMinor, credit, null, provider.id(), null, null,
                    OrderStatus.PENDING, now, 0L, 0L, expiresAt, 0, null);
                dao.insertOrder(connection, order);
                audit.record(connection, playerName, "ORDER_CREATE", playerId.toString(),
                    "order=" + orderNo + " amount=" + amountMinor + " credit=" + credit
                        + " provider=" + provider.id(), now);
                return order;
            });
            return Result.of(created);
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.SEVERE, "建立儲值訂單失敗。", failure);
            return Result.fail(Status.ERROR);
        }
    }

    /** Grants the credit for a top-up that a payment channel has confirmed. */
    Result settleTopup(StoreOrder order) throws SQLException {
        StoreDao.AdjustResult result = wallet.grant(order.playerId(), order.creditAmount(),
            "TOPUP", order.orderNo(), "儲值訂單 " + order.orderNo());
        if (!result.applied()) {
            // Almost always the balance cap. Refusing to deliver is correct — the
            // money is recorded as paid and an admin can resolve it deliberately.
            return new Result(Status.ERROR, order, "餘額上限或帳本拒絕，餘額 " + result.balance());
        }
        return Result.of(order);
    }

    // ── 商店購買 ────────────────────────────────────────────────────────────

    /**
     * Buys a product with the player's own balance.
     *
     * Stock, per-player and per-day caps are all re-checked inside the same
     * transaction that takes the money and writes the purchase rows — checking
     * them when the menu was drawn would let two fast clicks both pass.
     */
    public Result purchase(Player player, Product product, int quantity) {
        if (quantity <= 0) {
            return Result.fail(Status.ERROR);
        }
        if (!product.visibleTo(player)) {
            return Result.fail(Status.NO_PERMISSION);
        }
        StoreSettings settings = plugin.settings();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        long unitPrice = product.priceFor(player);
        long total = unitPrice * quantity;
        long now = clock.now();
        String dayKey = clock.dayKey(now);

        boolean vaultPaid = false;
        try {
            if (product.currency() == PriceCurrency.VAULT) {
                if (!settings.vault().enabled() || plugin.vault() == null) {
                    return Result.fail(Status.VAULT_UNAVAILABLE);
                }
                VaultBridge bridge = plugin.vault();
                Boolean taken = plugin.schedulers().callOnGlobalRegion(() -> {
                    if (!bridge.has(playerId, total)) {
                        return Boolean.FALSE;
                    }
                    return bridge.withdraw(playerId, total);
                }, Boolean.FALSE, VAULT_TIMEOUT_MILLIS, plugin.getLogger());
                if (!Boolean.TRUE.equals(taken)) {
                    return Result.fail(Status.INSUFFICIENT_VAULT, total);
                }
                vaultPaid = true;
            }

            final boolean paidWithVault = vaultPaid;
            Result outcome = dao.database().transaction(connection -> {
                if (!product.unlimitedStock()) {
                    int sold = dao.purchasedTotal(connection, product.id());
                    if (sold + quantity > product.stock()) {
                        return Result.fail(Status.OUT_OF_STOCK);
                    }
                }
                if (product.limitPerPlayer() > 0) {
                    int owned = dao.purchasedByPlayer(connection, playerId, product.id());
                    if (owned + quantity > product.limitPerPlayer()) {
                        return Result.fail(Status.LIMIT_REACHED, product.limitPerPlayer());
                    }
                }
                if (product.limitPerDay() > 0) {
                    int today = dao.purchasedByPlayerOn(connection, playerId, product.id(), dayKey);
                    if (today + quantity > product.limitPerDay()) {
                        return Result.fail(Status.DAILY_LIMIT_REACHED, product.limitPerDay());
                    }
                }
                if (!paidWithVault) {
                    StoreDao.AdjustResult debit = dao.debit(connection, playerId, total,
                        "PURCHASE", null, product.id(), now);
                    if (!debit.applied()) {
                        return new Result(Status.INSUFFICIENT_CREDIT, null, String.valueOf(debit.balance()));
                    }
                }
                String orderNo = allocateOrderNo(connection, plugin.settings().topup().orderPrefix());
                StoreOrder order = new StoreOrder(orderNo, playerId, playerName, OrderType.PURCHASE,
                    product.id(), quantity, 0L, total, product.currency(), "internal",
                    null, product.currency().name(), OrderStatus.PAID, now, now, 0L, 0L, 0, null);
                dao.insertOrder(connection, order);
                dao.recordPurchase(connection, playerId, product.id(), quantity, orderNo, dayKey, now);
                audit.record(connection, playerName, "PURCHASE", playerId.toString(),
                    "order=" + orderNo + " product=" + product.id() + " x" + quantity
                        + " price=" + total + " currency=" + product.currency(), now);
                return Result.of(order);
            });

            if (!outcome.ok() && paidWithVault) {
                refundVault(playerId, total, "purchase-rollback");
            }
            return outcome;
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.SEVERE, "購買處理失敗: " + product.id(), failure);
            if (vaultPaid) {
                refundVault(playerId, total, "purchase-error");
            }
            return Result.fail(Status.ERROR);
        }
    }

    private void refundVault(UUID playerId, long amount, String reason) {
        VaultBridge bridge = plugin.vault();
        if (bridge == null) {
            plugin.getLogger().severe("需要退回 " + amount + " 遊戲幣給 " + playerId
                + "（" + reason + "），但 Vault 不可用。請人工處理。");
            return;
        }
        plugin.schedulers().global(() -> {
            if (!bridge.deposit(playerId, amount)) {
                plugin.getLogger().severe("退回 " + amount + " 遊戲幣給 " + playerId
                    + "（" + reason + "）失敗。請人工處理。");
            }
        });
        audit.record(reason, "VAULT_ROLLBACK", playerId.toString(), "amount=" + amount);
    }

    // ── 管理操作 ────────────────────────────────────────────────────────────

    /** Manual settlement: marks an order paid without a gateway. Used for bank transfers. */
    public Result approve(String orderNo, String actor) {
        try {
            Optional<StoreOrder> found = dao.findOrder(orderNo);
            if (found.isEmpty()) {
                return Result.fail(Status.NOT_FOUND);
            }
            StoreOrder order = found.get();
            long now = clock.now();
            boolean moved = dao.database().transaction(connection -> {
                boolean applied = dao.markPaid(connection, orderNo,
                    "MANUAL:" + actor, "MANUAL", now);
                if (applied) {
                    audit.record(connection, actor, "ORDER_APPROVE", order.playerId().toString(),
                        "order=" + orderNo + " amount=" + order.amountMinor(), now);
                }
                return applied;
            });
            return moved ? Result.of(order) : new Result(Status.WRONG_STATE, order, order.status().name());
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.SEVERE, "核銷訂單失敗: " + orderNo, failure);
            return Result.fail(Status.ERROR);
        }
    }

    public Result cancel(String orderNo, String actor) {
        try {
            Optional<StoreOrder> found = dao.findOrder(orderNo);
            if (found.isEmpty()) {
                return Result.fail(Status.NOT_FOUND);
            }
            StoreOrder order = found.get();
            long now = clock.now();
            boolean moved = dao.database().transaction(connection -> {
                boolean applied = dao.markCancelled(connection, orderNo);
                if (applied) {
                    audit.record(connection, actor, "ORDER_CANCEL", order.playerId().toString(),
                        "order=" + orderNo, now);
                }
                return applied;
            });
            return moved ? Result.of(order) : new Result(Status.WRONG_STATE, order, order.status().name());
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.SEVERE, "取消訂單失敗: " + orderNo, failure);
            return Result.fail(Status.ERROR);
        }
    }

    /**
     * Reverses a settled order.
     *
     * This is the path a card chargeback takes, so it has to work even when the
     * player has already spent what they were given: if the balance no longer
     * covers the clawback it is taken down to zero and the shortfall is audited,
     * rather than the refund silently failing and leaving the server out of pocket
     * with no record of why.
     */
    public Result refund(String orderNo, String actor) {
        try {
            Optional<StoreOrder> found = dao.findOrder(orderNo);
            if (found.isEmpty()) {
                return Result.fail(Status.NOT_FOUND);
            }
            StoreOrder order = found.get();
            if (!order.status().paid() || order.status() == OrderStatus.REFUNDED) {
                return new Result(Status.WRONG_STATE, order, order.status().name());
            }
            long now = clock.now();
            long clawback = order.type() == OrderType.TOPUP ? order.creditAmount() : 0L;

            boolean moved = dao.database().transaction(connection -> {
                if (!dao.markRefunded(connection, orderNo)) {
                    return false;
                }
                // Deleting the purchase rows returns both the stock and the
                // player's per-player / per-day allowance in one step.
                dao.deletePurchases(connection, orderNo);

                if (clawback > 0) {
                    StoreDao.AdjustResult debit = dao.debit(connection, order.playerId(), clawback,
                        "REFUND", orderNo, "退款回收", now);
                    if (!debit.applied()) {
                        long balance = debit.balance();
                        dao.setBalance(connection, order.playerId(), 0L, "REFUND", orderNo,
                            "退款回收：餘額不足，已歸零", now);
                        audit.record(connection, actor, "REFUND_SHORTFALL", order.playerId().toString(),
                            "order=" + orderNo + " needed=" + clawback + " had=" + balance, now);
                    }
                }
                if (order.type() == OrderType.PURCHASE && order.currency() == PriceCurrency.CREDIT) {
                    dao.credit(connection, order.playerId(), order.creditAmount(),
                        plugin.settings().credit().maxBalance(), "REFUND", orderNo, "購買退款", now);
                }
                audit.record(connection, actor, "ORDER_REFUND", order.playerId().toString(),
                    "order=" + orderNo + " type=" + order.type() + " clawback=" + clawback, now);
                return true;
            });
            if (!moved) {
                return new Result(Status.WRONG_STATE, order, order.status().name());
            }

            if (order.type() == OrderType.PURCHASE && order.currency() == PriceCurrency.VAULT) {
                refundVault(order.playerId(), order.creditAmount(), "order-refund");
            }
            revokeGrants(order);
            return Result.of(order);
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.SEVERE, "退款處理失敗: " + orderNo, failure);
            return Result.fail(Status.ERROR);
        }
    }

    /** Runs the product's revoke actions and drops any VIP this order paid for. */
    private void revokeGrants(StoreOrder order) throws SQLException {
        ActionRunner.Context context = new ActionRunner.Context(order.playerId(), order.playerName(),
            order.orderNo(), order.productId(), order.quantity(), order.amountMinor(), order.creditAmount());
        if (order.productId() != null) {
            Product product = plugin.catalog().products().get(order.productId());
            if (product != null && !product.revokeActions().isEmpty()) {
                actions.run(product.revokeActions(), context);
            }
        }
        Optional<VipRecord> held = dao.findVip(order.playerId());
        if (held.isPresent() && order.orderNo().equals(held.get().sourceOrder())) {
            vip.revoke(order.playerId(), order.playerName(), "REFUND:" + order.orderNo());
        }
    }

    /** Puts a stuck order back in the delivery queue. */
    public Result redeliver(String orderNo, String actor) {
        try {
            Optional<StoreOrder> found = dao.findOrder(orderNo);
            if (found.isEmpty()) {
                return Result.fail(Status.NOT_FOUND);
            }
            StoreOrder order = found.get();
            long now = clock.now();
            boolean moved = dao.database().transaction(connection -> {
                boolean applied = dao.requeue(connection, orderNo);
                if (applied) {
                    audit.record(connection, actor, "ORDER_REDELIVER", order.playerId().toString(),
                        "order=" + orderNo, now);
                }
                return applied;
            });
            return moved ? Result.of(order) : new Result(Status.WRONG_STATE, order, order.status().name());
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.SEVERE, "重新發貨失敗: " + orderNo, failure);
            return Result.fail(Status.ERROR);
        }
    }

    /** Expires unpaid orders past their window, telling anyone online what happened. */
    public int sweepExpired(int limit) {
        try {
            List<StoreOrder> expired = dao.reapExpired(clock.now(), limit);
            for (StoreOrder order : expired) {
                audit.record("SYSTEM", "ORDER_EXPIRE", order.playerId().toString(), "order=" + order.orderNo());
                Player player = Bukkit.getPlayer(order.playerId());
                if (player != null) {
                    player.getScheduler().execute(plugin, () -> plugin.messages()
                        .send(player, "order.expired-notice", "order", order.orderNo()), null, 1L);
                }
            }
            return expired.size();
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.WARNING, "逾期訂單掃描失敗。", failure);
            return 0;
        }
    }

    public Optional<StoreOrder> find(String orderNo) throws SQLException {
        return dao.findOrder(normalise(orderNo));
    }

    public List<StoreOrder> recent(UUID playerId, int limit) throws SQLException {
        return dao.recentOrders(playerId, limit);
    }

    public static String normalise(String orderNo) {
        return orderNo.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Builds an order number that fits every gateway's field limits.
     *
     * ECPay's {@code MerchantTradeNo} caps at 20 alphanumeric characters, which
     * this stays inside: prefix (≤6) + base-36 seconds (≤7) + 6 random. The random
     * tail comes from {@link SecureRandom} because the number appears in the
     * payment URL — a guessable one would let a stranger open someone else's
     * payment page.
     */
    private String allocateOrderNo(java.sql.Connection connection, String prefix) throws SQLException {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder builder = new StringBuilder(prefix);
            builder.append(Long.toString(clock.now() / 1000L, 36).toUpperCase(Locale.ROOT));
            for (int index = 0; index < RANDOM_SUFFIX_LENGTH; index++) {
                builder.append(ORDER_ALPHABET.charAt(random.nextInt(ORDER_ALPHABET.length())));
            }
            String candidate = builder.length() > ORDER_NO_MAX_LENGTH
                ? builder.substring(0, ORDER_NO_MAX_LENGTH)
                : builder.toString();
            if (!dao.orderExists(connection, candidate)) {
                return candidate;
            }
        }
        throw new SQLException("連續 8 次無法產生不重複的訂單號，請檢查資料庫。");
    }
}
