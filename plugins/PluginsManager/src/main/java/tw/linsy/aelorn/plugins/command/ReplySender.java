package tw.linsy.aelorn.plugins.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;
import tw.linsy.aelorn.plugins.platform.Sched;

/**
 * Getting a reply onto a sender's screen, on the right thread.
 *
 * <p>Two things that have to happen together and were previously spread out. Messages to
 * a player must be delivered on the thread that owns that player, which on a regionised
 * server is their entity scheduler; and the prefix has to be applied consistently across
 * every line of a multi-line report.
 *
 * <p>The previous version did the thread hop by reflecting {@code getScheduler} off the
 * sender and calling {@code execute} with a four-argument signature it looked up by
 * name, wrapped in {@code catch (Throwable)} so a signature change would silently fall
 * back to sending from the wrong thread. {@link org.bukkit.entity.Entity#getScheduler()}
 * has been plain API on both supported forks all along.
 */
public final class ReplySender {

    private final MessageCatalog messages;
    private final SettingsStore settings;
    private final Sched sched;

    public ReplySender(MessageCatalog messages, SettingsStore settings, Sched sched) {
        this.messages = messages;
        this.settings = settings;
        this.sched = sched;
    }

    public void send(CommandSender sender, Reply reply) {
        send(sender, reply.lines());
    }

    /**
     * Sends resolved markup lines.
     *
     * Renders on the calling thread and only the delivery is scheduled: rendering is
     * pure string work, and doing it inside the scheduled task would put MiniMessage
     * parsing on a region thread for no reason.
     */
    public void send(CommandSender sender, java.util.List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        boolean prefixEveryLine = settings.manager().display().prefixEveryLine();
        String prefix = messages.prefix();
        java.util.List<Component> rendered = new java.util.ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            boolean withPrefix = prefixEveryLine || index == 0;
            rendered.add(messages.render(withPrefix ? prefix + lines.get(index) : lines.get(index)));
        }
        deliver(sender, rendered);
    }

    /** One key, for the short refusals the dispatcher itself produces. */
    public void sendKey(CommandSender sender, String key, Object... placeholders) {
        send(sender, java.util.List.of(messages.raw(key, placeholders)));
    }

    private void deliver(CommandSender sender, java.util.List<Component> rendered) {
        if (sender instanceof Player player) {
            // A player who logged out between the command and the reply simply drops the
            // task; there is nobody left to tell.
            sched.entity(player, () -> {
                for (Component line : rendered) {
                    player.sendMessage(line);
                }
            });
            return;
        }
        // Console and command blocks accept messages from any thread.
        for (Component line : rendered) {
            sender.sendMessage(line);
        }
    }
}
