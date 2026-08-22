package tw.linsy.serverbackup;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import tw.linsy.serverbackup.core.BackupCategory;
import tw.linsy.serverbackup.core.BackupLogger;
import tw.linsy.serverbackup.core.BackupProgressSink;
import tw.linsy.serverbackup.core.BackupResult;
import tw.linsy.serverbackup.core.BackupService;
import tw.linsy.serverbackup.core.BackupSettings;
import tw.linsy.serverbackup.core.PerformanceOptimizer;
import tw.linsy.serverbackup.core.PerformancePlan;
import tw.linsy.serverbackup.integration.LuckPermsExportCoordinator;
import tw.linsy.serverbackup.update.UpdateAuditReport;
import tw.linsy.serverbackup.update.UpdateGuardService;
import tw.linsy.serverbackup.update.UpdateSnapshot;

public final class ServerBackupPlugin extends JavaPlugin {

    /**
     * Folia 排程由 AelornLib 提供。延遲解析:即使有呼叫在 onEnable 完成前抵達,
     * 拿到的也是可用的 facade 而不是 null。
     */
    private tw.linsy.aelorn.lib.sched.Schedulers schedulers;

    public tw.linsy.aelorn.lib.sched.Schedulers schedulers() {
        if (schedulers == null) {
            schedulers = tw.linsy.aelorn.lib.AelornLib.require().schedulersFor(this);
        }
        return schedulers;
    }
   private final AtomicBoolean backupRunning = new AtomicBoolean(false);
   private volatile BackupSettings backupSettings;
   private volatile BackupService backupService;
   private volatile String saveCommand;
   private volatile boolean scheduleEnabled;
   private volatile long scheduleIntervalHours;
   private volatile long scheduleInitialDelayMinutes;
   private volatile boolean performanceAutoTuneEnabled;
   private volatile boolean performanceRefreshBeforeBackup;
   private volatile String performanceAutoTuneProfile;
   private volatile int performanceAutoTuneMinMegabytesPerSecond;
   private volatile int performanceAutoTuneMaxMegabytesPerSecond;
   private volatile boolean performanceDiskProbeEnabled;
   private volatile int performanceDiskProbeMegabytes;
   private volatile int performanceProgressLogIntervalSeconds;
   private volatile BackupProgressDisplay progressDisplay;
   private volatile boolean updateGuardEnabled;
   private volatile UpdateGuardService updateGuardService;
   private volatile LuckPermsExportCoordinator luckPermsExportCoordinator;
   private ScheduledTask scheduledBackupTask;

   public void onEnable() {
      this.saveDefaultConfig();
      this.reloadBackupSettings();
      BackupCommand var1 = new BackupCommand(this);
      PluginCommand var2 = (PluginCommand)Objects.requireNonNull(this.getCommand("serverbackup"), "serverbackup command is missing");
      var2.setExecutor(var1);
      var2.setTabCompleter(var1);
      this.restartAutomaticBackups();
      this.scheduleStartupAudit();
      this.getLogger().info("ServerBackup enabled. Backup root: " + String.valueOf(this.backupSettings.backupRoot()));
   }

   public void onDisable() {
      this.cancelAutomaticBackups();
      if (this.progressDisplay != null) {
         this.progressDisplay.shutdown();
      }

   }

   public void reloadBackupSettings() {
      this.reloadConfig();
      this.getConfig().options().copyDefaults(true);
      this.saveConfig();
      FileConfiguration var1 = this.getConfig();
      Path var2 = this.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
      Path var3 = resolvePath(var2, var1.getString("backup.root", "Server/Backup"));
      BukkitBackupLogger var4 = new BukkitBackupLogger();
      long var5 = Math.max(0L, var1.getLong("performance.max-mb-per-second", 16L));
      long var7 = var5 * 1024L * 1024L;
      int var9 = var1.getBoolean("progress.console.enabled", true) ? var1.getInt("progress.console.interval-seconds", var1.getInt("performance.progress-log-interval-seconds", 60)) : 0;
      PerformancePlan var10 = createPerformancePlan(var1, var3, var7, var9, var4);
      EnumMap var11 = new EnumMap(BackupCategory.class);

      for(BackupCategory var15 : BackupCategory.values()) {
         var11.put(var15, var1.getString("categories." + var15.configKey(), var15.defaultFolder()));
      }

      this.backupSettings = BackupSettings.builder(var2, var3).archiveFolderName(var1.getString("backup.archive-folder", "archives")).datePattern(var1.getString("backup.date-format", "yyyy-MM-dd_HH-mm-ss")).worldMarkerFile(var1.getString("classification.world-marker-file", "level.dat")).keepCategorizedFolder(var1.getBoolean("backup.keep-categorized-folder", true)).includeEmptyDirectories(var1.getBoolean("backup.include-empty-directories", true)).retentionEnabled(var1.getBoolean("retention.enabled", true)).retentionKeepLast(var1.getInt("retention.keep-last", 10)).integrityEnabled(var1.getBoolean("integrity.enabled", true)).verifyCopiedFileSize(var1.getBoolean("integrity.verify-copied-file-size", true)).verifyZipAfterCreate(var1.getBoolean("integrity.verify-zip-after-create", true)).retryChangedFiles(var1.getInt("integrity.retry-changed-files", 1)).materialSafeModeEnabled(var1.getBoolean("integrity.material-safe-mode.enabled", true)).failOnMaterialWarning(var1.getBoolean("integrity.material-safe-mode.fail-on-warning", true)).materialPathRules(var1.getStringList("integrity.material-safe-mode.paths")).materialExtensions(var1.getStringList("integrity.material-safe-mode.extensions")).databaseSafeModeEnabled(var1.getBoolean("integrity.database-safe-mode.enabled", true)).failOnDatabaseWarning(var1.getBoolean("integrity.database-safe-mode.fail-on-warning", true)).failOnDatabaseCopyFailure(var1.getBoolean("integrity.database-safe-mode.fail-on-copy-failure", true)).databaseRetryAttempts(var1.getInt("integrity.database-safe-mode.retry-attempts", 5)).databaseRetryDelayMillis(var1.getInt("integrity.database-safe-mode.retry-delay-ms", 500)).databaseStabilityCheckMillis(var1.getInt("integrity.database-safe-mode.stability-check-ms", 300)).databaseMaxStabilityWaitMillis(var1.getInt("integrity.database-safe-mode.max-stability-wait-ms", 3000)).databaseWindowsFallbackEnabled(var1.getBoolean("integrity.database-safe-mode.windows-locked-file-fallback.enabled", true)).databaseWindowsFallbackBackupMode(var1.getBoolean("integrity.database-safe-mode.windows-locked-file-fallback.backup-mode", true)).databaseWindowsFallbackRetries(var1.getInt("integrity.database-safe-mode.windows-locked-file-fallback.retries", 1)).databaseWindowsFallbackWaitSeconds(var1.getInt("integrity.database-safe-mode.windows-locked-file-fallback.wait-seconds", 1)).databaseWindowsFallbackTimeoutSeconds(var1.getInt("integrity.database-safe-mode.windows-locked-file-fallback.timeout-seconds", 30)).databaseWindowsEsentutlFallbackEnabled(var1.getBoolean("integrity.database-safe-mode.windows-esentutl-fallback.enabled", true)).databaseWindowsEsentutlFallbackTimeoutSeconds(var1.getInt("integrity.database-safe-mode.windows-esentutl-fallback.timeout-seconds", 30)).databaseWindowsVssFallbackEnabled(var1.getBoolean("integrity.database-safe-mode.windows-vss-fallback.enabled", true)).databaseWindowsVssFallbackTimeoutSeconds(var1.getInt("integrity.database-safe-mode.windows-vss-fallback.timeout-seconds", 90)).databasePathRules(var1.getStringList("integrity.database-safe-mode.paths")).databaseExtensions(var1.getStringList("integrity.database-safe-mode.extensions")).databaseFileNames(var1.getStringList("integrity.database-safe-mode.file-names")).performancePlan(var10).excludeRules(var1.getStringList("backup.excludes")).categoryFolders(var11).build();
      this.backupService = new BackupService(this.backupSettings, var4);
      this.updateGuardEnabled = var1.getBoolean("update-guard.enabled", true);
      this.updateGuardService = this.updateGuardEnabled ? new UpdateGuardService(var2, var3, var1.getStringList("update-guard.tracked-plugins"), var1.getStringList("update-guard.legacy-jar-patterns"), var1.getStringList("update-guard.protected-server-files"), var1.getInt("update-guard.keep-last", 20), var4) : null;
      this.luckPermsExportCoordinator = new LuckPermsExportCoordinator(var2, var1.getBoolean("integrity.database-safe-mode.luckperms-export.enabled", true), var1.getString("integrity.database-safe-mode.luckperms-export.file-name", "serverbackup-latest"), var1.getInt("integrity.database-safe-mode.luckperms-export.timeout-seconds", 60), var1.getInt("integrity.database-safe-mode.luckperms-export.stability-ms", 1000), this.getLogger());
      this.saveCommand = var1.getString("backup.save-command", "");
      this.scheduleEnabled = var1.getBoolean("schedule.enabled", true);
      this.scheduleIntervalHours = Math.max(1L, var1.getLong("schedule.interval-hours", 12L));
      this.scheduleInitialDelayMinutes = Math.max(1L, var1.getLong("schedule.initial-delay-minutes", 720L));
      this.performanceAutoTuneEnabled = var1.getBoolean("performance.auto-tune.enabled", true);
      this.performanceRefreshBeforeBackup = var1.getBoolean("performance.auto-tune.refresh-before-backup", true);
      this.performanceAutoTuneProfile = var1.getString("performance.auto-tune.profile", "balanced");
      this.performanceAutoTuneMinMegabytesPerSecond = var1.getInt("performance.auto-tune.min-mb-per-second", 6);
      this.performanceAutoTuneMaxMegabytesPerSecond = var1.getInt("performance.auto-tune.max-mb-per-second", 32);
      this.performanceDiskProbeEnabled = var1.getBoolean("performance.auto-tune.disk-probe-enabled", true);
      this.performanceDiskProbeMegabytes = var1.getInt("performance.auto-tune.disk-probe-mb", 8);
      this.performanceProgressLogIntervalSeconds = var9;
      if (this.progressDisplay != null) {
         this.progressDisplay.shutdown();
      }

      boolean var16 = var1.getBoolean("progress.op-display.bossbar", true);
      boolean var17 = !var16 && var1.getBoolean("progress.op-display.actionbar", false);
      this.progressDisplay = new BackupProgressDisplay(this, var1.getBoolean("progress.op-display.enabled", true), var16, var17, var1.getBoolean("progress.op-display.op-only", true), var1.getString("progress.op-display.permission", "serverbackup.progress"), var1.getLong("progress.op-display.update-interval-ticks", 20L), var1.getLong("progress.op-display.hide-delay-seconds", 5L));
      this.getLogger().info("Host performance: " + var10.hostSnapshot().summary());
      this.getLogger().info("Backup performance plan: " + var10.summary());
   }

   public void restartAutomaticBackups() {
      this.cancelAutomaticBackups();
      if (!this.scheduleEnabled) {
         this.getLogger().info("Automatic backups are disabled.");
      } else {
         long var1 = this.scheduleIntervalHours * 60L;
         this.scheduledBackupTask = schedulers().asyncRepeating(() -> this.requestBackup((CommandSender)null, "scheduled"), this.scheduleInitialDelayMinutes, var1, TimeUnit.MINUTES);
         this.getLogger().info("Automatic backups scheduled every " + this.scheduleIntervalHours + " hour(s), first run in " + this.scheduleInitialDelayMinutes + " minute(s).");
      }
   }

   public boolean requestBackup(CommandSender var1, String var2) {
      return this.requestBackup(var1, var2, (String)null);
   }

   public boolean requestUpdateSnapshot(CommandSender var1, String var2) {
      if (this.updateGuardEnabled && this.updateGuardService != null) {
         return this.requestBackup(var1, "pre-update:" + var2, var2);
      } else {
         send(var1, NamedTextColor.RED, "更新防護尚未啟用。");
         return false;
      }
   }

   public void requestUpdateAudit(CommandSender var1) {
      UpdateGuardService var2 = this.updateGuardService;
      if (this.updateGuardEnabled && var2 != null) {
         send(var1, NamedTextColor.AQUA, "正在非同步檢查插件版本、雜湊與殘留檔案...");
         schedulers().async(() -> {
            try {
               UpdateAuditReport var4 = var2.audit();
               this.logAudit(var4, "手動");
               this.executeForSender(var1, () -> sendAudit(var1, var4));
            } catch (IOException var5) {
               this.getLogger().log(Level.SEVERE, "Update guard audit failed", var5);
               this.executeForSender(var1, () -> send(var1, NamedTextColor.RED, "更新稽核失敗：" + var5.getMessage()));
            }

         });
      } else {
         send(var1, NamedTextColor.RED, "更新防護尚未啟用。");
      }
   }

   public boolean updateGuardEnabled() {
      return this.updateGuardEnabled;
   }

   public Path updateGuardRoot() {
      UpdateGuardService var1 = this.updateGuardService;
      return var1 == null ? null : var1.guardRoot();
   }

   private boolean requestBackup(CommandSender var1, String var2, String var3) {
      if (!this.backupRunning.compareAndSet(false, true)) {
         if (var1 != null) {
            send(var1, NamedTextColor.YELLOW, "目前已有備份正在執行。");
         } else {
            this.getLogger().info("Skipped " + var2 + " backup because another backup is already running.");
         }

         return false;
      } else {
         if (var1 != null) {
            send(var1, NamedTextColor.AQUA, var3 == null ? "伺服器備份已開始，完成前請勿變更插件檔案。" : "更新前快照已開始，將保存 JAR 雜湊並建立完整備份。");
         } else {
            this.getLogger().info("Starting " + var2 + " backup.");
         }

         BackupProgressDisplay var4 = this.progressDisplay;
         if (var4 != null) {
            var4.start(var2);
         }

         schedulers().async(() -> {
            LuckPermsExportCoordinator var8 = this.luckPermsExportCoordinator;

            BackupService var6;
            UpdateSnapshotContext var7;
            try {
               var6 = this.prepareBackupServiceForRun();
               var7 = this.captureUpdateSnapshot(var3);
               var8.prepare();
            } catch (Exception var10) {
               this.failBeforeCopy(var1, var4, (UpdateSnapshotContext)null, var10);
               return;
            }

            schedulers().global( () -> {
               if (!this.saveServerState()) {
                  this.failBeforeCopy(var1, var4, var7, new IOException("Pre-backup server save failed."));
               } else {
                  try {
                     var8.trigger();
                  } catch (RuntimeException var8x) {
                     this.failBeforeCopy(var1, var4, var7, var8x);
                     return;
                  }

                  BackupProgressSink var7x = var4 == null ? BackupProgressSink.NOOP : var4;
                  schedulers().async(() -> {
                     try {
                        var8.awaitCompletion();
                     } catch (IOException var10) {
                        this.failBeforeCopy(var1, var4, var7, var10);
                        return;
                     }

                     this.runBackup(var1, var6, var2, var7x, var7);
                  });
               }
            });
         });
         return true;
      }
   }

   public boolean isBackupRunning() {
      return this.backupRunning.get();
   }

   public boolean scheduleEnabled() {
      return this.scheduleEnabled;
   }

   public long scheduleIntervalHours() {
      return this.scheduleIntervalHours;
   }

   public long scheduleInitialDelayMinutes() {
      return this.scheduleInitialDelayMinutes;
   }

   public boolean performanceAutoTuneEnabled() {
      return this.performanceAutoTuneEnabled;
   }

   public boolean performanceRefreshBeforeBackup() {
      return this.performanceRefreshBeforeBackup;
   }

   public BackupSettings backupSettings() {
      return this.backupSettings;
   }

   public BackupService backupService() {
      return this.backupService;
   }

   public String saveCommand() {
      return this.saveCommand == null ? "" : this.saveCommand.trim();
   }

   private void cancelAutomaticBackups() {
      if (this.scheduledBackupTask != null) {
         this.scheduledBackupTask.cancel();
         this.scheduledBackupTask = null;
      }

   }

   private boolean saveServerState() {
      long var1 = System.nanoTime();

      try {
         Bukkit.getServer().savePlayers();
         int var3 = 0;

         for(World var5 : Bukkit.getWorlds()) {
            var5.save(true);
            ++var3;
         }

         String var8 = this.saveCommand();
         if (!var8.isBlank() && !Bukkit.dispatchCommand(Bukkit.getConsoleSender(), var8)) {
            throw new IllegalStateException("Additional save command was not accepted: " + var8);
         } else {
            long var9 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - var1);
            this.getLogger().info("Pre-backup save completed for " + var3 + " world(s) and online players in " + var9 + " ms.");
            return true;
         }
      } catch (RuntimeException var7) {
         this.getLogger().log(Level.SEVERE, "Pre-backup save failed; backup was cancelled to protect integrity", var7);
         return false;
      }
   }

   private BackupService prepareBackupServiceForRun() {
      if (this.performanceAutoTuneEnabled && this.performanceRefreshBeforeBackup) {
         try {
            BackupSettings var1 = this.backupSettings;
            BukkitBackupLogger var2 = new BukkitBackupLogger();
            PerformancePlan var3 = PerformanceOptimizer.autoTune(var1.backupRoot(), this.performanceAutoTuneProfile, this.performanceAutoTuneMinMegabytesPerSecond, this.performanceAutoTuneMaxMegabytesPerSecond, this.performanceDiskProbeEnabled, this.performanceDiskProbeMegabytes, this.performanceProgressLogIntervalSeconds, var2);
            BackupSettings var4 = var1.withPerformancePlan(var3);
            this.backupSettings = var4;
            this.backupService = new BackupService(var4, var2);
            this.getLogger().info("Refreshed backup performance plan: " + var3.summary());
         } catch (RuntimeException var5) {
            this.getLogger().log(Level.WARNING, "Could not refresh performance plan, using previous backup settings", var5);
         }

         return this.backupService;
      } else {
         return this.backupService;
      }
   }

   private UpdateSnapshotContext captureUpdateSnapshot(String var1) throws IOException {
      if (var1 == null) {
         return null;
      } else {
         UpdateGuardService var2 = this.updateGuardService;
         if (this.updateGuardEnabled && var2 != null) {
            return new UpdateSnapshotContext(var2, var2.capturePreUpdate(var1));
         } else {
            throw new IOException("Update guard is disabled.");
         }
      }
   }

   private void failBeforeCopy(CommandSender var1, BackupProgressDisplay var2, UpdateSnapshotContext var3, Exception var4) {
      this.backupRunning.set(false);
      this.getLogger().log(Level.SEVERE, "Backup preparation failed", var4);
      if (var3 != null) {
         var3.service().markBackupFailed(var3.snapshot(), var4.getMessage());
      }

      if (var2 != null) {
         var2.fail("備份準備失敗");
      }

      if (var1 != null) {
         this.executeForSender(var1, () -> send(var1, NamedTextColor.RED, "備份準備失敗：" + var4.getMessage()));
      }

   }

   private void runBackup(CommandSender var1, BackupService var2, String var3, BackupProgressSink var4, UpdateSnapshotContext var5) {
      try {
         BackupResult var6 = var2.createBackup(var4);
         if (var5 != null) {
            var5.service().markBackupComplete(var5.snapshot(), var6);
         }

         this.getLogger().info("Backup completed from " + var3 + ": " + String.valueOf(var6.archiveFile()));
         if (var1 != null) {
            this.executeForSender(var1, () -> this.sendSuccess(var1, var6, var5));
         }
      } catch (Exception var10) {
         this.getLogger().log(Level.SEVERE, "Backup failed", var10);
         if (var5 != null) {
            var5.service().markBackupFailed(var5.snapshot(), var10.getMessage());
         }

         if (var1 != null) {
            this.executeForSender(var1, () -> send(var1, NamedTextColor.RED, "備份失敗：" + var10.getMessage()));
         }
      } finally {
         this.backupRunning.set(false);
      }

   }

   private void sendSuccess(CommandSender var1, BackupResult var2, UpdateSnapshotContext var3) {
      send(var1, NamedTextColor.GREEN, var3 == null ? "備份完成。" : "更新前快照與完整備份均已完成。");
      NamedTextColor var10001 = NamedTextColor.GRAY;
      String var10002 = String.valueOf(var2.snapshotFolder());
      send(var1, var10001, "分類資料夾：" + var10002);
      var10001 = NamedTextColor.GRAY;
      var10002 = String.valueOf(var2.archiveFile());
      send(var1, var10001, "ZIP：" + var10002);
      var10001 = NamedTextColor.GRAY;
      var10002 = var2.copyVerified() ? "通過" : "未通過";
      send(var1, var10001, "完整性：複製" + var10002 + "、ZIP " + (var2.zipVerified() ? "通過" : "未通過") + "，報告：" + String.valueOf(var2.integrityReportFile()));
      var10001 = NamedTextColor.GRAY;
      long var9 = var2.fileCount();
      send(var1, var10001, "檔案：" + var9 + "，材質：" + var2.materialFileCount() + "，資料庫：" + var2.databaseFileCount() + "，容量：" + var2.humanReadableBytes());
      if (var3 != null) {
         send(var1, NamedTextColor.GRAY, "更新基準：" + String.valueOf(var3.snapshot().snapshotDirectory()));
      }

      if (!var2.warnings().isEmpty()) {
         send(var1, NamedTextColor.YELLOW, "警告：" + var2.warnings().size() + " 個項目未完整處理，請查看 manifest.json。");
      }

   }

   private void scheduleStartupAudit() {
      UpdateGuardService var1 = this.updateGuardService;
      if (this.updateGuardEnabled && var1 != null) {
         schedulers().asyncDelayed(() -> {
            try {
               this.logAudit(var1.audit(), "啟動");
            } catch (IOException var4) {
               this.getLogger().log(Level.SEVERE, "Startup update guard audit failed", var4);
            }

         }, 30L, TimeUnit.SECONDS);
      }
   }

   private void logAudit(UpdateAuditReport var1, String var2) {
      Level var3 = var1.healthy() ? Level.INFO : Level.SEVERE;
      this.getLogger().log(var3, "更新防護 " + var2 + "稽核：" + var1.summary());

      for(String var5 : var1.errors()) {
         this.getLogger().severe("[更新防護] " + var5);
      }

      for(String var8 : var1.warnings()) {
         this.getLogger().warning("[更新防護] " + var8);
      }

      for(String var9 : var1.changes()) {
         this.getLogger().info("[更新防護] " + var9);
      }

   }

   private static void sendAudit(CommandSender var0, UpdateAuditReport var1) {
      NamedTextColor var10001 = var1.healthy() ? NamedTextColor.GREEN : NamedTextColor.RED;
      String var10002 = var1.healthy() ? "通過：" : "未通過：";
      send(var0, var10001, "更新稽核" + var10002 + var1.summary());
      var1.errors().stream().limit(10L).forEach((var1x) -> send(var0, NamedTextColor.RED, "錯誤：" + var1x));
      var1.warnings().stream().limit(10L).forEach((var1x) -> send(var0, NamedTextColor.YELLOW, "提醒：" + var1x));
      var1.changes().stream().limit(10L).forEach((var1x) -> send(var0, NamedTextColor.AQUA, "差異：" + var1x));
   }

   private void executeForSender(CommandSender var1, Runnable var2) {
      if (var1 instanceof Player var3) {
         var3.getScheduler().execute(this, var2, (Runnable)null, 1L);
      } else {
         schedulers().global( var2);
      }
   }

   private static void send(CommandSender var0, NamedTextColor var1, String var2) {
      var0.sendMessage(Component.text(var2, var1));
   }

   private static Path resolvePath(Path var0, String var1) {
      Path var2 = Paths.get(var1 != null && !var1.isBlank() ? var1 : "Server/Backup");
      if (!var2.isAbsolute()) {
         var2 = var0.resolve(var2);
      }

      return var2.toAbsolutePath().normalize();
   }

   private static PerformancePlan createPerformancePlan(FileConfiguration var0, Path var1, long var2, int var4, BackupLogger var5) {
      return var0.getBoolean("performance.auto-tune.enabled", true) ? PerformanceOptimizer.autoTune(var1, var0.getString("performance.auto-tune.profile", "balanced"), var0.getInt("performance.auto-tune.min-mb-per-second", 6), var0.getInt("performance.auto-tune.max-mb-per-second", 32), var0.getBoolean("performance.auto-tune.disk-probe-enabled", true), var0.getInt("performance.auto-tune.disk-probe-mb", 8), var4, var5) : PerformanceOptimizer.manual(var1, var2, var0.getInt("performance.buffer-kb", 256) * 1024, var0.getInt("performance.pause-between-files-ms", 1), var0.getInt("performance.zip-compression-level", 1), var4, var5);
   }

   private final class BukkitBackupLogger implements BackupLogger {
      private BukkitBackupLogger() {
      }

      public void info(String var1) {
         ServerBackupPlugin.this.getLogger().info(var1);
      }

      public void warn(String var1) {
         ServerBackupPlugin.this.getLogger().warning(var1);
      }

      public void error(String var1, Throwable var2) {
         ServerBackupPlugin.this.getLogger().log(Level.SEVERE, var1, var2);
      }
   }

   private static record UpdateSnapshotContext(UpdateGuardService service, UpdateSnapshot snapshot) {
   }
}
