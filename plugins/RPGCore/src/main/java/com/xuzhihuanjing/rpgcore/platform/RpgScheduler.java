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

   /**
    * 全域定時任務。
    *
    * <p>簽章與實機 0.26.0 的 class 一致（javap 取得）：吃的是
    * {@code Consumer<ScheduledTask>} 而不是 {@code Runnable}，因為週期任務通常需要
    * 拿到自己的 handle 才能在條件滿足時自我取消。
    *
    * <p>登記進 {@code repeatingTasks} 是必要的 —— {@link #shutdown()} 靠那份集合
    * 在插件停用時收掉所有週期任務，漏登記的任務會在插件卸載後繼續跑，
    * 而且是對著已經失效的 class loader 跑。
    */
   public ScheduledTask runGlobalAtFixedRate(Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
      ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task, Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
      this.repeatingTasks.add(scheduled);
      return scheduled;
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
