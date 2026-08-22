package tw.linsy.serverbackup.core;

public interface BackupLogger {
   void info(String var1);

   void warn(String var1);

   void error(String var1, Throwable var2);
}
