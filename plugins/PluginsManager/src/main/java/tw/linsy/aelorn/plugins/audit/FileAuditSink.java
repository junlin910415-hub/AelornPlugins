package tw.linsy.aelorn.plugins.audit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Audit records as JSON lines in {@code audit.log}.
 *
 * The default sink. JSON lines rather than a formatted log because the point of this
 * file is to answer "who disabled that plugin, and when" months later, possibly from
 * a script; reading it back in game is the secondary use.
 *
 * <h2>Two costs the previous implementation paid needlessly</h2>
 * The writer is opened once and kept, instead of open-append-close per record: a
 * batch group operation writes one line per member, so on a thirty-plugin group that
 * was thirty file opens. Each record is still flushed immediately — an audit trail
 * that loses its last entries in a crash is not one.
 *
 * <p>{@link #tail} reads from the <em>end</em> of the file rather than reading all of
 * it and discarding all but the last twenty lines. The file grows without bound by
 * design, so the old approach got slower every day the server ran.
 */
public final class FileAuditSink implements AuditSink {

    /** How much of the file's tail to read per attempt before doubling. */
    private static final int TAIL_CHUNK_BYTES = 8 * 1024;
    private static final int TAIL_MAX_BYTES = 4 * 1024 * 1024;

    private final Path logFile;
    private final Logger logger;

    private @Nullable BufferedWriter writer;

    public FileAuditSink(Path logFile, Logger logger) {
        this.logFile = logFile;
        this.logger = logger;
    }

    public Path file() {
        return logFile;
    }

    /**
     * Synchronized because the folder watcher writes from its own thread while
     * commands write from the global region.
     */
    @Override
    public synchronized void write(AuditRecord record) {
        String line = "{\"time\":\"" + escape(record.time().toString())
            + "\",\"actor\":\"" + escape(record.actor())
            + "\",\"action\":\"" + escape(record.action())
            + "\",\"target\":\"" + escape(record.target())
            + "\",\"status\":\"" + escape(record.status())
            + "\",\"detail\":\"" + escape(record.detail()) + "\"}";
        try {
            BufferedWriter open = openWriter();
            open.write(line);
            open.newLine();
            // Flushed per record: the entries worth having are the ones written
            // immediately before whatever went wrong.
            open.flush();
        } catch (IOException unwritable) {
            logger.log(Level.WARNING, "無法寫入稽核紀錄。", unwritable);
            closeQuietly();
        }
    }

    @Override
    public void flush() {
        // Every record is already flushed as it is written.
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }

    @Override
    public String describe() {
        return "file:" + logFile.getFileName();
    }

    private BufferedWriter openWriter() throws IOException {
        BufferedWriter current = writer;
        if (current != null) {
            return current;
        }
        Path parent = logFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        current = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        writer = current;
        return current;
    }

    private void closeQuietly() {
        BufferedWriter current = writer;
        writer = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (IOException ignored) {
            // Nothing useful to do while shutting down, and the records were already
            // flushed as they were written.
        }
    }

    /**
     * The last {@code count} lines, oldest first.
     *
     * Reads backwards from the end in growing chunks until enough newlines are seen,
     * so the cost is bounded by what is returned rather than by how long the server
     * has been running. A chunk boundary can split a multi-byte UTF-8 character,
     * which is why the first (possibly partial) line of a chunk is dropped unless the
     * chunk starts at the beginning of the file.
     */
    @Override
    public synchronized List<String> tail(int count) {
        int wanted = Math.max(1, count);
        if (!Files.isRegularFile(logFile)) {
            return List.of();
        }
        try (SeekableByteChannel channel = Files.newByteChannel(logFile, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size == 0) {
                return List.of();
            }
            int window = TAIL_CHUNK_BYTES;
            while (true) {
                long from = Math.max(0, size - window);
                List<String> lines = readLines(channel, from, size - from, from > 0);
                if (lines.size() >= wanted || from == 0 || window >= TAIL_MAX_BYTES) {
                    return lastOf(lines, wanted);
                }
                window *= 2;
            }
        } catch (IOException unreadable) {
            logger.log(Level.WARNING, "無法讀取稽核紀錄。", unreadable);
            return List.of();
        }
    }

    private static List<String> readLines(SeekableByteChannel channel, long from, long length,
                                          boolean dropFirstPartial) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(length, TAIL_MAX_BYTES));
        channel.position(from);
        while (buffer.hasRemaining() && channel.read(buffer) > 0) {
            // One read is not guaranteed to fill the buffer.
        }
        buffer.flip();
        String text = StandardCharsets.UTF_8.decode(buffer).toString();
        List<String> lines = new ArrayList<>(List.of(text.split("\\R", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (dropFirstPartial && !lines.isEmpty()) {
            lines.remove(0);
        }
        return lines;
    }

    private static List<String> lastOf(List<String> lines, int count) {
        if (lines.size() <= count) {
            return List.copyOf(lines);
        }
        return List.copyOf(lines.subList(lines.size() - count, lines.size()));
    }

    static String escape(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            switch (character) {
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (character < ' ') {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        return out.toString();
    }
}
