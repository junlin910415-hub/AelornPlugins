package tw.linsy.serverbackup.core;

@FunctionalInterface
public interface BackupProgressSink {
   BackupProgressSink NOOP = (var0) -> {
   };

   void publish(BackupProgress var1);
}
