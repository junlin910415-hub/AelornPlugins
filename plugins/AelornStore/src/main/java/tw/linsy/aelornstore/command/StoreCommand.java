package tw.linsy.aelornstore.command;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelornstore.AelornStorePlugin;
import tw.linsy.aelornstore.config.StoreSettings;
import tw.linsy.aelornstore.model.Money;

/**
 * {@code /store} — the player's whole surface area.
 *
 * Amounts are typed in dollars and converted to minor units here, once, using
 * {@link BigDecimal}. Parsing "300.50" with a double and multiplying by 100 is
 * how a store ends up charging 30049 cents; this path either produces an exact
 * integer or refuses the input.
 */
public final class StoreCommand implements CommandExecutor, TabCompleter {

    private final AelornStorePlugin plugin;

    public StoreCommand(AelornStorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!plugin.storageReady()) {
            plugin.messages().send(sender, "general.storage-unavailable");
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openRoot(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "balance", "bal", "餘額" -> plugin.storefront().sendBalance(player);
            case "orders", "order", "訂單" -> plugin.storefront().sendOrders(player, 10);
            case "claim", "領取" -> plugin.storefront().claim(player);
            case "vip" -> plugin.storefront().sendVipStatus(player);
            case "topup", "儲值" -> handleTopup(player, label, args);
            default -> plugin.messages().send(player, "general.unknown-subcommand", "label", label);
        }
        return true;
    }

    private void handleTopup(Player player, String label, String[] args) {
        if (!player.hasPermission("aelornstore.topup")) {
            plugin.messages().send(player, "general.no-permission");
            return;
        }
        StoreSettings settings = plugin.settings();
        if (!settings.topup().enabled()) {
            plugin.messages().send(player, "topup.disabled");
            return;
        }
        if (args.length < 2) {
            plugin.menus().openTopup(player);
            return;
        }
        Long amountMinor = parseAmount(args[1]);
        if (amountMinor == null) {
            plugin.messages().send(player, "general.invalid-number", "value", args[1]);
            plugin.messages().send(player, "topup.usage", "label", label);
            return;
        }
        String provider = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : settings.defaultProvider();
        plugin.storefront().topup(player, amountMinor, provider, 0L);
    }

    /**
     * Converts a dollar amount to minor units, or {@code null} if it is not exact.
     * Rejecting sub-cent input is deliberate: silently rounding a price is the one
     * thing a payment system must never do.
     */
    private @Nullable Long parseAmount(String raw) {
        try {
            BigDecimal major = new BigDecimal(raw.trim().replace(",", ""));
            if (major.signum() <= 0) {
                return null;
            }
            return major.movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("balance", "topup", "orders", "claim", "vip"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("topup")) {
            List<String> amounts = new ArrayList<>();
            plugin.catalog().orderedTopupPackages().forEach(entry ->
                amounts.add(String.valueOf(entry.amountMinor() / Money.MINOR_UNITS_PER_MAJOR)));
            return filter(amounts, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("topup")) {
            List<String> channels = new ArrayList<>();
            plugin.settings().enabledProviders().forEach(channel -> channels.add(channel.id()));
            return filter(channels, args[2]);
        }
        return List.of();
    }

    static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
