package tw.linsy.serverbackup.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class LuckPermsExportCoordinator {
   private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");
   private static final int VALIDATION_BUFFER_SIZE = 65536;
   private final boolean enabled;
   private final String exportName;
   private final Path exportFile;
   private final long timeoutNanos;
   private final long stabilityNanos;
   private final Logger logger;

   public LuckPermsExportCoordinator(Path var1, boolean var2, String var3, int var4, int var5, Logger var6) {
      this.enabled = var2;
      this.exportName = normalizeExportName(var3);
      Path var7 = var1.toAbsolutePath().normalize().resolve("plugins").resolve("LuckPerms");
      this.exportFile = var7.resolve(this.exportName + ".json.gz").normalize();
      if (!this.exportFile.startsWith(var7)) {
         throw new IllegalArgumentException("LuckPerms export path escaped the plugin folder.");
      } else {
         this.timeoutNanos = TimeUnit.SECONDS.toNanos((long)Math.max(5, var4));
         this.stabilityNanos = TimeUnit.MILLISECONDS.toNanos((long)Math.max(250, var5));
         this.logger = var6;
      }
   }

   public boolean enabled() {
      return this.enabled;
   }

   public Path exportFile() {
      return this.exportFile;
   }

   public void prepare() throws IOException {
      if (this.enabled) {
         Files.createDirectories(this.exportFile.getParent());
         Files.deleteIfExists(this.exportFile);
      }
   }

   public void trigger() {
      if (this.enabled) {
         Plugin var1 = Bukkit.getPluginManager().getPlugin("LuckPerms");
         if (var1 != null && var1.isEnabled()) {
            String var2 = "luckperms:lp export " + this.exportName;
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), var2)) {
               throw new IllegalStateException("LuckPerms rejected its pre-backup export command.");
            }
         } else {
            throw new IllegalStateException("LuckPerms is unavailable; a consistent permissions export cannot be created.");
         }
      }
   }

   public void awaitCompletion() throws IOException {
      if (this.enabled) {
         long var1 = System.nanoTime() + this.timeoutNanos;
         long var3 = -1L;
         long var5 = -1L;
         long var7 = -1L;

         IOException var9;
         for(var9 = null; System.nanoTime() < var1; sleep(Duration.ofMillis(100L))) {
            if (Files.isRegularFile(this.exportFile, new LinkOption[0])) {
               BasicFileAttributes var10 = Files.readAttributes(this.exportFile, BasicFileAttributes.class);
               long var11 = var10.size();
               long var13 = var10.lastModifiedTime().toMillis();
               if (var11 > 0L && var11 == var5 && var13 == var7) {
                  if (var3 < 0L) {
                     var3 = System.nanoTime();
                  } else if (System.nanoTime() - var3 >= this.stabilityNanos) {
                     try {
                        long var15 = validateGzipJson(this.exportFile);
                        this.logger.info("LuckPerms consistent export ready: " + String.valueOf(this.exportFile) + " (" + var11 + " compressed bytes, " + var15 + " JSON bytes)");
                        return;
                     } catch (IOException var17) {
                        var9 = var17;
                        var3 = -1L;
                     }
                  }
               } else {
                  var5 = var11;
                  var7 = var13;
                  var3 = -1L;
               }
            }
         }

         IOException var18 = new IOException("Timed out waiting for a valid LuckPerms export: " + String.valueOf(this.exportFile));
         if (var9 != null) {
            var18.addSuppressed(var9);
         }

         throw var18;
      }
   }

   private static long validateGzipJson(Path var0) throws IOException {
      long var1 = 0L;
      int var3 = -1;
      byte[] var4 = new byte[65536];
      InputStream var5 = Files.newInputStream(var0);

      try {
         GZIPInputStream var6 = new GZIPInputStream(var5, 65536);

         int var7;
         try {
            while((var7 = var6.read(var4)) >= 0) {
               for(int var8 = 0; var3 < 0 && var8 < var7; ++var8) {
                  int var9 = var4[var8] & 255;
                  if (!Character.isWhitespace(var9)) {
                     var3 = var9;
                  }
               }

               var1 += (long)var7;
            }
         } catch (Throwable var12) {
            try {
               var6.close();
            } catch (Throwable var11) {
               var12.addSuppressed(var11);
            }

            throw var12;
         }

         var6.close();
      } catch (Throwable var13) {
         if (var5 != null) {
            try {
               var5.close();
            } catch (Throwable var10) {
               var13.addSuppressed(var10);
            }
         }

         throw var13;
      }

      if (var5 != null) {
         var5.close();
      }

      if (var1 > 0L && var3 == 123) {
         return var1;
      } else {
         throw new IOException("LuckPerms export is not a non-empty JSON GZIP file: " + String.valueOf(var0));
      }
   }

   private static void sleep(Duration var0) throws IOException {
      try {
         Thread.sleep(var0.toMillis());
      } catch (InterruptedException var2) {
         Thread.currentThread().interrupt();
         throw new IOException("Interrupted while waiting for the LuckPerms export.", var2);
      }
   }

   private static String normalizeExportName(String var0) {
      String var1 = var0 == null ? "" : var0.trim();
      if (var1.toLowerCase(Locale.ROOT).endsWith(".json.gz")) {
         var1 = var1.substring(0, var1.length() - ".json.gz".length());
      }

      if (SAFE_FILE_NAME.matcher(var1).matches() && !var1.equals(".") && !var1.equals("..")) {
         return var1;
      } else {
         throw new IllegalArgumentException("Invalid LuckPerms export file name: " + var0);
      }
   }
}
