package tw.linsy.aelornbackpack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * 儲物袋：常駐在玩家快捷欄的功能物品，右鍵開啟背包。
 *
 * 設計取自「保留格位」模式而非取代原版介面——玩家犧牲一格快捷欄，
 * 換到一個永遠在手邊、且 tooltip 會即時列出內容物的入口。
 *
 * 鎖定規則：不可移動、不可丟棄、不可放進容器。
 * 判定一律走 PDC 標記，不用顯示名稱比對（名稱會因語系與資源包改變）。
 */
public final class PouchService {

    private static final int TOOLTIP_ENTRIES = 8;

    private final Plugin plugin;
    private final NamespacedKey markerKey;

    public PouchService(Plugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "pouch");
    }

    /** 以 PDC 標記判定，避免玩家用改名物品偽造。 */
    public boolean isPouch(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    /**
     * 確保玩家的指定格位放著最新的儲物袋。
     * 內容變動後重新呼叫即可刷新 tooltip。
     */
    public void refresh(Player player, int slot, Material material, ItemStack[] contents) {
        if (slot < 0 || slot > 8) {
            return;
        }
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing != null && !existing.getType().isAir() && !isPouch(existing)) {
            // 該格被別的東西佔著：先讓出來，避免直接覆蓋玩家的物品。
            Map<Integer, ItemStack> overflow =
                    player.getInventory().addItem(existing.clone());
            if (!overflow.isEmpty()) {
                // 背包滿了就不強佔，這一輪略過，下次進入再試。
                return;
            }
        }
        player.getInventory().setItem(slot, build(material, contents));
    }

    private ItemStack build(Material material, ItemStack[] contents) {
        ItemStack pouch = new ItemStack(material == null ? Material.BUNDLE : material);
        pouch.editMeta(meta -> {
            meta.displayName(Component.text("儲物袋", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            meta.lore(buildLore(contents));
        });
        return pouch;
    }

    /** tooltip 即時列出前幾項內容物與總數，讓玩家不必開啟就知道裝了什麼。 */
    private List<Component> buildLore(ItemStack[] contents) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("右鍵開啟", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        Map<String, Integer> tally = new LinkedHashMap<>();
        int total = 0;
        if (contents != null) {
            for (ItemStack item : contents) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                tally.merge(displayName(item), item.getAmount(), Integer::sum);
                total += item.getAmount();
            }
        }

        if (tally.isEmpty()) {
            lore.add(Component.text("（空）", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            return lore;
        }

        int shown = 0;
        for (Map.Entry<String, Integer> entry : tally.entrySet()) {
            if (shown >= TOOLTIP_ENTRIES) {
                lore.add(Component.text("…另有 " + (tally.size() - shown) + " 種",
                                NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                break;
            }
            lore.add(Component.text(entry.getValue() + " × ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(entry.getKey(), NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
            shown++;
        }
        lore.add(Component.empty());
        lore.add(Component.text("共 " + total + " 件", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    /**
     * 取得可讀名稱。
     *
     * 刻意不解析自訂顯示名稱：那需要 plain-text 序列化器，
     * 而 tooltip 只是概覽，用材質類型名已足夠且零依賴。
     */
    private static String displayName(ItemStack item) {
        return humanize(item.getType().name());
    }

    private static String humanize(String raw) {
        String[] parts = raw.toLowerCase(java.util.Locale.ROOT).split("_+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    public UUID pluginId() {
        return UUID.nameUUIDFromBytes(plugin.getName().getBytes());
    }
}
