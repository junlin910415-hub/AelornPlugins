package tw.linsy.aelorn.plugins.model;

import java.nio.file.Path;

/**
 * One jar in the plugins folder as the scanner found it.
 *
 * @param path        the jar, always inside the plugins folder
 * @param descriptor  what the jar claims about itself
 * @param fingerprint size, timestamp and hash; may report itself
 *                    {@linkplain JarFingerprint#unreadable() unreadable} while
 *                    the file is still being written
 * @param loaded      whether a plugin by that name is currently registered
 */
public record ScanEntry(Path path, JarDescriptor descriptor, JarFingerprint fingerprint, boolean loaded) {

    public String fileName() {
        return path.getFileName().toString();
    }
}
