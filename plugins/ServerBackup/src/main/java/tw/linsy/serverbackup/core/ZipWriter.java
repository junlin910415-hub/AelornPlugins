package tw.linsy.serverbackup.core;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ZipWriter {

    private ZipWriter() {
    }

    static void zipDirectory(Path sourceRoot, Path archive, int compressionLevel, int bufferSizeBytes,
                             ThroughputLimiter limiter, ZipProgress progress) throws IOException {
        Files.createDirectories(archive.getParent());
        // One buffer for the whole archive rather than one per entry.
        CopyBuffer buffer = new CopyBuffer(bufferSizeBytes);

        // ZipOutputStream gives the deflater a 512-byte buffer and flushes it
        // straight to the sink, so an unbuffered file stream turned a multi-GB
        // archive into millions of tiny writes. Buffer the sink to batch them.
        try (OutputStream fileOut = Files.newOutputStream(archive);
             OutputStream bufferedOut = new BufferedOutputStream(fileOut, buffer.size());
             ZipOutputStream zipOut = new ZipOutputStream(bufferedOut)) {
            zipOut.setLevel(compressionLevel);
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                    String name = normalize(sourceRoot.relativize(directory));
                    if (!name.isBlank()) {
                        ZipEntry entry = new ZipEntry(name + "/");
                        entry.setTime(attributes.lastModifiedTime().toMillis());
                        zipOut.putNextEntry(entry);
                        zipOut.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    ZipEntry entry = new ZipEntry(normalize(sourceRoot.relativize(file)));
                    entry.setTime(attributes.lastModifiedTime().toMillis());
                    zipOut.putNextEntry(entry);
                    long written = BackupIo.copyToStream(file, zipOut, buffer, limiter);
                    zipOut.closeEntry();
                    if (progress != null) {
                        progress.afterFile(written);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    @FunctionalInterface
    interface ZipProgress {
        void afterFile(long bytes);
    }
}
