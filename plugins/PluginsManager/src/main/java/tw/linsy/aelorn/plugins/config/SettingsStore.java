package tw.linsy.aelorn.plugins.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads the four YAML files and holds the parsed snapshots.
 *
 * One volatile reference per snapshot, replaced wholesale on reload. Readers never
 * see a half-applied reload: they either get the old settings or the new ones, not
 * a mix, which is what the previous version's live {@code getConfig()} calls could
 * produce mid-operation.
 *
 * <p>Every file gets the jar's copy installed as defaults and written back, so a
 * key added in a later version appears in the admin's file with its default rather
 * than resolving invisibly.
 */
public final class SettingsStore {

    /** Auxiliary files, in the order the validation report lists them. */
    private static final List<String> MANAGED_FILES =
        List.of("messages.yml", "groups.yml", "commands.yml", "version-control.yml");

    private static final String GROUPS = "groups.yml";
    private static final String COMMANDS = "commands.yml";
    private static final String VERSION_CONTROL = "version-control.yml";

    private final JavaPlugin plugin;

    private volatile ManagerSettings manager;
    private volatile GroupCatalog groups;
    private volatile CommandSettings commands;
    private volatile ArchiveSettings archive;

    public SettingsStore(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Re-reads every file. Callers should be on the global region. */
    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        config.options().copyDefaults(true);
        plugin.saveConfig();

        this.manager = ManagerSettings.from(config);
        this.groups = GroupCatalog.from(load(GROUPS));
        this.commands = CommandSettings.from(load(COMMANDS));
        this.archive = ArchiveSettings.from(load(VERSION_CONTROL), plugin.getLogger());
    }

    public ManagerSettings manager() {
        return manager;
    }

    public GroupCatalog groups() {
        return groups;
    }

    public CommandSettings commands() {
        return commands;
    }

    public ArchiveSettings archive() {
        return archive;
    }

    public List<String> managedFileNames() {
        return MANAGED_FILES;
    }

    /**
     * A per-file health line plus warnings for settings that silently disable a
     * feature, which is the case an admin most often reaches for this command to
     * explain ("why does nothing happen when I run it").
     *
     * @return raw markup lines; the caller resolves nothing further
     */
    public List<String> validationReport() {
        List<String> lines = new ArrayList<>();
        for (String name : MANAGED_FILES) {
            File file = new File(plugin.getDataFolder(), name);
            YamlConfiguration loaded = load(name);
            lines.add("&7- &f" + name + " &aOK &8(" + loaded.getKeys(true).size()
                + " keys, " + file.length() + " bytes)");
        }
        FileConfiguration config = plugin.getConfig();
        lines.add("&7- &fconfig.yml &aOK &8(" + config.getKeys(true).size() + " keys)");

        if (groups.groups().isEmpty()) {
            lines.add("&e警告：groups.yml 沒有任何啟用中的群組。");
        }
        if (!commands.enabled()) {
            lines.add("&e提示：commands.yml 已停用指令索引控制。");
        }
        if (!archive.enabled()) {
            lines.add("&e提示：version-control.yml 已停用版本控制。");
        }
        if (!manager.guards().allowUnload()) {
            lines.add("&e提示：config.yml 已停用 unload。");
        }
        if (!manager.scanner().watchEnabled()) {
            lines.add("&e提示：config.yml 已停用 plugins 資料夾監看。");
        }
        return lines;
    }

    /**
     * Reads one auxiliary file, installing and back-filling the bundled copy.
     *
     * Writing the merged file back is what makes new keys visible to an admin
     * editing it, rather than only existing as an in-memory default they cannot
     * discover.
     */
    private YamlConfiguration load(String fileName) {
        File target = new File(plugin.getDataFolder(), fileName);
        if (!target.isFile() && plugin.getResource(fileName) != null) {
            plugin.saveResource(fileName, false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target);
        InputStream bundledStream = plugin.getResource(fileName);
        if (bundledStream == null) {
            return loaded;
        }
        try (Reader reader = new InputStreamReader(bundledStream, StandardCharsets.UTF_8)) {
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(reader);
            loaded.setDefaults(bundled);
            loaded.options().copyDefaults(true);
            loaded.save(target);
        } catch (IOException unwritable) {
            plugin.getLogger().log(Level.WARNING,
                "無法載入或補齊 " + fileName + "，將使用目前讀到的內容。", unwritable);
        }
        return loaded;
    }
}
