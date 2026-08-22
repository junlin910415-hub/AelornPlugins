package tw.linsy.aelorn.kt;

import org.bukkit.plugin.java.JavaPlugin;
import tw.linsy.aelorn.lib.AelornLib;

/**
 * Carries the Kotlin extension surface, and the Kotlin runtime it needs.
 *
 * <h2>Why this is a separate plugin rather than part of AelornLib</h2>
 * The extensions are Kotlin, so they need {@code kotlin-stdlib} at runtime. Putting
 * them in the core would make every server — including every server whose plugins are
 * all Java — depend on resolving that library at startup, and a failed resolve takes
 * the whole Aelorn stack down. The core has exactly two runtime libraries and each one
 * is something it genuinely imports; adding a third for a feature most servers will
 * not use is the wrong trade.
 *
 * <p>So Kotlin support is opt-in at the deployment level: drop this jar in, and Kotlin
 * plugins declare {@code depend: [AelornLibKt]}. Java plugins never load it and never
 * pay for it.
 *
 * <h2>Why the entry point is Java</h2>
 * Nothing here needs Kotlin, and keeping it Java means the plugin still loads far
 * enough to log a clear message if the Kotlin runtime is the thing that is missing —
 * a Kotlin main class would fail at class-load with a {@link NoClassDefFoundError}
 * naming an internal stdlib class, which tells an admin nothing.
 */
public final class AelornLibKtPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        AelornLib core = AelornLib.get();
        if (core == null) {
            // depend: [AelornLib] means this should be impossible, so if it happens
            // the useful thing is to say so rather than NPE in an extension later.
            getLogger().severe("AelornLib 未載入，Kotlin 擴充面不可用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        String kotlin = kotlinVersion();
        if (kotlin == null) {
            getLogger().severe("Kotlin 執行期不可用，這個外掛沒有作用。"
                + "請確認伺服器第一次啟動時能連上網路下載 kotlin-stdlib。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Kotlin 擴充面就緒（stdlib " + kotlin + "、核心 " + core.version() + "）。"
            + "以 Kotlin 開發的插件請宣告 depend: [AelornLibKt]。");
    }

    /**
     * Proves the Kotlin runtime is actually loadable, and says which one.
     *
     * <p>Reflective because this class is Java and must not link against the stdlib —
     * that would defeat the point of checking.
     */
    private String kotlinVersion() {
        try {
            Class<?> version = Class.forName("kotlin.KotlinVersion", true, getClassLoader());
            Object current = version.getField("CURRENT").get(null);
            return String.valueOf(current);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
            return null;
        }
    }
}
