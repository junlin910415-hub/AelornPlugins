package tw.linsy.serverbackup.update;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public record PluginArtifact(Path source, String relativePath, String pluginName, String version, long size, long lastModifiedMillis, String sha256) {
   public PluginArtifact(Path source, String relativePath, String pluginName, String version, long size, long lastModifiedMillis, String sha256) {
      source = ((Path)Objects.requireNonNull(source, "source")).toAbsolutePath().normalize();
      relativePath = (String)Objects.requireNonNull(relativePath, "relativePath");
      pluginName = (String)Objects.requireNonNull(pluginName, "pluginName");
      version = (String)Objects.requireNonNull(version, "version");
      sha256 = (String)Objects.requireNonNull(sha256, "sha256");
      this.source = source;
      this.relativePath = relativePath;
      this.pluginName = pluginName;
      this.version = version;
      this.size = size;
      this.lastModifiedMillis = lastModifiedMillis;
      this.sha256 = sha256;
   }

   public String identityKey() {
      return this.pluginName.toLowerCase(Locale.ROOT);
   }
}
