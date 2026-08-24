package tw.linsy.aelorn.rpgcore.item;

import tw.linsy.aelorn.rpgcore.integration.mmoitems.MmoItemsBridge;
import java.util.Locale;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class CustomItemMarker {
    private final MmoItemsBridge mmoItems;
    private final Set<String> allowedNamespaces;
    private final NamespacedKey customItemKey;
    private final NamespacedKey categoryKey;
    private final NamespacedKey contentIdKey;

    public CustomItemMarker(Plugin plugin, MmoItemsBridge mmoItems, Set<String> allowedNamespaces) {
        this.mmoItems = mmoItems;
        this.allowedNamespaces = Set.copyOf(allowedNamespaces);
        this.customItemKey = new NamespacedKey(plugin, "custom_item");
        this.categoryKey = new NamespacedKey(plugin, "content_category");
        this.contentIdKey = new NamespacedKey(plugin, "content_id");
    }

    public ItemStack mark(ItemStack item, String category, String contentId) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        item.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(customItemKey, PersistentDataType.BYTE, (byte) 1);
            data.set(categoryKey, PersistentDataType.STRING, normalize(category));
            data.set(contentIdKey, PersistentDataType.STRING, normalize(contentId));
        });
        return item;
    }

    public boolean isCustom(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        if (mmoItems.inspect(item).isPresent()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (data.has(customItemKey, PersistentDataType.BYTE)) {
            return true;
        }
        for (NamespacedKey key : data.getKeys()) {
            if (allowedNamespaces.contains(key.getNamespace().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/-]", "_");
    }
}
