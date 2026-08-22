package tw.linsy.serverbackup.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PerformanceOptimizer {
   private PerformanceOptimizer() {
   }

   public static PerformancePlan autoTune(Path var0, String var1, int var2, int var3, boolean var4, int var5, int var6, BackupLogger var7) {
      HostPerformanceSnapshot var8 = HostPerformanceProbe.inspect(var0, var4, var5, var7);
      ArrayList var9 = new ArrayList();
      String var10 = normalizeProfile(var1);
      int var11 = calculateThrottleMegabytes(var8, var10, var2, var3, var9);
      int var12 = calculateBufferKilobytes(var11, var8);
      int var13 = calculatePauseMillis(var11, var8);
      int var14 = calculateZipLevel(var8, var10);
      var9.add("profile=" + var10);
      var9.add("throttle=" + var11 + "MB/s");
      var9.add("buffer=" + var12 + "KB");
      var9.add("pause=" + var13 + "ms");
      var9.add("zipLevel=" + var14);
      return new PerformancePlan(true, var10, (long)var11 * 1024L * 1024L, var12 * 1024, var13, var14, Math.max(0, var6), var8, List.copyOf(var9));
   }

   public static PerformancePlan manual(Path var0, long var1, int var3, int var4, int var5, int var6, BackupLogger var7) {
      HostPerformanceSnapshot var8 = HostPerformanceProbe.inspect(var0, false, 0, var7);
      return new PerformancePlan(false, "manual", Math.max(0L, var1), Math.max(8192, var3), Math.max(0, var4), clamp(var5, 0, 9), Math.max(0, var6), var8, List.of("manual settings from config.yml"));
   }

   private static int calculateThrottleMegabytes(HostPerformanceSnapshot var0, String var1, int var2, int var3, List<String> var4) {
      int var5 = Math.max(1, var2);
      int var6 = Math.max(var5, var3);
      int var7;
      if (!var0.diskProbeSuccessful()) {
         var7 = 8;
         var4.add("disk probe unavailable, conservative throttle");
      } else if (var0.diskWriteMegabytesPerSecond() >= (double)220.0F) {
         var7 = 32;
         var4.add("fast backup disk detected");
      } else if (var0.diskWriteMegabytesPerSecond() >= (double)120.0F) {
         var7 = 24;
         var4.add("healthy backup disk detected");
      } else if (var0.diskWriteMegabytesPerSecond() >= (double)60.0F) {
         var7 = 16;
         var4.add("moderate backup disk detected");
      } else if (var0.diskWriteMegabytesPerSecond() >= (double)30.0F) {
         var7 = 10;
         var4.add("slow backup disk detected");
      } else {
         var7 = 6;
         var4.add("very slow backup disk detected");
      }

      if (var0.processors() <= 2) {
         var7 = Math.min(var7, 6);
         var4.add("low CPU core count");
      } else if (var0.processors() <= 4) {
         var7 = Math.min(var7, 12);
         var4.add("limited CPU core count");
      }

      if (var0.jvmMaxMemoryBytes() > 0L && var0.jvmMaxMemoryBytes() < 4294967296L) {
         var7 = Math.min(var7, 8);
         var4.add("limited JVM memory");
      }

      if (var0.backupDiskFreePercent() > (double)0.0F && var0.backupDiskFreePercent() < (double)15.0F) {
         var7 = Math.min(var7, 8);
         var4.add("backup disk free space is low");
      }

      if ("conservative".equals(var1)) {
         var7 = Math.max(1, (int)Math.floor((double)var7 * 0.65));
      } else if ("aggressive".equals(var1)) {
         var7 = (int)Math.ceil((double)var7 * (double)1.25F);
      }

      return clamp(var7, var5, var6);
   }

   private static int calculateBufferKilobytes(int var0, HostPerformanceSnapshot var1) {
      if (var1.jvmMaxMemoryBytes() > 0L && var1.jvmMaxMemoryBytes() < 3221225472L) {
         return 128;
      } else if (var0 <= 8) {
         return 128;
      } else {
         return var0 <= 16 ? 256 : 512;
      }
   }

   private static int calculatePauseMillis(int var0, HostPerformanceSnapshot var1) {
      if (var1.processors() > 2 && var0 > 6) {
         if (var0 <= 10) {
            return 3;
         } else {
            return var0 <= 16 ? 2 : 1;
         }
      } else {
         return 4;
      }
   }

   private static int calculateZipLevel(HostPerformanceSnapshot var0, String var1) {
      return "aggressive".equals(var1) && var0.processors() >= 8 ? 2 : 1;
   }

   private static String normalizeProfile(String var0) {
      if (var0 == null) {
         return "balanced";
      } else {
         // The decompiler dropped the local holding the normalised value and
         // emitted a reference to a variable that does not exist; restored here.
         String normalized = var0.toLowerCase(Locale.ROOT).trim();
         return switch (normalized) {
            case "conservative", "balanced", "aggressive" -> normalized;
            default -> "balanced";
         };
      }
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var2, var0));
   }
}
