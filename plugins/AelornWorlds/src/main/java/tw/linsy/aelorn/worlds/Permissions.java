package tw.linsy.aelorn.worlds;

import org.bukkit.permissions.Permissible;

/**
 * Permission checks that survive the rename from WorldLoaderX.
 *
 * The canonical nodes moved to {@code aelorn.worlds.*}, but every existing
 * LuckPerms grant on the live server names {@code worldloaderx.*}. Renaming
 * without a fallback would silently revoke staff permissions the moment the jar
 * is swapped — the kind of breakage that only shows up when someone tries to use
 * a command. So every check accepts either spelling.
 *
 * <p>This applies to configured nodes too: as long as an admin writes the new
 * prefix in config.yml, {@link #legacyOf} derives the old node automatically, so
 * {@code aelorn.worlds.world.build} still matches a grant of
 * {@code worldloaderx.world.build}.
 *
 * <p>The legacy fallback is a migration aid, not a permanent contract. Once
 * grants are updated it can be deleted along with this class.
 *
 * <h2>Why the admin node is split</h2>
 * Everything that changes state used to sit behind one {@link #ADMIN} node, so
 * handing a moderator the ability to clear mobs out of a laggy world also handed
 * them config reload, world-border resizing and the world spawn. The actions below
 * are separated by blast radius, and {@link #ADMIN} is declared their parent in
 * plugin.yml — an existing grant of {@code aelorn.worlds.admin} keeps working
 * unchanged, and nobody has to be over-privileged to do one job.
 */
final class Permissions {

    static final String PREFIX = "aelorn.worlds.";
    static final String LEGACY_PREFIX = "worldloaderx.";

    /** Read-only inspection: menu, status, info, rules, chunk counters. */
    static final String USE = PREFIX + "use";

    /**
     * The entity census behind {@code /aw health}.
     *
     * <p>Separate from {@link #USE} because it is the one read command that is
     * genuinely expensive: it posts a task per loaded chunk across every region of
     * a world, and with no world argument it does that for every world at once.
     * Left on a {@code default: true} node it is a denial-of-service button that any
     * player can hold down.
     */
    static final String INSPECT = PREFIX + "inspect";

    static final String TRANSFER = PREFIX + "transfer";

    /** Sending <em>somebody else</em> to a world; used to require full admin. */
    static final String TRANSFER_OTHERS = PREFIX + "transfer.others";

    static final String ENTRY_BYPASS = PREFIX + "entry.bypass";
    static final String RULES_BYPASS = PREFIX + "rules.bypass";

    /** Removing entities in bulk. Destructive and not undoable. */
    static final String PURGE = PREFIX + "purge";

    /** Flushing region files to disk. Safe, but IO-heavy. */
    static final String SAVE = PREFIX + "save";

    /** World border and spawn point — changes what players experience immediately. */
    static final String EDIT = PREFIX + "edit";

    /** Reloading and re-applying configuration. */
    static final String ADMIN = PREFIX + "admin";

    /** Default value of {@code transfer.permission-prefix}; admins may override it. */
    static final String WORLD_PREFIX = PREFIX + "world.";

    private Permissions() {
    }

    /** True when the holder has {@code node} or its pre-rename equivalent. */
    static boolean has(Permissible holder, String node) {
        if (node.isEmpty()) {
            return true;
        }
        if (holder.hasPermission(node)) {
            return true;
        }
        String legacy = legacyOf(node);
        return !legacy.equals(node) && holder.hasPermission(legacy);
    }

    /**
     * True when the holder has {@code node}, or holds {@link #ADMIN} outright.
     *
     * <p>For nodes that are generated rather than declared — the per-world transfer
     * nodes derived from {@code transfer.permission-prefix}. Those cannot be listed
     * as children of {@link #ADMIN} in plugin.yml because the world list is not known
     * until config is read, so without this an administrator is refused entry to
     * their own worlds the moment {@code require-per-world-permission} is switched on.
     */
    static boolean hasOrAdmin(Permissible holder, String node) {
        return has(holder, node) || has(holder, ADMIN);
    }

    /**
     * The pre-rename spelling of a node, or the node unchanged when it does not
     * use the Aelorn prefix (an admin may have configured something entirely
     * their own, which must be honoured verbatim).
     */
    static String legacyOf(String node) {
        return node.startsWith(PREFIX) ? LEGACY_PREFIX + node.substring(PREFIX.length()) : node;
    }
}
