package tw.linsy.aelorn.plugins.nms.impl.v26_2;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.entrypoint.dependency.MetaDependencyTree;
import io.papermc.paper.plugin.manager.PaperPluginManagerImpl;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.Plugin;
import tw.linsy.aelorn.plugins.nms.InternalsFailure;
import tw.linsy.aelorn.plugins.nms.ServerInternals;

/**
 * Server internals for the 26.2 family, including every 26.2.X patch release, on
 * both LightingLuminol (Folia-family) and Purpur (Paper-family).
 *
 * This is the only class in the plugin that imports {@code net.minecraft},
 * {@code org.bukkit.craftbukkit} or Paper's plugin manager. The 26.2 servers ship
 * Mojang-mapped with unversioned CraftBukkit packages, so it compiles straight
 * against the server jar — no mappings, no remap step.
 *
 * <p><b>Adding 26.3:</b> copy this class to {@code impl.v26_3.Internals26_3}, add
 * the version's server jar to {@code build-all.ps1}'s adapter table for this
 * project, add the family to {@code ServerInternalsLoader}, and fix whatever no
 * longer compiles. Nothing outside this package changes — the compiler proves it,
 * because the version-free tree is built with no server core on the classpath.
 *
 * <h2>Why there is still reflection in here</h2>
 * Three of Paper's plugin-registry members are package-private or private, and
 * this adapter lives in a different package. Every handle is resolved <em>once</em>
 * in the constructor and cached, so a call costs a field read rather than a
 * {@code getDeclaredField} walk — the previous implementation re-resolved its
 * handles on every single call, inside a loop over the plugin list.
 *
 * <p>Resolution failing is a constructor failure, which the loader turns into
 * "internals unavailable" with the reason. That is the point of doing it eagerly:
 * an incompatible server is reported at enable, not discovered by an admin
 * halfway through an unload.
 */
public final class Internals26_2 implements ServerInternals {

    private static final String FAMILY = "26.2";

    /** {@code PaperPluginManagerImpl.instanceManager} — package-private. */
    private final Field instanceManagerField;
    /** {@code PaperPluginInstanceManager.plugins} — private; the real plugin list. */
    private final Field pluginsField;
    /** {@code PaperPluginInstanceManager.lookupNames} — private; the real name index. */
    private final Field lookupNamesField;
    /** {@code PaperPluginInstanceManager.dependencyTree} — private. */
    private final Field dependencyTreeField;
    /** {@code PaperPluginInstanceManager.isTransitiveDepend} — public on a package-private class. */
    private final Method isTransitiveDependMethod;

    /**
     * Instantiated reflectively by the loader; must stay public and no-arg.
     *
     * @throws InternalsFailure when Paper's registry does not have the expected
     *                          shape, so the loader can degrade with a reason
     */
    public Internals26_2() {
        try {
            this.instanceManagerField = PaperPluginManagerImpl.class.getDeclaredField("instanceManager");
            this.instanceManagerField.setAccessible(true);

            Class<?> instanceManagerType = instanceManagerField.getType();
            this.pluginsField = declaredField(instanceManagerType, "plugins");
            this.lookupNamesField = declaredField(instanceManagerType, "lookupNames");
            this.dependencyTreeField = declaredField(instanceManagerType, "dependencyTree");
            this.isTransitiveDependMethod = instanceManagerType
                .getDeclaredMethod("isTransitiveDepend", PluginMeta.class, PluginMeta.class);
            this.isTransitiveDependMethod.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException unexpectedShape) {
            throw new InternalsFailure(
                "Paper 插件註冊表的結構與預期不符，無法建立 26.2 介接層。", unexpectedShape);
        }
    }

    @Override
    public String targetFamily() {
        return FAMILY;
    }

    // ── 執行緒 ────────────────────────────────────────────────────────────
    // 分區伺服器把世界切成區域執行緒；在不擁有該區域的執行緒上動伺服器狀態不會拋例外,
    // 而是直接損壞狀態。Moonrise 的檢查在兩個 fork 上都在,單執行緒核心會回答「是否主執行緒」。

    @Override
    public boolean isTickThread() {
        return TickThread.isTickThread();
    }

    // ── 插件註冊表 ────────────────────────────────────────────────────────

    @Override
    public void deregisterPlugin(Plugin plugin) {
        Object instanceManager = instanceManager();
        PluginMeta meta = plugin.getPluginMeta();
        try {
            removeFromPluginList(instanceManager, plugin);
            removeFromLookupNames(instanceManager, plugin, meta);
            removeFromDependencyTree(instanceManager, meta);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new InternalsFailure(
                "無法從 Paper 插件註冊表移除 " + plugin.getName() + "。", failure);
        }
    }

    private void removeFromPluginList(Object instanceManager, Plugin plugin)
            throws ReflectiveOperationException {
        Object value = pluginsField.get(instanceManager);
        if (!(value instanceof List<?> plugins)) {
            throw new InternalsFailure("Paper 的 plugins 欄位不是 List，實際為 "
                + (value == null ? "null" : value.getClass().getName()));
        }
        plugins.remove(plugin);
    }

    /**
     * Drops every key pointing at the plugin.
     *
     * By identity first, then by declared name and each {@code provides} alias,
     * because a plugin is indexed under all of them and leaving one behind makes
     * the server report it as still loaded.
     */
    private void removeFromLookupNames(Object instanceManager, Plugin plugin, PluginMeta meta)
            throws ReflectiveOperationException {
        Object value = lookupNamesField.get(instanceManager);
        if (!(value instanceof Map<?, ?> lookupNames)) {
            throw new InternalsFailure("Paper 的 lookupNames 欄位不是 Map，實際為 "
                + (value == null ? "null" : value.getClass().getName()));
        }
        lookupNames.entrySet().removeIf(entry -> {
            if (entry.getValue() == plugin) {
                return true;
            }
            String key = String.valueOf(entry.getKey());
            if (key.equalsIgnoreCase(meta.getName())) {
                return true;
            }
            for (String provided : meta.getProvidedPlugins()) {
                if (key.equalsIgnoreCase(provided)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Removes the plugin's node from the dependency graph.
     *
     * Skipped by the previous implementation entirely, which left a stale node
     * behind: loading the same plugin again then re-added an edge to a name the
     * graph already knew, and Paper's dependency resolution saw a plugin that was
     * both present and absent.
     */
    private void removeFromDependencyTree(Object instanceManager, PluginMeta meta)
            throws ReflectiveOperationException {
        Object value = dependencyTreeField.get(instanceManager);
        if (value instanceof MetaDependencyTree tree) {
            tree.remove(meta);
        }
        // A shape change here is not worth failing the unload over: a stale graph
        // node degrades a later load, while a half-finished unload breaks now.
    }

    // ── 指令樹 ────────────────────────────────────────────────────────────

    @Override
    public void syncCommandTree() {
        try {
            ((CraftServer) Bukkit.getServer()).syncCommands();
        } catch (RuntimeException failure) {
            // ClassCastException 本來就是 RuntimeException，分開列會編不過；
            // 這個 catch 已經涵蓋了「伺服器不是 CraftServer」那條路徑。
            throw new InternalsFailure("無法重建並重送指令樹。", failure);
        }
    }

    // ── 相依關係 ──────────────────────────────────────────────────────────

    @Override
    public boolean dependsOn(Plugin dependant, Plugin dependency) {
        try {
            Object answer = isTransitiveDependMethod.invoke(instanceManager(),
                dependant.getPluginMeta(), dependency.getPluginMeta());
            return Boolean.TRUE.equals(answer);
        } catch (InvocationTargetException thrownByServer) {
            throw new InternalsFailure("查詢相依關係時伺服器拋出例外。", thrownByServer.getCause());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new InternalsFailure("無法查詢 " + dependant.getName()
                + " 是否相依於 " + dependency.getName() + "。", failure);
        }
    }

    @Override
    public String describe() {
        return FAMILY + " (" + Bukkit.getName().toLowerCase(Locale.ROOT) + ")";
    }

    // ── 內部 ──────────────────────────────────────────────────────────────

    /**
     * Paper's real plugin manager, reached through {@link CraftServer}'s public
     * field rather than {@code PaperPluginManagerImpl.getInstance()} — the field
     * is the instance the server actually uses, and cannot be null while a plugin
     * of ours is running.
     */
    private Object instanceManager() {
        try {
            PaperPluginManagerImpl manager = ((CraftServer) Bukkit.getServer()).paperPluginManager;
            Object instanceManager = instanceManagerField.get(manager);
            if (instanceManager == null) {
                throw new InternalsFailure("Paper 的 instanceManager 為 null。");
            }
            return instanceManager;
        } catch (ClassCastException | ReflectiveOperationException failure) {
            throw new InternalsFailure("無法取得 Paper 的插件註冊表。", failure);
        }
    }

    private static Field declaredField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
