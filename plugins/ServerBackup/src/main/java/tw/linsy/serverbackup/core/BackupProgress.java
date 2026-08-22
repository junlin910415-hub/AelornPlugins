package tw.linsy.serverbackup.core;

import java.time.Duration;

public record BackupProgress(String backupId, BackupPhase phase, double progress, long currentFiles, long totalFiles, long currentBytes, long totalBytes, long materialFiles, long databaseFiles, String message, Duration elapsed) {
   public static BackupProgress of(String var0, BackupPhase var1, double var2, long var4, long var6, long var8, long var10, long var12, long var14, String var16, Duration var17) {
      return new BackupProgress(var0, var1, clamp(var2), Math.max(0L, var4), Math.max(0L, var6), Math.max(0L, var8), Math.max(0L, var10), Math.max(0L, var12), Math.max(0L, var14), var16 == null ? "" : var16, var17 == null ? Duration.ZERO : var17);
   }

   public int percent() {
      return (int)Math.round(this.progress * (double)100.0F);
   }

   public String humanCurrentBytes() {
      return humanReadableBytes(this.currentBytes);
   }

   public String humanTotalBytes() {
      return humanReadableBytes(this.totalBytes);
   }

   public boolean terminal() {
      return this.phase == BackupPhase.COMPLETE || this.phase == BackupPhase.FAILED;
   }

   public String consoleLine() {
      StringBuilder var1 = new StringBuilder();
      var1.append("Backup progress [").append(this.phase.displayName()).append("] ");
      var1.append(this.percent()).append("%");
      if (this.totalFiles > 0L) {
         var1.append(" | files ").append(this.currentFiles).append('/').append(this.totalFiles);
      } else if (this.currentFiles > 0L) {
         var1.append(" | files ").append(this.currentFiles);
      }

      if (this.totalBytes > 0L) {
         var1.append(" | bytes ").append(this.humanCurrentBytes()).append('/').append(this.humanTotalBytes());
      } else if (this.currentBytes > 0L) {
         var1.append(" | bytes ").append(this.humanCurrentBytes());
      }

      if (this.materialFiles > 0L) {
         var1.append(" | material files ").append(this.materialFiles);
      }

      if (this.databaseFiles > 0L) {
         var1.append(" | database files ").append(this.databaseFiles);
      }

      if (!this.message.isBlank()) {
         var1.append(" | ").append(this.message);
      }

      return var1.toString();
   }

   public static String humanReadableBytes(long var0) {
      if (var0 < 1024L) {
         return var0 + " B";
      } else {
         double var2 = (double)var0;
         String[] var4 = new String[]{"KB", "MB", "GB", "TB"};

         int var5;
         for(var5 = -1; var2 >= (double)1024.0F && var5 < var4.length - 1; ++var5) {
            var2 /= (double)1024.0F;
         }

         return String.format("%.2f %s", var2, var4[var5]);
      }
   }

   private static double clamp(double var0) {
      return !Double.isNaN(var0) && !Double.isInfinite(var0) ? Math.max((double)0.0F, Math.min((double)1.0F, var0)) : (double)0.0F;
   }
}
