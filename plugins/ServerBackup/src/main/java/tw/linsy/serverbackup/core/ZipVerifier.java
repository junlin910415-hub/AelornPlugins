package tw.linsy.serverbackup.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads a finished archive back to prove it is intact.
 *
 * This costs two full passes over the archive — one to digest the compressed
 * bytes, one to inflate every entry — which is the price of catching a corrupt
 * backup now rather than during a restore.
 */
final class ZipVerifier {

    private ZipVerifier() {
    }

    static Result verify(Path archive, List<String> expectedEntries, long expectedBytes, int bufferSizeBytes,
                         ThroughputLimiter limiter, VerificationProgress progress) throws IOException {
        List<String> warnings = new ArrayList<>();
        CopyBuffer buffer = new CopyBuffer(bufferSizeBytes);

        String archiveSha256 = sha256(archive, buffer, limiter);
        Set<String> missing = new HashSet<>(expectedEntries);
        long fileEntries = 0L;
        long uncompressedBytes = 0L;
        byte[] bytes = buffer.bytes();

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.indexOf('\\') >= 0) {
                    warnings.add("Zip entry uses an unsafe separator: " + name);
                }
                if (entry.isDirectory()) {
                    continue;
                }

                fileEntries++;
                long entryBytes = 0L;
                try (InputStream in = zip.getInputStream(entry)) {
                    int read;
                    while ((read = in.read(bytes)) >= 0) {
                        entryBytes += read;
                        limiter.afterBytes(read);
                    }
                }
                limiter.afterFile();
                uncompressedBytes += entryBytes;

                if (entry.getSize() >= 0L && entry.getSize() != entryBytes) {
                    warnings.add("Zip entry size mismatch: " + name);
                }
                if (!missing.remove(name)) {
                    warnings.add("Unexpected zip entry: " + name);
                }
                if (progress != null) {
                    progress.afterEntry(fileEntries, uncompressedBytes);
                }
            }
        }

        for (String entry : missing) {
            warnings.add("Missing zip entry: " + entry);
        }
        if (uncompressedBytes < expectedBytes) {
            warnings.add("Zip uncompressed bytes are smaller than expected: "
                + BackupProgress.humanReadableBytes(uncompressedBytes) + " < "
                + BackupProgress.humanReadableBytes(expectedBytes));
        }
        return new Result(warnings.isEmpty(), fileEntries, uncompressedBytes, archiveSha256, List.copyOf(warnings));
    }

    private static String sha256(Path archive, CopyBuffer buffer, ThroughputLimiter limiter) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException("SHA-256 digest is unavailable", unavailable);
        }
        byte[] bytes = buffer.bytes();
        try (InputStream in = Files.newInputStream(archive)) {
            int read;
            while ((read = in.read(bytes)) >= 0) {
                digest.update(bytes, 0, read);
                limiter.afterBytes(read);
            }
        }
        limiter.afterFile();
        return HexFormat.of().formatHex(digest.digest());
    }

    record Result(boolean verified, long fileEntries, long uncompressedBytes, String archiveSha256,
                  List<String> warnings) {
    }

    @FunctionalInterface
    interface VerificationProgress {
        void afterEntry(long entries, long bytes);
    }
}
