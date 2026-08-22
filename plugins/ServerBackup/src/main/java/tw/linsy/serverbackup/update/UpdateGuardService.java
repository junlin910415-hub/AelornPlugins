package tw.linsy.serverbackup.update;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import tw.linsy.serverbackup.core.BackupLogger;
import tw.linsy.serverbackup.core.BackupResult;

public final class UpdateGuardService {
   private static final DateTimeFormatter SNAPSHOT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
   private static final int COPY_BUFFER_SIZE = 1048576;
   private final Path serverRoot;
   private final Path pluginsRoot;
   private final Path guardRoot;
   private final Set<String> trackedPluginNames;
   private final List<String> legacyJarPatterns;
   private final List<String> protectedServerFiles;
   private final int keepLast;
   private final BackupLogger logger;

   public UpdateGuardService(Path var1, Path var2, List<String> var3, List<String> var4, List<String> var5, int var6, BackupLogger var7) {
      this.serverRoot = normalize(var1);
      this.pluginsRoot = this.serverRoot.resolve("plugins").normalize();
      this.guardRoot = normalize(var2).resolve("update-guard").normalize();
      this.trackedPluginNames = normalizeNames(var3);
      this.legacyJarPatterns = cleanValues(var4);
      this.protectedServerFiles = cleanValues(var5);
      this.keepLast = Math.max(1, var6);
      this.logger = var7;
      requireInside(this.serverRoot, this.pluginsRoot, "plugins");
      requireInside(normalize(var2), this.guardRoot, "update guard");
   }

   public synchronized UpdateAuditReport audit() throws IOException {
      var var1 = this.createInventory();
      UpdateAuditReport var2 = this.auditInventory(var1);
      Files.createDirectories(this.guardRoot);
      writeAuditReport(this.guardRoot.resolve("audit-latest.properties"), var2);
      return var2;
   }

   public synchronized UpdateSnapshot capturePreUpdate(String var1) throws IOException {
      String var2 = sanitizeLabel(var1);
      String var10000 = SNAPSHOT_TIME.format(Instant.now());
      String var3 = "pre-update-" + var10000 + "-" + var2;
      Files.createDirectories(this.guardRoot);
      Path var4 = this.guardRoot.resolve(var3).normalize();
      Path var5 = this.guardRoot.resolve("." + var3 + ".tmp").normalize();
      requireInside(this.guardRoot, var4, "snapshot");
      requireInside(this.guardRoot, var5, "temporary snapshot");
      if (!Files.exists(var4, new LinkOption[0]) && !Files.exists(var5, new LinkOption[0])) {
         var var6 = this.createInventory();
         UpdateAuditReport var7 = this.auditInventory(var6);
         if (!var7.healthy()) {
            throw new IOException("Update guard audit failed: " + String.join("; ", var7.errors()));
         } else {
            long var8 = 0L;

            try {
               Path var10 = var5.resolve("plugins");
               Files.createDirectories(var10);

               for(PluginArtifact var12 : var6) {
                  Path var13 = var10.resolve(var12.source().getFileName().toString()).normalize();
                  requireInside(var10, var13, "plugin snapshot file");
                  copyAndVerify(var12.source(), var13, var12.sha256());
                  var8 += var12.size();
               }

               Path var16 = var5.resolve("server");
               Files.createDirectories(var16);
               var8 += this.copyProtectedServerFiles(var16);
               Properties var17 = this.inventoryProperties(var3, var2, "PENDING_BACKUP", var6, var8);
               Path var18 = var5.resolve("inventory.properties");
               writePropertiesAtomic(var18, var17, "ServerBackup update snapshot");
               writeAuditReport(var5.resolve("audit.properties"), var7);
               writeRestoreGuide(var5.resolve("RESTORE-GUIDE.txt"), var3);
               moveAtomically(var5, var4);
               this.cleanupOldSnapshots();
               this.logger.info("Update snapshot captured: " + String.valueOf(var4));
               return new UpdateSnapshot(var3, var2, var4, var4.resolve("inventory.properties"), var6.size(), var8);
            } catch (RuntimeException | IOException var14) {
               deleteTree(var5);
               throw var14;
            }
         }
      } else {
         throw new IOException("Update snapshot already exists: " + var3);
      }
   }

   public synchronized void markBackupComplete(UpdateSnapshot var1, BackupResult var2) throws IOException {
      if (!var2.copyVerified()) {
         throw new IOException("Full backup copy verification did not pass.");
      } else if (!var2.zipVerified()) {
         throw new IOException("Full backup ZIP verification did not pass.");
      } else if (!var2.warnings().isEmpty()) {
         throw new IOException("Full backup contains " + var2.warnings().size() + " integrity warning(s).");
      } else {
         Properties var3 = loadProperties(var1.inventoryManifest());
         var3.setProperty("snapshot.status", "COMPLETE");
         var3.setProperty("backup.id", var2.backupId());
         var3.setProperty("backup.archive", var2.archiveFile().toString());
         var3.setProperty("backup.integrity-report", var2.integrityReportFile().toString());
         var3.setProperty("backup.copy-verified", Boolean.toString(var2.copyVerified()));
         var3.setProperty("backup.zip-verified", Boolean.toString(var2.zipVerified()));
         var3.setProperty("backup.warnings", Integer.toString(var2.warnings().size()));
         writePropertiesAtomic(var1.inventoryManifest(), var3, "ServerBackup completed update snapshot");
      }
   }

   public synchronized void markBackupFailed(UpdateSnapshot var1, String var2) {
      try {
         Properties var3 = loadProperties(var1.inventoryManifest());
         var3.setProperty("snapshot.status", "BACKUP_FAILED");
         var3.setProperty("backup.failure", var2 == null ? "unknown" : var2);
         writePropertiesAtomic(var1.inventoryManifest(), var3, "ServerBackup failed update snapshot");
      } catch (IOException var4) {
         this.logger.error("Could not mark update snapshot as failed: " + var1.snapshotId(), var4);
      }

   }

   public Path guardRoot() {
      return this.guardRoot;
   }

   private UpdateAuditReport auditInventory(List<PluginArtifact> var1) throws IOException {
      ArrayList<String> var2 = new ArrayList<>();
      ArrayList<String> var3 = new ArrayList<>();
      ArrayList<String> var4 = new ArrayList<>();
      LinkedHashMap<String, List<PluginArtifact>> var5 = new LinkedHashMap<>();

      for(PluginArtifact var7 : var1) {
         var5.computeIfAbsent(var7.identityKey(), (var0) -> new ArrayList<>()).add(var7);
         if (this.matchesLegacyPattern(var7.source().getFileName())) {
            var2.add("發現舊版或已整合殘留 JAR：" + var7.relativePath());
         }
      }

      for(Map.Entry<String, List<PluginArtifact>> var12 : var5.entrySet()) {
         if (var12.getValue().size() > 1) {
            String var10001 = var12.getValue().get(0).pluginName();
            var2.add("插件 ID 重複 " + var10001 + "：" + String.valueOf(var12.getValue().stream().map(PluginArtifact::relativePath).toList()));
         }
      }

      for(String var13 : this.trackedPluginNames) {
         if (!var5.containsKey(var13)) {
            var2.add("必要 RPG 插件不存在：" + var13);
         }
      }

      var var11 = this.listServerCoreJars();
      if (var11.size() != 1) {
         var2.add("伺服器根目錄必須恰好保留一個 Folia 核心，目前為 " + var11.size() + " 個。");
      }

      for(Path var8 : var11) {
         if (var8.getFileName().toString().contains("26.1")) {
            var2.add("伺服器根目錄仍有 26.1 核心：" + String.valueOf(var8.getFileName()));
         }
      }

      Path var15 = this.findLatestCompletedManifest();
      if (var15 == null) {
         var3.add("尚未建立完成的更新前基準；請先執行 /serverbackup snapshot <標籤>。");
      } else {
         this.compareWithBaseline(var1, var15, var4, var3);
      }

      return new UpdateAuditReport(var1, var2, var3, var4, var15);
   }

   private List<PluginArtifact> createInventory() throws IOException {
      if (!Files.isDirectory(this.pluginsRoot, new LinkOption[0])) {
         throw new IOException("Plugins directory does not exist: " + String.valueOf(this.pluginsRoot));
      } else {
         var var2 = Files.list(this.pluginsRoot);

         List<Path> var1;
         try {
            var1 = var2.filter((var0) -> Files.isRegularFile(var0, new LinkOption[0])).filter((var0) -> var0.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).sorted(Comparator.comparing((var0) -> var0.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
         } catch (Throwable var8) {
            if (var2 != null) {
               try {
                  var2.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (var2 != null) {
            var2.close();
         }

         ArrayList<PluginArtifact> var9 = new ArrayList<>(var1.size());

         for(Path var4 : var1) {
            BasicFileAttributes var5 = Files.readAttributes(var4, BasicFileAttributes.class);
            PluginDescription var6 = readPluginDescription(var4);
            var9.add(new PluginArtifact(var4, this.serverRoot.relativize(var4).toString().replace('\\', '/'), var6.name(), var6.version(), var5.size(), var5.lastModifiedTime().toMillis(), sha256(var4)));
         }

         return List.copyOf(var9);
      }
   }

   private static PluginDescription readPluginDescription(Path var0) throws IOException {
      JarFile var1 = new JarFile(var0.toFile(), true);

      PluginDescription var12;
      label53: {
         PluginDescription var5;
         try {
            ZipEntry var2 = var1.getEntry("plugin.yml");
            if (var2 == null) {
               String var11 = stripJarExtension(var0.getFileName().toString());
               var12 = new PluginDescription(var11, "unknown");
               break label53;
            }

            try {
               InputStream var3 = var1.getInputStream(var2);

               try {
                  PluginDescriptionFile var4 = new PluginDescriptionFile(var3);
                  var5 = new PluginDescription(var4.getName(), var4.getVersion());
               } catch (Throwable var8) {
                  if (var3 != null) {
                     try {
                        var3.close();
                     } catch (Throwable var7) {
                        var8.addSuppressed(var7);
                     }
                  }

                  throw var8;
               }

               if (var3 != null) {
                  var3.close();
               }
            } catch (InvalidDescriptionException var9) {
               throw new IOException("Invalid plugin.yml in " + String.valueOf(var0), var9);
            }
         } catch (Throwable var10) {
            try {
               var1.close();
            } catch (Throwable var6) {
               var10.addSuppressed(var6);
            }

            throw var10;
         }

         var1.close();
         return var5;
      }

      var1.close();
      return var12;
   }

   private long copyProtectedServerFiles(Path var1) throws IOException {
      long var2 = 0L;
      LinkedHashSet<Path> var4 = new LinkedHashSet<>(this.listServerCoreJars());

      for(String var6 : this.protectedServerFiles) {
         Path var7 = this.serverRoot.resolve(var6).normalize();
         requireInside(this.serverRoot, var7, "protected server file");
         if (Files.isRegularFile(var7, new LinkOption[0])) {
            var4.add(var7);
         }
      }

      for(Path var10 : var4) {
         Path var11 = this.serverRoot.relativize(var10);
         Path var8 = var1.resolve(var11).normalize();
         requireInside(var1, var8, "protected snapshot file");
         Files.createDirectories(var8.getParent());
         copyAndVerify(var10, var8, sha256(var10));
         var2 += Files.size(var10);
      }

      return var2;
   }

   private static void copyAndVerify(Path var0, Path var1, String var2) throws IOException {
      Files.createDirectories(var1.getParent());
      Path var3 = var1.resolveSibling(String.valueOf(var1.getFileName()) + ".part");

      try {
         BufferedInputStream var4 = new BufferedInputStream(Files.newInputStream(var0), 1048576);

         try {
            BufferedOutputStream var5 = new BufferedOutputStream(Files.newOutputStream(var3), 1048576);

            try {
               ((InputStream)var4).transferTo(var5);
            } catch (Throwable var16) {
               try {
                  ((OutputStream)var5).close();
               } catch (Throwable var15) {
                  var16.addSuppressed(var15);
               }

               throw var16;
            }

            ((OutputStream)var5).close();
         } catch (Throwable var17) {
            try {
               ((InputStream)var4).close();
            } catch (Throwable var14) {
               var17.addSuppressed(var14);
            }

            throw var17;
         }

         ((InputStream)var4).close();
         if (Files.size(var0) != Files.size(var3)) {
            throw new IOException("Snapshot copy size mismatch: " + String.valueOf(var0));
         }

         String var19 = sha256(var3);
         if (!var2.equalsIgnoreCase(var19)) {
            throw new IOException("Snapshot copy SHA-256 mismatch: " + String.valueOf(var0));
         }

         moveAtomically(var3, var1);
      } finally {
         Files.deleteIfExists(var3);
      }

   }

   private void compareWithBaseline(List<PluginArtifact> var1, Path var2, List<String> var3, List<String> var4) throws IOException {
      var var5 = readInventory(var2);
      var var6 = uniqueArtifactsByName(var1);
      var var7 = uniqueArtifactsByName(var5);
      LinkedHashSet<String> var8 = new LinkedHashSet<>();
      var8.addAll(var7.keySet());
      var8.addAll(var6.keySet());

      for(String var10 : var8) {
         PluginArtifact var11 = (PluginArtifact)var7.get(var10);
         PluginArtifact var12 = (PluginArtifact)var6.get(var10);
         if (var11 == null) {
            String var10001 = var12.pluginName();
            var3.add("新增插件：" + var10001 + " " + var12.version());
         } else if (var12 == null) {
            String var13 = var11.pluginName();
            var3.add("移除插件：" + var13 + " " + var11.version());
         } else if (!var11.sha256().equalsIgnoreCase(var12.sha256())) {
            String var14 = var11.pluginName();
            var3.add("更新插件：" + var14 + " " + var11.version() + " -> " + var12.version());
         } else if (!var11.relativePath().equals(var12.relativePath())) {
            String var15 = var11.relativePath();
            var4.add("插件檔名已變更但內容相同：" + var15 + " -> " + var12.relativePath());
         }
      }

   }

   private Path findLatestCompletedManifest() throws IOException {
      if (!Files.isDirectory(this.guardRoot, new LinkOption[0])) {
         return null;
      } else {
         var var1 = Files.list(this.guardRoot);

         Path var6;
         label50: {
            try {
               for(Path var4 : var1.filter((var0) -> Files.isDirectory(var0, new LinkOption[0])).map((var0) -> var0.resolve("inventory.properties")).filter((var0) -> Files.isRegularFile(var0, new LinkOption[0])).sorted(Comparator.comparingLong(UpdateGuardService::lastModified).reversed()).toList()) {
                  Properties var5 = loadProperties(var4);
                  if ("COMPLETE".equalsIgnoreCase(var5.getProperty("snapshot.status", ""))) {
                     var6 = var4;
                     break label50;
                  }
               }
            } catch (Throwable var8) {
               if (var1 != null) {
                  try {
                     var1.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }
               }

               throw var8;
            }

            if (var1 != null) {
               var1.close();
            }

            return null;
         }

         if (var1 != null) {
            var1.close();
         }

         return var6;
      }
   }

   private static List<PluginArtifact> readInventory(Path var0) throws IOException {
      Properties var1 = loadProperties(var0);
      int var2 = parseInt(var1.getProperty("artifact.count"), 0);
      ArrayList var3 = new ArrayList(var2);
      Path var4 = var0.getParent();

      for(int var5 = 0; var5 < var2; ++var5) {
         String var6 = "artifact." + var5 + ".";
         String var7 = var1.getProperty(var6 + "path", "");
         Path var8 = var4.resolve("plugins").resolve(Path.of(var7).getFileName().toString()).normalize();
         var3.add(new PluginArtifact(var8, var7, var1.getProperty(var6 + "name", "unknown"), var1.getProperty(var6 + "version", "unknown"), parseLong(var1.getProperty(var6 + "size"), 0L), parseLong(var1.getProperty(var6 + "modified"), 0L), var1.getProperty(var6 + "sha256", "")));
      }

      return List.copyOf(var3);
   }

   private Properties inventoryProperties(String var1, String var2, String var3, List<PluginArtifact> var4, long var5) {
      Properties var7 = new Properties();
      var7.setProperty("format.version", "1");
      var7.setProperty("snapshot.id", var1);
      var7.setProperty("snapshot.label", var2);
      var7.setProperty("snapshot.status", var3);
      var7.setProperty("snapshot.created-at", Instant.now().toString());
      var7.setProperty("snapshot.server-root", this.serverRoot.toString());
      var7.setProperty("snapshot.copied-bytes", Long.toString(var5));
      var7.setProperty("artifact.count", Integer.toString(var4.size()));

      for(int var8 = 0; var8 < var4.size(); ++var8) {
         PluginArtifact var9 = (PluginArtifact)var4.get(var8);
         String var10 = "artifact." + var8 + ".";
         var7.setProperty(var10 + "path", var9.relativePath());
         var7.setProperty(var10 + "name", var9.pluginName());
         var7.setProperty(var10 + "version", var9.version());
         var7.setProperty(var10 + "size", Long.toString(var9.size()));
         var7.setProperty(var10 + "modified", Long.toString(var9.lastModifiedMillis()));
         var7.setProperty(var10 + "sha256", var9.sha256());
      }

      return var7;
   }

   private static void writeAuditReport(Path var0, UpdateAuditReport var1) throws IOException {
      Properties var2 = new Properties();
      var2.setProperty("format.version", "1");
      var2.setProperty("audit.created-at", Instant.now().toString());
      var2.setProperty("audit.healthy", Boolean.toString(var1.healthy()));
      var2.setProperty("audit.summary", var1.summary());
      var2.setProperty("audit.baseline", var1.baselineManifest() == null ? "" : var1.baselineManifest().toString());
      putList(var2, "errors", var1.errors());
      putList(var2, "warnings", var1.warnings());
      putList(var2, "changes", var1.changes());
      writePropertiesAtomic(var0, var2, "ServerBackup update audit");
   }

   private static void writeRestoreGuide(Path var0, String var1) throws IOException {
      var var2 = List.of("ServerBackup 更新回復指南", "", "快照：" + var1, "1. 先正常關閉伺服器，確認 Folia Java 程序已結束。", "2. 再備份目前 plugins 與核心，保留事故現場。", "3. 依 inventory.properties 核對插件 ID、版本與 SHA-256。", "4. 從 plugins 資料夾還原 JAR；server 資料夾保存核心與啟動設定。", "5. 啟動後執行 /serverbackup audit、/mythiccore selftest、/mmoitems validate。", "6. 確認 latest.log 無 ERROR、重複插件 ID、舊核心與非同步指令錯誤。");
      Files.write(var0, var2, StandardCharsets.UTF_8);
   }

   private void cleanupOldSnapshots() throws IOException {
      var var1 = Files.list(this.guardRoot);

      try {
         var var2 = var1.filter((var0) -> Files.isDirectory(var0, new LinkOption[0])).filter((var0) -> var0.getFileName().toString().startsWith("pre-update-")).sorted(Comparator.comparingLong(UpdateGuardService::lastModified).reversed()).toList();

         for(int var3 = this.keepLast; var3 < var2.size(); ++var3) {
            deleteTree((Path)var2.get(var3));
         }
      } catch (Throwable var5) {
         if (var1 != null) {
            try {
               var1.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }
         }

         throw var5;
      }

      if (var1 != null) {
         var1.close();
      }

   }

   private List<Path> listServerCoreJars() throws IOException {
      DirectoryStream<Path> var1 = Files.newDirectoryStream(this.serverRoot, "folia-*.jar");

      List<Path> var7;
      try {
         ArrayList<Path> var2 = new ArrayList<>();

         for(Path var4 : var1) {
            if (Files.isRegularFile(var4, new LinkOption[0])) {
               var2.add(var4.toAbsolutePath().normalize());
            }
         }

         var2.sort(Comparator.comparing((var0) -> var0.getFileName().toString()));
         var7 = List.copyOf(var2);
      } catch (Throwable var6) {
         if (var1 != null) {
            try {
               var1.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }
         }

         throw var6;
      }

      if (var1 != null) {
         var1.close();
      }

      return var7;
   }

   private boolean matchesLegacyPattern(Path var1) {
      for(String var3 : this.legacyJarPatterns) {
         try {
            if (var1.getFileSystem().getPathMatcher("glob:" + var3).matches(var1)) {
               return true;
            }
         } catch (IllegalArgumentException var5) {
            this.logger.warn("Ignored invalid update-guard legacy pattern: " + var3);
         }
      }

      return false;
   }

   private static Map<String, PluginArtifact> uniqueArtifactsByName(List<PluginArtifact> var0) {
      LinkedHashMap var1 = new LinkedHashMap();

      for(PluginArtifact var3 : var0) {
         var1.putIfAbsent(var3.identityKey(), var3);
      }

      return var1;
   }

   private static void putList(Properties var0, String var1, List<String> var2) {
      var0.setProperty(var1 + ".count", Integer.toString(var2.size()));

      for(int var3 = 0; var3 < var2.size(); ++var3) {
         var0.setProperty(var1 + "." + var3, (String)var2.get(var3));
      }

   }

   private static Properties loadProperties(Path var0) throws IOException {
      Properties var1 = new Properties();
      BufferedReader var2 = Files.newBufferedReader(var0, StandardCharsets.UTF_8);

      try {
         var1.load(var2);
      } catch (Throwable var6) {
         if (var2 != null) {
            try {
               var2.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }
         }

         throw var6;
      }

      if (var2 != null) {
         var2.close();
      }

      return var1;
   }

   private static void writePropertiesAtomic(Path var0, Properties var1, String var2) throws IOException {
      Files.createDirectories(var0.getParent());
      Path var3 = var0.resolveSibling(String.valueOf(var0.getFileName()) + ".tmp");

      try {
         BufferedWriter var4 = Files.newBufferedWriter(var3, StandardCharsets.UTF_8);

         try {
            var1.store(var4, var2);
         } catch (Throwable var12) {
            if (var4 != null) {
               try {
                  var4.close();
               } catch (Throwable var11) {
                  var12.addSuppressed(var11);
               }
            }

            throw var12;
         }

         if (var4 != null) {
            var4.close();
         }

         moveAtomically(var3, var0);
      } finally {
         Files.deleteIfExists(var3);
      }

   }

   private static void moveAtomically(Path var0, Path var1) throws IOException {
      try {
         Files.move(var0, var1, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException var3) {
         Files.move(var0, var1, StandardCopyOption.REPLACE_EXISTING);
      }

   }

   private static String sha256(Path var0) throws IOException {
      MessageDigest var1;
      try {
         var1 = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException var7) {
         throw new IllegalStateException("SHA-256 is unavailable", var7);
      }

      byte[] var2 = new byte[1048576];
      BufferedInputStream var3 = new BufferedInputStream(Files.newInputStream(var0), 1048576);

      int var4;
      try {
         while((var4 = ((InputStream)var3).read(var2)) >= 0) {
            if (var4 > 0) {
               var1.update(var2, 0, var4);
            }
         }
      } catch (Throwable var8) {
         try {
            ((InputStream)var3).close();
         } catch (Throwable var6) {
            var8.addSuppressed(var6);
         }

         throw var8;
      }

      ((InputStream)var3).close();
      return HexFormat.of().formatHex(var1.digest());
   }

   private static void deleteTree(Path var0) throws IOException {
      if (var0 != null && Files.exists(var0, new LinkOption[0])) {
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

   private static Set<String> normalizeNames(List<String> var0) {
      LinkedHashSet var1 = new LinkedHashSet();

      for(String var3 : cleanValues(var0)) {
         var1.add(var3.toLowerCase(Locale.ROOT));
      }

      return Set.copyOf(var1);
   }

   private static List<String> cleanValues(List<String> var0) {
      return var0 != null && !var0.isEmpty() ? var0.stream().filter(Objects::nonNull).map(String::trim).filter(Predicate.not(String::isBlank)).distinct().toList() : List.of();
   }

   private static String sanitizeLabel(String var0) {
      String var1 = var0 == null ? "" : var0.trim();
      var1 = var1.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "-");
      var1 = var1.replaceAll("\\s+", "-");
      var1 = var1.replaceAll("-{2,}", "-");
      var1 = var1.replaceAll("^[.\\-]+|[.\\-]+$", "");
      if (var1.isBlank()) {
         var1 = "update";
      }

      return var1.length() <= 48 ? var1 : var1.substring(0, 48);
   }

   private static Path normalize(Path var0) {
      return var0.toAbsolutePath().normalize();
   }

   private static void requireInside(Path var0, Path var1, String var2) {
      Path var3 = normalize(var0);
      Path var4 = normalize(var1);
      if (!var4.startsWith(var3)) {
         throw new IllegalArgumentException(var2 + " path escaped its root: " + String.valueOf(var1));
      }
   }

   private static long lastModified(Path var0) {
      try {
         return Files.getLastModifiedTime(var0).toMillis();
      } catch (IOException var2) {
         return Long.MIN_VALUE;
      }
   }

   private static int parseInt(String var0, int var1) {
      try {
         return Integer.parseInt(var0);
      } catch (NumberFormatException var3) {
         return var1;
      }
   }

   private static long parseLong(String var0, long var1) {
      try {
         return Long.parseLong(var0);
      } catch (NumberFormatException var4) {
         return var1;
      }
   }

   private static String stripJarExtension(String var0) {
      return var0.toLowerCase(Locale.ROOT).endsWith(".jar") ? var0.substring(0, var0.length() - 4) : var0;
   }

   private static record PluginDescription(String name, String version) {
   }
}
