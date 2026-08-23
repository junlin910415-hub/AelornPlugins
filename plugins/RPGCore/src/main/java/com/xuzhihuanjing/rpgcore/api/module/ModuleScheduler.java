package com.xuzhihuanjing.rpgcore.api.module;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** Folia-safe scheduling surface. Every delayed/repeating task is owned by the module scope. */
public interface ModuleScheduler {
    boolean executeEntity(Entity entity, Runnable task, Runnable retired);

    AutoCloseable runEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks);

    AutoCloseable runEntityAtFixedRate(
            Entity entity,
            Runnable task,
            Runnable retired,
            long initialDelayTicks,
            long periodTicks);

    void executeRegion(Location location, Runnable task);

    AutoCloseable runRegionLater(Location location, Runnable task, long delayTicks);

    AutoCloseable runRegionAtFixedRate(
            Location location,
            Runnable task,
            long initialDelayTicks,
            long periodTicks);

    void executeGlobal(Runnable task);

    AutoCloseable runGlobalLater(Runnable task, long delayTicks);

    AutoCloseable runGlobalAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks);
}
