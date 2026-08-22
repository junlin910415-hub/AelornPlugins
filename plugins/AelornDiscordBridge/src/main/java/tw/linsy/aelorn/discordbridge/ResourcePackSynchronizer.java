package tw.linsy.aelorn.discordbridge;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies the canonical generated resource pack over the InteractiveChatDiscordSrvAddon copy.
 * All methods do blocking file I/O; call from the async scheduler or onEnable only.
 */
final class ResourcePackSynchronizer {

    private ResourcePackSynchronizer() {
    }

    static Result synchronize(Path pluginsRoot, String sourceRelative, String copyRelative,
                              boolean enabled, boolean allowWrite) throws IOException {
        if (!enabled) {
            return new Result(Status.DISABLED, 0L);
        }
        Path root = pluginsRoot.toAbsolutePath().normalize();
        Path source = root.resolve(sourceRelative).normalize();
        Path copy = root.resolve(copyRelative).normalize();
        requireInsideRoot(root, source);
        requireInsideRoot(root, copy);

        if (!Files.isRegularFile(source)) {
            return new Result(Status.SOURCE_MISSING, 0L);
        }
        long sourceBytes = Files.size(source);

        try {
            if (Files.isRegularFile(copy) && Files.mismatch(source, copy) == -1L) {
                return new Result(Status.CURRENT, sourceBytes);
            }
        } catch (AccessDeniedException locked) {
            return new Result(Status.LOCKED, sourceBytes);
        }

        if (!allowWrite) {
            return new Result(Status.PENDING_RESTART, sourceBytes);
        }

        Files.createDirectories(copy.getParent());
        Path temp = copy.resolveSibling(copy.getFileName() + ".tmp");
        try {
            Files.deleteIfExists(temp);
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            if (Files.mismatch(source, temp) != -1L) {
                throw new IOException("Temporary resource pack failed content verification.");
            }
            try {
                Files.move(temp, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException fallback) {
                Files.move(temp, copy, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.setLastModifiedTime(copy, Files.getLastModifiedTime(source));
            return new Result(Status.UPDATED, sourceBytes);
        } catch (AccessDeniedException locked) {
            return new Result(Status.LOCKED, sourceBytes);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private static void requireInsideRoot(Path root, Path candidate) throws IOException {
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            throw new IOException("Resource pack path escapes the plugins directory.");
        }
    }

    enum Status {
        DISABLED,
        SOURCE_MISSING,
        CURRENT,
        UPDATED,
        PENDING_RESTART,
        LOCKED,
        FAILED
    }

    record Result(Status status, long bytes) {
        boolean changed() {
            return status == Status.UPDATED;
        }
    }
}
