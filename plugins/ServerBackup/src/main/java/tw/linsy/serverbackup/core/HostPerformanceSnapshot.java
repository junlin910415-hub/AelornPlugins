package tw.linsy.serverbackup.core;

import java.util.Locale;

public record HostPerformanceSnapshot(int processors, double systemLoadAverage, long jvmMaxMemoryBytes, long physicalMemoryBytes, long freePhysicalMemoryBytes, long backupDiskUsableBytes, long backupDiskTotalBytes, double diskWriteMegabytesPerSecond, boolean diskProbeSuccessful) {
   public double backupDiskFreePercent() {
      return this.backupDiskTotalBytes <= 0L ? (double)0.0F : (double)this.backupDiskUsableBytes * (double)100.0F / (double)this.backupDiskTotalBytes;
   }

   public String summary() {
      int var10000 = this.processors;
      return var10000 + " CPU, JVM " + humanReadableBytes(this.jvmMaxMemoryBytes) + ", RAM free " + humanReadableBytes(this.freePhysicalMemoryBytes) + "/" + humanReadableBytes(this.physicalMemoryBytes) + ", disk free " + humanReadableBytes(this.backupDiskUsableBytes) + " (" + String.format(Locale.ROOT, "%.1f%%", this.backupDiskFreePercent()) + "), probe " + String.format(Locale.ROOT, "%.1f MB/s", this.diskWriteMegabytesPerSecond);
   }

   public static String humanReadableBytes(long var0) {
      if (var0 <= 0L) {
         return "unknown";
      } else if (var0 < 1024L) {
         return var0 + " B";
      } else {
         double var2 = (double)var0;
         String[] var4 = new String[]{"KB", "MB", "GB", "TB"};

         int var5;
         for(var5 = -1; var2 >= (double)1024.0F && var5 < var4.length - 1; ++var5) {
            var2 /= (double)1024.0F;
         }

         return String.format(Locale.ROOT, "%.2f %s", var2, var4[var5]);
      }
   }
}
