package tw.linsy.serverbackup.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BackupSettings {
   private final Path serverRoot;
   private final Path backupRoot;
   private final String archiveFolderName;
   private final String datePattern;
   private final String worldMarkerFile;
   private final boolean keepCategorizedFolder;
   private final boolean includeEmptyDirectories;
   private final boolean retentionEnabled;
   private final int retentionKeepLast;
   private final long maxBytesPerSecond;
   private final int bufferSizeBytes;
   private final int pauseBetweenFilesMillis;
   private final int zipCompressionLevel;
   private final int progressLogIntervalSeconds;
   private final boolean integrityEnabled;
   private final boolean verifyCopiedFileSize;
   private final boolean verifyZipAfterCreate;
   private final int retryChangedFiles;
   private final boolean materialSafeModeEnabled;
   private final boolean failOnMaterialWarning;
   private final List<String> materialPathRules;
   private final List<String> materialExtensions;
   private final boolean databaseSafeModeEnabled;
   private final boolean failOnDatabaseWarning;
   private final boolean failOnDatabaseCopyFailure;
   private final int databaseRetryAttempts;
   private final int databaseRetryDelayMillis;
   private final int databaseStabilityCheckMillis;
   private final int databaseMaxStabilityWaitMillis;
   private final boolean databaseWindowsFallbackEnabled;
   private final boolean databaseWindowsFallbackBackupMode;
   private final int databaseWindowsFallbackRetries;
   private final int databaseWindowsFallbackWaitSeconds;
   private final int databaseWindowsFallbackTimeoutSeconds;
   private final boolean databaseWindowsEsentutlFallbackEnabled;
   private final int databaseWindowsEsentutlFallbackTimeoutSeconds;
   private final boolean databaseWindowsVssFallbackEnabled;
   private final int databaseWindowsVssFallbackTimeoutSeconds;
   private final List<String> databasePathRules;
   private final List<String> databaseExtensions;
   private final List<String> databaseFileNames;
   private final PerformancePlan performancePlan;
   private final List<String> excludeRules;
   private final Map<BackupCategory, String> categoryFolders;

   private BackupSettings(Builder var1) {
      this.serverRoot = var1.serverRoot.toAbsolutePath().normalize();
      this.backupRoot = var1.backupRoot.toAbsolutePath().normalize();
      this.archiveFolderName = requireText(var1.archiveFolderName, "archiveFolderName");
      this.datePattern = requireText(var1.datePattern, "datePattern");
      this.worldMarkerFile = requireText(var1.worldMarkerFile, "worldMarkerFile");
      this.keepCategorizedFolder = var1.keepCategorizedFolder;
      this.includeEmptyDirectories = var1.includeEmptyDirectories;
      this.retentionEnabled = var1.retentionEnabled;
      this.retentionKeepLast = Math.max(1, var1.retentionKeepLast);
      this.maxBytesPerSecond = Math.max(0L, var1.maxBytesPerSecond);
      this.bufferSizeBytes = Math.max(8192, var1.bufferSizeBytes);
      this.pauseBetweenFilesMillis = Math.max(0, var1.pauseBetweenFilesMillis);
      this.zipCompressionLevel = clamp(var1.zipCompressionLevel, 0, 9);
      this.progressLogIntervalSeconds = Math.max(0, var1.progressLogIntervalSeconds);
      this.integrityEnabled = var1.integrityEnabled;
      this.verifyCopiedFileSize = var1.verifyCopiedFileSize;
      this.verifyZipAfterCreate = var1.verifyZipAfterCreate;
      this.retryChangedFiles = Math.max(0, var1.retryChangedFiles);
      this.materialSafeModeEnabled = var1.materialSafeModeEnabled;
      this.failOnMaterialWarning = var1.failOnMaterialWarning;
      this.materialPathRules = List.copyOf(var1.materialPathRules);
      this.materialExtensions = List.copyOf(var1.materialExtensions);
      this.databaseSafeModeEnabled = var1.databaseSafeModeEnabled;
      this.failOnDatabaseWarning = var1.failOnDatabaseWarning;
      this.failOnDatabaseCopyFailure = var1.failOnDatabaseCopyFailure;
      this.databaseRetryAttempts = Math.max(1, var1.databaseRetryAttempts);
      this.databaseRetryDelayMillis = Math.max(0, var1.databaseRetryDelayMillis);
      this.databaseStabilityCheckMillis = Math.max(0, var1.databaseStabilityCheckMillis);
      this.databaseMaxStabilityWaitMillis = Math.max(this.databaseStabilityCheckMillis, var1.databaseMaxStabilityWaitMillis);
      this.databaseWindowsFallbackEnabled = var1.databaseWindowsFallbackEnabled;
      this.databaseWindowsFallbackBackupMode = var1.databaseWindowsFallbackBackupMode;
      this.databaseWindowsFallbackRetries = Math.max(0, var1.databaseWindowsFallbackRetries);
      this.databaseWindowsFallbackWaitSeconds = Math.max(0, var1.databaseWindowsFallbackWaitSeconds);
      this.databaseWindowsFallbackTimeoutSeconds = Math.max(5, var1.databaseWindowsFallbackTimeoutSeconds);
      this.databaseWindowsEsentutlFallbackEnabled = var1.databaseWindowsEsentutlFallbackEnabled;
      this.databaseWindowsEsentutlFallbackTimeoutSeconds = Math.max(5, var1.databaseWindowsEsentutlFallbackTimeoutSeconds);
      this.databaseWindowsVssFallbackEnabled = var1.databaseWindowsVssFallbackEnabled;
      this.databaseWindowsVssFallbackTimeoutSeconds = Math.max(15, var1.databaseWindowsVssFallbackTimeoutSeconds);
      this.databasePathRules = List.copyOf(var1.databasePathRules);
      this.databaseExtensions = List.copyOf(var1.databaseExtensions);
      this.databaseFileNames = List.copyOf(var1.databaseFileNames);
      this.performancePlan = var1.performancePlan;
      this.excludeRules = List.copyOf(var1.excludeRules);
      this.categoryFolders = Map.copyOf(var1.categoryFolders);
   }

   public static Builder builder(Path var0, Path var1) {
      return new Builder(var0, var1);
   }

   public Path serverRoot() {
      return this.serverRoot;
   }

   public Path backupRoot() {
      return this.backupRoot;
   }

   public Path archiveRoot() {
      return this.backupRoot.resolve(this.archiveFolderName).normalize();
   }

   public String archiveFolderName() {
      return this.archiveFolderName;
   }

   public String datePattern() {
      return this.datePattern;
   }

   public String worldMarkerFile() {
      return this.worldMarkerFile;
   }

   public boolean keepCategorizedFolder() {
      return this.keepCategorizedFolder;
   }

   public boolean includeEmptyDirectories() {
      return this.includeEmptyDirectories;
   }

   public boolean retentionEnabled() {
      return this.retentionEnabled;
   }

   public int retentionKeepLast() {
      return this.retentionKeepLast;
   }

   public long maxBytesPerSecond() {
      return this.maxBytesPerSecond;
   }

   public int bufferSizeBytes() {
      return this.bufferSizeBytes;
   }

   public int pauseBetweenFilesMillis() {
      return this.pauseBetweenFilesMillis;
   }

   public int zipCompressionLevel() {
      return this.zipCompressionLevel;
   }

   public int progressLogIntervalSeconds() {
      return this.progressLogIntervalSeconds;
   }

   public boolean integrityEnabled() {
      return this.integrityEnabled;
   }

   public boolean verifyCopiedFileSize() {
      return this.verifyCopiedFileSize;
   }

   public boolean verifyZipAfterCreate() {
      return this.verifyZipAfterCreate;
   }

   public int retryChangedFiles() {
      return this.retryChangedFiles;
   }

   public boolean materialSafeModeEnabled() {
      return this.materialSafeModeEnabled;
   }

   public boolean failOnMaterialWarning() {
      return this.failOnMaterialWarning;
   }

   public List<String> materialPathRules() {
      return this.materialPathRules;
   }

   public List<String> materialExtensions() {
      return this.materialExtensions;
   }

   public boolean databaseSafeModeEnabled() {
      return this.databaseSafeModeEnabled;
   }

   public boolean failOnDatabaseWarning() {
      return this.failOnDatabaseWarning;
   }

   public boolean failOnDatabaseCopyFailure() {
      return this.failOnDatabaseCopyFailure;
   }

   public int databaseRetryAttempts() {
      return this.databaseRetryAttempts;
   }

   public int databaseRetryDelayMillis() {
      return this.databaseRetryDelayMillis;
   }

   public int databaseStabilityCheckMillis() {
      return this.databaseStabilityCheckMillis;
   }

   public int databaseMaxStabilityWaitMillis() {
      return this.databaseMaxStabilityWaitMillis;
   }

   public boolean databaseWindowsFallbackEnabled() {
      return this.databaseWindowsFallbackEnabled;
   }

   public boolean databaseWindowsFallbackBackupMode() {
      return this.databaseWindowsFallbackBackupMode;
   }

   public int databaseWindowsFallbackRetries() {
      return this.databaseWindowsFallbackRetries;
   }

   public int databaseWindowsFallbackWaitSeconds() {
      return this.databaseWindowsFallbackWaitSeconds;
   }

   public int databaseWindowsFallbackTimeoutSeconds() {
      return this.databaseWindowsFallbackTimeoutSeconds;
   }

   public boolean databaseWindowsEsentutlFallbackEnabled() {
      return this.databaseWindowsEsentutlFallbackEnabled;
   }

   public int databaseWindowsEsentutlFallbackTimeoutSeconds() {
      return this.databaseWindowsEsentutlFallbackTimeoutSeconds;
   }

   public boolean databaseWindowsVssFallbackEnabled() {
      return this.databaseWindowsVssFallbackEnabled;
   }

   public int databaseWindowsVssFallbackTimeoutSeconds() {
      return this.databaseWindowsVssFallbackTimeoutSeconds;
   }

   public List<String> databasePathRules() {
      return this.databasePathRules;
   }

   public List<String> databaseExtensions() {
      return this.databaseExtensions;
   }

   public List<String> databaseFileNames() {
      return this.databaseFileNames;
   }

   public PerformancePlan performancePlan() {
      return this.performancePlan;
   }

   public List<String> excludeRules() {
      return this.excludeRules;
   }

   public String folderFor(BackupCategory var1) {
      return (String)this.categoryFolders.getOrDefault(var1, var1.defaultFolder());
   }

   public Map<BackupCategory, String> categoryFolders() {
      return this.categoryFolders;
   }

   public BackupSettings withPerformancePlan(PerformancePlan var1) {
      return builder(this.serverRoot, this.backupRoot).archiveFolderName(this.archiveFolderName).datePattern(this.datePattern).worldMarkerFile(this.worldMarkerFile).keepCategorizedFolder(this.keepCategorizedFolder).includeEmptyDirectories(this.includeEmptyDirectories).retentionEnabled(this.retentionEnabled).retentionKeepLast(this.retentionKeepLast).excludeRules(this.excludeRules).categoryFolders(this.categoryFolders).integrityEnabled(this.integrityEnabled).verifyCopiedFileSize(this.verifyCopiedFileSize).verifyZipAfterCreate(this.verifyZipAfterCreate).retryChangedFiles(this.retryChangedFiles).materialSafeModeEnabled(this.materialSafeModeEnabled).failOnMaterialWarning(this.failOnMaterialWarning).materialPathRules(this.materialPathRules).materialExtensions(this.materialExtensions).databaseSafeModeEnabled(this.databaseSafeModeEnabled).failOnDatabaseWarning(this.failOnDatabaseWarning).failOnDatabaseCopyFailure(this.failOnDatabaseCopyFailure).databaseRetryAttempts(this.databaseRetryAttempts).databaseRetryDelayMillis(this.databaseRetryDelayMillis).databaseStabilityCheckMillis(this.databaseStabilityCheckMillis).databaseMaxStabilityWaitMillis(this.databaseMaxStabilityWaitMillis).databaseWindowsFallbackEnabled(this.databaseWindowsFallbackEnabled).databaseWindowsFallbackBackupMode(this.databaseWindowsFallbackBackupMode).databaseWindowsFallbackRetries(this.databaseWindowsFallbackRetries).databaseWindowsFallbackWaitSeconds(this.databaseWindowsFallbackWaitSeconds).databaseWindowsFallbackTimeoutSeconds(this.databaseWindowsFallbackTimeoutSeconds).databaseWindowsEsentutlFallbackEnabled(this.databaseWindowsEsentutlFallbackEnabled).databaseWindowsEsentutlFallbackTimeoutSeconds(this.databaseWindowsEsentutlFallbackTimeoutSeconds).databaseWindowsVssFallbackEnabled(this.databaseWindowsVssFallbackEnabled).databaseWindowsVssFallbackTimeoutSeconds(this.databaseWindowsVssFallbackTimeoutSeconds).databasePathRules(this.databasePathRules).databaseExtensions(this.databaseExtensions).databaseFileNames(this.databaseFileNames).performancePlan(var1).build();
   }

   private static String requireText(String var0, String var1) {
      if (var0 != null && !var0.isBlank()) {
         return var0.trim();
      } else {
         throw new IllegalArgumentException(var1 + " must not be blank");
      }
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var2, var0));
   }

   public static final class Builder {
      private final Path serverRoot;
      private final Path backupRoot;
      private String archiveFolderName = "archives";
      private String datePattern = "yyyy-MM-dd_HH-mm-ss";
      private String worldMarkerFile = "level.dat";
      private boolean keepCategorizedFolder = true;
      private boolean includeEmptyDirectories = true;
      private boolean retentionEnabled = true;
      private int retentionKeepLast = 10;
      private long maxBytesPerSecond = 16777216L;
      private int bufferSizeBytes = 262144;
      private int pauseBetweenFilesMillis = 1;
      private int zipCompressionLevel = 1;
      private int progressLogIntervalSeconds = 60;
      private boolean integrityEnabled = true;
      private boolean verifyCopiedFileSize = true;
      private boolean verifyZipAfterCreate = true;
      private int retryChangedFiles = 1;
      private boolean materialSafeModeEnabled = true;
      private boolean failOnMaterialWarning = true;
      private List<String> materialPathRules = new ArrayList();
      private List<String> materialExtensions = new ArrayList();
      private boolean databaseSafeModeEnabled = true;
      private boolean failOnDatabaseWarning = true;
      private boolean failOnDatabaseCopyFailure = true;
      private int databaseRetryAttempts = 5;
      private int databaseRetryDelayMillis = 500;
      private int databaseStabilityCheckMillis = 300;
      private int databaseMaxStabilityWaitMillis = 3000;
      private boolean databaseWindowsFallbackEnabled = true;
      private boolean databaseWindowsFallbackBackupMode = true;
      private int databaseWindowsFallbackRetries = 1;
      private int databaseWindowsFallbackWaitSeconds = 1;
      private int databaseWindowsFallbackTimeoutSeconds = 30;
      private boolean databaseWindowsEsentutlFallbackEnabled = true;
      private int databaseWindowsEsentutlFallbackTimeoutSeconds = 30;
      private boolean databaseWindowsVssFallbackEnabled = true;
      private int databaseWindowsVssFallbackTimeoutSeconds = 90;
      private List<String> databasePathRules = new ArrayList();
      private List<String> databaseExtensions = new ArrayList();
      private List<String> databaseFileNames = new ArrayList();
      private PerformancePlan performancePlan;
      private List<String> excludeRules = new ArrayList();
      private Map<BackupCategory, String> categoryFolders = defaultCategoryFolders();

      private Builder(Path var1, Path var2) {
         this.serverRoot = (Path)Objects.requireNonNull(var1, "serverRoot");
         this.backupRoot = (Path)Objects.requireNonNull(var2, "backupRoot");
      }

      public Builder archiveFolderName(String var1) {
         this.archiveFolderName = var1;
         return this;
      }

      public Builder datePattern(String var1) {
         this.datePattern = var1;
         return this;
      }

      public Builder worldMarkerFile(String var1) {
         this.worldMarkerFile = var1;
         return this;
      }

      public Builder keepCategorizedFolder(boolean var1) {
         this.keepCategorizedFolder = var1;
         return this;
      }

      public Builder includeEmptyDirectories(boolean var1) {
         this.includeEmptyDirectories = var1;
         return this;
      }

      public Builder retentionEnabled(boolean var1) {
         this.retentionEnabled = var1;
         return this;
      }

      public Builder retentionKeepLast(int var1) {
         this.retentionKeepLast = var1;
         return this;
      }

      public Builder maxBytesPerSecond(long var1) {
         this.maxBytesPerSecond = var1;
         return this;
      }

      public Builder bufferSizeBytes(int var1) {
         this.bufferSizeBytes = var1;
         return this;
      }

      public Builder pauseBetweenFilesMillis(int var1) {
         this.pauseBetweenFilesMillis = var1;
         return this;
      }

      public Builder zipCompressionLevel(int var1) {
         this.zipCompressionLevel = var1;
         return this;
      }

      public Builder progressLogIntervalSeconds(int var1) {
         this.progressLogIntervalSeconds = var1;
         return this;
      }

      public Builder integrityEnabled(boolean var1) {
         this.integrityEnabled = var1;
         return this;
      }

      public Builder verifyCopiedFileSize(boolean var1) {
         this.verifyCopiedFileSize = var1;
         return this;
      }

      public Builder verifyZipAfterCreate(boolean var1) {
         this.verifyZipAfterCreate = var1;
         return this;
      }

      public Builder retryChangedFiles(int var1) {
         this.retryChangedFiles = var1;
         return this;
      }

      public Builder materialSafeModeEnabled(boolean var1) {
         this.materialSafeModeEnabled = var1;
         return this;
      }

      public Builder failOnMaterialWarning(boolean var1) {
         this.failOnMaterialWarning = var1;
         return this;
      }

      public Builder materialPathRules(List<String> var1) {
         this.materialPathRules = var1 == null ? List.<String>of() : new ArrayList<>(var1);
         return this;
      }

      public Builder materialExtensions(List<String> var1) {
         this.materialExtensions = var1 == null ? List.<String>of() : new ArrayList<>(var1);
         return this;
      }

      public Builder databaseSafeModeEnabled(boolean var1) {
         this.databaseSafeModeEnabled = var1;
         return this;
      }

      public Builder failOnDatabaseWarning(boolean var1) {
         this.failOnDatabaseWarning = var1;
         return this;
      }

      public Builder failOnDatabaseCopyFailure(boolean var1) {
         this.failOnDatabaseCopyFailure = var1;
         return this;
      }

      public Builder databaseRetryAttempts(int var1) {
         this.databaseRetryAttempts = var1;
         return this;
      }

      public Builder databaseRetryDelayMillis(int var1) {
         this.databaseRetryDelayMillis = var1;
         return this;
      }

      public Builder databaseStabilityCheckMillis(int var1) {
         this.databaseStabilityCheckMillis = var1;
         return this;
      }

      public Builder databaseMaxStabilityWaitMillis(int var1) {
         this.databaseMaxStabilityWaitMillis = var1;
         return this;
      }

      public Builder databaseWindowsFallbackEnabled(boolean var1) {
         this.databaseWindowsFallbackEnabled = var1;
         return this;
      }

      public Builder databaseWindowsFallbackBackupMode(boolean var1) {
         this.databaseWindowsFallbackBackupMode = var1;
         return this;
      }

      public Builder databaseWindowsFallbackRetries(int var1) {
         this.databaseWindowsFallbackRetries = var1;
         return this;
      }

      public Builder databaseWindowsFallbackWaitSeconds(int var1) {
         this.databaseWindowsFallbackWaitSeconds = var1;
         return this;
      }

      public Builder databaseWindowsFallbackTimeoutSeconds(int var1) {
         this.databaseWindowsFallbackTimeoutSeconds = var1;
         return this;
      }

      public Builder databaseWindowsEsentutlFallbackEnabled(boolean var1) {
         this.databaseWindowsEsentutlFallbackEnabled = var1;
         return this;
      }

      public Builder databaseWindowsEsentutlFallbackTimeoutSeconds(int var1) {
         this.databaseWindowsEsentutlFallbackTimeoutSeconds = var1;
         return this;
      }

      public Builder databaseWindowsVssFallbackEnabled(boolean var1) {
         this.databaseWindowsVssFallbackEnabled = var1;
         return this;
      }

      public Builder databaseWindowsVssFallbackTimeoutSeconds(int var1) {
         this.databaseWindowsVssFallbackTimeoutSeconds = var1;
         return this;
      }

      public Builder databasePathRules(List<String> var1) {
         this.databasePathRules = var1 == null ? List.<String>of() : new ArrayList<>(var1);
         return this;
      }

      public Builder databaseExtensions(List<String> var1) {
         this.databaseExtensions = var1 == null ? List.<String>of() : new ArrayList<>(var1);
         return this;
      }

      public Builder databaseFileNames(List<String> var1) {
         this.databaseFileNames = var1 == null ? List.<String>of() : new ArrayList<>(var1);
         return this;
      }

      public Builder performancePlan(PerformancePlan var1) {
         this.performancePlan = var1;
         if (var1 != null) {
            this.maxBytesPerSecond = var1.maxBytesPerSecond();
            this.bufferSizeBytes = var1.bufferSizeBytes();
            this.pauseBetweenFilesMillis = var1.pauseBetweenFilesMillis();
            this.zipCompressionLevel = var1.zipCompressionLevel();
            this.progressLogIntervalSeconds = var1.progressLogIntervalSeconds();
         }

         return this;
      }

      public Builder excludeRules(List<String> var1) {
         this.excludeRules = var1 == null ? List.<String>of() : new ArrayList<>(var1);
         return this;
      }

      public Builder categoryFolders(Map<BackupCategory, String> var1) {
         this.categoryFolders = new EnumMap(BackupCategory.class);

         for(BackupCategory var5 : BackupCategory.values()) {
            String var6 = var1 == null ? null : (String)var1.get(var5);
            this.categoryFolders.put(var5, var6 != null && !var6.isBlank() ? var6.trim() : var5.defaultFolder());
         }

         return this;
      }

      public BackupSettings build() {
         return new BackupSettings(this);
      }

      private static Map<BackupCategory, String> defaultCategoryFolders() {
         EnumMap var0 = new EnumMap(BackupCategory.class);

         for(BackupCategory var4 : BackupCategory.values()) {
            var0.put(var4, var4.defaultFolder());
         }

         return var0;
      }
   }
}
