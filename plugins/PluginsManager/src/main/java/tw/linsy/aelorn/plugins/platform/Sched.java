package tw.linsy.aelorn.plugins.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import org.bukkit.entity.Entity;

/**
 * Where this plugin's own work runs.
 *
 * Two implementations exist and the difference between them is not the platform —
 * it is whether AelornLib is installed. {@link ApiSched} talks to the regionised
 * scheduler API directly; {@link CoreSched} hands the same calls to the core so a
 * future change in the core's scheduling policy reaches this plugin too. Both
 * work on Folia and on Paper-family servers, because Paper ships the same
 * scheduler interfaces and runs them on its single main thread.
 *
 * <p>Deliberately narrow: a plugin manager only ever needs global-region work
 * (plugin state), async work (disk) and per-player work (sending a reply). It
 * never touches blocks or entities, so no region or location overloads exist to
 * be misused.
 */
public interface Sched {

    /** Plugin registry state — enable, disable, load, command map. Global-region work. */
    void global(Runnable task);

    /** Disk scanning, hashing, archive copies. Never touches server state. */
    void async(Runnable task);

    /**
     * Work owned by one entity, which for this plugin means sending a reply to a
     * player on the thread that owns them.
     */
    void entity(Entity entity, Runnable task);

    ScheduledTask asyncRepeating(Runnable task, long initialDelay, long period, TimeUnit unit);

    ScheduledTask asyncDelayed(Runnable task, long delay, TimeUnit unit);

    /** Cancels everything this plugin scheduled; call from {@code onDisable}. */
    void cancelAll();

    /** Which implementation this is, for the status report. */
    String describe();
}
