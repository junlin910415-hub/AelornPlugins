package tw.linsy.aelorn.plugins.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Scheduling with nothing but the server API — the standalone path.
 *
 * This is what runs when AelornLib is absent, and it is the reason this plugin
 * can be the tool that fixes a broken core rather than another casualty of it.
 *
 * <p>No reflection and no platform branch. The regionised scheduler interfaces
 * ({@code GlobalRegionScheduler}, {@code AsyncScheduler}, {@code EntityScheduler})
 * are part of the server API on Folia <em>and</em> on Paper-family servers such
 * as Purpur, which implement them against their single main thread. The previous
 * version reached these through reflection to stay portable; the portability was
 * already there in the API.
 */
final class ApiSched implements Sched {

    private final Plugin owner;

    ApiSched(Plugin owner) {
        this.owner = owner;
    }

    @Override
    public void global(Runnable task) {
        owner.getServer().getGlobalRegionScheduler().execute(owner, task);
    }

    @Override
    public void async(Runnable task) {
        owner.getServer().getAsyncScheduler().runNow(owner, ignored -> task.run());
    }

    @Override
    public void entity(Entity entity, Runnable task) {
        entity.getScheduler().run(owner, ignored -> task.run(), null);
    }

    @Override
    public ScheduledTask asyncRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return owner.getServer().getAsyncScheduler()
            .runAtFixedRate(owner, ignored -> task.run(), initialDelay, period, unit);
    }

    @Override
    public ScheduledTask asyncDelayed(Runnable task, long delay, TimeUnit unit) {
        return owner.getServer().getAsyncScheduler().runDelayed(owner, ignored -> task.run(), delay, unit);
    }

    @Override
    public void cancelAll() {
        owner.getServer().getGlobalRegionScheduler().cancelTasks(owner);
        owner.getServer().getAsyncScheduler().cancelTasks(owner);
    }

    @Override
    public String describe() {
        return "server-api";
    }
}
