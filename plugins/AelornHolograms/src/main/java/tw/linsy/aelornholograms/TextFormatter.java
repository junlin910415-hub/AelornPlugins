package tw.linsy.aelornholograms;

import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import tw.linsy.aelorn.lib.text.Text;

public final class TextFormatter {

    private static final Map<String, String> DECENT_REPLACEMENTS = Map.of(
        "[x]", "█", "[X]", "█", "[/]", "▌", "[,]", "░", "[,,]", "▒", "[,,,]", "▓", "[p]", "•", "[P]", "•");

    private final AelornHologramsPlugin plugin;

    public TextFormatter(AelornHologramsPlugin plugin) {
        this.plugin = plugin;
    }

    public Component format(String text) {
        return format(text, null);
    }

    public Component format(String text, OfflinePlayer player) {
        return deserialize(process(text, player));
    }

    /**
     * Static text transforms + placeholder substitution, producing the string to
     * deserialize. Split from {@link #deserialize} so the render loop can compare
     * this cheap string against the cached copy and skip identical updates.
     */
    String process(String text, OfflinePlayer player) {
        String processed = applyDecentReplacements(text == null ? "" : text);
        return plugin.placeholderBridge().apply(player, processed);
    }

    /**
     * Rendering itself is not hologram-specific, so it goes through the core and
     * every Aelorn plugin resolves colour the same way. AUTO is exactly the old
     * behaviour: MiniMessage when the text carries a tag, legacy otherwise, with
     * a legacy fallback when a tag turns out to be malformed.
     */
    Component deserialize(String processed) {
        return Text.render(processed, plugin.settings().miniMessageEnabled()
            ? Text.Format.AUTO
            : Text.Format.LEGACY);
    }

    static String applyDecentReplacements(String text) {
        if (text.indexOf('[') < 0) {
            return text;
        }
        String replaced = text;
        for (Map.Entry<String, String> entry : DECENT_REPLACEMENTS.entrySet()) {
            replaced = replaced.replace(entry.getKey(), entry.getValue());
        }
        return replaced;
    }

}
