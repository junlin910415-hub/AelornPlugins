package tw.linsy.serverbackup.core;

import java.util.List;

public record PerformancePlan(boolean autoTuned, String profile, long maxBytesPerSecond, int bufferSizeBytes, int pauseBetweenFilesMillis, int zipCompressionLevel, int progressLogIntervalSeconds, HostPerformanceSnapshot hostSnapshot, List<String> decisions) {
   public String throttleText() {
      if (this.maxBytesPerSecond <= 0L) {
         return "unlimited";
      } else {
         long var10000 = this.maxBytesPerSecond / 1024L;
         return var10000 / 1024L + " MB/s";
      }
   }

   public String summary() {
      String var10000 = this.autoTuned ? "auto" : "manual";
      return var10000 + "/" + this.profile + ", throttle " + this.throttleText() + ", buffer " + this.bufferSizeBytes / 1024 + " KB, pause " + this.pauseBetweenFilesMillis + " ms, zip level " + this.zipCompressionLevel;
   }
}
