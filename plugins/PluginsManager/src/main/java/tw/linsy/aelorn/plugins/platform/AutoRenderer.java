package tw.linsy.aelorn.plugins.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * The standalone renderer: MiniMessage when the line contains a tag, legacy
 * {@code &} codes otherwise.
 *
 * Used when AelornLib is absent — which is exactly when an admin needs a plugin
 * manager and cannot be told to fix the core first.
 *
 * <p>Adventure, not {@code ChatColor}. The legacy helper this replaces
 * ({@code ChatColor.translateAlternateColorCodes}) is deprecated for removal and
 * cannot express hex colours, so an admin writing {@code &#a0c8ff} in
 * messages.yml got the literal text back.
 */
final class AutoRenderer implements Renderer {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexCharacter('#')
        .hexColors()
        .build();
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Override
    public Component render(String markup) {
        if (markup == null || markup.isEmpty()) {
            return Component.empty();
        }
        if (looksLikeMiniMessage(markup)) {
            try {
                return MINI.deserialize(markup);
            } catch (RuntimeException malformed) {
                // A broken tag must not swallow the whole message.
                return LEGACY.deserialize(markup);
            }
        }
        return LEGACY.deserialize(markup);
    }

    @Override
    public String plain(String markup) {
        return markup == null || markup.isEmpty() ? "" : PLAIN.serialize(render(markup));
    }

    @Override
    public String describe() {
        return "standalone-adventure";
    }

    /** A {@code <tag>} anywhere is taken as MiniMessage; bare {@code <} is not. */
    private static boolean looksLikeMiniMessage(String markup) {
        int open = markup.indexOf('<');
        return open >= 0 && markup.indexOf('>', open + 1) > open;
    }
}
