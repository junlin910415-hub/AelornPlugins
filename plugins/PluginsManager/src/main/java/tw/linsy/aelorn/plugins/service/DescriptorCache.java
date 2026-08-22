package tw.linsy.aelorn.plugins.service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.model.JarDescriptor;

/**
 * Parsed jar descriptors, kept in memory and keyed on the jar's identity.
 *
 * <h2>What this saves</h2>
 * Resolving a plugin by name opens and parses <em>every</em> jar in the folder to find
 * the one that declares it. With thirty-six plugins that is thirty-six zip opens and
 * thirty-six YAML parses, and it happens on every {@code load}, {@code archive},
 * {@code versions} and {@code restore} — the previous version did exactly that, every
 * time, with no memoisation at all.
 *
 * <h2>Why the key includes size and timestamp</h2>
 * Caching on the file name alone would serve a stale descriptor after a jar is
 * replaced, which is the one moment this plugin exists for. Size plus modification
 * time is the same cheap identity {@link tw.linsy.aelorn.plugins.model.JarFingerprint}
 * uses, and it comes free from the directory listing the caller already did.
 *
 * <h2>On Caffeine</h2>
 * This is the contract a Caffeine cache would provide — bounded, LRU, and safe for
 * concurrent access — implemented against the JDK because Caffeine is not present in
 * this server's offline library tree, and the build resolves nothing from the network.
 * If Caffeine is added, only this class changes: an access-ordered
 * {@link LinkedHashMap} becomes {@code Caffeine.newBuilder().maximumSize(...)}, and the
 * two methods below keep their signatures.
 *
 * <p>The bound matters more than the eviction policy here: entries are a few hundred
 * bytes and the population is "jars in one folder", so this is a leak guard rather than
 * a hit-rate optimisation.
 */
final class DescriptorCache {

    /** Comfortably above any real plugins folder, low enough to bound the heap. */
    private static final int MAX_ENTRIES = 512;

    /** Jar identity: name plus the attributes that change when it is replaced. */
    private record Key(String fileName, long size, long lastModified) {
    }

    private final Map<Key, JarDescriptor> entries;

    DescriptorCache() {
        // Access-ordered so removeEldestEntry evicts least-recently-used, not
        // least-recently-inserted; the difference matters when one jar is looked up
        // repeatedly while others are only scanned.
        this.entries = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, JarDescriptor> eldest) {
                return size() > MAX_ENTRIES;
            }
        };
    }

    /**
     * @return the cached descriptor for this exact jar state, or {@code null}
     */
    synchronized @Nullable JarDescriptor get(Path jar, long size, long lastModified) {
        return entries.get(new Key(jar.getFileName().toString(), size, lastModified));
    }

    synchronized void put(Path jar, long size, long lastModified, JarDescriptor descriptor) {
        entries.put(new Key(jar.getFileName().toString(), size, lastModified), descriptor);
    }

    synchronized int size() {
        return entries.size();
    }

    /** Called on a settings reload, so a manual edit inside a jar is not held forever. */
    synchronized void clear() {
        entries.clear();
    }
}
