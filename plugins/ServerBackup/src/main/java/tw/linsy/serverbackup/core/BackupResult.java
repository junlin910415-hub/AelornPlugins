package tw.linsy.serverbackup.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record BackupResult(String backupId, Path snapshotFolder, Path archiveFile, long fileCount, long directoryCount, long totalBytes, Duration duration, List<String> warnings, Path integrityReportFile, boolean copyVerified, boolean zipVerified, long materialFileCount, long databaseFileCount) {
   public String humanReadableBytes() {
      if (this.totalBytes < 1024L) {
         return this.totalBytes + " B";
      } else {
         double var1 = (double)this.totalBytes;
         String[] var3 = new String[]{"KB", "MB", "GB", "TB"};

         int var4;
         for(var4 = -1; var1 >= (double)1024.0F && var4 < var3.length - 1; ++var4) {
            var1 /= (double)1024.0F;
         }

         return String.format("%.2f %s", var1, var3[var4]);
      }
   }
}
