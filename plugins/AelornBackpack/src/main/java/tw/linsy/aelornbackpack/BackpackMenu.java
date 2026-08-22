package tw.linsy.aelornbackpack;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 背包介面的版面與繪製。
 *
 * 版面（54 格）：
 *   第 0 列 0-8   頭盔 胸甲 護腿 靴子 ┃ 戒指 戒指 手鐲 項鍊 ┃ 角色摘要
 *   第 1-4 列 9-44 背包儲存（36 格，與原版主區同容量）
 *   第 5 列 45-53  導航：上一頁 / 關閉 / 頁碼 / 下一頁
 *
 * 主手與副手刻意不佔格——快捷欄本來就看得見，把空間讓給飾品欄。
 *
 * 護甲列是即時鏡像（唯讀）：不做穿脫，因為 RPGCore 的 EquipmentService
 * 與 MMOItems 的穿戴需求都掛在原版裝備事件上，另開穿脫路徑會繞過驗證。
 * 飾品欄則是本插件自有的槽位，可自由放入取出。
 */
public final class BackpackMenu {

    public static final int MENU_SIZE = 54;
    public static final int STORAGE_START = 9;
    public static final int STORAGE_END = 44;

    private static final int SLOT_HELMET = 0;
    private static final int SLOT_CHEST = 1;
    private static final int SLOT_LEGS = 2;
    private static final int SLOT_BOOTS = 3;
    private static final int SLOT_SUMMARY = 8;

    /** 飾品欄位置；順序對應飾品儲存索引 0-3。 */
    public static final int[] ACCESSORY_SLOTS = {4, 5, 6, 7};
    public static final int ACCESSORY_COUNT = ACCESSORY_SLOTS.length;
    private static final String[] ACCESSORY_LABELS = {"戒指", "戒指", "手鐲", "項鍊"};

    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_CLOSE = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private BackpackMenu() {
    }

    public static boolean isStorageSlot(int rawSlot) {
        return rawSlot >= STORAGE_START && rawSlot <= STORAGE_END;
    }

    public static boolean isAccessorySlot(int rawSlot) {
        return accessoryIndex(rawSlot) >= 0;
    }

    /** 介面槽位 → 飾品儲存索引；非飾品格回傳 -1。 */
    public static int accessoryIndex(int rawSlot) {
        for (int index = 0; index < ACCESSORY_SLOTS.length; index++) {
            if (ACCESSORY_SLOTS[index] == rawSlot) {
                return index;
            }
        }
        return -1;
    }

    /** 飾品欄接受的物品類型關鍵字；索引對應 ACCESSORY_SLOTS。 */
    public static String accessoryLabel(int index) {
        return index >= 0 && index < ACCESSORY_LABELS.length ? ACCESSORY_LABELS[index] : "";
    }

    public static int previousSlot() {
        return SLOT_PREVIOUS;
    }

    public static int nextSlot() {
        return SLOT_NEXT;
    }

    public static int closeSlot() {
        return SLOT_CLOSE;
    }

    /** 建立並填好一頁背包介面。 */
    public static Inventory create(Player player, int page, int maxPages,
                                   ItemStack[] contents, ItemStack[] accessories,
                                   Component title) {
        BackpackHolder holder = new BackpackHolder(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title);
        holder.attach(inventory);

        renderArmourMirror(inventory, player);
        renderAccessories(inventory, accessories);
        inventory.setItem(SLOT_SUMMARY, summary(player));
        renderStorage(inventory, contents);
        renderNavigation(inventory, page, maxPages);
        return inventory;
    }

    private static void renderArmourMirror(Inventory inventory, Player player) {
        PlayerInventory source = player.getInventory();
        inventory.setItem(SLOT_HELMET, mirror(source.getHelmet(), Material.LEATHER_HELMET, "頭盔"));
        inventory.setItem(SLOT_CHEST,
                mirror(source.getChestplate(), Material.LEATHER_CHESTPLATE, "胸甲"));
        inventory.setItem(SLOT_LEGS,
                mirror(source.getLeggings(), Material.LEATHER_LEGGINGS, "護腿"));
        inventory.setItem(SLOT_BOOTS, mirror(source.getBoots(), Material.LEATHER_BOOTS, "靴子"));
    }

    /** 飾品欄：有裝就顯示實物，空的顯示標示用佔位物。 */
    private static void renderAccessories(Inventory inventory, ItemStack[] accessories) {
        for (int index = 0; index < ACCESSORY_SLOTS.length; index++) {
            ItemStack worn = accessories == null || index >= accessories.length
                    ? null : accessories[index];
            if (worn != null && !worn.getType().isAir()) {
                inventory.setItem(ACCESSORY_SLOTS[index], worn);
            } else {
                inventory.setItem(ACCESSORY_SLOTS[index],
                        emptyAccessoryMarker(ACCESSORY_LABELS[index]));
            }
        }
    }

    /**
     * 空飾品欄的標示物。
     *
     * 用 LIGHT_GRAY 玻璃片而不是 BARRIER：玩家要能把飾品拖進來，
     * 視覺上必須看得出「這格是空的、可以放東西」而不是「這格被封鎖」。
     */
    private static ItemStack emptyAccessoryMarker(String label) {
        ItemStack marker = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        marker.editMeta(meta -> {
            meta.displayName(Component.text(label + "欄", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("放入" + label + "以裝備", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
        });
        return marker;
    }

    /** 判斷某個 ItemStack 是否為空飾品欄的佔位物（不可被玩家取走）。 */
    public static boolean isAccessoryMarker(ItemStack item) {
        if (item == null || item.getType() != Material.LIGHT_GRAY_STAINED_GLASS_PANE
                || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        for (String label : ACCESSORY_LABELS) {
            Component expected = Component.text(label + "欄", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);
            if (expected.equals(item.getItemMeta().displayName())) {
                return true;
            }
        }
        return false;
    }

    private static void renderStorage(Inventory inventory, ItemStack[] contents) {
        for (int index = 0; index < BackpackStorage.SLOTS_PER_PAGE; index++) {
            ItemStack item = contents == null || index >= contents.length ? null : contents[index];
            inventory.setItem(STORAGE_START + index, item);
        }
    }

    private static void renderNavigation(Inventory inventory, int page, int maxPages) {
        for (int slot = 45; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, filler());
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREVIOUS,
                    button(Material.ARROW, "上一頁", NamedTextColor.YELLOW,
                            List.of("第 " + page + " 頁")));
        }
        if (page + 1 < maxPages) {
            inventory.setItem(SLOT_NEXT,
                    button(Material.ARROW, "下一頁", NamedTextColor.YELLOW,
                            List.of("第 " + (page + 2) + " 頁")));
        }
        inventory.setItem(SLOT_CLOSE,
                button(Material.BARRIER, "關閉", NamedTextColor.RED, List.of("關閉背包")));
        inventory.setItem(SLOT_PAGE_INFO,
                button(Material.BOOK, "背包 " + (page + 1) + "/" + maxPages,
                        NamedTextColor.WHITE,
                        List.of("可用格數：" + BackpackStorage.SLOTS_PER_PAGE)));
    }

    /** 護甲鏡像：有穿就顯示實物，沒穿就顯示灰色佔位。 */
    private static ItemStack mirror(ItemStack worn, Material placeholder, String label) {
        if (worn != null && !worn.getType().isAir()) {
            ItemStack copy = worn.clone();
            copy.editMeta(meta -> {
                List<Component> lore = meta.hasLore()
                        ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("裝備中 · " + label, NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            return copy;
        }
        ItemStack empty = new ItemStack(placeholder);
        empty.editMeta(meta -> {
            meta.displayName(Component.text("未裝備 " + label, NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.setEnchantmentGlintOverride(false);
        });
        return empty;
    }

    private static ItemStack summary(Player player) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.displayName(Component.text("角色摘要", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line("玩家", player.getName()));
            lore.add(line("等級", Integer.toString(player.getLevel())));
            var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            long cap = Math.round(maxHealth == null ? player.getHealth() : maxHealth.getValue());
            lore.add(line("生命", Math.round(player.getHealth()) + " / " + cap));
            lore.add(Component.empty());
            lore.add(Component.text("詳細屬性請用 /rpg", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return item;
    }

    private static Component line(String label, String value) {
        return Component.text(label + "：", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }

    private static ItemStack button(Material material, String name, NamedTextColor colour,
                                    List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name, colour)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(text -> (Component) Component.text(text, NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .toList());
        });
        return item;
    }
}
