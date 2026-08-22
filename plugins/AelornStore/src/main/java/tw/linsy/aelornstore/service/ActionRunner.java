package tw.linsy.aelornstore.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.AelornStorePlugin;
import tw.linsy.aelornstore.model.StoreAction;

/**
 * Executes the {@code actions:} lists from shop.yml and vip.yml.
 *
 * Actions fall into two groups, and the split is deliberate:
 *
 * <ul>
 *   <li><b>Ledger actions</b> ({@code credit}, {@code vip}) run inline on the
 *       calling thread and throw on failure, so an undelivered grant is retried.</li>
 *   <li><b>Presentation actions</b> (commands, items, messages, sounds) are
 *       dispatched to the correct Folia scheduler and are <em>not</em> retried.
 *       Re-running an arbitrary console command is not safe — a second
 *       {@code lp user … parent add} is harmless but a second {@code give} is not
 *       — so a failure here is logged and audited for a human instead of being
 *       replayed blindly.</li>
 * </ul>
 */
public final class ActionRunner {

    /** Everything an action's placeholders can refer to. */
    public record Context(
        UUID playerId,
        String playerName,
        String orderNo,
        @Nullable String productId,
        int quantity,
        long amountMinor,
        long creditAmount
    ) { }

    private final AelornStorePlugin plugin;
    private final Logger logger;
    private WalletService wallet;
    private VipService vip;

    public ActionRunner(AelornStorePlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Wired after construction because wallet and VIP both need this runner themselves. */
    public void bind(WalletService wallet, VipService vip) {
        this.wallet = wallet;
        this.vip = vip;
    }

    /**
     * Runs every action in order.
     *
     * @return true when a required player was online for the actions that needed
     *         one; false means the caller should defer the whole delivery.
     */
    public boolean run(List<StoreAction> actions, Context context) throws SQLException {
        if (requiresOnlinePlayer(actions) && Bukkit.getPlayer(context.playerId()) == null) {
            return false;
        }
        for (StoreAction action : actions) {
            switch (action.kind()) {
                case CREDIT -> runCredit(action, context);
                case VIP -> runVip(action, context);
                case VAULT -> runVault(action, context);
                case CONSOLE -> dispatchConsole(apply(action.argument(), context));
                case BROADCAST -> broadcast(apply(action.argument(), context));
                case PLAYER -> onPlayer(context, player ->
                    player.performCommand(apply(action.argument(), context)));
                case MESSAGE -> onPlayer(context, player ->
                    player.sendMessage(plugin.messages().inline(apply(action.argument(), context))));
                case ITEM -> onPlayer(context, player -> giveItem(player, action.argument()));
                case SOUND -> onPlayer(context, player -> playSound(player, action.argument()));
            }
        }
        return true;
    }

    /** True when at least one action can only be carried out on an online player. */
    public boolean requiresOnlinePlayer(List<StoreAction> actions) {
        for (StoreAction action : actions) {
            switch (action.kind()) {
                case PLAYER, ITEM, MESSAGE, SOUND -> {
                    return true;
                }
                default -> {
                    // Console commands take the player name and work while offline.
                }
            }
        }
        return false;
    }

    private void runCredit(StoreAction action, Context context) throws SQLException {
        long amount = parseLong(action.argument(), 0L);
        if (amount <= 0) {
            logger.warning("credit 動作的數量無效，已略過: " + action.argument());
            return;
        }
        wallet.grant(context.playerId(), amount, "ACTION", context.orderNo(),
            "商品發放 " + (context.productId() == null ? "-" : context.productId()));
    }

    private void runVip(StoreAction action, Context context) throws SQLException {
        String tierId = action.option("tier", "");
        long days = action.optionLong("days", 0L);
        vip.grant(context.playerId(), context.playerName(), tierId, days, context.orderNo());
    }

    private void runVault(StoreAction action, Context context) {
        double amount = parseDouble(action.argument());
        VaultBridge bridge = plugin.vault();
        if (bridge == null) {
            logger.warning("vault 動作需要 Vault 經濟服務，但目前不可用；訂單 " + context.orderNo());
            return;
        }
        if (amount <= 0) {
            logger.warning("vault 動作的數量無效，已略過: " + action.argument());
            return;
        }
        // Economy providers are not uniformly thread-safe; keep every call on a tick.
        plugin.schedulers().global(() -> {
            if (!bridge.deposit(context.playerId(), amount)) {
                logger.warning("發放 " + amount + " 遊戲幣失敗；訂單 " + context.orderNo());
            }
        });
    }

    private void dispatchConsole(String command) {
        plugin.schedulers().global(() -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "執行主控台指令失敗: " + command, failure);
            }
        });
    }

    private void broadcast(String text) {
        plugin.schedulers().global(
            () -> Bukkit.broadcast(plugin.messages().inline(text)));
    }

    /** Runs on the player's own region thread, which is where entity access is legal on Folia. */
    private void onPlayer(Context context, PlayerTask task) {
        Player player = Bukkit.getPlayer(context.playerId());
        if (player == null) {
            return;
        }
        player.getScheduler().execute(plugin, () -> {
            Player current = Bukkit.getPlayer(context.playerId());
            if (current == null || !current.isOnline()) {
                return;
            }
            try {
                task.run(current);
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "對 " + context.playerName() + " 執行發放動作失敗。", failure);
            }
        }, null, 1L);
    }

    private void giveItem(Player player, String argument) {
        String[] parts = argument.trim().split("\\s+");
        Material material = Material.matchMaterial(parts[0], false);
        if (material == null || !material.isItem()) {
            logger.warning("item 動作的材質無效: " + parts[0]);
            return;
        }
        int amount = parts.length > 1 ? (int) parseLong(parts[1], 1L) : 1;
        if (amount <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(material, Math.min(amount, material.getMaxStackSize() * 36));
        // Anything that does not fit drops at the player's feet rather than vanishing.
        player.getInventory().addItem(stack).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void playSound(Player player, String argument) {
        String[] parts = argument.trim().split("\\s+");
        float volume = parts.length > 1 ? (float) parseDouble(parts[1]) : 1.0f;
        float pitch = parts.length > 2 ? (float) parseDouble(parts[2]) : 1.0f;
        // The string overload avoids depending on how this server version models
        // the sound registry, and silently no-ops on an unknown key.
        player.playSound(player.getLocation(), parts[0].toLowerCase(Locale.ROOT),
            volume <= 0 ? 1.0f : volume, pitch <= 0 ? 1.0f : pitch);
    }

    private String apply(String template, Context context) {
        return template
            .replace("{player}", context.playerName())
            .replace("{uuid}", context.playerId().toString())
            .replace("{order}", context.orderNo())
            .replace("{product}", context.productId() == null ? "" : context.productId())
            .replace("{quantity}", String.valueOf(context.quantity()))
            .replace("{amount}", String.valueOf(context.amountMinor()))
            .replace("{credit}", String.valueOf(context.creditAmount()));
    }

    private long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException notANumber) {
            return 0.0d;
        }
    }

    @FunctionalInterface
    private interface PlayerTask {
        void run(Player player);
    }
}
