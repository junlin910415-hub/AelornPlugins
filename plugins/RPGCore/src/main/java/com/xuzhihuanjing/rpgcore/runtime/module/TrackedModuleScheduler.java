package com.xuzhihuanjing.rpgcore.runtime.module;

import com.xuzhihuanjing.rpgcore.api.module.ModuleScheduler;
import com.xuzhihuanjing.rpgcore.runtime.lifecycle.RegistrationScope;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Schedules under the contributing plugin and suppresses every callback after its module scope closes. */
final class TrackedModuleScheduler implements ModuleScheduler {
    private static final AutoCloseable NO_TASK = () -> { };

    private final Plugin owner;
    private final RegistrationScope scope;

    TrackedModuleScheduler(Plugin owner, RegistrationScope scope) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    @Override
    public boolean executeEntity(Entity entity, Runnable task, Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        Runnable guarded = scope.guard(task);
        Runnable guardedRetired = guardNullable(retired);
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            guarded.run();
            return true;
        }
        return entity.getScheduler().execute(owner, guarded, guardedRetired, 1L);
    }

    @Override
    public AutoCloseable runEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks) {
        Objects.requireNonNull(entity, "entity");
        AtomicReference<TrackedTask> handle = new AtomicReference<>();
        Runnable completion = oneShot(task, handle);
        Runnable retirement = oneShot(retired, handle);
        ScheduledTask scheduled = entity.getScheduler().runDelayed(
                owner, ignored -> completion.run(), retirement, Math.max(1L, delayTicks));
        return track(scheduled, handle);
    }

    @Override
    public AutoCloseable runEntityAtFixedRate(
            Entity entity,
            Runnable task,
            Runnable retired,
            long initialDelayTicks,
            long periodTicks) {
        Objects.requireNonNull(entity, "entity");
        Runnable guarded = scope.guard(task);
        AtomicReference<TrackedTask> handle = new AtomicReference<>();
        Runnable retirement = oneShot(retired, handle);
        ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(
                owner,
                ignored -> guarded.run(),
                retirement,
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks));
        return track(scheduled, handle);
    }

    @Override
    public void executeRegion(Location location, Runnable task) {
        Bukkit.getRegionScheduler().execute(owner, copy(location), scope.guard(task));
    }

    @Override
    public AutoCloseable runRegionLater(Location location, Runnable task, long delayTicks) {
        AtomicReference<TrackedTask> handle = new AtomicReference<>();
        Runnable completion = oneShot(task, handle);
        ScheduledTask scheduled = Bukkit.getRegionScheduler().runDelayed(
                owner, copy(location), ignored -> completion.run(), Math.max(1L, delayTicks));
        return track(scheduled, handle);
    }

    @Override
    public AutoCloseable runRegionAtFixedRate(
            Location location,
            Runnable task,
            long initialDelayTicks,
            long periodTicks) {
        Runnable guarded = scope.guard(task);
        ScheduledTask scheduled = Bukkit.getRegionScheduler().runAtFixedRate(
                owner,
                copy(location),
                ignored -> guarded.run(),
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks));
        return track(scheduled);
    }

    @Override
    public void executeGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(owner, scope.guard(task));
    }

    @Override
    public AutoCloseable runGlobalLater(Runnable task, long delayTicks) {
        AtomicReference<TrackedTask> handle = new AtomicReference<>();
        Runnable completion = oneShot(task, handle);
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(
                owner, ignored -> completion.run(), Math.max(1L, delayTicks));
        return track(scheduled, handle);
    }

    @Override
    public AutoCloseable runGlobalAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks) {
        Runnable guarded = scope.guard(task);
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                owner,
                ignored -> guarded.run(),
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks));
        return track(scheduled);
    }

    private AutoCloseable track(ScheduledTask task) {
        if (task == null) {
            return NO_TASK;
        }
        return scope.add("scheduled-task", new TrackedTask(task));
    }

    private AutoCloseable track(ScheduledTask task, AtomicReference<TrackedTask> reference) {
        if (task == null) {
            return NO_TASK;
        }
        TrackedTask tracked = new TrackedTask(task);
        reference.set(tracked);
        return scope.add("scheduled-task", tracked);
    }

    private Runnable oneShot(Runnable task, AtomicReference<TrackedTask> reference) {
        Runnable guarded = task == null ? () -> { } : scope.guard(task);
        return () -> {
            try {
                guarded.run();
            } finally {
                TrackedTask tracked = reference.get();
                if (tracked != null) {
                    scope.release(tracked);
                    tracked.complete();
                }
            }
        };
    }

    private Runnable guardNullable(Runnable task) {
        return task == null ? null : scope.guard(task);
    }

    private static Location copy(Location location) {
        return Objects.requireNonNull(location, "location").clone();
    }

    private static final class TrackedTask implements AutoCloseable {
        private final ScheduledTask task;
        private final AtomicBoolean finished = new AtomicBoolean();

        private TrackedTask(ScheduledTask task) {
            this.task = task;
        }

        private void complete() {
            finished.set(true);
        }

        @Override
        public void close() {
            if (finished.compareAndSet(false, true)) {
                task.cancel();
            }
        }
    }
}
