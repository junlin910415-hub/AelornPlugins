package tw.linsy.aelorn.plugins.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.ArchiveSettings;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.JarDescriptor;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;

/**
 * Keeping old copies of a plugin's jar so an upgrade can be undone.
 *
 * The operational point: replacing a jar is the one plugin operation with no undo,
 * and an admin who overwrites a working build at 3am has nothing to go back to.
 * Archives live under the plugin's own data folder, one directory per plugin,
 * newest-first by modification time.
 *
 * <h2>Path safety</h2>
 * Every name that reaches the filesystem — plugin name, admin-supplied note,
 * configured folder — is reduced to {@code [A-Za-z0-9._-]}, and every delete is
 * checked to be inside the archive root before it happens. Both matter because the
 * inputs are admin-supplied strings that become path components, and {@code prune}
 * deletes files.
 *
 * <p>All IO, no server state: safe to run on an async thread, which is where the
 * command layer puts it.
 */
public final class VersionArchiveService {

    /** Separates timestamp, note and original file name in an archive file name. */
    private static final String FIELD_SEPARATOR = "__";

    private final Path dataFolder;
    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final AuditLog audit;
    private final JarIndex jars;
    private final Logger logger;

    public VersionArchiveService(Path dataFolder, SettingsStore settings, MessageCatalog messages,
                                AuditLog audit, JarIndex jars, Logger logger) {
        this.dataFolder = dataFolder;
        this.settings = settings;
        this.messages = messages;
        this.audit = audit;
        this.jars = jars;
        this.logger = logger;
    }

    // ── 保存 ──────────────────────────────────────────────────────────────

    public Reply archive(String actor, @Nullable String query, @Nullable String note) {
        ArchiveSettings config = settings.archive();
        if (!config.enabled()) {
            return Reply.fail(messages.raw("archive.disabled"));
        }
        JarIndex.JarRef jar = jars.resolveJar(query);
        if (!jar.found()) {
            return Reply.fail(messages.raw(jar.errorKey(), "value", jar.errorArg()));
        }
        Path source = jar.require();
        try {
            String archiveName = archiveNameFor(source);
            Path directory = archiveRoot(config).resolve(archiveName);
            Files.createDirectories(directory);

            String stamp = config.timestamp().format(LocalDateTime.now());
            String fileName = note == null || note.isBlank()
                ? stamp + FIELD_SEPARATOR + source.getFileName()
                : stamp + FIELD_SEPARATOR + safeName(note) + FIELD_SEPARATOR + source.getFileName();
            Path target = directory.resolve(fileName);

            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            int pruned = prune(directory, config);

            audit.record(actor, "archive", archiveName, "SUCCESS",
                target.getFileName() + (pruned > 0 ? "; pruned=" + pruned : ""));
            List<String> lines = new ArrayList<>();
            lines.add(messages.raw("archive.saved",
                "plugin", archiveName, "file", target.getFileName().toString()));
            if (pruned > 0) {
                lines.add(messages.raw("archive.pruned", "count", pruned, "keep", config.maxPerPlugin()));
            }
            return Reply.ok(lines);
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.WARNING, "保存版本失敗。", failure);
            audit.record(actor, "archive", source.getFileName().toString(), "FAIL",
                Texts.summarise(failure));
            return Reply.fail(messages.raw("archive.save-failed", "reason", Texts.summarise(failure)));
        }
    }

    // ── 列出 ──────────────────────────────────────────────────────────────

    public Reply versions(@Nullable String query) {
        ArchiveSettings config = settings.archive();
        if (!config.enabled()) {
            return Reply.fail(messages.raw("archive.disabled"));
        }
        String archiveName = archiveNameFromQuery(query);
        List<Path> archives = archivesOf(archiveName, config);
        if (archives.isEmpty()) {
            return Reply.fail(messages.raw("archive.none", "plugin", archiveName));
        }
        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("archive.list-header", "plugin", archiveName, "count", archives.size()));
        for (Path archive : archives) {
            long size;
            try {
                size = Files.size(archive);
            } catch (IOException unreadable) {
                size = -1;
            }
            lines.add(messages.raw("archive.list-row",
                "file", archive.getFileName().toString(),
                "size", size < 0 ? messages.raw("common.unknown") : String.valueOf(size)));
        }
        return Reply.ok(lines);
    }

    // ── 還原 ──────────────────────────────────────────────────────────────

    /**
     * Copies an archived jar back over the live one.
     *
     * Refuses while the plugin is loaded, by default: on Windows a loaded jar is
     * locked and the copy fails halfway, and even where it succeeds the running
     * plugin keeps serving classes from the old jar until it is unloaded, so the
     * restore appears to have done nothing.
     */
    public Reply restore(String actor, @Nullable String query, @Nullable String version, boolean confirmed) {
        ArchiveSettings config = settings.archive();
        if (!config.enabled()) {
            return Reply.fail(messages.raw("archive.disabled"));
        }
        if (!confirmed) {
            return Reply.fail(messages.raw("common.needs-confirm", "usage",
                messages.raw("command.root") + " restore " + display(query)
                    + " " + display(version) + " --confirm"));
        }
        String archiveName = archiveNameFromQuery(query);
        Path archive = resolveArchive(archiveName, version, config);
        if (archive == null) {
            return Reply.fail(messages.raw("archive.version-not-found", "version", display(version)));
        }
        try {
            JarDescriptor descriptor = jars.readDescriptor(archive);
            String pluginName = descriptor.hasName() ? descriptor.name() : archiveName;
            if (config.restoreRequiresUnloaded()
                && Bukkit.getPluginManager().getPlugin(pluginName) != null) {
                return Reply.fail(messages.raw("archive.still-loaded", "plugin", pluginName));
            }
            Path target = restoreTarget(archive);
            Files.copy(archive, target, StandardCopyOption.REPLACE_EXISTING);

            audit.record(actor, "restore", pluginName, "SUCCESS",
                archive.getFileName() + " -> " + target.getFileName());
            return Reply.ok(messages.raw("archive.restored",
                "plugin", pluginName, "file", target.getFileName().toString()));
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.WARNING, "還原版本失敗。", failure);
            audit.record(actor, "restore", archive.getFileName().toString(), "FAIL",
                Texts.summarise(failure));
            return Reply.fail(messages.raw("archive.restore-failed", "reason", Texts.summarise(failure)));
        }
    }

    // ── 補全 ──────────────────────────────────────────────────────────────

    /** Archive directory names, for completing the plugin argument. */
    public List<String> suggestArchives(@Nullable String partial) {
        String needle = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        Path root = archiveRoot(settings.archive());
        if (!Files.isDirectory(root)) {
            return names;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path directory : stream) {
                String name = directory.getFileName().toString();
                if (name.toLowerCase(Locale.ROOT).startsWith(needle)) {
                    names.add(name);
                }
            }
        } catch (IOException unreadable) {
            return names;
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Archive file names for one plugin, plus the {@code latest} keyword. */
    public List<String> suggestVersions(@Nullable String pluginQuery, @Nullable String partial) {
        String needle = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        if ("latest".startsWith(needle)) {
            names.add("latest");
        }
        for (Path archive : archivesOf(archiveNameFromQuery(pluginQuery), settings.archive())) {
            String name = archive.getFileName().toString();
            if (name.toLowerCase(Locale.ROOT).contains(needle)) {
                names.add(name);
            }
        }
        return names;
    }

    // ── 內部 ──────────────────────────────────────────────────────────────

    private Path archiveRoot(ArchiveSettings config) {
        return dataFolder.resolve(config.folderName()).normalize();
    }

    /** Newest first, so {@code latest} is simply the head of the list. */
    private List<Path> archivesOf(String archiveName, ArchiveSettings config) {
        Path directory = archiveRoot(config).resolve(safeName(archiveName));
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> archives = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory,
            entry -> Files.isRegularFile(entry)
                && entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))) {
            for (Path archive : stream) {
                archives.add(archive);
            }
        } catch (IOException unreadable) {
            return List.of();
        }
        archives.sort(Comparator.comparingLong(VersionArchiveService::modifiedAt).reversed());
        return archives;
    }

    private static long modifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }

    /**
     * @param version a file name, a substring of one, or {@code latest}/{@code newest}
     * @return the single match, or {@code null} when nothing or more than one matches
     */
    private @Nullable Path resolveArchive(String archiveName, @Nullable String version,
                                          ArchiveSettings config) {
        List<Path> archives = archivesOf(archiveName, config);
        if (archives.isEmpty()) {
            return null;
        }
        String needle = version == null ? "" : version.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty() || needle.equals("latest") || needle.equals("newest")) {
            return archives.get(0);
        }
        Path found = null;
        for (Path archive : archives) {
            String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.equals(needle)) {
                // An exact file name wins outright; a substring that also matches
                // another archive would otherwise make the exact name ambiguous.
                return archive;
            }
            if (name.contains(needle)) {
                if (found != null) {
                    return null;
                }
                found = archive;
            }
        }
        return found;
    }

    /**
     * Deletes the oldest archives beyond the configured keep count.
     *
     * @return how many were removed
     */
    private int prune(Path directory, ArchiveSettings config) throws IOException {
        List<Path> archives = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory,
            entry -> Files.isRegularFile(entry)
                && entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))) {
            for (Path archive : stream) {
                archives.add(archive);
            }
        }
        if (archives.size() <= config.maxPerPlugin()) {
            return 0;
        }
        archives.sort(Comparator.comparingLong(VersionArchiveService::modifiedAt).reversed());
        Path root = archiveRoot(config).toAbsolutePath().normalize();
        int pruned = 0;
        for (int index = config.maxPerPlugin(); index < archives.size(); index++) {
            Path victim = archives.get(index).toAbsolutePath().normalize();
            // Checked immediately before the delete, not when the path was built:
            // this is the last point at which a symlink or a crafted name could
            // still point outside the archive root.
            if (!victim.startsWith(root)) {
                throw new IOException("拒絕刪除版本根目錄外的檔案：" + victim);
            }
            if (Files.deleteIfExists(victim)) {
                pruned++;
            }
        }
        return pruned;
    }

    /** The archive directory name for a jar: its declared plugin name, or its file name. */
    private String archiveNameFor(Path jar) {
        try {
            JarDescriptor descriptor = jars.readDescriptor(jar);
            if (descriptor.hasName()) {
                return safeName(descriptor.name());
            }
        } catch (IOException unreadable) {
            // Falls through to the file name, which is still a usable grouping.
        }
        return safeName(baseName(jar.getFileName().toString()));
    }

    /**
     * Resolves what the admin typed into an archive directory name.
     *
     * Tries the live plugins folder first so {@code /… versions MyPlugin} works from
     * the plugin name, and falls back to treating the input as the directory name so
     * archives of a jar that is no longer installed remain reachable.
     */
    private String archiveNameFromQuery(@Nullable String query) {
        JarIndex.JarRef jar = jars.resolveJar(query);
        if (jar.found()) {
            return archiveNameFor(jar.require());
        }
        String text = query == null || query.isBlank() ? "unknown" : query.trim();
        return safeName(text.startsWith("@") ? text.substring(1) : text);
    }

    /**
     * Where an archived jar is written back to, verified to be inside the plugins
     * folder.
     *
     * <p>This is the one write in the plugin that lands in {@code plugins/}, and
     * overwriting a jar there is code execution on the next load — so it gets the same
     * containment check every other path operation here already had, rather than
     * trusting that a file name read off disk cannot contain a separator. The archive
     * name is also re-sanitised: {@link #safeName} runs when an archive is
     * <em>created</em>, but nothing stops an admin dropping a hand-named file into the
     * archive directory afterwards.
     *
     * @throws IOException when the resolved target would land outside the folder
     */
    private Path restoreTarget(Path archive) throws IOException {
        String jarName = safeName(originalJarName(archive.getFileName().toString()));
        if (!jarName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            jarName = jarName + ".jar";
        }
        Path folder = jars.pluginsFolder().toAbsolutePath().normalize();
        Path target = folder.resolve(jarName).toAbsolutePath().normalize();
        if (!target.startsWith(folder) || target.equals(folder)) {
            throw new IOException("拒絕寫入插件資料夾外的路徑：" + target);
        }
        return target;
    }

    /** {@code 20260812-1200__note__Foo.jar} to {@code Foo.jar}. */
    private static String originalJarName(String archiveFileName) {
        int separator = archiveFileName.lastIndexOf(FIELD_SEPARATOR);
        return separator >= 0 && separator + FIELD_SEPARATOR.length() < archiveFileName.length()
            ? archiveFileName.substring(separator + FIELD_SEPARATOR.length())
            : archiveFileName;
    }

    private static String baseName(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".jar")
            ? fileName.substring(0, fileName.length() - 4)
            : fileName;
    }

    /**
     * Reduces a name to characters safe as a single path component.
     *
     * Not an escape but a replacement: the result is used for grouping and display,
     * so two plugins whose names differ only in stripped characters sharing a
     * directory is acceptable, while a name that escapes the archive root is not.
     */
    private static String safeName(@Nullable String raw) {
        String trimmed = raw == null || raw.isBlank() ? "unknown" : raw.trim();
        String cleaned = trimmed.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..") ? "unknown" : cleaned;
    }

    private String display(@Nullable String value) {
        return value == null || value.isBlank() ? messages.raw("common.none") : value.trim();
    }
}
