package tw.linsy.aelorn.plugins.nms;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the internals adapter for the running server, or degrades.
 *
 * <p>Selection is by version <em>family</em>, so every 26.2.X patch release loads
 * the 26.2 adapter and a patch update needs no change anywhere.
 *
 * <p>Adapters are found by name rather than by {@code ServiceLoader} or a static
 * table: a static reference would load every adapter class, and the ones compiled
 * against other releases cannot resolve their internals. Naming them keeps
 * exactly one on the class loader.
 *
 * <p><b>Degrades instead of failing.</b> A missing or broken adapter yields
 * {@link UnavailableInternals} with a loud warning, not a disabled plugin — see
 * that class for why a plugin manager makes the opposite trade to AelornLib.
 *
 * <p>One adapter serves both supported forks. LightingLuminol (Folia-family) and
 * Purpur (Paper-family) share Mojang-mapped internals, unversioned CraftBukkit
 * packages, Paper's plugin manager and Moonrise's tick-thread checks; the only
 * difference that reaches this plugin is whether the world is split across region
 * threads, and that is a runtime question answered by
 * {@link tw.linsy.aelorn.plugins.platform.PlatformProfile} rather than a
 * compile-time one. A second adapter would be two copies of the same file.
 */
public final class ServerInternalsLoader {

    /** Families with an adapter in this build. Newest first, purely for the log. */
    private static final List<String> KNOWN_FAMILIES = List.of("26_2");

    private static final String IMPL_PACKAGE = "tw.linsy.aelorn.plugins.nms.impl.v";

    private ServerInternalsLoader() {
    }

    /**
     * Never throws. The returned value is either a working adapter or one that
     * reports each internals-backed capability as unavailable.
     */
    public static ServerInternals load(Logger logger) {
        String detected = Bukkit.getMinecraftVersion();
        String family = familyOf(detected);
        String supported = String.join("、", displayFamilies());

        if (family == null) {
            String reason = "無法判讀伺服器版本字串 " + detected;
            logger.warning(reason + "；內部介接層將停用，需要伺服器內部的功能會回報不可用。");
            return new UnavailableInternals(detected, supported, reason);
        }
        if (!KNOWN_FAMILIES.contains(family)) {
            String reason = "沒有對應 " + family.replace('_', '.') + " 的介接層";
            logger.warning(reason + "。本版本支援 " + supported
                + "。查詢與 API 層面的功能照常運作；unload 會被拒絕，"
                + "其餘需要伺服器內部的功能會回報不可用。"
                + "新增支援的方式是加一個 " + IMPL_PACKAGE + family + " 的 adapter 類別。");
            return new UnavailableInternals(detected, supported, reason);
        }

        String className = IMPL_PACKAGE + family + ".Internals" + family;
        try {
            Class<?> type = Class.forName(className, true, ServerInternalsLoader.class.getClassLoader());
            ServerInternals internals = (ServerInternals) type.getDeclaredConstructor().newInstance();
            logger.info("內部介接層：" + internals.targetFamily()
                + "（實際伺服器 " + detected + " / " + Bukkit.getName() + "）");
            return internals;
        } catch (ClassNotFoundException missing) {
            String reason = "JAR 未包含介接層類別 " + className;
            logger.warning(reason + "；建置時可能漏了 adapter 的編譯步驟。");
            return new UnavailableInternals(detected, supported, reason);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError broken) {
            // LinkageError is the interesting one: the adapter compiled against
            // internals this server does not have — the mismatch this class exists
            // to turn into a clear message instead of a crash.
            String reason = className + " 無法初始化：" + broken.getClass().getSimpleName()
                + (broken.getMessage() == null ? "" : " " + broken.getMessage());
            logger.warning(reason + "；通常表示伺服器內部結構與 adapter 編譯時不同。");
            return new UnavailableInternals(detected, supported, reason);
        }
    }

    /** {@code ["26.2"]} — for messages, where underscores read as typos. */
    public static List<String> displayFamilies() {
        return KNOWN_FAMILIES.stream().map(name -> name.replace('_', '.')).toList();
    }

    /**
     * Reads a leading {@code major.minor} out of a version string and joins them
     * with an underscore, ignoring any patch component and any suffix.
     *
     * @return {@code 26_2} for {@code 26.2}, {@code 26.2.1} and
     *         {@code 26.2-pre1}; {@code null} when the text does not start with
     *         two numbers
     */
    static @Nullable String familyOf(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        int index = 0;
        int[] parts = new int[2];
        int found = 0;
        while (found < 2 && index < text.length()) {
            if (!Character.isDigit(text.charAt(index))) {
                break;
            }
            int value = 0;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                value = value * 10 + (text.charAt(index) - '0');
                index++;
            }
            parts[found++] = value;
            if (index < text.length() && text.charAt(index) == '.') {
                index++;
            } else {
                break;
            }
        }
        return found == 2 ? parts[0] + "_" + parts[1] : null;
    }
}
