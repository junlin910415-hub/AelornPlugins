package com.xuzhihuanjing.rpgcore.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class RpgScheduler {
   private final Plugin plugin;
   private final boolean folia;
   private final Set<ScheduledTask> repeatingTasks = ConcurrentHashMap.newKeySet();

   public RpgScheduler(Plugin plugin) {
      this.plugin = plugin;
      this.folia = this.detectFolia();
   }

   public boolean isFolia() {
      return this.folia;
   }

   public boolean executeEntity(Entity entity, Runnable task, Runnable retired) {
      if (Bukkit.isOwnedByCurrentRegion(entity)) {
         task.run();
         return true;
      } else {
         return entity.getScheduler().execute(this.plugin, task, retired, 1L);
      }
   }

   public ScheduledTask runEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks) {
      return entity.getScheduler().runDelayed(this.plugin, (ignored) -> task.run(), retired, Math.max(1L, delayTicks));
   }

   public ScheduledTask runEntityAtFixedRate(Entity entity, Consumer<ScheduledTask> task, Runnable retired, long initialDelayTicks, long periodTicks) {
      ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(this.plugin, task, retired, Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
      if (scheduled != null) {
         this.repeatingTasks.add(scheduled);
      }

      return scheduled;
   }

   public ScheduledTask runRegionAtFixedRate(Location location, Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
      ScheduledTask scheduled = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, location.clone(), task, Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
      this.repeatingTasks.add(scheduled);
      return scheduled;
   }

   public void executeRegion(Location location, Runnable task) {
      Bukkit.getRegionScheduler().execute(this.plugin, location.clone(), task);
   }

   public ScheduledTask runRegionLater(Location location, Runnable task, long delayTicks) {
      return Bukkit.getRegionScheduler().runDelayed(this.plugin, location.clone(), (ignored) -> task.run(), Math.max(1L, delayTicks));
   }

   public void executeGlobal(Runnable task) {
      Bukkit.getGlobalRegionScheduler().execute(this.plugin, task);
   }

   public ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
      return Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, (ignored) -> task.run(), Math.max(1L, delayTicks));
   }

   public void cancel(ScheduledTask task) {
      if (task != null) {
         this.repeatingTasks.remove(task);
         task.cancel();
      }

   }

   public void shutdown() {
      this.repeatingTasks.forEach(ScheduledTask::cancel);
      this.repeatingTasks.clear();
      Bukkit.getGlobalRegionScheduler().cancelTasks(this.plugin);
   }

   private boolean detectFolia() {
      try {
         Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
         return true;
      } catch (ClassNotFoundException var2) {
         return false;
      }
   }
}
