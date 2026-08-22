package tw.linsy.aelornstore.command;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.AelornStorePlugin;
import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.db.StoreDao;
import tw.linsy.aelornstore.model.AuditEntry;
import tw.linsy.aelornstore.model.OrderStatus;
import tw.linsy.aelornstore.model.OrderType;
import tw.linsy.aelornstore.model.StoreOrder;
import tw.linsy.aelornstore.service.OrderService;

/**
 * {@code /aelornstore} — operator tooling.
 *
 * Everything money-related here is deliberately slow and explicit: a target is
 * resolved from the local profile cache rather than a blocking web lookup, every
 * action names its actor in the audit trail, and each command reports what
 * actually changed rather than assuming it worked.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final int AUDIT_DEFAULT_LIMIT = 10;

    private final AelornStorePlugin plugin;

    public AdminCommand(AelornStorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("aelornstore.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "credit" -> handleCredit(sender, args);
            case "order" -> handleOrder(sender, args);
            case "redeliver" -> handleRedeliver(sender, args);
            case "vip" -> handleVip(sender, args);
            case "audit" -> handleAudit(sender, args);
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        plugin.messages().send(sender, "admin.usage-header");
        plugin.messages().sendList(sender, "admin.usage-lines", "label", label);
    }

    private void handleReload(CommandSender sender) {
        plugin.schedulers().async(() -> {
            try {
                plugin.reloadEverything();
                plugin.messages().send(sender, "general.reloaded",
                    "products", plugin.catalog().products().size(),
                    "tiers", plugin.catalog().vipTiers().size(),
                    "providers", plugin.settings().enabledProviders().size());
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, "重新載入失敗。", failure);
                plugin.messages().send(sender, "general.reload-failed",
                    "error", String.valueOf(failure.getMessage()));
            }
        });
    }

    private void handleStatus(CommandSender sender) {
        plugin.schedulers().async(() -> {
            StoreSettings settings = plugin.settings();
            String dbStatus;
            int pending = 0;
            int awaiting = 0;
            int attention = 0;
            try {
                pending = plugin.dao().countByStatus(OrderStatus.CREATED)
                    + plugin.dao().countByStatus(OrderStatus.PENDING);
                awaiting = plugin.dao().countByStatus(OrderStatus.PAID)
                    + plugin.dao().countByStatus(OrderStatus.DELIVERING);
                attention = plugin.dao().countByStatus(OrderStatus.NEEDS_ATTENTION);
                dbStatus = "連線正常";
            } catch (SQLException failure) {
                dbStatus = "連線失敗：" + failure.getMessage();
            }
            plugin.messages().send(sender, "admin.status-header");
            plugin.messages().sendList(sender, "admin.status-lines",
                "storage", settings.storage().describe(),
                "db_status", dbStatus,
                "products", plugin.catalog().products().size(),
                "categories", plugin.catalog().categories().size(),
                "tiers", plugin.catalog().vipTiers().size(),
                "providers", plugin.payments().describeUsable(settings),
                "pending", pending,
                "awaiting", awaiting,
                "attention", attention);
        });
    }

    private void handleCredit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aelornstore.admin.credit")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 4) {
            plugin.messages().send(sender, "general.unknown-subcommand", "label", "aelornstore");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", "player", args[1]);
            return;
        }
        Long amount = parseLong(args[3]);
        if (amount == null || amount < 0) {
            plugin.messages().send(sender, "general.invalid-number", "value", args[3]);
            return;
        }
        String mode = args[2].toLowerCase(Locale.ROOT);
        String reason = args.length > 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)) : "";
        String actor = sender.getName();
        UUID targetId = target.getUniqueId();
        String targetName = target.getName() == null ? args[1] : target.getName();
        StoreSettings settings = plugin.settings();

        plugin.schedulers().async(() -> {
            try {
                StoreDao.AdjustResult result = switch (mode) {
                    case "give", "add" -> plugin.wallet().grant(targetId, amount, actor, null, reason);
                    case "take", "remove" -> plugin.wallet().take(targetId, amount, actor, null, reason);
                    case "set" -> plugin.wallet().set(targetId, amount, actor, reason);
                    default -> null;
                };
                if (result == null) {
                    plugin.messages().send(sender, "general.unknown-subcommand", "label", "aelornstore");
                    return;
                }
                if (!result.applied()) {
                    plugin.messages().send(sender, mode.equals("give")
                        ? "wallet.max-balance" : "wallet.insufficient",
                        "max", settings.money().formatCredit(settings.credit().maxBalance()),
                        "credit", settings.credit().displayName(),
                        "symbol", settings.credit().symbol(),
                        "need", settings.money().formatCredit(amount),
                        "have", settings.money().formatCredit(result.balance()));
                    return;
                }
                String key = switch (mode) {
                    case "set" -> "wallet.admin-set";
                    case "take", "remove" -> "wallet.admin-took";
                    default -> "wallet.admin-credited";
                };
                plugin.messages().send(sender, key,
                    "player", targetName,
                    "credit", settings.credit().displayName(),
                    "symbol", settings.credit().symbol(),
                    "amount", settings.money().formatCredit(amount),
                    "balance", settings.money().formatCredit(result.balance()));
            } catch (SQLException failure) {
                plugin.getLogger().log(Level.WARNING, "調整儲值金失敗。", failure);
                plugin.messages().send(sender, "general.storage-unavailable");
            }
        });
    }

    private void handleOrder(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aelornstore.admin.order")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "general.unknown-subcommand", "label", "aelornstore");
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        String orderNo = OrderService.normalise(args[2]);
        String actor = sender.getName();

        plugin.schedulers().async(() -> {
            switch (mode) {
                case "info" -> sendOrderInfo(sender, orderNo);
                case "approve" -> {
                    OrderService.Result result = plugin.orders().approve(orderNo, actor);
                    reportOrder(sender, result, orderNo, "order.approved", "order.approve-not-allowed");
                    if (result.ok()) {
                        plugin.delivery().pollOnce();
                    }
                }
                case "cancel" -> reportOrder(sender, plugin.orders().cancel(orderNo, actor), orderNo,
                    "topup.cancelled", "topup.cancel-not-allowed");
                case "refund" -> {
                    OrderService.Result result = plugin.orders().refund(orderNo, actor);
                    reportOrder(sender, result, orderNo, "order.refunded", "order.refund-not-allowed");
                    if (result.ok() && result.order() != null) {
                        Player online = Bukkit.getPlayer(result.order().playerId());
                        if (online != null) {
                            online.getScheduler().execute(plugin, () -> plugin.messages()
                                .send(online, "order.refund-notice", "order", orderNo), null, 1L);
                        }
                    }
                }
                default -> plugin.messages().send(sender, "general.unknown-subcommand",
                    "label", "aelornstore");
            }
        });
    }

    private void handleRedeliver(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aelornstore.admin.order")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "general.unknown-subcommand", "label", "aelornstore");
            return;
        }
        String orderNo = OrderService.normalise(args[1]);
        String actor = sender.getName();
        plugin.schedulers().async(() -> {
            OrderService.Result result = plugin.orders().redeliver(orderNo, actor);
            reportOrder(sender, result, orderNo, "order.redelivered", "order.approve-not-allowed");
            if (result.ok()) {
                plugin.delivery().pollOnce();
            }
        });
    }

    private void handleVip(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aelornstore.admin.vip")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "general.unknown-subcommand", "label", "aelornstore");
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = resolve(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", "player", args[2]);
            return;
        }
        UUID targetId = target.getUniqueId();
        String targetName = target.getName() == null ? args[2] : target.getName();
        String actor = sender.getName();

        if (mode.equals("clear")) {
            plugin.schedulers().async(() -> {
                try {
                    plugin.vip().revoke(targetId, targetName, actor);
                    plugin.messages().send(sender, "vip.admin-cleared", "player", targetName);
                } catch (SQLException failure) {
                    plugin.getLogger().log(Level.WARNING, "清除 VIP 失敗。", failure);
                    plugin.messages().send(sender, "general.storage-unavailable");
                }
            });
            return;
        }
        if (!mode.equals("set") || args.length < 4) {
            plugin.messages().send(sender, "general.unknown-subcommand", "label", "aelornstore");
            return;
        }
        String tierId = args[3].toLowerCase(Locale.ROOT);
        if (plugin.catalog().tier(tierId) == null) {
            plugin.messages().send(sender, "vip.unknown-tier",
                "tier", tierId, "available", plugin.catalog().tierIdsForDisplay());
            return;
        }
        Long days = args.length > 4 ? parseLong(args[4]) : 0L;
        if (days == null) {
            plugin.messages().send(sender, "general.invalid-number", "value", args[4]);
            return;
        }
        plugin.schedulers().async(() -> {
            try {
                plugin.vip().grant(targetId, targetName, tierId, days, "ADMIN:" + actor)
                    .ifPresent(record -> plugin.messages().send(sender, "vip.admin-set",
                        "player", targetName,
                        "tier", plugin.vip().displayName(record),
                        "expires", record.permanent() ? "—" : plugin.clock().format(record.expiresAt())));
            } catch (SQLException failure) {
                plugin.getLogger().log(Level.WARNING, "設定 VIP 失敗。", failure);
                plugin.messages().send(sender, "general.storage-unavailable");
            }
        });
    }

    private void handleAudit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aelornstore.admin.audit")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "general.unknown-subcommand", "label", "aelornstore");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", "player", args[1]);
            return;
        }
        Long parsed = args.length > 2 ? parseLong(args[2]) : (long) AUDIT_DEFAULT_LIMIT;
        int limit = parsed == null ? AUDIT_DEFAULT_LIMIT : (int) Math.max(1, Math.min(50, parsed));
        String targetName = target.getName() == null ? args[1] : target.getName();

        plugin.schedulers().async(() -> {
            try {
                List<AuditEntry> entries = plugin.dao().auditFor(target.getUniqueId().toString(), limit);
                if (entries.isEmpty()) {
                    plugin.messages().send(sender, "admin.audit-empty");
                    return;
                }
                plugin.messages().send(sender, "admin.audit-header",
                    "player", targetName, "count", entries.size());
                for (AuditEntry entry : entries) {
                    plugin.messages().send(sender, "admin.audit-entry",
                        "time", plugin.clock().format(entry.createdAt()),
                        "action", entry.action(),
                        "detail", entry.detail() == null ? "" : entry.detail());
                }
            } catch (SQLException failure) {
                plugin.getLogger().log(Level.WARNING, "查詢稽核紀錄失敗。", failure);
                plugin.messages().send(sender, "general.storage-unavailable");
            }
        });
    }

    private void sendOrderInfo(CommandSender sender, String orderNo) {
        try {
            Optional<StoreOrder> found = plugin.orders().find(orderNo);
            if (found.isEmpty()) {
                plugin.messages().send(sender, "order.not-found", "order", orderNo);
                return;
            }
            StoreOrder order = found.get();
            StoreSettings settings = plugin.settings();
            String amount = order.type() == OrderType.TOPUP
                ? settings.money().formatMinor(order.amountMinor())
                : settings.credit().symbol() + settings.money().formatCredit(order.creditAmount());
            plugin.messages().send(sender, "order.info-header", "order", order.orderNo());
            plugin.messages().sendList(sender, "order.info-lines",
                "player", order.playerName(),
                "type", order.type().name(),
                "status", order.status().name(),
                "summary", order.summary(),
                "amount", amount,
                "provider", order.provider(),
                "trade_no", order.providerTradeNo() == null ? "—" : order.providerTradeNo(),
                "created", plugin.clock().format(order.createdAt()),
                "paid", plugin.clock().format(order.paidAt()),
                "delivered", plugin.clock().format(order.deliveredAt()));
        } catch (SQLException failure) {
            plugin.getLogger().log(Level.WARNING, "查詢訂單失敗。", failure);
            plugin.messages().send(sender, "general.storage-unavailable");
        }
    }

    private void reportOrder(CommandSender sender, OrderService.Result result, String orderNo,
                             String successKey, String wrongStateKey) {
        switch (result.status()) {
            case OK -> plugin.messages().send(sender, successKey, "order", orderNo);
            case NOT_FOUND -> plugin.messages().send(sender, "order.not-found", "order", orderNo);
            case WRONG_STATE -> plugin.messages().send(sender, wrongStateKey,
                "order", orderNo, "status", result.detail());
            default -> plugin.messages().send(sender, "general.internal-error");
        }
    }

    /**
     * Resolves a name without leaving the server.
     *
     * {@code getOfflinePlayerIfCached} only consults the local profile cache, so
     * an admin typo produces "player not found" instead of a blocking call to
     * Mojang on whatever thread this happens to be.
     */
    private @Nullable OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private @Nullable Long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, String[] args) {
        if (!sender.hasPermission("aelornstore.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return StoreCommand.filter(
                List.of("reload", "status", "credit", "order", "redeliver", "vip", "audit"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "credit", "audit" -> StoreCommand.filter(onlineNames(), args[1]);
                case "order" -> StoreCommand.filter(List.of("info", "approve", "cancel", "refund"), args[1]);
                case "vip" -> StoreCommand.filter(List.of("set", "clear"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "credit" -> StoreCommand.filter(List.of("give", "take", "set"), args[2]);
                case "vip" -> StoreCommand.filter(onlineNames(), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && sub.equals("vip")) {
            return StoreCommand.filter(new ArrayList<>(plugin.catalog().vipTiers().keySet()), args[3]);
        }
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
        return names;
    }
}
