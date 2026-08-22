package tw.linsy.serverbackup.core;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class HostPerformanceProbe {
   private static final int PROBE_BUFFER_BYTES = 262144;

   private HostPerformanceProbe() {
   }

   static HostPerformanceSnapshot inspect(Path var0, boolean var1, int var2, BackupLogger var3) {
      int var4 = Math.max(1, Runtime.getRuntime().availableProcessors());
      double var5 = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
      long var7 = Runtime.getRuntime().maxMemory();
      long var9 = 0L;
      long var11 = 0L;
      OperatingSystemMXBean var13 = ManagementFactory.getOperatingSystemMXBean();
      if (var13 instanceof com.sun.management.OperatingSystemMXBean var14) {
         var9 = var14.getTotalMemorySize();
         var11 = var14.getFreeMemorySize();
      }

      long var20 = 0L;
      long var16 = 0L;

      try {
         Files.createDirectories(var0);
         var20 = Files.getFileStore(var0).getUsableSpace();
         var16 = Files.getFileStore(var0).getTotalSpace();
      } catch (IOException var19) {
         var3.warn("Could not read backup disk space: " + var19.getMessage());
      }

      DiskProbeResult var18 = var1 ? measureWriteSpeed(var0, var2, var3) : new DiskProbeResult((double)0.0F, false);
      return new HostPerformanceSnapshot(var4, var5, var7, var9, var11, var20, var16, var18.megabytesPerSecond(), var18.successful());
   }

   private static DiskProbeResult measureWriteSpeed(Path var0, int var1, BackupLogger var2) {
      int var3 = Math.max(1, Math.min(64, var1));
      Path var4 = var0.resolve(".serverbackup-disk-probe-" + System.nanoTime() + ".tmp");
      byte[] var5 = new byte[262144];
      long var6 = (long)var3 * 1024L * 1024L;
      long var8 = 0L;
      long var10 = System.nanoTime();

      DiskProbeResult var13;
      try {
         Files.createDirectories(var0);
         OutputStream var12 = Files.newOutputStream(var4, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

         try {
            while(var8 < var6) {
               int var33 = (int)Math.min((long)var5.length, var6 - var8);
               var12.write(var5, 0, var33);
               var8 += (long)var33;
            }
         } catch (Throwable var29) {
            if (var12 != null) {
               try {
                  var12.close();
               } catch (Throwable var28) {
                  var29.addSuppressed(var28);
               }
            }

            throw var29;
         }

         if (var12 != null) {
            var12.close();
         }

         long var32 = Math.max(1L, System.nanoTime() - var10);
         double var14 = (double)var8 / (double)1024.0F / (double)1024.0F / ((double)var32 / (double)1.0E9F);
         DiskProbeResult var16 = new DiskProbeResult(var14, true);
         return var16;
      } catch (IOException var30) {
         var2.warn("Disk probe failed, using conservative backup profile: " + var30.getMessage());
         var13 = new DiskProbeResult((double)0.0F, false);
      } finally {
         try {
            Files.deleteIfExists(var4);
         } catch (IOException var27) {
            var2.warn("Could not delete disk probe file: " + var27.getMessage());
         }

      }

      return var13;
   }

   private static record DiskProbeResult(double megabytesPerSecond, boolean successful) {
   }
}
