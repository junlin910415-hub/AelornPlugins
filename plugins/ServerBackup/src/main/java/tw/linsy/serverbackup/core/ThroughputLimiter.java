package tw.linsy.serverbackup.core;

import java.io.IOException;

final class ThroughputLimiter {
   private static final long NANOS_PER_SECOND = 1000000000L;
   private static final long MAX_SLEEP_NANOS = 250000000L;
   private static final long FILE_PAUSE_BATCH_NANOS = 16000000L;
   private final long bytesPerSecond;
   private final int pauseBetweenFilesMillis;
   private long windowStartNanos;
   private long windowBytes;
   private long pendingFilePauseNanos;

   ThroughputLimiter(long var1, int var3) {
      this.bytesPerSecond = Math.max(0L, var1);
      this.pauseBetweenFilesMillis = Math.max(0, var3);
      this.windowStartNanos = System.nanoTime();
   }

   void afterBytes(long var1) throws IOException {
      if (this.bytesPerSecond > 0L && var1 > 0L) {
         this.windowBytes += var1;
         long var3 = System.nanoTime() - this.windowStartNanos;
         long var5 = this.windowBytes * 1000000000L / this.bytesPerSecond;
         long var7 = var5 - var3;
         if (var7 > 0L) {
            sleep(var7);
         }

         if (System.nanoTime() - this.windowStartNanos >= 1000000000L) {
            this.windowStartNanos = System.nanoTime();
            this.windowBytes = 0L;
         }

      }
   }

   void afterFile() throws IOException {
      if (this.pauseBetweenFilesMillis > 0) {
         this.pendingFilePauseNanos += (long)this.pauseBetweenFilesMillis * 1000000L;
         if (this.pendingFilePauseNanos >= 16000000L) {
            long var1 = this.pendingFilePauseNanos;
            this.pendingFilePauseNanos = 0L;
            sleep(var1);
         }

      }
   }

   private static void sleep(long var0) throws IOException {
      long var2 = var0;

      try {
         while(var2 > 0L) {
            long var4 = Math.min(var2, 250000000L);
            Thread.sleep(var4 / 1000000L, (int)(var4 % 1000000L));
            var2 -= var4;
         }

      } catch (InterruptedException var6) {
         Thread.currentThread().interrupt();
         throw new IOException("Backup was interrupted", var6);
      }
   }
}
