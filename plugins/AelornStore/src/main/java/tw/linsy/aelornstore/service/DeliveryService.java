package tw.linsy.aelornstore.service;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import tw.linsy.aelornstore.AelornStorePlugin;
import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.db.StoreDao;
import tw.linsy.aelornstore.model.OrderType;
import tw.linsy.aelornstore.model.Product;
import tw.linsy.aelornstore.model.StoreAction;
import tw.linsy.aelornstore.model.StoreOrder;
import tw.linsy.aelornstore.util.AuditLog;
import tw.linsy.aelornstore.util.Clock;

/**
 * The one place anything is granted to a player.
 *
 * The web backend never delivers; it only records that money arrived. This poller
 * picks settled orders up, claims each one with a conditional update, and grants
 * its contents exactly once. Everything follows from that split:
 *
 * <ul>
 *   <li>A gateway that fires its callback three times still delivers once.</li>
 *   <li>An offline player costs nothing — the order goes back in the queue and is
 *       retried when they log in, with no attempt burned.</li>
 *   <li>A crash mid-delivery leaves the order in DELIVERING; the retry sweep
 *       returns it to the queue rather than losing it.</li>
 * </ul>
 */
public final class DeliveryService {

    /** Orders stuck in DELIVERING for longer than this are assumed to be crash debris. */
    private static final long STALE_CLAIM_MILLIS = 300_000L;

    private final AelornStorePlugin plugin;
    private final StoreDao dao;
    private final OrderService orders;
    private final ActionRunner actions;
    private final AuditLog audit;
    private final Clock clock;

    public DeliveryService(AelornStorePlugin plugin, StoreDao dao, OrderService orders,
                           ActionRunner actions, AuditLog audit, Clock clock) {
        this.plugin = plugin;
        this.dao = dao;
        this.orders = orders;
        this.actions = actions;
        this.audit = audit;
        this.clock = clock;
    }

    /** One poll cycle. Runs on the async scheduler; never touches the main thread directly. */
    public void pollOnce() {
        StoreSettings.Delivery settings = plugin.settings().delivery();
        try {
            List<StoreOrder> claimed = dao.claimForDelivery(settings.batchSize(), clock.now());
            for (StoreOrder order : claimed) {
                deliver(order, settings);
            }
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.WARNING, "發貨輪詢失敗。", failure);
        }
    }

    private void deliver(StoreOrder order, StoreSettings.Delivery settings) {
        try {
            List<StoreAction> toRun = resolveActions(order);
            if (toRun == null) {
                fail(order, settings, "找不到商品定義: " + order.productId(), true);
                return;
            }
            if (actions.requiresOnlinePlayer(toRun) && Bukkit.getPlayer(order.playerId()) == null) {
                if (!settings.queueOnOffline()) {
                    fail(order, settings, "玩家不在線且未啟用離線佇列", true);
                    return;
                }
                defer(order, settings);
                return;
            }

            if (order.type() == OrderType.TOPUP) {
                OrderService.Result settled = orders.settleTopup(order);
                if (!settled.ok()) {
                    fail(order, settings, settled.detail(), false);
                    return;
                }
            }

            ActionRunner.Context context = new ActionRunner.Context(order.playerId(), order.playerName(),
                order.orderNo(), order.productId(), order.quantity(),
                order.amountMinor(), order.creditAmount());
            if (!actions.run(toRun, context)) {
                defer(order, settings);
                return;
            }

            long now = clock.now();
            boolean marked = dao.database().transaction(connection -> {
                boolean applied = dao.markDelivered(connection, order.orderNo(), now);
                if (applied) {
                    audit.record(connection, "DELIVERY", "ORDER_DELIVERED", order.playerId().toString(),
                        "order=" + order.orderNo() + " summary=" + order.summary(), now);
                }
                return applied;
            });
            if (marked) {
                announce(order, settings);
            }
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.WARNING, "發貨失敗: " + order.orderNo(), failure);
            fail(order, settings, failure.getMessage() == null ? "SQL 例外" : failure.getMessage(), false);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "發貨時發生未預期的錯誤: " + order.orderNo(), failure);
            fail(order, settings, String.valueOf(failure.getMessage()), false);
        }
    }

    /**
     * What this order should grant.
     *
     * A top-up's credit is granted by {@link OrderService#settleTopup} rather than
     * by an action, so top-ups return an empty list here and still deliver.
     * A purchase whose product has since been deleted from shop.yml returns null —
     * that needs a human, not a silent no-op.
     */
    private List<StoreAction> resolveActions(StoreOrder order) {
        if (order.type() == OrderType.TOPUP) {
            return List.of();
        }
        if (order.productId() == null) {
            return null;
        }
        Product product = plugin.catalog().products().get(order.productId());
        return product == null ? null : product.actions();
    }

    private void defer(StoreOrder order, StoreSettings.Delivery settings) throws SQLException {
        long nextAttempt = clock.now() + settings.retryBackoffSeconds() * 1000L;
        dao.database().execute(connection -> {
            dao.deferDelivery(connection, order.orderNo(), nextAttempt);
            return null;
        });
    }

    private void fail(StoreOrder order, StoreSettings.Delivery settings, String reason, boolean giveUpNow) {
        boolean giveUp = giveUpNow || order.attempts() + 1 >= settings.maxAttempts();
        long nextAttempt = clock.now() + settings.retryBackoffSeconds() * 1000L;
        try {
            dao.database().transaction(connection -> {
                dao.markDeliveryFailed(connection, order.orderNo(), reason, giveUp, nextAttempt);
                audit.record(connection, "DELIVERY",
                    giveUp ? "ORDER_NEEDS_ATTENTION" : "ORDER_DELIVERY_RETRY",
                    order.playerId().toString(),
                    "order=" + order.orderNo() + " attempts=" + (order.attempts() + 1)
                        + " reason=" + reason, clock.now());
                return null;
            });
        } catch (SQLException nested) {
            plugin.getLogger().log(Level.SEVERE,
                "無法標記發貨失敗狀態: " + order.orderNo() + "。請人工檢查此訂單。", nested);
            return;
        }
        if (giveUp) {
            plugin.getLogger().severe("訂單 " + order.orderNo() + " 已達重試上限，轉人工處理。原因: " + reason);
            notifyPlayer(order, "delivery.failed", "order", order.orderNo());
        }
    }

    private void announce(StoreOrder order, StoreSettings.Delivery settings) {
        if (settings.notifyPlayer()) {
            if (order.type() == OrderType.TOPUP) {
                notifyPlayer(order, "order.paid-notice", "order", order.orderNo());
            }
            notifyPlayer(order, "delivery.success", "summary", order.summary());
        }
        if (settings.broadcastAboveMinor() > 0 && order.amountMinor() >= settings.broadcastAboveMinor()) {
            plugin.schedulers().global(() -> Bukkit.broadcast(
                plugin.messages().component("delivery.broadcast", "player", order.playerName())));
        }
    }

    private void notifyPlayer(StoreOrder order, String key, Object... placeholders) {
        Player player = Bukkit.getPlayer(order.playerId());
        if (player == null) {
            return;
        }
        player.getScheduler().execute(plugin,
            () -> plugin.messages().send(player, key, placeholders), null, 1L);
    }

    /**
     * Returns orders abandoned mid-delivery to the queue.
     *
     * A server killed between {@code claimForDelivery} and {@code markDelivered}
     * leaves a row in DELIVERING that nothing else will ever pick up. This is the
     * sweep that makes a hard crash survivable rather than a support ticket.
     */
    public int recoverStaleClaims() {
        try {
            List<StoreOrder> stuck = dao.staleClaims(clock.now() - STALE_CLAIM_MILLIS, 100);
            int recovered = 0;
            for (StoreOrder order : stuck) {
                boolean requeued = dao.database().execute(connection ->
                    dao.requeue(connection, order.orderNo()));
                if (requeued) {
                    recovered++;
                    audit.record("SYSTEM", "ORDER_RECOVERED", order.playerId().toString(),
                        "order=" + order.orderNo());
                }
            }
            if (recovered > 0) {
                plugin.getLogger().info("已回收 " + recovered + " 筆中斷的發貨作業。");
            }
            return recovered;
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.WARNING, "回收中斷發貨作業失敗。", failure);
            return 0;
        }
    }

    /** Called on join: clears the backoff so anything waiting on this player runs now. */
    public void nudgeFor(Player player) {
        try {
            int released = dao.releaseBackoffFor(player.getUniqueId());
            int awaiting = dao.awaitingDeliveryFor(player.getUniqueId());
            if (released > 0) {
                pollOnce();
            }
            if (awaiting > 0 && plugin.settings().delivery().notifyPlayer()) {
                player.getScheduler().execute(plugin, () -> plugin.messages()
                    .send(player, "delivery.queued-offline", "count", awaiting), null, 1L);
            }
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.WARNING, "檢查待發貨訂單失敗: " + player.getName(), failure);
        }
    }
}
