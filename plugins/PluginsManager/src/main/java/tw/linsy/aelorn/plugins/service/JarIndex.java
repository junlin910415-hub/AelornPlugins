package tw.linsy.aelorn.plugins.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.model.JarDescriptor;
import tw.linsy.aelorn.plugins.model.JarFingerprint;
import tw.linsy.aelorn.plugins.model.ScanEntry;
import tw.linsy.aelorn.plugins.model.ScanReport;

/**
 * Everything that reads the plugins folder from disk.
 *
 * Pure IO with no server state, so every method here is safe — and expected — to
 * run on an async thread. That separation is why it is its own service: the folder
 * watcher, the scan command, the archive service and the loader all need it, and
 * none of them should each own a copy of "how do I read a jar's name".
 */
public final class JarIndex {

    private static final String JAR_SUFFIX = ".jar";

    private final Path pluginsFolder;
    private final DescriptorCache descriptors = new DescriptorCache();

    public JarIndex() {
        this.pluginsFolder = Bukkit.getPluginsFolder().toPath();
    }

    public Path pluginsFolder() {
        return pluginsFolder;
    }

    /** Drops memoised descriptors; called on a settings reload. */
    public void clearCache() {
        descriptors.clear();
    }

    /** How many descriptors are memoised, for the status report. */
    public int cachedDescriptors() {
        return descriptors.size();
    }

    /** Where a jar lookup ended up: a path, or the message key explaining why not. */
    public record JarRef(@Nullable Path path, @Nullable String errorKey, String errorArg) {

        static JarRef found(Path path) {
            return new JarRef(path, null, "");
        }

        static JarRef error(String key, String arg) {
            return new JarRef(null, key, arg);
        }

        public boolean found() {
            return path != null;
        }

        public Path require() {
            if (path == null) {
                throw new IllegalStateException("JarRef 未找到檔案，呼叫端應先檢查 found()。");
            }
            return path;
        }
    }

    // ── 掃描 ──────────────────────────────────────────────────────────────

    /**
     * Hashes and describes every jar in the folder.
     *
     * Always hashes for real, unlike the watcher's cached fingerprints: this is the
     * command an admin runs precisely when they suspect the cheap checks missed
     * something.
     *
     * <h3>Fanned out across virtual threads</h3>
     * Hashing thirty-six jars is thirty-six independent reads of a few megabytes each,
     * and done sequentially the whole scan waits on one disk queue at a time. Each jar
     * gets a virtual thread, so the reads overlap and the scan finishes in roughly the
     * time of the slowest jar rather than the sum of all of them.
     *
     * <p>Virtual threads specifically, rather than a pool sized to the CPU: this work is
     * blocking IO, not computation, and a virtual thread parked on a read costs no
     * platform thread. On Java 25 there is also no pinning concern for file IO, so the
     * carrier threads stay free for whatever else the async scheduler is running.
     *
     * <p>The executor is closed before returning, which waits for every task — this
     * method is called from an async scheduler thread and must not hand back a report
     * that is still filling in.
     *
     * @param loadedNames lower-cased names of currently loaded plugins
     */
    public ScanReport scan(Set<String> loadedNames) {
        List<Path> jars;
        try {
            jars = listJars();
        } catch (IOException unreadable) {
            // A technical detail, not a sentence: it is rendered into the
            // scan.error template, which supplies the wording.
            return ScanReport.failed(pluginsFolder + " — " + Texts.summarise(unreadable));
        }
        List<ScanEntry> entries = Collections.synchronizedList(new ArrayList<>(jars.size()));
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Path jar : jars) {
                workers.submit(() -> {
                    try {
                        entries.add(describeAndHash(jar, loadedNames));
                    } catch (Exception unreadable) {
                        errors.add(jar.getFileName() + ": " + Texts.summarise(unreadable));
                    }
                });
            }
            // close() on the try-with-resources awaits termination.
        }

        List<ScanEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(ScanEntry::fileName, String.CASE_INSENSITIVE_ORDER));
        List<String> sortedErrors = new ArrayList<>(errors);
        sortedErrors.sort(String.CASE_INSENSITIVE_ORDER);
        return new ScanReport(sorted, sortedErrors, duplicateNames(sorted));
    }

    /** One jar's descriptor, hash and loaded state; the unit of work a scan fans out. */
    private ScanEntry describeAndHash(Path jar, Set<String> loadedNames) throws IOException {
        long size = Files.size(jar);
        long modified = Files.getLastModifiedTime(jar).toMillis();
        JarDescriptor descriptor = readDescriptor(jar, size, modified);
        JarFingerprint fingerprint;
        try {
            fingerprint = JarFingerprint.of(size, modified, sha256(jar));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is mandated by the platform; treat its absence as unreadable
            // rather than propagating a checked exception nobody can act on.
            throw new IOException("SHA-256 不可用", impossible);
        }
        boolean loaded = descriptor.hasName()
            && loadedNames.contains(descriptor.name().toLowerCase(Locale.ROOT));
        return new ScanEntry(jar, descriptor, fingerprint, loaded);
    }

    /**
     * Fingerprints every jar, reusing cached hashes.
     *
     * A jar whose size and modification time are unchanged since {@code previous}
     * keeps its stored hash and is never read. In the steady state this turns a
     * check from hashing the whole folder into a directory listing — see
     * {@link JarFingerprint} for the cost this avoids and the one case it misses.
     */
    public Map<String, JarFingerprint> fingerprints(Map<String, JarFingerprint> previous) {
        List<Path> jars;
        try {
            jars = listJars();
        } catch (IOException unreadable) {
            return Map.of();
        }
        Map<String, JarFingerprint> current = new HashMap<>(jars.size() * 2);
        for (Path jar : jars) {
            String name = jar.getFileName().toString();
            long size;
            long modified;
            try {
                size = Files.size(jar);
                modified = Files.getLastModifiedTime(jar).toMillis();
            } catch (IOException vanished) {
                // Deleted between listing and stat: leave it out, and the caller
                // reports it as removed on this tick.
                continue;
            }
            JarFingerprint cached = previous.get(name);
            if (cached != null && cached.matches(size, modified) && !cached.unreadable()) {
                current.put(name, cached);
                continue;
            }
            try {
                current.put(name, JarFingerprint.of(size, modified, sha256(jar)));
            } catch (Exception midWrite) {
                // Kept in the map so the jar still counts as present; auto-load
                // skips unreadable hashes and retries on the next check.
                current.put(name, JarFingerprint.unreadable(size, modified, midWrite));
            }
        }
        return current;
    }

    // ── 解析 ──────────────────────────────────────────────────────────────

    /**
     * Finds a jar by file name or by the plugin name it declares.
     *
     * Every returned path is canonical and verified to sit inside the plugins
     * folder, so {@code ../../etc/passwd} resolves to an error rather than a file.
     */
    public JarRef resolveJar(@Nullable String query) {
        String needle = query == null ? "" : query.trim();
        if (needle.isEmpty()) {
            return JarRef.error("jar.missing-name", "");
        }
        Path root;
        try {
            root = pluginsFolder.toRealPath();
        } catch (IOException unreadable) {
            return JarRef.error("jar.folder-unreadable", Texts.summarise(unreadable));
        }

        Path direct = insideRoot(root, needle.toLowerCase(Locale.ROOT).endsWith(JAR_SUFFIX)
            ? needle : needle + JAR_SUFFIX);
        if (direct != null) {
            return JarRef.found(direct);
        }

        List<Path> matches = new ArrayList<>();
        try {
            for (Path jar : listJars()) {
                if (jar.getFileName().toString().equalsIgnoreCase(needle)) {
                    matches.add(jar);
                    continue;
                }
                try {
                    JarDescriptor descriptor = readDescriptor(jar);
                    if (descriptor.hasName() && descriptor.name().equalsIgnoreCase(needle)) {
                        matches.add(jar);
                    }
                } catch (Exception unreadable) {
                    // A jar we cannot read cannot be the one asked for by plugin
                    // name; the scan command is where unreadable jars get reported.
                }
            }
        } catch (IOException unreadable) {
            return JarRef.error("jar.folder-unreadable", Texts.summarise(unreadable));
        }

        if (matches.isEmpty()) {
            return JarRef.error("jar.not-found", needle);
        }
        if (matches.size() > 1) {
            List<String> names = new ArrayList<>();
            for (Path match : matches) {
                names.add(match.getFileName().toString());
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return JarRef.error("jar.ambiguous", String.join(", ", names));
        }
        return JarRef.found(matches.get(0));
    }

    /**
     * @return the resolved jar when {@code candidate} names a real file inside
     *         {@code root}, or {@code null} otherwise — including when the name
     *         resolves outside the folder
     */
    private @Nullable Path insideRoot(Path root, String candidate) {
        try {
            Path resolved = pluginsFolder.resolve(candidate).normalize();
            if (!Files.isRegularFile(resolved)) {
                return null;
            }
            Path real = resolved.toRealPath();
            return real.startsWith(root) ? real : null;
        } catch (IOException | java.nio.file.InvalidPathException unusable) {
            return null;
        }
    }

    // ── 描述檔 ────────────────────────────────────────────────────────────

    /**
     * Reads a jar's descriptor, preferring {@code paper-plugin.yml}.
     *
     * Preference order matters and is the server's: a jar shipping both is a Paper
     * plugin, and describing it from its legacy {@code plugin.yml} would report a
     * main class the server never loads.
     *
     * <p>The Paper descriptor is parsed with the server's own YAML reader rather
     * than the line-by-line parser the previous version hand-rolled, which mangled
     * any value containing a {@code #} and could not see a quoted name.
     *
     * @throws IOException when the jar cannot be opened or has no descriptor
     */
    public JarDescriptor readDescriptor(Path jar) throws IOException {
        return readDescriptor(jar, Files.size(jar), Files.getLastModifiedTime(jar).toMillis());
    }

    /**
     * Reads a descriptor, serving a memoised one when the jar has not changed.
     *
     * The size and timestamp are taken as parameters rather than read here because every
     * caller already has them from the directory listing, and stat-ing the file again
     * would give the cache a cost it exists to avoid.
     *
     * @throws IOException when the jar cannot be opened or has no descriptor
     */
    public JarDescriptor readDescriptor(Path jar, long size, long lastModified) throws IOException {
        JarDescriptor cached = descriptors.get(jar, size, lastModified);
        if (cached != null) {
            return cached;
        }
        JarDescriptor parsed = parseDescriptor(jar);
        descriptors.put(jar, size, lastModified, parsed);
        return parsed;
    }

    private JarDescriptor parseDescriptor(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            byte[] paper = entryBytes(file, JarDescriptor.Source.PAPER.fileName());
            if (paper != null) {
                return parsePaper(paper);
            }
            byte[] bukkit = entryBytes(file, JarDescriptor.Source.BUKKIT.fileName());
            if (bukkit != null) {
                return parseBukkit(bukkit);
            }
            throw new IOException("缺少 plugin.yml 或 paper-plugin.yml");
        }
    }

    private static @Nullable byte[] entryBytes(JarFile file, String name) throws IOException {
        JarEntry entry = file.getJarEntry(name);
        if (entry == null) {
            return null;
        }
        try (InputStream stream = file.getInputStream(entry)) {
            return stream.readAllBytes();
        }
    }

    private static JarDescriptor parseBukkit(byte[] raw) throws IOException {
        try {
            PluginDescriptionFile parsed = new PluginDescriptionFile(new ByteArrayInputStream(raw));
            return new JarDescriptor(parsed.getName(), parsed.getVersion(), parsed.getMain(),
                parsed.getAPIVersion(), parsed.isFoliaSupported(), JarDescriptor.Source.BUKKIT);
        } catch (Exception malformed) {
            throw new IOException("plugin.yml 無法解析：" + Texts.summarise(malformed), malformed);
        }
    }

    private static JarDescriptor parsePaper(byte[] raw) {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8)) {
            YamlConfiguration parsed = YamlConfiguration.loadConfiguration(reader);
            return new JarDescriptor(
                parsed.getString("name"),
                parsed.getString("version"),
                parsed.getString("main"),
                parsed.getString("api-version"),
                parsed.getBoolean("folia-supported", false),
                JarDescriptor.Source.PAPER);
        } catch (IOException | RuntimeException malformed) {
            // A descriptor that will not parse still identifies itself as a Paper
            // plugin; the scan report shows it with unknown fields rather than
            // dropping the jar from the listing entirely.
            return new JarDescriptor(null, null, null, null, false, JarDescriptor.Source.PAPER);
        }
    }

    // ── 內部 ──────────────────────────────────────────────────────────────

    private List<Path> listJars() throws IOException {
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsFolder,
            entry -> Files.isRegularFile(entry)
                && entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(JAR_SUFFIX))) {
            for (Path jar : stream) {
                jars.add(jar);
            }
        }
        return jars;
    }

    /**
     * Hashes a jar.
     *
     * {@link DigestInputStream} rather than a read-and-update loop, and
     * {@link HexFormat} rather than {@code String.format("%02x")} per byte — the
     * latter allocated a formatter and a string for every one of 32 bytes, on every
     * jar, on every check.
     */
    static String sha256(Path jar) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (DigestInputStream stream = new DigestInputStream(Files.newInputStream(jar), digest)) {
            while (stream.read(buffer) >= 0) {
                // DigestInputStream updates the digest as it reads.
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Map<String, List<String>> duplicateNames(List<ScanEntry> entries) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (ScanEntry entry : entries) {
            if (!entry.descriptor().hasName()) {
                continue;
            }
            byName.computeIfAbsent(entry.descriptor().name().toLowerCase(Locale.ROOT),
                ignored -> new ArrayList<>()).add(entry.fileName());
        }
        byName.entrySet().removeIf(entry -> entry.getValue().size() < 2);
        return byName;
    }
}
