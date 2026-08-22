package tw.linsy.aelorn.plugins.platform;

import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.nms.ServerInternals;
import tw.linsy.aelorn.plugins.nms.ServerInternalsLoader;

/**
 * Everything the plugin needs from its surroundings, decided once at enable.
 *
 * Four questions, answered here and nowhere else: what kind of server is this,
 * where does work run, how is text rendered, and can the server internals be
 * reached. Services take this and stop caring about any of it.
 *
 * <h2>AelornLib is optional, on purpose</h2>
 * The core is a {@code softdepend}, not a {@code depend}. It fails closed when no
 * NMS adapter matches the server — correct for a core whose thread guards would
 * otherwise answer wrongly — and a plugin manager must not inherit that. The
 * moment an admin needs to disable or roll back a plugin is right after an upgrade
 * broke something, which is exactly when the core is down.
 *
 * <p>So the core is used when it is there and replaced when it is not: scheduling
 * falls back to the server API and rendering to a serializer of our own. The two
 * classes that name {@code tw.linsy.aelorn.lib} ({@link CoreSched},
 * {@link CoreRenderer}) are reached only through static factories inside
 * {@link #detect}, behind a plugin-presence check and a {@code catch (Throwable)}.
 * That shape matters: a field of a core type, or an {@code instanceof} against
 * one, would resolve the class on a server without the core and turn the fallback
 * into a {@code NoClassDefFoundError}.
 */
public final class Platform {

    private static final String CORE_PLUGIN = "AelornLib";

    private final PlatformProfile profile;
    private final Sched sched;
    private final MessageCatalog messages;
    private final ServerInternals internals;
    private final boolean coreBacked;

    private Platform(PlatformProfile profile, Sched sched, MessageCatalog messages,
                     ServerInternals internals, boolean coreBacked) {
        this.profile = profile;
        this.sched = sched;
        this.messages = messages;
        this.internals = internals;
        this.coreBacked = coreBacked;
    }

    /**
     * Probes the server and assembles the platform. Never throws: every optional
     * piece has a fallback, and the internals layer degrades rather than failing.
     */
    public static Platform detect(Plugin owner, String messagesFile) {
        Logger logger = owner.getLogger();
        PlatformProfile profile = PlatformProfile.detect();

        boolean corePresent = owner.getServer().getPluginManager().getPlugin(CORE_PLUGIN) != null;
        Sched sched = corePresent ? tryCoreSched(owner, logger) : null;
        Renderer renderer = corePresent ? tryCoreRenderer(logger) : null;
        boolean coreBacked = sched != null && renderer != null;

        if (sched == null) {
            sched = new ApiSched(owner);
        }
        if (renderer == null) {
            renderer = new AutoRenderer();
        }

        MessageCatalog messages = new MessageCatalog(owner, messagesFile, renderer);
        ServerInternals internals = ServerInternalsLoader.load(logger);

        logger.info("平台：" + profile.serverName() + " " + profile.minecraftVersion()
            + "（" + profile.threadingModel() + "）"
            + "，排程 " + sched.describe()
            + "，文字 " + renderer.describe()
            + "，內部介接層 " + internals.describe());
        return new Platform(profile, sched, messages, internals, coreBacked);
    }

    /**
     * @return the core-backed scheduler, or {@code null} when the core's classes
     *         are absent or it is loaded but not enabled
     */
    private static @Nullable Sched tryCoreSched(Plugin owner, Logger logger) {
        try {
            return CoreSched.create(owner);
        } catch (Throwable unavailable) {
            logger.warning("AelornLib 已安裝但無法取得排程器（" + describe(unavailable)
                + "），改用伺服器 API 排程。");
            return null;
        }
    }

    private static @Nullable Renderer tryCoreRenderer(Logger logger) {
        try {
            return CoreRenderer.create();
        } catch (Throwable unavailable) {
            logger.warning("AelornLib 已安裝但無法取得文字格式（" + describe(unavailable)
                + "），改用內建 Adventure 渲染。");
            return null;
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public PlatformProfile profile() {
        return profile;
    }

    public Sched sched() {
        return sched;
    }

    public MessageCatalog messages() {
        return messages;
    }

    public ServerInternals internals() {
        return internals;
    }

    /** True when both scheduling and rendering are delegated to AelornLib. */
    public boolean coreBacked() {
        return coreBacked;
    }
}
