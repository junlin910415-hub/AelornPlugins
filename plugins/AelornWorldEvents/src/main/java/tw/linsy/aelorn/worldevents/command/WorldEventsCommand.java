package tw.linsy.aelorn.worldevents.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.text.Messages;
import tw.linsy.aelorn.worldevents.AelornWorldEventsPlugin;
import tw.linsy.aelorn.worldevents.model.EventNode;
import tw.linsy.aelorn.worldevents.service.EventCoordinator;

/**
 * {@code /aelornworldevents} — inspect and reload.
 *
 * Only decides which service to call and which message key to send; every
 * string the player sees comes from messages.yml.
 */
public final class WorldEventsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("status", "list", "reload");

    private final AelornWorldEventsPlugin plugin;

    public WorldEventsCommand(AelornWorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission(AelornWorldEventsPlugin.PERMISSION_ADMIN)) {
            messages.send(sender, "command.no-permission");
            return true;
        }
        String subcommand = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "reload" -> plugin.reloadAsync(sender);
            case "list" -> sendList(sender, messages);
            case "status" -> sendStatus(sender, messages);
            default -> messages.send(sender, "command.usage",
                "label", label, "subcommands", String.join("|", SUBCOMMANDS));
        }
        return true;
    }

    private void sendStatus(CommandSender sender, Messages messages) {
        EventCoordinator.Snapshot snapshot = plugin.coordinator().snapshot(System.currentTimeMillis());
        messages.send(sender, "status.summary",
            "nodes", plugin.catalog().size(),
            "active", snapshot.active(),
            "cooling", snapshot.coolingDown(),
            "started", snapshot.started(),
            "failed", snapshot.failed(),
            "tracked", plugin.tracker().tracked());
        for (Map.Entry<String, Integer> entry : snapshot.activeByRegion().entrySet()) {
            messages.send(sender, "status.region", "region", entry.getKey(), "count", entry.getValue());
        }
    }

    private void sendList(CommandSender sender, Messages messages) {
        messages.send(sender, "list.header", "nodes", plugin.catalog().size());
        for (EventNode node : plugin.catalog().nodes()) {
            messages.send(sender, "list.line",
                "event", node.id(),
                "name", node.displayName(),
                "region", node.region(),
                "encounter", node.encounter(),
                "world", node.world(),
                "level", node.level(),
                "x", Math.round(node.x()), "y", Math.round(node.y()), "z", Math.round(node.z()));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, String @NotNull [] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : SUBCOMMANDS) {
            if (candidate.startsWith(prefix)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
