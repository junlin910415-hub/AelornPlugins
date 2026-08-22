package tw.linsy.serverbackup.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record BackupIntegrityReport(boolean copyVerified, boolean zipVerified, long scannedFiles, long copiedFiles, long materialFiles, long databaseFiles, long copiedBytes, long archiveBytes, String archiveSha256, List<String> warnings, List<String> materialWarnings, List<String> databaseWarnings, Instant verifiedAt) {
   void write(Path var1) throws IOException {
      Files.createDirectories(var1.getParent());
      Files.writeString(var1, this.toJson(), StandardCharsets.UTF_8);
   }

   private String toJson() {
      StringBuilder var1 = new StringBuilder();
      var1.append("{\n");
      field(var1, "copyVerified", String.valueOf(this.copyVerified), false, true);
      field(var1, "zipVerified", String.valueOf(this.zipVerified), false, true);
      field(var1, "scannedFiles", String.valueOf(this.scannedFiles), false, true);
      field(var1, "copiedFiles", String.valueOf(this.copiedFiles), false, true);
      field(var1, "materialFiles", String.valueOf(this.materialFiles), false, true);
      field(var1, "databaseFiles", String.valueOf(this.databaseFiles), false, true);
      field(var1, "copiedBytes", String.valueOf(this.copiedBytes), false, true);
      field(var1, "archiveBytes", String.valueOf(this.archiveBytes), false, true);
      field(var1, "archiveSha256", this.archiveSha256 == null ? "" : this.archiveSha256, true, true);
      field(var1, "verifiedAt", this.verifiedAt.toString(), true, true);
      array(var1, "warnings", this.warnings, true);
      array(var1, "materialWarnings", this.materialWarnings, true);
      array(var1, "databaseWarnings", this.databaseWarnings, false);
      var1.append("}\n");
      return var1.toString();
   }

   private static void field(StringBuilder var0, String var1, String var2, boolean var3, boolean var4) {
      var0.append("  \"").append(var1).append("\": ");
      if (var3) {
         var0.append("\"").append(escape(var2)).append("\"");
      } else {
         var0.append(var2);
      }

      var0.append(var4 ? ",\n" : "\n");
   }

   private static void array(StringBuilder var0, String var1, List<String> var2, boolean var3) {
      var0.append("  \"").append(var1).append("\": [");

      for(int var4 = 0; var4 < var2.size(); ++var4) {
         if (var4 > 0) {
            var0.append(", ");
         }

         var0.append("\"").append(escape((String)var2.get(var4))).append("\"");
      }

      var0.append(var3 ? "],\n" : "]\n");
   }

   private static String escape(String var0) {
      return var0.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
   }
}
