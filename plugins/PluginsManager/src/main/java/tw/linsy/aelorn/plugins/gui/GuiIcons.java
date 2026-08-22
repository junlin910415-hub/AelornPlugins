package tw.linsy.aelorn.plugins.gui;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * Which material stands for which plugin state, read from {@code gui.yml}.
 *
 * <p>Materials are configuration for the same reason messages are: a server with a
 * resource pack, or an admin who simply dislikes lime dye, should not need a rebuild
 * to change an icon. CONVENTIONS.md §4 names material thresholds explicitly.
 *
 * <p>An unrecognised material name falls back rather than throwing. A menu that
 * refuses to open because of a typo in a cosmetic setting is a worse failure than a
 * menu with one wrong-looking button, and the log line names the key.
 */
public final class GuiIcons {

    private final ConfigurationSection section;
    private final java.util.logging.Logger logger;

    public GuiIcons(ConfigurationSection section, java.util.logging.Logger logger) {
        this.section = section;
        this.logger = logger;
    }

    /**
     * The material configured at {@code key}.
     *
     * @param fallback used when the key is absent or names no known material
     */
    public Material material(String key, Material fallback) {
        String configured = section.getString(key);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        Material found = Material.matchMaterial(configured.trim().toUpperCase(Locale.ROOT));
        if (found == null) {
            logger.warning("gui.yml 的 " + key + " 不是有效材質（" + configured
                + "），改用 " + fallback + "。");
            return fallback;
        }
        return found;
    }

    /**
     * An icon with a name and lore.
     *
     * <p>Italics are switched off explicitly. Minecraft renders every custom item name
     * italic by default, which reads as emphasis nobody asked for and makes a menu look
     * like it is shouting.
     */
    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
            .map(line -> line.decoration(TextDecoration.ITALIC, false))
            .toList());
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack icon(Material material, Component name) {
        return icon(material, name, List.of());
    }

    /** A blank pane for borders and padding. */
    public static ItemStack filler(Material material) {
        return icon(material, Component.empty());
    }

    /** @return the nested section, or an empty one so callers never null-check */
    public static ConfigurationSection sectionOrEmpty(@Nullable ConfigurationSection parent,
                                                      String path) {
        if (parent == null) {
            return new org.bukkit.configuration.MemoryConfiguration();
        }
        ConfigurationSection child = parent.getConfigurationSection(path);
        return child != null ? child : new org.bukkit.configuration.MemoryConfiguration();
    }
}
