package tw.linsy.aelorn.rpgbridge;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

final class BridgeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("status", "item", "snapshot", "reload");
    private static final List<String> SNAPSHOT_PRIORITY = List.of(
        "maximum_health", "maximum_mana", "attack_power", "defense", "resistance",
        "speed", "ability_power", "spell_damage", "critical_chance", "critical_damage");
    private static final double VISIBLE_EPSILON = 1.0E-4;
    private static final int SNAPSHOT_FALLBACK_LIMIT = 12;

    private final RpgCoreMythicBridgePlugin plugin;

    BridgeCommand(RpgCoreMythicBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("rpgbridge.admin")) {
            send(sender, "You do not have permission.", NamedTextColor.RED);
            return true;
        }
        switch (args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> showStatus(sender);
            case "item" -> {
                if (sender instanceof Player player) {
                    showHeldItem(player);
                } else {
                    send(sender, "Only players can inspect held items.", NamedTextColor.RED);
                }
            }
            case "snapshot" -> {
                if (sender instanceof Player player) {
                    showSnapshot(player);
                } else {
                    send(sender, "Only players can inspect snapshots.", NamedTextColor.RED);
                }
            }
            case "reload" -> send(sender, "Registered " + plugin.reregisterStats()
                + " shared RPG stats again.", NamedTextColor.GREEN);
            default -> send(sender, "Usage: /rpgbridge [status|item|snapshot|reload]", NamedTextColor.YELLOW);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, String @NotNull [] args) {
        if (args.length != 1 || !sender.hasPermission("rpgbridge.admin")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private void showStatus(CommandSender sender) {
        send(sender, "RPGCore Mythic Bridge", NamedTextColor.AQUA);
        send(sender, "MythicCore: " + plugin.statusOf("MythicCore"), NamedTextColor.GRAY);
        send(sender, "AelornItems: " + plugin.statusOf("AelornItems"), NamedTextColor.GRAY);
        send(sender, "RPGCore: " + plugin.statusOf("RPGCore"), NamedTextColor.GRAY);
        send(sender, "Nexo: " + plugin.statusOf("Nexo") + " | AeloriaHUD: " + plugin.statusOf("AeloriaHUD") + " | MythicMobs: " + plugin.statusOf("MythicMobs"),
            NamedTextColor.GRAY);
        send(sender, "Shared stats: " + plugin.registeredStats(), NamedTextColor.GREEN);
        send(sender, "Ability casts observed: " + plugin.abilityCastCount(), NamedTextColor.GRAY);
        send(sender, "Mythic item damage observations: " + plugin.mythicDamageCount(), NamedTextColor.GRAY);
        MythicItemInspector.ItemInspection lastItem = plugin.lastMythicItem();
        if (lastItem.present()) {
            send(sender, "Last Mythic item: " + lastItem.summary(), NamedTextColor.DARK_AQUA);
        }
    }

    private void showHeldItem(Player player) {
        MythicItemInspector.ItemInspection item = plugin.inspector().inspect(player.getInventory().getItemInMainHand());
        if (!item.present()) {
            send(player, "Main hand item has no MythicCore/AelornItems data.", NamedTextColor.YELLOW);
            return;
        }
        send(player, "Held item: " + item.summary(), NamedTextColor.AQUA);
        if (item.stats().isEmpty()) {
            send(player, "Stats: none", NamedTextColor.GRAY);
            return;
        }
        for (Map.Entry<String, Double> entry : new TreeMap<>(item.stats()).entrySet()) {
            send(player, " - " + entry.getKey() + ": " + trim(entry.getValue()), NamedTextColor.GRAY);
        }
    }

    private void showSnapshot(Player player) {
        try {
            StatSnapshot snapshot = plugin.mythicCore().snapshot(player);
            Map<String, Double> stats = snapshot.asMap();
            send(player, "Player stat snapshot", NamedTextColor.AQUA);
            int shown = 0;
            for (String key : SNAPSHOT_PRIORITY) {
                Double value = stats.get(key);
                if (value != null && Math.abs(value) > VISIBLE_EPSILON) {
                    send(player, " - " + key + ": " + trim(value), NamedTextColor.GRAY);
                    shown++;
                }
            }
            if (shown == 0) {
                for (Map.Entry<String, Double> entry : new TreeMap<>(stats).entrySet()) {
                    if (shown >= SNAPSHOT_FALLBACK_LIMIT) {
                        break;
                    }
                    if (Math.abs(entry.getValue()) > VISIBLE_EPSILON) {
                        send(player, " - " + entry.getKey() + ": " + trim(entry.getValue()), NamedTextColor.GRAY);
                        shown++;
                    }
                }
            }
            if (shown == 0) {
                send(player, "No non-zero shared stats are currently visible.", NamedTextColor.YELLOW);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                "Failed to read MythicCore stat snapshot for " + player.getName(), exception);
            send(player, "Failed to read player stat snapshot. See console for details.", NamedTextColor.RED);
        }
    }

    private static String trim(double value) {
        return Math.abs(value - Math.rint(value)) < VISIBLE_EPSILON
            ? Long.toString(Math.round(value))
            : String.format(Locale.US, "%.2f", value);
    }

    private static void send(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
    }
}
