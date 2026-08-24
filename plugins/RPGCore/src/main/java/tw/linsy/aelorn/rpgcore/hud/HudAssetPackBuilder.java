package tw.linsy.aelorn.rpgcore.hud;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Builds the exact RPGCore asset slice that AeloriaHUD may merge. */
final class HudAssetPackBuilder {
    private static final int MAX_ENTRIES = 2048;
    private static final long MAX_UNCOMPRESSED_BYTES = 32L * 1024L * 1024L;
    private static final long FIXED_ZIP_TIME = 315_532_800_000L;

    /**
     * Only these GUI assets are legal when AeloriaHUD owns the visible HUD.
     * In particular, no vanilla boss-bar, experience-bar, hotbar or text-shader
     * replacement may leak into the merged pack from RPGCore.
     */
    private static final Set<String> SHARED_ENTRIES = Set.of(
            "assets/rpgcore_hud/font/gui.json",
            "assets/rpgcore_hud/font/interface.json",
            "assets/rpgcore_hud/font/space.json",
            "assets/rpgcore_hud/textures/gui/ability_tree.png",
            "assets/rpgcore_hud/textures/gui/character_menu.png",
            "assets/rpgcore_hud/textures/gui/character_profile.png",
            "assets/rpgcore_hud/textures/gui/content_book.png",
            "assets/rpgcore_hud/textures/gui/identification.png",
            "assets/rpgcore_hud/textures/gui/item_browser.png",
            "assets/rpgcore_hud/textures/gui/item_editor.png",
            "assets/rpgcore_hud/textures/gui/main_menu.png",
            "assets/rpgcore_hud/textures/gui/party_menu.png",
            "assets/rpgcore_hud/textures/gui/profession_menu.png",
            "assets/rpgcore_hud/textures/gui/quest_journal.png",
            "assets/rpgcore_hud/textures/gui/skill_crystal.png",
            "assets/rpgcore_hud/textures/interface/key_f.png");

    private HudAssetPackBuilder() {
    }

    static byte[] build(InputStream source, boolean internalRenderer) throws IOException {
        if (internalRenderer) {
            throw new IOException("The legacy INTERNAL HUD pack is unsupported; use the AeloriaHUD asset profile");
        }
        TreeMap<String, byte[]> selected = new TreeMap<>();
        Set<String> names = new HashSet<>();
        long[] uncompressedBytes = {0L};
        int entryCount = 0;

        try (ZipInputStream zip = new ZipInputStream(source, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("Bundled RPGCore HUD pack exceeds " + MAX_ENTRIES + " entries");
                }
                String name = entry.getName();
                requireSafePath(name);
                if (!names.add(name)) {
                    throw new IOException("Duplicate bundled RPGCore HUD entry: " + name);
                }
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }

                byte[] bytes = readBounded(zip, uncompressedBytes);
                if (SHARED_ENTRIES.contains(name)) {
                    selected.put(name, bytes);
                }
                zip.closeEntry();
            }
        }

        Set<String> missing = new HashSet<>(SHARED_ENTRIES);
        missing.removeAll(selected.keySet());
        if (!missing.isEmpty() || selected.size() != SHARED_ENTRIES.size()) {
            throw new IOException("Bundled RPGCore shared asset set drifted; missing=" + missing);
        }
        return writeDeterministic(selected);
    }

    static Set<String> sharedEntries() {
        return SHARED_ENTRIES;
    }

    private static byte[] readBounded(InputStream input, long[] total) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            total[0] += read;
            if (total[0] > MAX_UNCOMPRESSED_BYTES) {
                throw new IOException("Bundled RPGCore HUD pack exceeds the uncompressed size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static byte[] writeDeterministic(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.setLevel(9);
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry output = new ZipEntry(entry.getKey());
                output.setTime(FIXED_ZIP_TIME);
                zip.putNextEntry(output);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void requireSafePath(String name) throws IOException {
        if (name == null || name.isBlank() || name.length() > 512 || name.startsWith("/")
                || name.startsWith("\\") || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
            throw new IOException("Unsafe bundled RPGCore HUD entry: " + name);
        }
        String[] segments = name.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (segment.equals("..") || segment.equals(".")) {
                throw new IOException("Unsafe bundled RPGCore HUD entry: " + name);
            }
            boolean trailingDirectoryMarker = index == segments.length - 1 && name.endsWith("/");
            if (segment.isEmpty() && !trailingDirectoryMarker) {
                throw new IOException("Unsafe bundled RPGCore HUD entry: " + name);
            }
        }
    }
}
