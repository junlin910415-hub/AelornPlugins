package tw.linsy.serverbackup.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BackupManifest {
   private final String backupId;
   private final Path serverRoot;
   private final Path snapshotFolder;
   private final Path archiveFile;
   private final Instant startedAt;
   private final PerformancePlan performancePlan;
   private final Map<BackupCategory, CategoryStats> categoryStats = new EnumMap(BackupCategory.class);
   private final List<String> warnings = new ArrayList();
   private Instant finishedAt;
   private long fileCount;
   private long directoryCount;
   private long totalBytes;
   private long materialFileCount;
   private long materialBytes;
   private long databaseFileCount;
   private long databaseBytes;
   private boolean copyVerified;
   private boolean zipVerified;
   private Path integrityReportFile;

   BackupManifest(String var1, Path var2, Path var3, Path var4, Instant var5, PerformancePlan var6) {
      this.backupId = var1;
      this.serverRoot = var2;
      this.snapshotFolder = var3;
      this.archiveFile = var4;
      this.startedAt = var5;
      this.performancePlan = var6;

      for(BackupCategory var10 : BackupCategory.values()) {
         this.categoryStats.put(var10, new CategoryStats());
      }

   }

   void addFile(BackupCategory var1, long var2) {
      ++this.fileCount;
      this.totalBytes += var2;
      ((CategoryStats)this.categoryStats.get(var1)).addFile(var2);
   }

   void addMaterialFile(long var1) {
      ++this.materialFileCount;
      this.materialBytes += var1;
   }

   void addDatabaseFile(long var1) {
      ++this.databaseFileCount;
      this.databaseBytes += var1;
   }

   void addDirectory(BackupCategory var1) {
      ++this.directoryCount;
      ((CategoryStats)this.categoryStats.get(var1)).addDirectory();
   }

   void addWarning(String var1) {
      this.warnings.add(var1);
   }

   void finish(Instant var1) {
      this.finishedAt = var1;
   }

   void setIntegrity(boolean var1, boolean var2, Path var3) {
      this.copyVerified = var1;
      this.zipVerified = var2;
      this.integrityReportFile = var3;
   }

   BackupResult toResult() {
      return new BackupResult(this.backupId, this.snapshotFolder, this.archiveFile, this.fileCount, this.directoryCount, this.totalBytes, Duration.between(this.startedAt, this.finishedAt == null ? Instant.now() : this.finishedAt), List.copyOf(this.warnings), this.integrityReportFile, this.copyVerified, this.zipVerified, this.materialFileCount, this.databaseFileCount);
   }

   void write(Path var1) throws IOException {
      Files.createDirectories(var1.getParent());
      Files.writeString(var1, this.toJson(), StandardCharsets.UTF_8);
   }

   private String toJson() {
      StringBuilder var1 = new StringBuilder();
      var1.append("{\n");
      appendField(var1, "backupId", this.backupId, true);
      appendField(var1, "startedAt", this.startedAt.toString(), true);
      appendField(var1, "finishedAt", (this.finishedAt == null ? Instant.now() : this.finishedAt).toString(), true);
      appendField(var1, "durationSeconds", String.valueOf(this.toResult().duration().toSeconds()), false, true);
      appendField(var1, "serverRoot", this.serverRoot.toString(), true);
      appendField(var1, "snapshotFolder", this.snapshotFolder.toString(), true);
      appendField(var1, "archiveFile", this.archiveFile.toString(), true);
      appendField(var1, "fileCount", String.valueOf(this.fileCount), false, true);
      appendField(var1, "directoryCount", String.valueOf(this.directoryCount), false, true);
      appendField(var1, "totalBytes", String.valueOf(this.totalBytes), false, true);
      appendField(var1, "materialFileCount", String.valueOf(this.materialFileCount), false, true);
      appendField(var1, "materialBytes", String.valueOf(this.materialBytes), false, true);
      appendField(var1, "databaseFileCount", String.valueOf(this.databaseFileCount), false, true);
      appendField(var1, "databaseBytes", String.valueOf(this.databaseBytes), false, true);
      var1.append("  \"integrity\": {\n");
      var1.append("    \"copyVerified\": ").append(this.copyVerified).append(",\n");
      var1.append("    \"zipVerified\": ").append(this.zipVerified).append(",\n");
      var1.append("    \"archiveManifestNote\": \"Archive manifest is written before post-zip verification; final verification is in integrityReportFile.\",\n");
      var1.append("    \"integrityReportFile\": \"").append(escape(this.integrityReportFile == null ? "" : this.integrityReportFile.toString())).append("\"\n");
      var1.append("  },\n");
      this.appendPerformancePlan(var1);
      var1.append("  \"categories\": {\n");
      int var2 = 0;

      for(Map.Entry var4 : this.categoryStats.entrySet()) {
         CategoryStats var5 = (CategoryStats)var4.getValue();
         var1.append("    \"").append(((BackupCategory)var4.getKey()).configKey()).append("\": {");
         var1.append("\"files\": ").append(var5.files).append(", ");
         var1.append("\"directories\": ").append(var5.directories).append(", ");
         var1.append("\"bytes\": ").append(var5.bytes).append("}");
         var1.append(var2++ < this.categoryStats.size() - 1 ? ",\n" : "\n");
      }

      var1.append("  },\n");
      var1.append("  \"warnings\": [");

      for(int var6 = 0; var6 < this.warnings.size(); ++var6) {
         if (var6 > 0) {
            var1.append(", ");
         }

         var1.append("\"").append(escape((String)this.warnings.get(var6))).append("\"");
      }

      var1.append("]\n");
      var1.append("}\n");
      return var1.toString();
   }

   private static void appendField(StringBuilder var0, String var1, String var2, boolean var3) {
      appendField(var0, var1, var2, var3, false);
   }

   private static void appendField(StringBuilder var0, String var1, String var2, boolean var3, boolean var4) {
      var0.append("  \"").append(var1).append("\": ");
      if (var3 && !var4) {
         var0.append("\"").append(escape(var2)).append("\"");
      } else {
         var0.append(var2);
      }

      var0.append(",\n");
   }

   private static String escape(String var0) {
      return var0.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
   }

   private void appendPerformancePlan(StringBuilder var1) {
      if (this.performancePlan == null) {
         var1.append("  \"performance\": null,\n");
      } else {
         HostPerformanceSnapshot var2 = this.performancePlan.hostSnapshot();
         var1.append("  \"performance\": {\n");
         var1.append("    \"autoTuned\": ").append(this.performancePlan.autoTuned()).append(",\n");
         var1.append("    \"profile\": \"").append(escape(this.performancePlan.profile())).append("\",\n");
         var1.append("    \"maxBytesPerSecond\": ").append(this.performancePlan.maxBytesPerSecond()).append(",\n");
         var1.append("    \"bufferSizeBytes\": ").append(this.performancePlan.bufferSizeBytes()).append(",\n");
         var1.append("    \"pauseBetweenFilesMillis\": ").append(this.performancePlan.pauseBetweenFilesMillis()).append(",\n");
         var1.append("    \"zipCompressionLevel\": ").append(this.performancePlan.zipCompressionLevel()).append(",\n");
         var1.append("    \"host\": {\n");
         var1.append("      \"processors\": ").append(var2.processors()).append(",\n");
         var1.append("      \"systemLoadAverage\": ").append(var2.systemLoadAverage()).append(",\n");
         var1.append("      \"jvmMaxMemoryBytes\": ").append(var2.jvmMaxMemoryBytes()).append(",\n");
         var1.append("      \"physicalMemoryBytes\": ").append(var2.physicalMemoryBytes()).append(",\n");
         var1.append("      \"freePhysicalMemoryBytes\": ").append(var2.freePhysicalMemoryBytes()).append(",\n");
         var1.append("      \"backupDiskUsableBytes\": ").append(var2.backupDiskUsableBytes()).append(",\n");
         var1.append("      \"backupDiskTotalBytes\": ").append(var2.backupDiskTotalBytes()).append(",\n");
         var1.append("      \"backupDiskFreePercent\": ").append(String.format(Locale.ROOT, "%.2f", var2.backupDiskFreePercent())).append(",\n");
         var1.append("      \"diskWriteMegabytesPerSecond\": ").append(String.format(Locale.ROOT, "%.2f", var2.diskWriteMegabytesPerSecond())).append(",\n");
         var1.append("      \"diskProbeSuccessful\": ").append(var2.diskProbeSuccessful()).append("\n");
         var1.append("    },\n");
         var1.append("    \"decisions\": [");

         for(int var3 = 0; var3 < this.performancePlan.decisions().size(); ++var3) {
            if (var3 > 0) {
               var1.append(", ");
            }

            var1.append("\"").append(escape((String)this.performancePlan.decisions().get(var3))).append("\"");
         }

         var1.append("]\n");
         var1.append("  },\n");
      }
   }

   private static final class CategoryStats {
      private long files;
      private long directories;
      private long bytes;

      private void addFile(long var1) {
         ++this.files;
         this.bytes += var1;
      }

      private void addDirectory() {
         ++this.directories;
      }
   }
}
