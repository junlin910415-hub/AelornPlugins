package tw.linsy.aelorn.plugins.model;

import org.jetbrains.annotations.Nullable;

/**
 * What the folder watcher remembers about one jar between checks.
 *
 * <p>A naive watcher re-hashes every jar every time. With 36 plugins and 128 MB
 * in the folder on a 10-second interval that is roughly 12 MB/s of SHA-256 for
 * as long as the server is up, to detect a change that happens a few times a
 * month.
 *
 * <p>So the fingerprint is two-tier: {@link #size} and {@link #lastModified}
 * come from the directory listing and are effectively free, and {@link #sha256}
 * is only recomputed when one of them moves. The hash is still what identifies a
 * jar for auto-load, so nothing downstream changes.
 *
 * <p>The tradeoff: a rewrite preserving both the byte count and the modification
 * timestamp goes unnoticed. No build tool or file copy does that, and an
 * explicit scan always hashes for real.
 *
 * @param sha256 the content hash, or an {@code ERROR:} marker when the jar could
 *               not be read — kept in the index rather than dropped so the jar
 *               still counts as present and auto-load knows to wait
 */
public record JarFingerprint(long size, long lastModified, String sha256) {

    private static final String ERROR_PREFIX = "ERROR:";

    /** True when the cheap attributes still match, so the cached hash stands. */
    public boolean matches(long currentSize, long currentLastModified) {
        return currentSize == size && currentLastModified == lastModified;
    }

    /** True when hashing failed, which means the jar is mid-write, not broken. */
    public boolean unreadable() {
        return sha256.startsWith(ERROR_PREFIX);
    }

    public static JarFingerprint of(long size, long lastModified, String sha256) {
        return new JarFingerprint(size, lastModified, sha256);
    }

    public static JarFingerprint unreadable(long size, long lastModified, @Nullable Throwable cause) {
        String reason = cause == null ? "Unknown" : cause.getClass().getSimpleName();
        return new JarFingerprint(size, lastModified, ERROR_PREFIX + reason);
    }

    /** First ten hex digits, which is all a scan listing has room to show. */
    public String shortHash() {
        return sha256.length() <= 10 ? sha256 : sha256.substring(0, 10);
    }
}
