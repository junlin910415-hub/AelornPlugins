package tw.linsy.serverbackup.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class BackupService {
   private static final long PROGRESS_SINK_INTERVAL_NANOS = 250000000L;
   private final BackupSettings settings;
   private final BackupLogger logger;
   private final PathRules pathRules;
   private final DateTimeFormatter fileNameFormatter;
   private final MaterialBackupRules materialRules;
   private final DatabaseBackupRules databaseRules;

   public BackupService(BackupSettings var1, BackupLogger var2) {
      this.settings = var1;
      this.logger = var2;
      this.pathRules = new PathRules(var1.excludeRules());
      this.fileNameFormatter = DateTimeFormatter.ofPattern(var1.datePattern(), Locale.ROOT).withZone(ZoneId.systemDefault());
      this.materialRules = new MaterialBackupRules(var1.materialSafeModeEnabled(), var1.materialPathRules(), var1.materialExtensions());
      this.databaseRules = new DatabaseBackupRules(var1.databaseSafeModeEnabled(), var1.databasePathRules(), var1.databaseExtensions(), var1.databaseFileNames());
   }

   public BackupResult createBackup() throws IOException {
      return this.createBackup(BackupProgressSink.NOOP);
   }

   public BackupResult createBackup(BackupProgressSink var1) throws IOException {
      Instant var2 = Instant.now();
      Files.createDirectories(this.settings.backupRoot());
      Files.createDirectories(this.settings.archiveRoot());
      String var3 = this.uniqueBackupId(var2);
      Path var4 = this.settings.backupRoot().resolve(var3).normalize();
      Path var5 = this.settings.archiveRoot().resolve(var3 + ".zip").normalize();
      Path var6 = this.settings.archiveRoot().resolve(var3 + ".integrity.json").normalize();
      ProgressReporter var7 = new ProgressReporter(var3, var2, var1);
      BackupManifest var8 = new BackupManifest(var3, this.settings.serverRoot(), var4, var5, var2, this.settings.performancePlan());
      ArrayList var9 = new ArrayList();
      ArrayList var10 = new ArrayList();
      ArrayList var11 = new ArrayList();
      boolean var12 = false;
      String var13 = "";
      boolean var14 = false;

      try {
         Files.createDirectories(var4);
         this.logger.info("Creating backup " + var3 + " in " + String.valueOf(var4));
         var7.publish(BackupPhase.SCAN, (double)0.0F, 0L, 0L, 0L, 0L, 0L, 0L, "Preparing file inventory", true);
         BackupInventory var15 = this.scanInventory(var7);
         var9.addAll(var15.warnings());
         List<String> var10000 = var15.warnings();
         Objects.requireNonNull(var8);
         var10000.forEach(var8::addWarning);
         var7.publish(BackupPhase.SCAN, (double)1.0F, (long)var15.files().size(), (long)var15.files().size(), var15.totalBytes(), var15.totalBytes(), var15.materialFiles(), var15.databaseFiles(), "Inventory ready", true);
         CopyStats var16 = this.copyInventory(var4, var15, var8, var7, var9, var10, var11);
         var8.setIntegrity(var16.copyVerified(), false, var6);
         var8.write(var4.resolve("manifest.json"));
         long var17 = Files.size(var4.resolve("manifest.json"));
         long var19 = var16.copiedBytes() + var17;
         ArrayList var21 = new ArrayList(var16.copiedZipEntries());
         var21.add("manifest.json");
         var7.publish(BackupPhase.ZIP, (double)0.0F, 0L, (long)var21.size(), 0L, var19, var15.materialFiles(), var15.databaseFiles(), "Writing archive", true);
         ThroughputLimiter var22 = new ThroughputLimiter(this.settings.maxBytesPerSecond(), 0);
         ZipCounter var23 = new ZipCounter();
         ZipWriter.zipDirectory(var4, var5, this.settings.zipCompressionLevel(), this.settings.bufferSizeBytes(), var22, (var7x) -> {
            var23.add(var7x);
            var7.publish(BackupPhase.ZIP, ratio(var23.bytes(), var19), var23.files(), (long)var21.size(), var23.bytes(), var19, var15.materialFiles(), var15.databaseFiles(), "Archive: " + String.valueOf(var5.getFileName()), false);
         });
         if (this.settings.integrityEnabled() && this.settings.verifyZipAfterCreate()) {
            var7.publish(BackupPhase.VERIFY, (double)0.0F, 0L, (long)var21.size(), 0L, var19, var15.materialFiles(), var15.databaseFiles(), "Reading archive back", true);
            ThroughputLimiter var24 = new ThroughputLimiter(this.settings.maxBytesPerSecond(), 0);
            ZipVerifier.Result var25 = ZipVerifier.verify(var5, var21, var19, this.settings.bufferSizeBytes(), var24, (var5x, var7x) -> var7.publish(BackupPhase.VERIFY, ratio(var7x, var19), var5x, (long)var21.size(), var7x, var19, var15.materialFiles(), var15.databaseFiles(), "CRC and entry check", false));
            var12 = var25.verified();
            var13 = var25.archiveSha256();
            var9.addAll(var25.warnings());
            if (!var12) {
               var14 = true;
            }
         } else {
            var12 = false;
         }

         BackupIntegrityReport var28 = new BackupIntegrityReport(var16.copyVerified(), var12, (long)var15.files().size(), var16.copiedFiles(), var15.materialFiles(), var15.databaseFiles(), var16.copiedBytes(), Files.size(var5), var13, List.copyOf(var9), List.copyOf(var10), List.copyOf(var11), Instant.now());
         var28.write(var6);
         var8.setIntegrity(var16.copyVerified(), var12, var6);
         var8.finish(Instant.now());
         var8.write(var4.resolve("manifest.json"));
         if (var14) {
            throw new IOException("Zip verification failed. See " + String.valueOf(var6));
         } else {
            var7.publish(BackupPhase.CLEANUP, (double)1.0F, var16.copiedFiles(), (long)var15.files().size(), var16.copiedBytes(), var15.totalBytes(), var15.materialFiles(), var15.databaseFiles(), "Applying retention", true);
            if (!this.settings.keepCategorizedFolder()) {
               this.deleteRecursively(var4);
            }

            if (this.settings.retentionEnabled()) {
               this.cleanupRetention();
            }

            BackupResult var29 = var8.toResult();
            BackupLogger var30 = this.logger;
            String var10001 = String.valueOf(var5);
            var30.info("Backup completed: " + var10001 + " (" + var29.humanReadableBytes() + ")");
            var7.publish(BackupPhase.COMPLETE, (double)1.0F, var29.fileCount(), var29.fileCount(), var29.totalBytes(), var29.totalBytes(), var29.materialFileCount(), var29.databaseFileCount(), "Backup complete", true);
            return var29;
         }
      } catch (RuntimeException | IOException var26) {
         var7.publish(BackupPhase.FAILED, (double)1.0F, 0L, 0L, 0L, 0L, 0L, 0L, ((Exception)var26).getMessage(), true);
         throw var26;
      }
   }

   private BackupInventory scanInventory(final ProgressReporter var1) throws IOException {
      final BackupClassifier var2 = new BackupClassifier(this.settings);
      final ArrayList<BackupSourceDirectory> var3 = new ArrayList<>();
      final ArrayList<BackupSourceFile> var4 = new ArrayList<>();
      final ArrayList<String> var5 = new ArrayList<>();
      Files.walkFileTree(this.settings.serverRoot(), new SimpleFileVisitor<Path>() {
         private long scannedBytes;
         private long materialFiles;
         private long databaseFiles;

         {
         }

         public FileVisitResult preVisitDirectory(Path var1x, BasicFileAttributes var2x) throws IOException {
            if (BackupService.this.shouldSkip(var1x)) {
               return FileVisitResult.SKIP_SUBTREE;
            } else {
               if (BackupService.this.settings.includeEmptyDirectories() && !var1x.equals(BackupService.this.settings.serverRoot())) {
                  BackupClassifier.CategorizedPath var3x = var2.categorize(var1x);
                  var3.add(new BackupSourceDirectory(var3x.category(), var3x.targetRelative()));
               }

               return FileVisitResult.CONTINUE;
            }
         }

         public FileVisitResult visitFile(Path var1x, BasicFileAttributes var2x) {
            if (!BackupService.this.shouldSkip(var1x) && var2x.isRegularFile()) {
               BackupClassifier.CategorizedPath var3x = var2.categorize(var1x);
               Path var4x = BackupService.this.settings.serverRoot().relativize(var1x.toAbsolutePath().normalize());
               boolean var5x = BackupService.this.materialRules.isCritical(var4x);
               boolean var6 = BackupService.this.databaseRules.isCritical(var4x);
               var4.add(new BackupSourceFile(var1x, var3x.category(), var3x.targetRelative(), var2x.size(), var2x.lastModifiedTime(), var5x, var6));
               this.scannedBytes += var2x.size();
               if (var5x) {
                  ++this.materialFiles;
               }

               if (var6) {
                  ++this.databaseFiles;
               }

               var1.publish(BackupPhase.SCAN, (double)0.0F, (long)var4.size(), 0L, this.scannedBytes, 0L, this.materialFiles, this.databaseFiles, "Scanning files", false);
               return FileVisitResult.CONTINUE;
            } else {
               return FileVisitResult.CONTINUE;
            }
         }

         public FileVisitResult visitFileFailed(Path var1x, IOException var2x) {
            String var10000 = String.valueOf(var1x);
            String var3x = "Skipped during scan " + var10000 + ": " + var2x.getMessage();
            var5.add(var3x);
            BackupService.this.logger.warn(var3x);
            return FileVisitResult.CONTINUE;
         }
      });
      long var6 = 0L;
      long var8 = 0L;
      long var10 = 0L;
      long var12 = 0L;
      long var14 = 0L;

      for(BackupSourceFile var17 : var4) {
         var6 += var17.size();
         if (var17.materialCritical()) {
            ++var10;
            var8 += var17.size();
         }

         if (var17.databaseCritical()) {
            ++var14;
            var12 += var17.size();
         }
      }

      // Databases first, then material, then everything else; ties broken by path
      // so the order is reproducible. The path key is computed once per file
      // rather than inside the comparator, which would rebuild the same string
      // O(n log n) times over an 18k-file inventory.
      Map<Path, String> var25 = new HashMap<>(var4.size() * 2);
      for(BackupSourceFile var26 : var4) {
         var25.put(var26.source(), normalize(var26.source()));
      }

      var4.sort(Comparator.comparingInt((BackupSourceFile var0) -> var0.databaseCritical() ? 0 : (var0.materialCritical() ? 1 : 2))
         .thenComparing((var0) -> var25.get(var0.source())));
      return new BackupInventory(List.copyOf(var4), List.copyOf(var3), var6, var10, var8, var14, var12, List.copyOf(var5));
   }

   private CopyStats copyInventory(Path var1, BackupInventory var2, BackupManifest var3, ProgressReporter var4, List<String> var5, List<String> var6, List<String> var7) throws IOException {
      ThroughputLimiter var8 = new ThroughputLimiter(this.settings.maxBytesPerSecond(), this.settings.pauseBetweenFilesMillis());
      // One buffer for the whole copy phase; see CopyBuffer.
      CopyBuffer var25 = new CopyBuffer(this.settings.bufferSizeBytes());
      long var9 = 0L;
      long var11 = 0L;
      boolean var13 = true;
      HashSet var14 = new HashSet();

      for(BackupSourceDirectory var16 : var2.directories()) {
         Path var17 = this.targetPath(var1, var16.category(), var16.targetRelative());
         Files.createDirectories(var17);
         var3.addDirectory(var16.category());
      }

      this.waitForDatabaseStability(var2);

      for(BackupSourceFile var22 : var2.files()) {
         Path var23 = this.targetPath(var1, var22.category(), var22.targetRelative());
         Files.createDirectories(var23.getParent());

         try {
            CopyOutcome var18 = this.copyFileWithIntegrity(var22, var23, var8, var25);
            ++var9;
            var11 += var18.bytes();
            var3.addFile(var22.category(), var18.bytes());
            if (var22.materialCritical()) {
               var3.addMaterialFile(var18.bytes());
            }

            if (var22.databaseCritical()) {
               var3.addDatabaseFile(var18.bytes());
            }

            var14.add(var22.zipEntryName(this.settings));
            if (var18.warning() != null) {
               var13 = false;
               var5.add(var18.warning());
               var3.addWarning(var18.warning());
               this.logger.warn(var18.warning());
               if (var22.materialCritical()) {
                  var6.add(var18.warning());
                  if (this.settings.failOnMaterialWarning()) {
                     throw new IOException("Critical material file could not be backed up safely: " + String.valueOf(var22.source()));
                  }
               }

               if (var22.databaseCritical()) {
                  var7.add(var18.warning());
                  if (this.settings.failOnDatabaseWarning()) {
                     throw new IOException("Critical database file changed during backup: " + String.valueOf(var22.source()));
                  }
               }
            }
         } catch (IOException var20) {
            var13 = false;
            String var10000 = String.valueOf(var22.source());
            String var19 = "Skipped " + var10000 + ": " + var20.getMessage();
            var5.add(var19);
            var3.addWarning(var19);
            this.logger.warn(var19);
            if (var22.materialCritical() && this.settings.failOnMaterialWarning()) {
               var6.add(var19);
               throw var20;
            }

            if (var22.databaseCritical()) {
               var7.add(var19);
               if (this.settings.failOnDatabaseCopyFailure()) {
                  throw new IOException(var19, var20);
               }
            }
         }

         BackupPhase var24 = var22.databaseCritical() ? BackupPhase.DATABASE : BackupPhase.COPY;
         var4.publish(var24, ratio(var11, var2.totalBytes()), var9, (long)var2.files().size(), var11, var2.totalBytes(), var2.materialFiles(), var2.databaseFiles(), var22.databaseCritical() ? "Protecting plugin data/databases" : "Copying categorized files", false);
      }

      return new CopyStats(var9, var11, var13, Set.copyOf(var14));
   }

   private CopyOutcome copyFileWithIntegrity(BackupSourceFile var1, Path var2, ThroughputLimiter var3, CopyBuffer var25) throws IOException {
      int var4 = var1.databaseCritical() ? this.settings.databaseRetryAttempts() : Math.max(1, this.settings.retryChangedFiles() + 1);
      IOException var5 = null;

      for(int var6 = 1; var6 <= var4; ++var6) {
         try {
            BasicFileAttributes var7 = this.readSourceAttributes(var1);
            boolean var8 = var1.databaseCritical() && var6 == var4;
            CopyAttemptResult var9 = this.copySourceFile(var1, var2, var7, var3, var8, var25);
            long var10 = var9.bytes();
            if (this.settings.integrityEnabled() && this.settings.verifyCopiedFileSize()) {
               long var12 = Files.size(var2);
               if (var12 != var10) {
                  throw new IOException("target size mismatch: copied " + var10 + " B but target has " + var12 + " B");
               }
            }

            BasicFileAttributes var15 = Files.readAttributes(var1.source(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            boolean var13 = var7.size() != var15.size() || !sameTime(var7.lastModifiedTime(), var15.lastModifiedTime());
            if (!var13) {
               if (var9.fallbackUsed()) {
                  this.logger.info("Database locked-file fallback succeeded: " + String.valueOf(var1.source()));
               }

               return new CopyOutcome(var10, (String)null);
            }

            if (var6 >= var4) {
               return new CopyOutcome(var10, "Source changed during backup: " + String.valueOf(var1.source()));
            }

            this.waitBeforeRetry(var1);
         } catch (IOException var14) {
            var5 = var14;
            if (var6 >= var4) {
               throw var14;
            }

            this.waitBeforeRetry(var1);
         }
      }

      throw var5 == null ? new IOException("copy failed") : var5;
   }

   private CopyAttemptResult copySourceFile(BackupSourceFile var1, Path var2, BasicFileAttributes var3, ThroughputLimiter var4, boolean var5, CopyBuffer var25) throws IOException {
      try {
         long var6 = BackupIo.copyFile(var1.source(), var2, var3, var25, var4);
         return new CopyAttemptResult(var6, false);
      } catch (IOException var12) {
         if (var5 && var1.databaseCritical() && (this.settings.databaseWindowsFallbackEnabled() || this.settings.databaseWindowsEsentutlFallbackEnabled() || this.settings.databaseWindowsVssFallbackEnabled())) {
            if (this.settings.databaseWindowsFallbackEnabled()) {
               try {
                  long var14 = BackupIo.copyFileWithRobocopyFallback(var1.source(), var2, var3.lastModifiedTime(), this.settings.databaseWindowsFallbackRetries(), this.settings.databaseWindowsFallbackWaitSeconds(), this.settings.databaseWindowsFallbackBackupMode(), this.settings.databaseWindowsFallbackTimeoutSeconds());
                  return new CopyAttemptResult(var14, true);
               } catch (IOException var11) {
                  var12.addSuppressed(var11);
               }
            }

            if (this.settings.databaseWindowsEsentutlFallbackEnabled()) {
               try {
                  long var13 = BackupIo.copyFileWithEsentutlFallback(var1.source(), var2, var3.lastModifiedTime(), this.settings.databaseWindowsEsentutlFallbackTimeoutSeconds());
                  return new CopyAttemptResult(var13, true);
               } catch (IOException var10) {
                  var12.addSuppressed(var10);
               }
            }

            if (this.settings.databaseWindowsVssFallbackEnabled()) {
               try {
                  long var7 = BackupIo.copyFileWithVssFallback(var1.source(), var2, var3.lastModifiedTime(), this.settings.databaseWindowsVssFallbackTimeoutSeconds());
                  return new CopyAttemptResult(var7, true);
               } catch (IOException var9) {
                  var12.addSuppressed(var9);
               }
            }

            throw new IOException("database/data file copy failed for " + this.sourceLabel(var1) + " after Java and configured Windows fallback attempts: " + var12.getMessage(), var12);
         } else {
            throw var12;
         }
      }
   }

   private BasicFileAttributes readSourceAttributes(BackupSourceFile var1) throws IOException {
      return Files.readAttributes(var1.source(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
   }

   private void waitForDatabaseStability(BackupInventory var1) throws IOException {
      if (var1.databaseFiles() > 0L && this.settings.databaseStabilityCheckMillis() > 0) {
         long var2 = System.nanoTime();
         long var4 = System.nanoTime() + (long)this.settings.databaseMaxStabilityWaitMillis() * 1000000L;

         // Narrow the inventory to the database files once. The poll loop below
         // runs until the batch settles, and re-filtering the whole inventory on
         // every pass scanned all 18k entries just to find a handful.
         List<Path> var21 = new ArrayList<>();
         for(BackupSourceFile var22 : var1.files()) {
            if (var22.databaseCritical()) {
               var21.add(var22.source());
            }
         }

         Map var7;
         for(Map var6 = readDatabaseAttributes(var21); System.nanoTime() <= var4; var6 = var7) {
            sleepMillis((long)this.settings.databaseStabilityCheckMillis(), "waiting for database batch to stabilize");
            var7 = readDatabaseAttributes(var21);
            if (sameAttributes(var6, var7)) {
               long var8 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - var2);
               BackupLogger var10000 = this.logger;
               long var10001 = var1.databaseFiles();
               var10000.info("Database stability window completed for " + var10001 + " file(s) in " + var8 + " ms.");
               return;
            }
         }

         this.logger.warn("Database files remained active during the " + this.settings.databaseMaxStabilityWaitMillis() + " ms stability window; changed files will use per-file retries.");
      }
   }

   private static Map<Path, BasicFileAttributes> readDatabaseAttributes(List<Path> var0) {
      HashMap var1 = new HashMap();

      for(Path var3 : var0) {
         try {
            var1.put(var3, Files.readAttributes(var3, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
         } catch (IOException var5) {
            // The file may be locked or gone mid-window; the next poll decides.
         }
      }

      return var1;
   }

   private static boolean sameAttributes(Map<Path, BasicFileAttributes> var0, Map<Path, BasicFileAttributes> var1) {
      if (var0.size() != var1.size()) {
         return false;
      } else {
         for(Map.Entry var3 : var0.entrySet()) {
            BasicFileAttributes var4 = (BasicFileAttributes)var1.get(var3.getKey());
            if (var4 == null || ((BasicFileAttributes)var3.getValue()).size() != var4.size() || !sameTime(((BasicFileAttributes)var3.getValue()).lastModifiedTime(), var4.lastModifiedTime())) {
               return false;
            }
         }

         return true;
      }
   }

   private void waitBeforeRetry(BackupSourceFile var1) throws IOException {
      if (var1.databaseCritical()) {
         sleepMillis((long)this.settings.databaseRetryDelayMillis(), "waiting before database retry");
      }

   }

   private static void sleepMillis(long var0, String var2) throws IOException {
      if (var0 > 0L) {
         try {
            Thread.sleep(var0);
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while " + var2, var4);
         }
      }
   }

   private Path targetPath(Path var1, BackupCategory var2, Path var3) {
      Path var4 = var1.resolve(this.settings.folderFor(var2));
      Path var5 = var4.resolve(var3).normalize();
      if (!var5.startsWith(var1)) {
         throw new IllegalStateException("Backup target escaped snapshot folder: " + String.valueOf(var5));
      } else {
         return var5;
      }
   }

   private String uniqueBackupId(Instant var1) throws IOException {
      String var10000 = this.fileNameFormatter.format(var1);
      String var2 = "server-backup-" + var10000;
      Path var3 = this.settings.backupRoot().resolve(var2);

      for(int var4 = 1; Files.exists(var3, new LinkOption[0]) || Files.exists(this.settings.archiveRoot().resolve(String.valueOf(var3.getFileName()) + ".zip"), new LinkOption[0]); ++var4) {
         var3 = this.settings.backupRoot().resolve(var2 + "-" + var4);
      }

      return var3.getFileName().toString();
   }

   private boolean shouldSkip(Path var1) {
      Path var2 = var1.toAbsolutePath().normalize();
      if (!var2.equals(this.settings.backupRoot()) && !var2.startsWith(this.settings.backupRoot())) {
         if (!var2.equals(this.settings.archiveRoot()) && !var2.startsWith(this.settings.archiveRoot())) {
            if (Files.isSymbolicLink(var1)) {
               return true;
            } else {
               Path var3 = this.settings.serverRoot().relativize(var2);
               return var3.getNameCount() > 0 && this.pathRules.isExcluded(var3);
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private void cleanupRetention() throws IOException {
      this.cleanupByPrefix(this.settings.archiveRoot(), "server-backup-", ".zip");
      this.cleanupByPrefix(this.settings.archiveRoot(), "server-backup-", ".integrity.json");
      if (this.settings.keepCategorizedFolder()) {
         this.cleanupByPrefix(this.settings.backupRoot(), "server-backup-", "");
      }

   }

   private void cleanupByPrefix(Path var1, String var2, String var3) throws IOException {
      if (Files.isDirectory(var1, new LinkOption[0])) {
         Stream<Path> var4 = Files.list(var1);

         try {
            List<Path> var5 = var4.filter((var2x) -> {
               String var3x = var2x.getFileName().toString();
               return var3x.startsWith(var2) && (var3.isEmpty() || var3x.endsWith(var3));
            }).sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed()).toList();

            for(int var6 = this.settings.retentionKeepLast(); var6 < var5.size(); ++var6) {
               this.deleteRecursively((Path)var5.get(var6));
            }
         } catch (Throwable var8) {
            if (var4 != null) {
               try {
                  var4.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (var4 != null) {
            var4.close();
         }

      }
   }

   private long lastModifiedMillis(Path var1) {
      try {
         return Files.getLastModifiedTime(var1).toMillis();
      } catch (IOException var3) {
         return 0L;
      }
   }

   private void deleteRecursively(Path var1) throws IOException {
      if (Files.exists(var1, new LinkOption[0])) {
         Files.walkFileTree(var1, new SimpleFileVisitor<Path>() {
            {
            }

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

   private static boolean sameTime(FileTime var0, FileTime var1) {
      return var0.toMillis() == var1.toMillis();
   }

   private static double ratio(long var0, long var2) {
      return var2 <= 0L ? (double)0.0F : (double)var0 / (double)var2;
   }

   private static String normalize(Path var0) {
      return var0.toString().replace('\\', '/');
   }

   private String sourceLabel(BackupSourceFile var1) {
      Path var2 = var1.source().toAbsolutePath().normalize();

      try {
         if (var2.startsWith(this.settings.serverRoot())) {
            return normalize(this.settings.serverRoot().relativize(var2));
         }
      } catch (IllegalArgumentException var4) {
      }

      return normalize(var2);
   }

   private static record BackupSourceFile(Path source, BackupCategory category, Path targetRelative, long size, FileTime lastModified, boolean materialCritical, boolean databaseCritical) {
      private String zipEntryName(BackupSettings var1) {
         return BackupService.normalize(Path.of(var1.folderFor(this.category)).resolve(this.targetRelative));
      }
   }

   private static record BackupSourceDirectory(BackupCategory category, Path targetRelative) {
   }

   private static record BackupInventory(List<BackupSourceFile> files, List<BackupSourceDirectory> directories, long totalBytes, long materialFiles, long materialBytes, long databaseFiles, long databaseBytes, List<String> warnings) {
   }

   private static record CopyOutcome(long bytes, String warning) {
   }

   private static record CopyStats(long copiedFiles, long copiedBytes, boolean copyVerified, Set<String> copiedZipEntries) {
   }

   private static record CopyAttemptResult(long bytes, boolean fallbackUsed) {
   }

   private static final class ZipCounter {
      private long files;
      private long bytes;

      private void add(long var1) {
         ++this.files;
         this.bytes += Math.max(0L, var1);
      }

      private long files() {
         return this.files;
      }

      private long bytes() {
         return this.bytes;
      }
   }

   private final class ProgressReporter {
      private final String backupId;
      private final Instant startedAt;
      private final BackupProgressSink sink;
      private long lastSinkNanos;
      private long lastLogNanos;

      private ProgressReporter(String var2, Instant var3, BackupProgressSink var4) {
         this.backupId = var2;
         this.startedAt = var3;
         this.sink = var4 == null ? BackupProgressSink.NOOP : var4;
         this.lastSinkNanos = 0L;
         this.lastLogNanos = 0L;
      }

      /**
       * Publishes progress, subject to the sink and log throttles.
       *
       * The throttles are evaluated *before* the snapshot is built. This runs
       * once per file in both the scan and the copy loop, so materialising a
       * BackupProgress (plus an Instant and a Duration) only to drop it on the
       * throttle cost tens of thousands of dead allocations per backup.
       */
      private void publish(BackupPhase var1, double var2, long var4, long var6, long var8, long var10, long var12, long var14, String var16, boolean var17) {
         long nowNanos = System.nanoTime();
         boolean toSink = var17 || nowNanos - this.lastSinkNanos >= PROGRESS_SINK_INTERVAL_NANOS;

         int logIntervalSeconds = BackupService.this.settings.progressLogIntervalSeconds();
         boolean toLog = logIntervalSeconds > 0
            && (var17 || nowNanos - this.lastLogNanos >= (long)logIntervalSeconds * 1000000000L);

         if (!toSink && !toLog) {
            return;
         }

         BackupProgress progress = BackupProgress.of(this.backupId, var1, var2, var4, var6, var8, var10, var12, var14,
            var16, Duration.between(this.startedAt, Instant.now()));
         if (toSink) {
            this.sink.publish(progress);
            this.lastSinkNanos = nowNanos;
         }
         if (toLog) {
            BackupService.this.logger.info(progress.consoleLine());
            this.lastLogNanos = nowNanos;
         }
      }
   }
}
