package tw.linsy.serverbackup.core;

public enum BackupCategory {
   WORLDS("worlds", "Worlds"),
   PLUGINS("plugins", "Plugins"),
   CONFIGS("configs", "Configs"),
   LOGS("logs", "Logs"),
   LIBRARIES("libraries", "Libraries"),
   VERSIONS("versions", "Versions"),
   CACHE("cache", "Cache"),
   SERVER_FILES("server-files", "ServerFiles"),
   OTHER("other", "Other");

   private final String configKey;
   private final String defaultFolder;

   private BackupCategory(String var3, String var4) {
      this.configKey = var3;
      this.defaultFolder = var4;
   }

   public String configKey() {
      return this.configKey;
   }

   public String defaultFolder() {
      return this.defaultFolder;
   }
}
