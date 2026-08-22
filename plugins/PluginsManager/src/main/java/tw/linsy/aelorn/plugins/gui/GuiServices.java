package tw.linsy.aelorn.plugins.gui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;
import tw.linsy.aelorn.plugins.platform.Sched;
import tw.linsy.aelorn.plugins.service.PluginLifecycleService;
import tw.linsy.aelorn.plugins.service.PluginLookup;
import tw.linsy.aelorn.plugins.service.PluginUnloadService;
import tw.linsy.aelorn.plugins.service.ProtectionService;
import tw.linsy.aelorn.plugins.service.VersionArchiveService;

/**
 * Builds the menu service, or an explaining stub.
 *
 * <p>The presence probe is on {@code core.ui.Menu} rather than on the AelornLib plugin
 * as a whole: an older core can be installed and enabled while having no menu
 * framework at all, and "the core is here" is not the same question as "the menus are".
 * The probe answers the one that matters.
 *
 * <p>{@link CoreGui} is reached only through its static factory inside the guarded
 * branch, and any failure — missing classes, a linkage error from a core built against
 * a different API — becomes the stub rather than a broken {@code onEnable}.
 */
public final class GuiServices {

    private static final String MENU_MARKER = "tw.linsy.aelorn.lib.ui.Menu";
    private static final String ICONS_FILE = "gui.yml";

    private GuiServices() {
    }

    public static GuiService open(Plugin owner, MessageCatalog messages, Sched sched,
                                  PluginLookup lookup, ProtectionService protection,
                                  PluginLifecycleService lifecycle, PluginUnloadService unload,
                                  VersionArchiveService archives) {
        if (!classPresent(MENU_MARKER)) {
            owner.getLogger().info("AelornLib 沒有提供選單框架，GUI 停用（指令不受影響）。");
            return new GuiService.Unavailable("gui.unavailable-no-core");
        }
        try {
            GuiIcons icons = new GuiIcons(loadIcons(owner), owner.getLogger());
            GuiService gui = CoreGui.create(owner, messages, sched, icons, lookup, protection,
                lifecycle, unload, archives);
            owner.getLogger().info("GUI 已就緒（AelornLib 選單框架）。");
            return gui;
        } catch (Throwable unavailable) {
            owner.getLogger().warning("無法建立 GUI（" + unavailable.getClass().getSimpleName()
                + (unavailable.getMessage() == null ? "" : ": " + unavailable.getMessage())
                + "），指令不受影響。");
            return new GuiService.Unavailable("gui.unavailable-failed");
        }
    }

    /**
     * Reads {@code gui.yml}, installing the bundled copy and back-filling new keys.
     *
     * <p>Same shape as every other config file here, and for the same reason: an icon
     * added in a later version should appear in the admin's file where they can find
     * it, not only as an in-memory default.
     */
    private static YamlConfiguration loadIcons(Plugin owner) {
        File target = new File(owner.getDataFolder(), ICONS_FILE);
        if (!target.isFile() && owner.getResource(ICONS_FILE) != null) {
            owner.saveResource(ICONS_FILE, false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target);
        InputStream bundled = owner.getResource(ICONS_FILE);
        if (bundled == null) {
            return loaded;
        }
        try (Reader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(reader));
            loaded.options().copyDefaults(true);
            loaded.save(target);
        } catch (IOException unwritable) {
            owner.getLogger().log(Level.WARNING,
                "無法補齊 " + ICONS_FILE + "，將僅使用內建圖示設定。", unwritable);
        }
        return loaded;
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, GuiServices.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }
}
