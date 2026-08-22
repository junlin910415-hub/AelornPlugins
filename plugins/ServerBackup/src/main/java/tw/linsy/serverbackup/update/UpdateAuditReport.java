package tw.linsy.serverbackup.update;

import java.nio.file.Path;
import java.util.List;

public record UpdateAuditReport(List<PluginArtifact> artifacts, List<String> errors, List<String> warnings, List<String> changes, Path baselineManifest) {
   public UpdateAuditReport(List<PluginArtifact> artifacts, List<String> errors, List<String> warnings, List<String> changes, Path baselineManifest) {
      artifacts = List.copyOf(artifacts);
      errors = List.copyOf(errors);
      warnings = List.copyOf(warnings);
      changes = List.copyOf(changes);
      this.artifacts = artifacts;
      this.errors = errors;
      this.warnings = warnings;
      this.changes = changes;
      this.baselineManifest = baselineManifest;
   }

   public boolean healthy() {
      return this.errors.isEmpty();
   }

   public String summary() {
      int var10000 = this.artifacts.size();
      return "插件 " + var10000 + "、錯誤 " + this.errors.size() + "、警告 " + this.warnings.size() + "、版本差異 " + this.changes.size();
   }
}
