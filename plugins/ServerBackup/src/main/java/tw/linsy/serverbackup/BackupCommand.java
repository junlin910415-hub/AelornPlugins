package tw.linsy.serverbackup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import tw.linsy.serverbackup.core.BackupSettings;
import tw.linsy.serverbackup.core.PerformancePlan;

public final class BackupCommand implements CommandExecutor, TabCompleter {
   private static final List<String> SUBCOMMANDS = List.of("start", "snapshot", "audit", "info", "reload");
   private final ServerBackupPlugin plugin;

   public BackupCommand(ServerBackupPlugin var1) {
      this.plugin = var1;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      switch (var4.length == 0 ? "start" : var4[0].toLowerCase(Locale.ROOT)) {
         case "start":
            this.startBackup(var1);
            return true;
         case "snapshot":
            this.createUpdateSnapshot(var1, var4);
            return true;
         case "audit":
            this.auditUpdateState(var1);
            return true;
         case "info":
            this.sendInfo(var1);
            return true;
         case "reload":
            this.reload(var1);
            return true;
         default:
            send(var1, NamedTextColor.YELLOW, "用法：/" + var3 + " [start|snapshot <更新標籤>|audit|info|reload]");
            return true;
      }
   }

   private void startBackup(CommandSender var1) {
      if (!var1.hasPermission("serverbackup.use")) {
         send(var1, NamedTextColor.RED, "你沒有執行伺服器備份的權限。");
      } else {
         this.plugin.requestBackup(var1, "manual");
      }
   }

   private void createUpdateSnapshot(CommandSender var1, String[] var2) {
      if (!var1.hasPermission("serverbackup.admin")) {
         send(var1, NamedTextColor.RED, "你沒有建立更新前快照的權限。");
      } else {
         String var3 = var2.length > 1 ? String.join("-", (CharSequence[])Arrays.copyOfRange(var2, 1, var2.length)) : "manual-update";
         this.plugin.requestUpdateSnapshot(var1, var3);
      }
   }

   private void auditUpdateState(CommandSender var1) {
      if (!var1.hasPermission("serverbackup.admin")) {
         send(var1, NamedTextColor.RED, "你沒有執行更新稽核的權限。");
      } else {
         this.plugin.requestUpdateAudit(var1);
      }
   }

   private void sendInfo(CommandSender var1) {
      BackupSettings var2 = this.plugin.backupSettings();
      send(var1, NamedTextColor.AQUA, "ServerBackup");
      NamedTextColor var10001 = NamedTextColor.GRAY;
      String var10002 = String.valueOf(var2.backupRoot());
      send(var1, var10001, "備份主目錄：" + var10002);
      var10001 = NamedTextColor.GRAY;
      var10002 = String.valueOf(var2.archiveRoot());
      send(var1, var10001, "ZIP 目錄：" + var10002);
      send(var1, NamedTextColor.GRAY, "執行狀態：" + (this.plugin.isBackupRunning() ? "備份中" : "待命"));
      var10001 = NamedTextColor.GRAY;
      var10002 = this.plugin.scheduleEnabled() ? "每 " + this.plugin.scheduleIntervalHours() + " 小時，啟動後 " + this.plugin.scheduleInitialDelayMinutes() + " 分鐘首次執行" : "已停用";
      send(var1, var10001, "自動排程：" + var10002);
      var10001 = NamedTextColor.GRAY;
      var10002 = yesNo(var2.keepCategorizedFolder());
      send(var1, var10001, "保留分類資料夾：" + var10002);
      var10001 = NamedTextColor.GRAY;
      int var15 = var2.retentionKeepLast();
      send(var1, var10001, "保留份數：" + var15 + " 份");
      var10001 = NamedTextColor.GRAY;
      String var16 = yesNo(var2.verifyCopiedFileSize());
      send(var1, var10001, "完整性：檔案大小=" + var16 + "、ZIP 驗證=" + yesNo(var2.verifyZipAfterCreate()) + "、材質保護=" + yesNo(var2.materialSafeModeEnabled()) + "、資料庫保護=" + yesNo(var2.databaseSafeModeEnabled()));
      var10001 = NamedTextColor.GRAY;
      var16 = this.plugin.updateGuardEnabled() ? "已啟用" : "已停用";
      send(var1, var10001, "更新防護：" + var16 + (this.plugin.updateGuardRoot() == null ? "" : "，基準目錄：" + String.valueOf(this.plugin.updateGuardRoot())));
      PerformancePlan var3 = var2.performancePlan();
      if (var3 != null) {
         send(var1, NamedTextColor.GRAY, "效能方案：" + var3.summary());
         var10001 = NamedTextColor.GRAY;
         boolean var18 = this.plugin.performanceAutoTuneEnabled() && this.plugin.performanceRefreshBeforeBackup();
         send(var1, var10001, "每次備份前重新偵測：" + yesNo(var18));
         send(var1, NamedTextColor.GRAY, "主機偵測：" + var3.hostSnapshot().summary());
      } else {
         var10001 = NamedTextColor.GRAY;
         var16 = throttleText(var2.maxBytesPerSecond());
         send(var1, var10001, "速度上限：" + var16 + "，ZIP 壓縮等級 " + var2.zipCompressionLevel());
      }

   }

   private void reload(CommandSender var1) {
      if (!var1.hasPermission("serverbackup.admin")) {
         send(var1, NamedTextColor.RED, "你沒有重新載入 ServerBackup 的權限。");
      } else {
         this.plugin.reloadBackupSettings();
         this.plugin.restartAutomaticBackups();
         send(var1, NamedTextColor.GREEN, "ServerBackup 設定已重新載入。");
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var4.length != 1) {
         return Collections.emptyList();
      } else {
         String var5 = var4[0].toLowerCase(Locale.ROOT);
         ArrayList var6 = new ArrayList();

         for(String var8 : SUBCOMMANDS) {
            if (var8.startsWith(var5)) {
               var6.add(var8);
            }
         }

         return var6;
      }
   }

   private static void send(CommandSender var0, NamedTextColor var1, String var2) {
      var0.sendMessage(Component.text(var2, var1));
   }

   private static String throttleText(long var0) {
      return var0 <= 0L ? "不限速" : var0 / 1024L / 1024L + " MB/s";
   }

   private static String yesNo(boolean var0) {
      return var0 ? "是" : "否";
   }
}
