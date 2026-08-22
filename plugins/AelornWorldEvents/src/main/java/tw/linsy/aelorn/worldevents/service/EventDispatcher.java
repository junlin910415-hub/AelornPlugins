package tw.linsy.aelorn.worldevents.service;

import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import tw.linsy.aelorn.worldevents.AelornWorldEventsPlugin;
import tw.linsy.aelorn.worldevents.config.EventSettings;
import tw.linsy.aelorn.worldevents.model.EventNode;

/**
 * Turns an approved activation into an actual encounter.
 *
 * The encounter itself belongs to RPGCore, so this hands it a console command
 * built from {@code settings.start-command}. Which placeholders that command
 * must contain is configuration too — the previous version hardcoded a list of
 * six and refused to start if the admin's command omitted any one of them.
 */
public final class EventDispatcher {

    private final AelornWorldEventsPlugin plugin;
    private final EventCoordinator coordinator;
    private final PlayerTracker tracker;

    public EventDispatcher(AelornWorldEventsPlugin plugin, EventCoordinator coordinator,
                           PlayerTracker tracker) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.tracker = tracker;
    }

    /**
     * Validates the configured command template once per load and reports what
     * is wrong, rather than failing at the moment an event would have fired.
     */
    public static List<String> validateTemplate(EventSettings settings) {
        if (settings.startCommand().isBlank()) {
            return List.of("settings.start-command 是空的，事件不會啟動任何遭遇。");
        }
        return settings.startCommandTokens().stream()
            .filter(token -> !settings.startCommand().contains(token))
            .map(token -> "settings.start-command 缺少必要的佔位符 " + token)
            .toList();
    }

    /** Dispatch runs on the global region: console commands are server-wide state. */
    public void start(EventNode node) {
        plugin.schedulers().global(() -> {
            String command = render(plugin.catalog().settings().startCommand(), node);
            boolean accepted;
            try {
                accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING,
                    "事件 " + node.id() + " 的啟動指令拋出例外: " + command, failure);
                accepted = false;
            }
            if (!accepted) {
                coordinator.dispatchFailed(node.id(), System.currentTimeMillis());
                plugin.getLogger().warning("事件 " + node.id() + " 的啟動指令未被接受: " + command);
                return;
            }
            plugin.getLogger().info("EVENT_STARTED id=" + node.id() + " encounter=" + node.encounter()
                + " region=" + node.region() + " level=" + node.level());
            announce(node);
        });
    }

    /**
     * Broadcasts to everyone the tracker believes is in range. Each message hops
     * onto its own player's scheduler, as Folia requires.
     */
    private void announce(EventNode node) {
        if (!plugin.messages().has("event.announce")) {
            return;
        }
        // display-name is admin-authored, so its colour codes are rendered rather
        // than stripped — substitution happens before the whole line is parsed.
        Component message = plugin.messages().component("event.announce",
            "event", node.displayName(),
            "region", node.region(),
            "level", node.level(),
            "world", node.world());
        tracker.forEachWithin(node.world(), node.x(), node.z(),
            plugin.catalog().settings().announceRadius(), playerId -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    plugin.schedulers().entity(player, () -> player.sendMessage(message));
                }
            });
    }

    private static String render(String template, EventNode node) {
        return template
            .replace("{encounter}", node.encounter())
            .replace("{event}", node.id())
            .replace("{region}", node.region())
            .replace("{world}", node.world())
            .replace("{x}", coordinate(node.x()))
            .replace("{y}", coordinate(node.y()))
            .replace("{z}", coordinate(node.z()))
            .replace("{level}", Integer.toString(node.level()));
    }

    /** Whole numbers render without a decimal tail so commands stay readable in logs. */
    private static String coordinate(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }
}
