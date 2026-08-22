package tw.linsy.serverbackup.core;

/**
 * A reusable byte buffer for one backup phase.
 *
 * The copy and archive phases each touch every file in the inventory, so
 * allocating a fresh buffer per file put roughly {@code fileCount × bufferSize}
 * of short-lived garbage through the heap — at 18k files and a 256 KiB buffer
 * that is about 4.6 GiB per phase, all of it avoidable. One buffer is created
 * per phase and threaded through the file loop instead.
 *
 * <p>Not thread-safe by design: each buffer belongs to exactly one sequential
 * phase. If the copy loop is ever parallelised, give every worker its own.
 */
final class CopyBuffer {

    private static final int MIN_SIZE = 8 * 1024;
    private static final int MAX_SIZE = 8 * 1024 * 1024;

    private final byte[] bytes;

    CopyBuffer(int requestedSize) {
        int size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, requestedSize));
        this.bytes = new byte[size];
    }

    byte[] bytes() {
        return bytes;
    }

    int size() {
        return bytes.length;
    }
}
