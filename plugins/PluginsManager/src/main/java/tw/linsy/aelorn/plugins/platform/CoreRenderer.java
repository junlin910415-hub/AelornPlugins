package tw.linsy.aelorn.plugins.platform;

import net.kyori.adventure.text.Component;
import tw.linsy.aelorn.lib.AelornLib;
import tw.linsy.aelorn.lib.text.Text;

/**
 * Rendering delegated to AelornLib, so this plugin honours the server-wide
 * {@code text-format} setting.
 *
 * <p><b>Load-order rule:</b> together with {@link CoreSched} this is one of only
 * two classes in the plugin that name {@code tw.linsy.aelorn.lib}. Nothing may
 * reference either from a field type or a signature — {@link Platform} reaches
 * them through a static factory behind a plugin-presence check and catches
 * {@link Throwable}, which is what turns "core absent" into a fallback instead of
 * a {@code NoClassDefFoundError} during {@code onEnable}.
 */
final class CoreRenderer implements Renderer {

    private final String coreVersion;
    private volatile Text.Format format;

    private CoreRenderer(AelornLib core) {
        this.coreVersion = core.version();
        this.format = core.textFormat();
    }

    /**
     * @throws Throwable when AelornLib's classes are missing or it is loaded but
     *                   not enabled; the caller treats any failure as "no core"
     */
    static Renderer create() {
        return new CoreRenderer(AelornLib.require());
    }

    /** Picks up a {@code /aelorncore reload} that changed the server-wide format. */
    @Override
    public void refresh() {
        this.format = AelornLib.require().textFormat();
    }

    @Override
    public Component render(String markup) {
        return Text.render(markup, format);
    }

    @Override
    public String plain(String markup) {
        return Text.plain(markup, format);
    }

    @Override
    public String describe() {
        return "AelornLib " + coreVersion + " (" + format + ")";
    }
}
