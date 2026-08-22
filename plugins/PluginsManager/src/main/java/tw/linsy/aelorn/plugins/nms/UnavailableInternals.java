package tw.linsy.aelorn.plugins.nms;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * What runs when no adapter matches this server.
 *
 * <p><b>Why this exists at all, when AelornLib refuses to enable in the same
 * situation.</b> The core fails closed because its region-ownership checks are
 * themselves internal calls, so a wrong answer corrupts world state silently — a
 * dead core is the cheaper failure. A plugin manager is the opposite case: the
 * moment an admin most needs to disable, reload or roll back a plugin is right
 * after a server upgrade broke something, which is exactly when no adapter
 * matches yet. A manager that refuses to start then is worse than useless.
 *
 * <p>So everything reachable through the API keeps working — list, info, scan,
 * enable, disable, reload, load, archive, restore, group operations, the command
 * index — and only the four internals-backed capabilities report themselves
 * unavailable, naming the fix. {@code unload} is the one operation that becomes
 * unsafe and refuses outright.
 */
final class UnavailableInternals implements ServerInternals {

    private final String detectedVersion;
    private final String supportedFamilies;
    private final String reason;

    UnavailableInternals(String detectedVersion, String supportedFamilies, String reason) {
        this.detectedVersion = detectedVersion;
        this.supportedFamilies = supportedFamilies;
        this.reason = reason;
    }

    @Override
    public String targetFamily() {
        return "none";
    }

    @Override
    public boolean available() {
        return false;
    }

    /**
     * Best effort from the API. On a single-threaded server this is exact; on a
     * regionised one it is true only for the global thread, which understates
     * ownership rather than overstating it — the safe direction for a fallback.
     */
    @Override
    public boolean isTickThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void deregisterPlugin(Plugin plugin) {
        throw unavailable("deregister-plugin");
    }

    @Override
    public void syncCommandTree() {
        throw unavailable("sync-command-tree");
    }

    @Override
    public boolean dependsOn(Plugin dependant, Plugin dependency) {
        throw unavailable("depends-on");
    }

    @Override
    public String describe() {
        return "unavailable (" + detectedVersion + "; " + reason + ")";
    }

    private InternalsFailure unavailable(String capability) {
        return new InternalsFailure(
            "此伺服器沒有可用的內部介接層，無法執行 " + capability
                + "。偵測到 " + detectedVersion + "，本版本支援 " + supportedFamilies
                + "。原因：" + reason);
    }
}
