package tw.linsy.aelornholograms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class HologramStore {

    private final AelornHologramsPlugin plugin;
    private final File hologramsFolder;

    public HologramStore(AelornHologramsPlugin plugin) {
        this.plugin = plugin;
        this.hologramsFolder = new File(plugin.getDataFolder(), "holograms");
    }

    public List<Hologram> loadAll() {
        List<Hologram> loaded = new ArrayList<>();
        if (!hologramsFolder.exists()) {
            hologramsFolder.mkdirs();
            return loaded;
        }
        File[] files = hologramsFolder.listFiles((folder, fileName) -> fileName.endsWith(".yml"));
        if (files == null) {
            return loaded;
        }
        for (File file : files) {
            Hologram hologram = load(file);
            if (hologram != null) {
                loaded.add(hologram);
            }
        }
        return loaded;
    }

    public Hologram load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String name = config.getString("name", file.getName().replaceFirst("\\.yml$", ""));
        Location location = readLocation(config, "location");
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Skipped hologram with invalid location: " + file.getName());
            return null;
        }
        List<String> lines = config.getStringList("lines");
        if (lines.isEmpty()) {
            lines = readDecentLines(config);
        }
        if (lines.isEmpty()) {
            lines = List.of(plugin.settings().defaultText());
        }
        DisplaySettings settings = plugin.settings();
        return new Hologram(name, location, lines,
            config.getBoolean("enabled", true),
            config.getString("permission", ""),
            config.getInt("display-range", settings.displayRange()),
            config.getInt("update-range", settings.updateRange()),
            config.getInt("update-interval", settings.updateInterval()),
            config.getDouble("line-height", settings.lineHeight()));
    }

    public void save(Hologram hologram) {
        hologramsFolder.mkdirs();
        File file = new File(hologramsFolder, safeName(hologram.name()) + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", hologram.name());
        writeLocation(config, "location", hologram.location());
        config.set("enabled", hologram.enabled());
        config.set("permission", hologram.permission());
        config.set("display-range", hologram.displayRange());
        config.set("update-range", hologram.updateRange());
        config.set("update-interval", hologram.updateInterval());
        config.set("line-height", hologram.lineHeight());
        synchronized (hologram) {
            config.set("lines", List.copyOf(hologram.lines()));
        }
        try {
            config.save(file);
        } catch (IOException saveFailure) {
            plugin.getLogger().severe("Could not save hologram " + hologram.name() + ": " + saveFailure.getMessage());
        }
    }

    public boolean delete(Hologram hologram) {
        File file = new File(hologramsFolder, safeName(hologram.name()) + ".yml");
        return !file.exists() || file.delete();
    }

    public int importDecentHolograms(boolean overwrite) {
        File decentFolder = new File(plugin.getServer().getWorldContainer(), "plugins/DecentHolograms/holograms");
        if (!decentFolder.isDirectory()) {
            return 0;
        }
        File[] files = decentFolder.listFiles((folder, fileName) -> fileName.endsWith(".yml"));
        if (files == null) {
            return 0;
        }
        int imported = 0;
        for (File file : files) {
            Hologram hologram = loadDecent(file);
            if (hologram == null) {
                continue;
            }
            File target = new File(hologramsFolder, safeName(hologram.name()) + ".yml");
            if (!target.exists() || overwrite) {
                save(hologram);
                imported++;
            }
        }
        return imported;
    }

    private Hologram loadDecent(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String name = config.getString("name", file.getName().replaceFirst("\\.yml$", ""));
        Location location = readLocation(config, "location");
        if (location == null || location.getWorld() == null) {
            return null;
        }
        List<String> lines = readDecentLines(config);
        if (lines.isEmpty()) {
            lines = config.getStringList("lines");
        }
        DisplaySettings settings = plugin.settings();
        return new Hologram(name, location,
            lines.isEmpty() ? List.of(settings.defaultText()) : lines,
            config.getBoolean("enabled", true),
            config.getString("permission", ""),
            config.getInt("display-range", settings.displayRange()),
            config.getInt("update-range", settings.updateRange()),
            config.getInt("update-interval", settings.updateInterval()),
            config.getDouble("line-height", settings.lineHeight()));
    }

    private static List<String> readDecentLines(YamlConfiguration config) {
        List<String> lines = new ArrayList<>();
        Object pages = config.get("pages");
        if (pages instanceof List<?> pageList && !pageList.isEmpty()) {
            Object firstPage = pageList.get(0);
            if (firstPage instanceof ConfigurationSection section) {
                collectLineObjects(lines, section.getList("lines"));
            } else if (firstPage instanceof Map<?, ?> map) {
                collectLineObjects(lines, map.get("lines"));
            }
        }
        if (lines.isEmpty()) {
            collectLineObjects(lines, config.getList("pages.0.lines"));
        }
        if (lines.isEmpty()) {
            collectLineObjects(lines, config.getList("page.0.lines"));
        }
        return lines;
    }

    private static void collectLineObjects(List<String> target, Object rawLines) {
        if (!(rawLines instanceof List<?> lineList)) {
            return;
        }
        for (Object element : lineList) {
            if (element instanceof String line) {
                target.add(line);
            } else if (element instanceof Map<?, ?> map) {
                Object content = map.get("content");
                if (content != null) {
                    target.add(String.valueOf(content));
                }
            } else if (element instanceof ConfigurationSection section) {
                target.add(section.getString("content", ""));
            }
        }
    }

    private static Location readLocation(YamlConfiguration config, String path) {
        Object value = config.get(path);
        if (value instanceof Location location) {
            return location;
        }
        if (value instanceof String locationString) {
            return parseLocationString(locationString);
        }
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world,
            section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
            (float) section.getDouble("yaw", 0.0D), (float) section.getDouble("pitch", 0.0D));
    }

    private static Location parseLocationString(String raw) {
        String[] parts = raw.split(":");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0.0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0.0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static void writeLocation(YamlConfiguration config, String path, Location location) {
        config.set(path + ".world", location.getWorld() == null ? "world" : location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
