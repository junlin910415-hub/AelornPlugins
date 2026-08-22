package tw.linsy.aelorn.plugins.platform;

import net.kyori.adventure.text.Component;

/**
 * Markup to component — the only thing AelornLib genuinely supplies for text.
 *
 * Everything else about messages (loading the file, bundled defaults,
 * placeholders, list keys) is identical either way and lives once in
 * {@link MessageCatalog}. What the core adds is the server-wide {@code
 * text-format} decision, so this plugin's output matches every other Aelorn
 * plugin instead of carrying its own copy of that knob.
 *
 * <p>Splitting it this narrowly is what keeps the standalone path cheap: the
 * fallback is twenty lines of serializer choice, not a second message system.
 */
public interface Renderer {

    Component render(String markup);

    /** Formatting removed, for audit records and logs that must not carry markup. */
    String plain(String markup);

    /**
     * Re-reads whatever the implementation's format depends on.
     *
     * A no-op by default, and called polymorphically on purpose: an
     * {@code instanceof CoreRenderer} test in the caller would resolve that class
     * on every reload, including on a server with no AelornLib, turning the
     * fallback path into a {@code NoClassDefFoundError}.
     */
    default void refresh() {
    }

    /** Which implementation this is, for the status report. */
    String describe();
}
