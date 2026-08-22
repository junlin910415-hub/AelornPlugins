package tw.linsy.aelorn.plugins.nms;

import org.bukkit.plugin.Plugin;

/**
 * Everything this plugin asks of the server internals, expressed without naming
 * them.
 *
 * <p><b>The rule that makes this worth having:</b> no signature here may mention a
 * {@code net.minecraft.*}, {@code org.bukkit.craftbukkit.*} or
 * {@code io.papermc.paper.plugin.manager.*} type. Internals are exchanged as
 * {@link Plugin}, {@link String} and primitives, and cast inside the adapter. The
 * moment an internal type appears here, every caller is bound to one server
 * release and the isolation this layer exists for is gone. The build enforces it:
 * the version-free tree compiles with no server core on the classpath, so a leak
 * fails the build rather than a code review.
 *
 * <p><b>Only four methods, and each earns its place.</b> Everything a plugin
 * manager needs that the API already provides — the command map, classloader
 * unregistration, permissions, services, listeners — is done through the API and
 * is deliberately absent here. What is left is the set of things with no API at
 * all:
 *
 * <ol>
 *   <li>{@link #deregisterPlugin} — the server's real plugin registry. Without
 *       this an unload leaves the plugin registered and the server keeps handing
 *       it out from a closed class loader.</li>
 *   <li>{@link #syncCommandTree} — rebuilding and resending the command tree, so
 *       clients stop offering completions for commands that no longer exist.</li>
 *   <li>{@link #dependsOn} — the server's own transitive dependency answer,
 *       rather than this plugin re-deriving one from declared dependencies.</li>
 *   <li>{@link #isTickThread} — whether the current thread may touch server
 *       state, which is the precondition for all of the above.</li>
 * </ol>
 *
 * <p>One implementation exists per version family, each compiled against its own
 * server jar, and only the matching one is ever loaded. Unlike AelornLib, a
 * missing adapter is <em>not</em> fatal here: see {@link ServerInternalsLoader}.
 */
public interface ServerInternals {

    /** The version family this adapter was compiled against, e.g. {@code 26.2}. */
    String targetFamily();

    /**
     * Whether the internals-backed capabilities actually work here.
     *
     * A capability question, not a version-string comparison: callers that must refuse
     * rather than degrade (unload) ask this, and comparing {@link #targetFamily()}
     * against a sentinel string put the answer in two places that could disagree.
     */
    default boolean available() {
        return true;
    }

    /**
     * True on a thread allowed to touch server state — any region or global tick
     * thread on a regionised server, the main thread otherwise.
     *
     * Checked rather than assumed because every other method here mutates state
     * the server expects to own.
     */
    boolean isTickThread();

    /**
     * Removes the plugin from the server's own registry: the plugin list, the
     * name lookup table, and the dependency graph.
     *
     * <p>This is the method the previous implementation got wrong, and it is worth
     * spelling out why. It reflected into {@code SimplePluginManager}'s
     * {@code plugins} and {@code lookupNames} fields, which on a modern
     * Paper-family server are vestigial — that class delegates almost everything
     * to Paper's own manager and keeps its own collections empty. So the unload
     * removed the plugin from two unused lists, reported success, and left the
     * server still holding the plugin behind a class loader it had just closed.
     *
     * @throws InternalsFailure when the registry could not be reached or changed;
     *                          the caller records it as a failed teardown step
     *                          rather than aborting the whole unload
     */
    void deregisterPlugin(Plugin plugin);

    /**
     * Rebuilds the command tree and resends it to every online player.
     *
     * Required after registering or unregistering commands: the client caches the
     * tree from login, so without this a removed command still autocompletes and
     * a new one does not, until the player reconnects.
     *
     * @throws InternalsFailure when the tree could not be rebuilt
     */
    void syncCommandTree();

    /**
     * Whether {@code dependant} needs {@code dependency}, directly or through
     * other plugins.
     *
     * Delegated to the server rather than derived here. Reading {@code depend}
     * and {@code softdepend} lists only finds direct edges, so disabling a plugin
     * two steps down the chain looked safe and broke something anyway.
     *
     * @throws InternalsFailure when the dependency graph could not be reached
     */
    boolean dependsOn(Plugin dependant, Plugin dependency);

    /** Human-readable summary for the status report. */
    String describe();
}
