package tw.linsy.serverbackup;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import tw.linsy.serverbackup.core.BackupPhase;
import tw.linsy.serverbackup.core.BackupProgress;
import tw.linsy.serverbackup.core.BackupProgressSink;

public final class BackupProgressDisplay implements BackupProgressSink {
   private final ServerBackupPlugin plugin;
   private final boolean enabled;
   private final boolean bossBarEnabled;
   private final boolean actionBarEnabled;
   private final boolean opOnly;
   private final String permission;
   private final long updateIntervalTicks;
   private final long hideDelaySeconds;
   private final AtomicReference<BackupProgress> latestProgress = new AtomicReference();
   private final AtomicLong runId = new AtomicLong();
   private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap();
   private ScheduledTask task;

   public BackupProgressDisplay(ServerBackupPlugin var1, boolean var2, boolean var3, boolean var4, boolean var5, String var6, long var7, long var9) {
      this.plugin = var1;
      this.enabled = var2;
      this.bossBarEnabled = var3;
      this.actionBarEnabled = var4;
      this.opOnly = var5;
      this.permission = var6 == null ? "" : var6.trim();
      this.updateIntervalTicks = Math.max(10L, var7);
      this.hideDelaySeconds = Math.max(1L, var9);
   }

   public void start(String var1) {
      if (this.enabled) {
         long var2 = this.runId.incrementAndGet();
         this.latestProgress.set(BackupProgress.of("preparing", BackupPhase.SCAN, (double)0.0F, 0L, 0L, 0L, 0L, 0L, 0L, "觸發來源：" + var1, Duration.ZERO));
         this.ensureTask(var2);
      }
   }

   public void fail(String var1) {
      this.publish(BackupProgress.of("failed", BackupPhase.FAILED, (double)0.0F, 0L, 0L, 0L, 0L, 0L, 0L, var1, Duration.ZERO));
   }

   public void publish(BackupProgress var1) {
      if (this.enabled && var1 != null) {
         this.latestProgress.set(var1);
         if (var1.terminal()) {
            this.scheduleHide(this.runId.get());
         }

      }
   }

   public void shutdown() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      this.hideAll();
   }

   private void ensureTask(long var1) {
      if (this.task == null || this.task.isCancelled()) {
         this.task = plugin.schedulers().globalRepeating(() -> {
            BackupProgress var4 = (BackupProgress)this.latestProgress.get();
            if (var4 != null) {
               for(Player var6 : Bukkit.getOnlinePlayers()) {
                  this.updatePlayer(var6, var4);
               }

               if (var4.terminal() && var1 == this.runId.get()) {
                  this.scheduleHide(var1);
               }

            }
         }, 1L, this.updateIntervalTicks);
      }
   }

   private void updatePlayer(Player var1, BackupProgress var2) {
      var1.getScheduler().execute(this.plugin, () -> {
         if (!this.canSee(var1)) {
            this.hidePlayerNow(var1);
         } else {
            if (this.bossBarEnabled) {
               BossBar var3 = (BossBar)this.bossBars.computeIfAbsent(var1.getUniqueId(), (var0) -> BossBar.bossBar(Component.text("ServerBackup"), 0.0F, Color.BLUE, Overlay.PROGRESS));
               var3.name(title(var2));
               var3.progress((float)Math.max((double)0.0F, Math.min((double)1.0F, var2.progress())));
               var3.color(color(var2.phase()));
               var1.showBossBar(var3);
            }

            if (this.actionBarEnabled) {
               var1.sendActionBar(actionBar(var2));
            }

         }
      }, () -> this.bossBars.remove(var1.getUniqueId()), 1L);
   }

   private void scheduleHide(long var1) {
      plugin.schedulers().asyncDelayed(() -> {
         if (var1 == this.runId.get()) {
            plugin.schedulers().global( () -> {
               this.hideAll();
               if (this.task != null) {
                  this.task.cancel();
                  this.task = null;
               }

            });
         }
      }, this.hideDelaySeconds, TimeUnit.SECONDS);
   }

   private void hideAll() {
      for(Player var2 : Bukkit.getOnlinePlayers()) {
         var2.getScheduler().execute(this.plugin, () -> this.hidePlayerNow(var2), () -> this.bossBars.remove(var2.getUniqueId()), 1L);
      }

   }

   private void hidePlayerNow(Player var1) {
      BossBar var2 = (BossBar)this.bossBars.remove(var1.getUniqueId());
      if (var2 != null) {
         var1.hideBossBar(var2);
      }

   }

   private boolean canSee(Player var1) {
      if (this.opOnly && !var1.isOp()) {
         return false;
      } else {
         return this.permission.isBlank() || var1.hasPermission(this.permission);
      }
   }

   private static Component title(BackupProgress var0) {
      return ((TextComponent)((TextComponent)Component.text("ServerBackup ", NamedTextColor.AQUA).append(Component.text(phaseName(var0.phase()), phaseTextColor(var0.phase())))).append(Component.text(" " + var0.percent() + "% ", NamedTextColor.WHITE))).append(Component.text(var0.humanCurrentBytes() + "/" + var0.humanTotalBytes(), NamedTextColor.GRAY));
   }

   private static Component actionBar(BackupProgress var0) {
      return ((TextComponent)((TextComponent)Component.text("[ServerBackup] ", NamedTextColor.AQUA).append(Component.text(progressBar(var0.progress()), phaseTextColor(var0.phase())))).append(Component.text(" " + phaseName(var0.phase()) + " " + var0.percent() + "% ", NamedTextColor.WHITE))).append(Component.text(var0.humanCurrentBytes() + "/" + var0.humanTotalBytes(), NamedTextColor.GRAY));
   }

   private static String progressBar(double var0) {
      byte var2 = 20;
      int var3 = (int)Math.round(Math.max((double)0.0F, Math.min((double)1.0F, var0)) * (double)var2);
      String var10000 = "#".repeat(var3);
      return "[" + var10000 + "-".repeat(var2 - var3) + "]";
   }

   private static BossBar.Color color(BackupPhase var0) {
      BossBar.Color var10000;
      switch (var0) {
         case COMPLETE -> var10000 = Color.GREEN;
         case FAILED -> var10000 = Color.RED;
         case VERIFY -> var10000 = Color.YELLOW;
         case DATABASE -> var10000 = Color.YELLOW;
         case ZIP -> var10000 = Color.PURPLE;
         default -> var10000 = Color.BLUE;
      }

      return var10000;
   }

   private static NamedTextColor phaseTextColor(BackupPhase var0) {
      NamedTextColor var10000;
      switch (var0) {
         case COMPLETE -> var10000 = NamedTextColor.GREEN;
         case FAILED -> var10000 = NamedTextColor.RED;
         case VERIFY -> var10000 = NamedTextColor.YELLOW;
         case DATABASE -> var10000 = NamedTextColor.GOLD;
         case ZIP -> var10000 = NamedTextColor.LIGHT_PURPLE;
         default -> var10000 = NamedTextColor.AQUA;
      }

      return var10000;
   }

   private static String phaseName(BackupPhase var0) {
      String var10000;
      switch (var0) {
         case COMPLETE -> var10000 = "完成";
         case FAILED -> var10000 = "失敗";
         case VERIFY -> var10000 = "驗證";
         case DATABASE -> var10000 = "資料庫";
         case ZIP -> var10000 = "壓縮";
         case SCAN -> var10000 = "掃描";
         case COPY -> var10000 = "複製";
         case CLEANUP -> var10000 = "整理";
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }
}
