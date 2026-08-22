package tw.linsy.aelorn.plugins.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import tw.linsy.aelorn.lib.AelornLib;
import tw.linsy.aelorn.lib.sched.Schedulers;

/**
 * Scheduling delegated to AelornLib, used when the core is installed.
 *
 * <p><b>This class must never be referenced from a field type, a signature, or
 * any code path that runs before the core has been confirmed present.</b> It is
 * the only place in the plugin that names {@code tw.linsy.aelorn.lib}, so its
 * first active use is what triggers loading those classes. {@link Platform}
 * reaches it through {@link #create} behind a plugin-presence check and catches
 * {@link Throwable}, which is what turns "core absent" into a fallback instead of
 * a {@code NoClassDefFoundError} during {@code onEnable}.
 *
 * <p>The delegation earns its keep by making the core's scheduling policy — task
 * ownership, cancellation semantics — apply here too, rather than this plugin
 * quietly keeping its own copy that drifts.
 */
final class CoreSched implements Sched {

    private final Schedulers delegate;
    private final String coreVersion;

    private CoreSched(Schedulers delegate, String coreVersion) {
        this.delegate = delegate;
        this.coreVersion = coreVersion;
    }

    /**
     * @throws Throwable when AelornLib's classes are missing or it is loaded but
     *                   not enabled; the caller treats any failure as "no core"
     */
    static Sched create(Plugin owner) {
        AelornLib core = AelornLib.require();
        return new CoreSched(core.schedulersFor(owner), core.version());
    }

    @Override
    public void global(Runnable task) {
        delegate.global(task);
    }

    @Override
    public void async(Runnable task) {
        delegate.async(task);
    }

    @Override
    public void entity(Entity entity, Runnable task) {
        delegate.entity(entity, task);
    }

    @Override
    public ScheduledTask asyncRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return delegate.asyncRepeating(task, initialDelay, period, unit);
    }

    @Override
    public ScheduledTask asyncDelayed(Runnable task, long delay, TimeUnit unit) {
        return delegate.asyncDelayed(task, delay, unit);
    }

    @Override
    public void cancelAll() {
        delegate.cancelAll();
    }

    @Override
    public String describe() {
        return "AelornLib " + coreVersion;
    }
}
