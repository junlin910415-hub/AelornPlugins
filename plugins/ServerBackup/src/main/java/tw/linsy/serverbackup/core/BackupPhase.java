package tw.linsy.serverbackup.core;

public enum BackupPhase {
   SCAN("Scanning"),
   DATABASE("Database"),
   COPY("Copying"),
   ZIP("Compressing"),
   VERIFY("Verifying"),
   CLEANUP("Cleaning"),
   COMPLETE("Complete"),
   FAILED("Failed");

   private final String displayName;

   private BackupPhase(String var3) {
      this.displayName = var3;
   }

   public String displayName() {
      return this.displayName;
   }
}
