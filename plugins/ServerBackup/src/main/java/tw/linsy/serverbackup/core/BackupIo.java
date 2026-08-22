package tw.linsy.serverbackup.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class BackupIo {
   private BackupIo() {
   }

   /**
    * Copies one file, reusing the caller's buffer.
    *
    * The buffer belongs to the phase, not to the file: allocating it here meant
    * one 256 KiB array per file across an 18k-file inventory.
    */
   static long copyFile(Path source, Path target, BasicFileAttributes sourceAttributes,
                        CopyBuffer buffer, ThroughputLimiter limiter) throws IOException {
      long copied = 0L;
      byte[] bytes = buffer.bytes();
      try {
         try (InputStream in = Files.newInputStream(source);
              OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                  StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            int read;
            while ((read = in.read(bytes)) >= 0) {
               out.write(bytes, 0, read);
               copied += read;
               limiter.afterBytes(read);
            }
         }
         Files.setLastModifiedTime(target, sourceAttributes.lastModifiedTime());
         limiter.afterFile();
         return copied;
      } catch (RuntimeException | IOException failure) {
         // A half-written target is worse than none: the manifest would record a
         // size that does not match the source.
         try {
            Files.deleteIfExists(target);
         } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
         }
         throw failure;
      }
   }

   /** Streams one file into an already-open sink (the zip), reusing the caller's buffer. */
   static long copyToStream(Path source, OutputStream target, CopyBuffer buffer,
                            ThroughputLimiter limiter) throws IOException {
      long copied = 0L;
      byte[] bytes = buffer.bytes();
      try (InputStream in = Files.newInputStream(source)) {
         int read;
         while ((read = in.read(bytes)) >= 0) {
            target.write(bytes, 0, read);
            copied += read;
            limiter.afterBytes(read);
         }
      }
      limiter.afterFile();
      return copied;
   }

   static long copyFileWithRobocopyFallback(Path var0, Path var1, FileTime var2, int var3, int var4, boolean var5, int var6) throws IOException {
      if (!isWindows()) {
         throw new IOException("Windows locked-file fallback is only available on Windows");
      } else if (var0.getParent() == null) {
         throw new IOException("source parent is not available for robocopy fallback");
      } else {
         Files.createDirectories(var1.getParent());
         Path var7 = Files.createTempDirectory(var1.getParent(), "sbk-robocopy-");

         long var15;
         try {
            ArrayList var8 = new ArrayList();
            var8.add("robocopy");
            var8.add(var0.getParent().toString());
            var8.add(var7.toString());
            var8.add(var0.getFileName().toString());
            var8.add("/R:" + Math.max(0, var3));
            var8.add("/W:" + Math.max(0, var4));
            var8.add("/COPY:DAT");
            var8.add("/DCOPY:DAT");
            var8.add("/J");
            var8.add("/NP");
            var8.add("/NFL");
            var8.add("/NDL");
            var8.add("/NJH");
            var8.add("/NJS");
            if (var5) {
               var8.add("/ZB");
            }

            ProcessBuilder var9 = new ProcessBuilder(var8);
            var9.redirectErrorStream(true);
            var9.redirectOutput(Redirect.DISCARD);
            Process var10 = var9.start();
            int var11 = Math.max(5, var6);
            boolean var12 = var10.waitFor((long)var11, TimeUnit.SECONDS);
            if (!var12) {
               var10.destroyForcibly();
               throw new IOException("robocopy fallback timed out after " + var11 + " seconds");
            }

            int var13 = var10.exitValue();
            if (var13 > 7) {
               throw new IOException("robocopy fallback failed with exit code " + var13);
            }

            Path var14 = var7.resolve(var0.getFileName().toString());
            if (!Files.isRegularFile(var14, new LinkOption[0])) {
               throw new IOException("robocopy fallback did not create target file");
            }

            if (var2 != null) {
               Files.setLastModifiedTime(var14, var2);
            }

            try {
               Files.move(var14, var1, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException var21) {
               Files.move(var14, var1, StandardCopyOption.REPLACE_EXISTING);
            }

            var15 = Files.size(var1);
         } catch (InterruptedException var22) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running robocopy fallback", var22);
         } finally {
            deleteRecursively(var7);
         }

         return var15;
      }
   }

   static long copyFileWithEsentutlFallback(Path var0, Path var1, FileTime var2, int var3) throws IOException {
      if (!isWindows()) {
         throw new IOException("Windows esentutl fallback is only available on Windows");
      } else {
         Files.createDirectories(var1.getParent());
         Path var4 = Files.createTempFile(var1.getParent(), "sbk-esentutl-", ".tmp");

         long var11;
         try {
            List var5 = List.of("esentutl.exe", "/y", var0.toString(), "/d", var4.toString(), "/o");
            ProcessBuilder var6 = new ProcessBuilder(var5);
            var6.redirectErrorStream(true);
            var6.redirectOutput(Redirect.DISCARD);
            Process var7 = var6.start();
            int var8 = Math.max(5, var3);
            boolean var9 = var7.waitFor((long)var8, TimeUnit.SECONDS);
            if (!var9) {
               var7.destroyForcibly();
               throw new IOException("esentutl fallback timed out after " + var8 + " seconds");
            }

            int var10 = var7.exitValue();
            if (var10 != 0) {
               throw new IOException("esentutl fallback failed with exit code " + var10);
            }

            if (var2 != null) {
               Files.setLastModifiedTime(var4, var2);
            }

            try {
               Files.move(var4, var1, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException var17) {
               Files.move(var4, var1, StandardCopyOption.REPLACE_EXISTING);
            }

            var11 = Files.size(var1);
         } catch (InterruptedException var18) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running esentutl fallback", var18);
         } finally {
            Files.deleteIfExists(var4);
         }

         return var11;
      }
   }

   static long copyFileWithVssFallback(Path var0, Path var1, FileTime var2, int var3) throws IOException {
      if (!isWindows()) {
         throw new IOException("Windows VSS fallback is only available on Windows");
      } else {
         Path var4 = var0.toAbsolutePath().normalize();
         Path var5 = var4.getRoot();
         if (var5 == null) {
            throw new IOException("source root is not available for VSS fallback");
         } else {
            Files.createDirectories(var1.getParent());
            Path var6 = Files.createTempFile(var1.getParent(), "sbk-vss-", ".tmp");

            long var14;
            try {
               String var7 = vssCopyScript(var5.toString(), var5.relativize(var4).toString(), var6.toAbsolutePath().normalize().toString());
               String var8 = Base64.getEncoder().encodeToString(var7.getBytes(StandardCharsets.UTF_16LE));
               ProcessBuilder var9 = new ProcessBuilder(new String[]{"powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", var8});
               var9.redirectErrorStream(true);
               var9.redirectOutput(Redirect.DISCARD);
               Process var10 = var9.start();
               int var11 = Math.max(15, var3);
               boolean var12 = var10.waitFor((long)var11, TimeUnit.SECONDS);
               if (!var12) {
                  var10.destroyForcibly();
                  throw new IOException("VSS fallback timed out after " + var11 + " seconds");
               }

               int var13 = var10.exitValue();
               if (var13 != 0) {
                  throw new IOException("VSS fallback failed with exit code " + var13);
               }

               if (!Files.isRegularFile(var6, new LinkOption[0])) {
                  throw new IOException("VSS fallback did not create target file");
               }

               if (var2 != null) {
                  Files.setLastModifiedTime(var6, var2);
               }

               try {
                  Files.move(var6, var1, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
               } catch (IOException var20) {
                  Files.move(var6, var1, StandardCopyOption.REPLACE_EXISTING);
               }

               var14 = Files.size(var1);
            } catch (InterruptedException var21) {
               Thread.currentThread().interrupt();
               throw new IOException("interrupted while running VSS fallback", var21);
            } finally {
               Files.deleteIfExists(var6);
            }

            return var14;
         }
      }
   }

   private static String vssCopyScript(String var0, String var1, String var2) {
      String var10000 = psLiteral(var0);
      return "$ErrorActionPreference = 'Stop'\n$volume = " + var10000 + "\n$relative = " + psLiteral(var1) + "\n$destination = " + psLiteral(var2) + "\n$shadowId = $null\n$shadow = Invoke-CimMethod -ClassName Win32_ShadowCopy -MethodName Create -Arguments @{Volume=$volume}\nif ($shadow.ReturnValue -ne 0) { throw ('VSS create failed: ' + $shadow.ReturnValue) }\n$shadowId = $shadow.ShadowID\ntry {\n  $copy = Get-CimInstance Win32_ShadowCopy | Where-Object { $_.ID -eq $shadowId } | Select-Object -First 1\n  if ($null -eq $copy) { throw 'VSS shadow copy not found' }\n  $shadowSource = ($copy.DeviceObject + '\\') + $relative\n  Copy-Item -LiteralPath $shadowSource -Destination $destination -Force\n} finally {\n  if ($shadowId) {\n    $copy = Get-CimInstance Win32_ShadowCopy | Where-Object { $_.ID -eq $shadowId } | Select-Object -First 1\n    if ($null -ne $copy) { Invoke-CimMethod -InputObject $copy -MethodName Delete | Out-Null }\n  }\n}\n";
   }

   private static String psLiteral(String var0) {
      return "'" + var0.replace("'", "''") + "'";
   }

   private static boolean isWindows() {
      return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
   }

   private static void deleteRecursively(Path var0) throws IOException {
      if (Files.exists(var0, new LinkOption[0])) {
         Files.walkFileTree(var0, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path var1, BasicFileAttributes var2) throws IOException {
               Files.deleteIfExists(var1);
               return FileVisitResult.CONTINUE;
            }

            public FileVisitResult postVisitDirectory(Path var1, IOException var2) throws IOException {
               if (var2 != null) {
                  throw var2;
               } else {
                  Files.deleteIfExists(var1);
                  return FileVisitResult.CONTINUE;
               }
            }
         });
      }
   }
}
