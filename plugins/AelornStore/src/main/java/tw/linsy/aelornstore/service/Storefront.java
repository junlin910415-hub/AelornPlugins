package tw.linsy.aelornstore.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import tw.linsy.aelornstore.AelornStorePlugin;
import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.model.Product;
import tw.linsy.aelornstore.model.StoreOrder;
import tw.linsy.aelornstore.model.TopupPackage;
import tw.linsy.aelornstore.model.VipRecord;
import tw.linsy.aelornstore.payment.CheckoutTicket;
import tw.linsy.aelornstore.payment.PaymentException;
import tw.linsy.aelornstore.payment.PaymentProvider;
import tw.linsy.aelorn.lib.text.Messages;

/**
 * The player-facing flows, shared by the menu and the commands.
 *
 * Both entry points must behave identically — the same limits, the same
 * messages, the same audit trail — so neither owns the logic. Everything here
 * moves itself onto an async thread first, then replies on the player's own
 * region thread, which is the only pattern that is legal on Folia.
 */
public final class Storefront {

    private final AelornStorePlugin plugin;

    public Storefront(AelornStorePlugin plugin) {
        this.plugin = plugin;
    }

    // ── 購買 ────────────────────────────────────────────────────────────────

    public void buy(Player player, String productId, int quantity) {
        Product product = plugin.catalog().products().get(productId);
        if (product == null) {
            plugin.messages().send(player, "shop.purchase-failed", "reason", productId);
            return;
        }
        if (!plugin.clickCooldowns().tryUse(player.getUniqueId(),
            plugin.settings().limits().guiClickCooldownMs())) {
            plugin.messages().send(player, "general.cooldown");
            return;
        }
        plugin.schedulers().async(() -> {
            OrderService.Result result = plugin.orders().purchase(player, product, quantity);
            reply(player, () -> {
                if (result.ok()) {
                    plugin.messages().send(player, "shop.purchased",
                        "item", product.name(),
                        "price", formatPrice(product, result.order()));
                    if (plugin.settings().shop().closeOnPurchase()) {
                        player.closeInventory();
                    }
                    return;
                }
                sendPurchaseFailure(player, product, result);
            });
            if (result.ok()) {
                // Settle immediately rather than waiting for the next poll tick;
                // the poller remains the safety net if this pass loses the claim.
                plugin.delivery().pollOnce();
            }
        });
    }

    private String formatPrice(Product product, StoreOrder order) {
        StoreSettings settings = plugin.settings();
        long amount = order == null ? product.price() : order.creditAmount();
        return switch (product.currency()) {
            case VAULT -> amount + " " + settings.vault().displayName();
            case CREDIT -> settings.credit().symbol() + settings.money().formatCredit(amount);
        };
    }

    private void sendPurchaseFailure(Player player, Product product, OrderService.Result result) {
        Messages messages = plugin.messages();
        StoreSettings settings = plugin.settings();
        switch (result.status()) {
            case NO_PERMISSION -> messages.send(player, "shop.requires-permission", "item", product.name());
            case OUT_OF_STOCK -> messages.send(player, "shop.out-of-stock", "item", product.name());
            case LIMIT_REACHED -> messages.send(player, "shop.limit-reached",
                "item", product.name(), "limit", result.detail());
            case DAILY_LIMIT_REACHED -> messages.send(player, "shop.daily-limit-reached",
                "item", product.name(), "limit", result.detail());
            case INSUFFICIENT_CREDIT -> messages.send(player, "wallet.insufficient",
                "credit", settings.credit().displayName(),
                "symbol", settings.credit().symbol(),
                "need", settings.money().formatCredit(product.priceFor(player)),
                "have", settings.money().formatCredit(parseLong(result.detail())));
            case INSUFFICIENT_VAULT -> messages.send(player, "wallet.insufficient-vault",
                "vault", settings.vault().displayName(),
                "need", result.detail(),
                "have", plugin.vault() == null ? "0" : String.valueOf(plugin.vault().balance(player.getUniqueId())));
            case VAULT_UNAVAILABLE -> messages.send(player, "shop.purchase-failed",
                "reason", settings.vault().displayName() + " 服務未啟用");
            default -> messages.send(player, "general.internal-error");
        }
    }

    // ── 儲值 ────────────────────────────────────────────────────────────────

    /** Creates a top-up order for a fixed package. */
    public void topupPackage(Player player, String packageId, String providerId) {
        TopupPackage entry = plugin.catalog().topupPackages().get(packageId);
        if (entry == null) {
            plugin.messages().send(player, "topup.usage", "label", "store");
            return;
        }
        topup(player, entry.amountMinor(), providerId, entry.bonusCredit());
    }

    /** Creates a top-up order for an arbitrary amount, in minor units. */
    public void topup(Player player, long amountMinor, String providerId, long bonusCredit) {
        if (!plugin.commandCooldowns().tryUse(player.getUniqueId(),
            plugin.settings().limits().commandCooldownMs())) {
            plugin.messages().send(player, "general.cooldown");
            return;
        }
        plugin.schedulers().async(() -> {
            OrderService.Result result = plugin.orders().createTopup(player, amountMinor, providerId);
            if (!result.ok()) {
                reply(player, () -> sendTopupFailure(player, result));
                return;
            }
            StoreOrder order = result.order();
            StoreSettings settings = plugin.settings();
            StoreSettings.ProviderSettings channel = settings.providers().get(order.provider());
            PaymentProvider provider = plugin.payments().find(order.provider());
            if (channel == null || provider == null) {
                reply(player, () -> plugin.messages().send(player, "topup.provider-unavailable",
                    "provider", order.provider()));
                return;
            }
            CheckoutTicket ticket;
            try {
                ticket = provider.checkout(order, channel);
            } catch (PaymentException refused) {
                plugin.getLogger().warning("建立付款失敗（" + order.orderNo() + "）: " + refused.getMessage());
                reply(player, () -> plugin.messages().send(player,
                    refused.notImplemented() ? "topup.provider-not-implemented" : "topup.provider-unavailable",
                    "provider", channel.displayName()));
                return;
            }
            reply(player, () -> announceOrder(player, order, ticket, bonusCredit));
        });
    }

    private void announceOrder(Player player, StoreOrder order, CheckoutTicket ticket, long bonusCredit) {
        Messages messages = plugin.messages();
        StoreSettings settings = plugin.settings();
        messages.send(player, "topup.created", "order", order.orderNo());
        messages.send(player, "topup.created-amount",
            "amount", settings.money().formatMinor(order.amountMinor()),
            "symbol", settings.credit().symbol(),
            "credit", settings.money().formatCredit(order.creditAmount() + bonusCredit),
            "credit_name", settings.credit().displayName());
        messages.send(player, "topup.created-expires", "expires", plugin.clock().format(order.expiresAt()));
        for (String line : ticket.instructions()) {
            messages.sendInline(player, line, "order", order.orderNo());
        }
        if (ticket.hasLink()) {
            messages.send(player, "topup.pay-link", "url", ticket.payUrl());
        }
    }

    private void sendTopupFailure(Player player, OrderService.Result result) {
        Messages messages = plugin.messages();
        StoreSettings settings = plugin.settings();
        switch (result.status()) {
            case DISABLED -> messages.send(player, "topup.disabled");
            case AMOUNT_TOO_LOW -> messages.send(player, "topup.amount-too-low", "min", result.detail());
            case AMOUNT_TOO_HIGH -> messages.send(player, "topup.amount-too-high", "max", result.detail());
            case NOT_WHOLE_UNITS -> messages.send(player, "topup.not-whole-units");
            case ACCOUNT_TOO_NEW -> messages.send(player, "topup.account-too-new", "hours", result.detail());
            case TOO_MANY_PENDING -> messages.send(player, "topup.too-many-pending",
                "count", result.detail(), "label", "store");
            case DAILY_LIMIT -> messages.send(player, "topup.daily-limit",
                "limit", settings.money().formatMinor(settings.topup().dailyLimitMinor()),
                "used", result.detail());
            case NO_PROVIDER -> messages.send(player, "topup.provider-unavailable",
                "provider", result.detail());
            default -> messages.send(player, "general.internal-error");
        }
    }

    // ── 查詢 ────────────────────────────────────────────────────────────────

    public void sendBalance(Player player) {
        plugin.schedulers().async(() -> {
            try {
                long balance = plugin.wallet().balance(player.getUniqueId());
                StoreSettings settings = plugin.settings();
                reply(player, () -> plugin.messages().send(player, "wallet.balance",
                    "credit", settings.credit().displayName(),
                    "symbol", settings.credit().symbol(),
                    "balance", settings.money().formatCredit(balance)));
            } catch (SQLException failure) {
                storageError(player, "查詢餘額", failure);
            }
        });
    }

    public void sendOrders(Player player, int limit) {
        plugin.schedulers().async(() -> {
            try {
                List<StoreOrder> found = plugin.orders().recent(player.getUniqueId(), limit);
                StoreSettings settings = plugin.settings();
                reply(player, () -> {
                    if (found.isEmpty()) {
                        plugin.messages().send(player, "order.list-empty");
                        return;
                    }
                    plugin.messages().send(player, "order.list-header", "count", found.size());
                    for (StoreOrder order : found) {
                        plugin.messages().send(player, "order.list-entry",
                            "order", order.orderNo(),
                            "status", order.status().name(),
                            "amount", order.type() == tw.linsy.aelornstore.model.OrderType.TOPUP
                                ? settings.money().formatMinor(order.amountMinor())
                                : settings.credit().symbol()
                                    + settings.money().formatCredit(order.creditAmount()),
                            "created", plugin.clock().format(order.createdAt()));
                    }
                });
            } catch (SQLException failure) {
                storageError(player, "查詢訂單", failure);
            }
        });
    }

    public void sendVipStatus(Player player) {
        plugin.schedulers().async(() -> {
            try {
                Optional<VipRecord> held = plugin.vip().current(player.getUniqueId());
                reply(player, () -> {
                    if (held.isEmpty()) {
                        plugin.messages().send(player, "vip.none");
                        return;
                    }
                    VipRecord record = held.get();
                    String tierName = plugin.vip().displayName(record);
                    if (record.permanent()) {
                        plugin.messages().send(player, "vip.status-permanent", "tier", tierName);
                        return;
                    }
                    plugin.messages().send(player, "vip.status",
                        "tier", tierName,
                        "expires", plugin.clock().format(record.expiresAt()),
                        "days", record.daysRemaining(plugin.clock().now()));
                });
            } catch (SQLException failure) {
                storageError(player, "查詢 VIP", failure);
            }
        });
    }

    /** Forces a delivery pass for this player and reports what was waiting. */
    public void claim(Player player) {
        plugin.schedulers().async(() -> {
            try {
                int awaiting = plugin.dao().awaitingDeliveryFor(player.getUniqueId());
                if (awaiting == 0) {
                    reply(player, () -> plugin.messages().send(player, "delivery.claim-none"));
                    return;
                }
                plugin.dao().releaseBackoffFor(player.getUniqueId());
                plugin.delivery().pollOnce();
                reply(player, () -> plugin.messages().send(player, "delivery.claim-done", "count", awaiting));
            } catch (SQLException failure) {
                storageError(player, "領取訂單", failure);
            }
        });
    }

    // ── 共用 ────────────────────────────────────────────────────────────────

    private void storageError(Player player, String what, SQLException failure) {
        plugin.getLogger().log(Level.WARNING, what + "失敗: " + player.getName(), failure);
        reply(player, () -> plugin.messages().send(player, "general.storage-unavailable"));
    }

    /** Hops back to the player's region thread; drops silently if they left. */
    private void reply(Player player, Runnable action) {
        if (!player.isOnline()) {
            return;
        }
        player.getScheduler().execute(plugin, action, null, 1L);
    }

    private static long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException notANumber) {
            return 0L;
        }
    }
}
