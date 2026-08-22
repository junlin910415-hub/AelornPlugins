package tw.linsy.serverbackup.update;

import java.nio.file.Path;

public record UpdateSnapshot(String snapshotId, String label, Path snapshotDirectory, Path inventoryManifest, int pluginCount, long totalBytes) {
}
