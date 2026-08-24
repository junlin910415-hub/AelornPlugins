package tw.linsy.aelorn.rpgcore.hud;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class HudAssetPackBuilderTest {
    private HudAssetPackBuilderTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the bundled RPGCore HUD pack path");
        }
        byte[] source = Files.readAllBytes(Path.of(arguments[0]));

        byte[] sharedFirst = HudAssetPackBuilder.build(new ByteArrayInputStream(source), false);
        byte[] sharedSecond = HudAssetPackBuilder.build(new ByteArrayInputStream(source), false);
        require(Arrays.equals(sharedFirst, sharedSecond), "shared pack output is not deterministic");
        Map<String, byte[]> shared = entries(sharedFirst);
        require(shared.keySet().equals(HudAssetPackBuilder.sharedEntries()),
                "shared pack is not the exact 16-entry allowlist: " + shared.keySet());
        require(shared.size() == 16, "shared pack entry count is not 16");
        require(shared.keySet().stream().noneMatch(name -> name.startsWith("assets/minecraft/")
                        || name.startsWith("rpgcore_hud_26_1/")
                        || name.contains("hud_rpgcore_status")),
                "shared pack leaked a vanilla HUD or internal renderer asset");

        try {
            HudAssetPackBuilder.build(new ByteArrayInputStream(source), true);
            throw new AssertionError("unsupported INTERNAL pack build was accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains("unsupported"),
                    "unexpected INTERNAL fail-closed result: " + expected);
        }

        try {
            HudAssetPackBuilder.build(new ByteArrayInputStream(unsafeArchive()), false);
            throw new AssertionError("unsafe ZIP entry was accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains("Unsafe"), "unexpected unsafe-path failure: " + expected);
        }

        System.out.println("HudAssetPackBuilderTest PASS (shared=16, INTERNAL fail-closed, deterministic and path-safe)");
    }

    private static Map<String, byte[]> entries(byte[] archive) throws IOException {
        TreeMap<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entries.putIfAbsent(entry.getName(), zip.readAllBytes()) != null) {
                    throw new IOException("duplicate test archive entry: " + entry.getName());
                }
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static byte[] unsafeArchive() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("../escape.txt"));
            zip.write(new byte[]{1});
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
