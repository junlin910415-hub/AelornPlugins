package com.xuzhihuanjing.rpgcore.gui;

import com.xuzhihuanjing.rpgcore.config.IdentificationSettings;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.equipment.EmeraldCurrencyService;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentIdentificationQuote;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentService;
import com.xuzhihuanjing.rpgcore.hud.InternalGuiTitle;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class IdentificationMenuService {
   public static final int MENU_SIZE = 36;
   public static final int CONFIRM_SLOT = 16;
   public static final int CLOSE_SLOT = 31;
   private static final int[] INPUT_SLOTS = new int[]{10, 11, 12, 13, 14, 19, 20, 21, 22, 23};
   private final CharacterService characterService;
   private final EquipmentService equipmentService;
   private final EmeraldCurrencyService currencyService;
   private final IdentificationSettings settings;
   private final MessageBundle messages;
   private final NamespacedKey markerKey;

   public IdentificationMenuService(JavaPlugin plugin, CharacterService characterService, EquipmentService equipmentService, EmeraldCurrencyService currencyService, IdentificationSettings settings, MessageBundle messages) {
      this.characterService = characterService;
      this.equipmentService = equipmentService;
      this.currencyService = currencyService;
      this.settings = settings;
      this.messages = messages;
      this.markerKey = new NamespacedKey(plugin, "identification_menu_marker");
   }

   public void open(Player player) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character == null) {
         player.sendMessage(this.messages.message("no-active-character"));
      } else {
         IdentificationMenuHolder holder = new IdentificationMenuHolder(player.getUniqueId(), character.id());
         Inventory inventory = Bukkit.createInventory(holder, 36, InternalGuiTitle.identification());
         holder.inventory(inventory);
         this.decorate(inventory);
         this.refresh(player, holder);
         player.openInventory(inventory);
      }
   }

   public void refresh(Player player, IdentificationMenuHolder holder) {
      Inventory inventory = holder.getInventory();

      for(int index = 0; index < INPUT_SLOTS.length; ++index) {
         int slot = INPUT_SLOTS[index];
         if (index >= this.settings.maximumBatchItems()) {
            inventory.setItem(slot, this.menuItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>未開放欄位</dark_gray>", List.of()));
         } else if (inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir()) {
            inventory.setItem(slot, this.placeholder());
         }
      }

      List<EquipmentIdentificationQuote> quotes = this.quotes(holder);
      int totalCost = this.totalCost(quotes);
      int balance = this.currencyService.balance(player);
      boolean ready = !quotes.isEmpty() && quotes.stream().allMatch(EquipmentIdentificationQuote::ready) && balance >= totalCost;
      List<String> confirmLore = new ArrayList();
      confirmLore.add("<gray>待處理：</gray><white>" + quotes.size() + " 件</white>");
      confirmLore.add("<gray>總費用：</gray><green>" + totalCost + " 綠寶石</green>");
      confirmLore.add("<gray>持有：</gray><white>" + balance + " 綠寶石</white>");
      confirmLore.add("");
      confirmLore.add(quotes.isEmpty() ? "<yellow>先從背包點擊裝備放入欄位</yellow>" : (ready ? "<green>點擊確認鑑定</green>" : "<red>綠寶石不足</red>"));
      inventory.setItem(16, this.menuItem(ready ? Material.LIME_CONCRETE : Material.RED_CONCRETE, ready ? "<green>確認鑑定</green>" : "<red>無法鑑定</red>", confirmLore));
   }

   public boolean addOne(IdentificationMenuHolder holder, ItemStack source) {
      if (!this.equipmentService.quote(source).ready()) {
         return false;
      } else {
         for(int index = 0; index < this.settings.maximumBatchItems(); ++index) {
            int slot = INPUT_SLOTS[index];
            if (this.isPlaceholder(holder.getInventory().getItem(slot))) {
               ItemStack moved = source.clone();
               moved.setAmount(1);
               holder.getInventory().setItem(slot, moved);
               return true;
            }
         }

         return false;
      }
   }

   public boolean isInputSlot(int rawSlot) {
      for(int index = 0; index < this.settings.maximumBatchItems(); ++index) {
         if (INPUT_SLOTS[index] == rawSlot) {
            return true;
         }
      }

      return false;
   }

   public List<ItemStack> inputItems(IdentificationMenuHolder holder) {
      List<ItemStack> items = new ArrayList();

      for(int index = 0; index < this.settings.maximumBatchItems(); ++index) {
         ItemStack item = holder.getInventory().getItem(INPUT_SLOTS[index]);
         if (item != null && !item.getType().isAir() && !this.isPlaceholder(item)) {
            items.add(item);
         }
      }

      return List.copyOf(items);
   }

   public List<EquipmentIdentificationQuote> quotes(IdentificationMenuHolder holder) {
      var var10000 = this.inputItems(holder).stream();
      EquipmentService var10001 = this.equipmentService;
      Objects.requireNonNull(var10001);
      return var10000.map(var10001::quote).toList();
   }

   public void replaceInputItems(IdentificationMenuHolder holder, List<ItemStack> replacements) {
      int replacementIndex = 0;

      for(int index = 0; index < this.settings.maximumBatchItems(); ++index) {
         int slot = INPUT_SLOTS[index];
         ItemStack current = holder.getInventory().getItem(slot);
         if (current != null && !current.getType().isAir() && !this.isPlaceholder(current)) {
            if (replacementIndex >= replacements.size()) {
               throw new IllegalArgumentException("Replacement count does not match identification inputs");
            }

            holder.getInventory().setItem(slot, (ItemStack)replacements.get(replacementIndex++));
         }
      }

      if (replacementIndex != replacements.size()) {
         throw new IllegalArgumentException("Replacement count does not match identification inputs");
      }
   }

   public int totalCost(List<EquipmentIdentificationQuote> quotes) {
      long total = quotes.stream().filter(EquipmentIdentificationQuote::ready).mapToLong(EquipmentIdentificationQuote::cost).sum();
      return (int)Math.min(2147483647L, total);
   }

   public void returnInput(Player player, IdentificationMenuHolder holder, int slot) {
      ItemStack item = holder.getInventory().getItem(slot);
      if (item != null && !item.getType().isAir() && !this.isPlaceholder(item)) {
         holder.getInventory().setItem(slot, (ItemStack)null);
         this.giveOrDrop(player, item);
         this.refresh(player, holder);
      }
   }

   public void returnAll(Player player, IdentificationMenuHolder holder) {
      if (holder.markReturned()) {
         for(int index = 0; index < this.settings.maximumBatchItems(); ++index) {
            int slot = INPUT_SLOTS[index];
            ItemStack item = holder.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir() && !this.isPlaceholder(item)) {
               holder.getInventory().setItem(slot, (ItemStack)null);
               this.giveOrDrop(player, item);
            }
         }

      }
   }

   private void decorate(Inventory inventory) {
      ItemStack border = this.menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());

      for(int slot = 0; slot < 36; ++slot) {
         inventory.setItem(slot, border);
      }

      inventory.setItem(4, this.menuItem(Material.KNOWLEDGE_BOOK, "<gold>鑑定說明</gold>", List.of("<gray>點擊背包中的未鑑定裝備即可放入。</gray>", "<gray>已鑑定裝備可再次擲骰全部能力。</gray>", "<yellow>重鑑可能提升，也可能降低數值。</yellow>")));
      inventory.setItem(25, this.menuItem(Material.AMETHYST_SHARD, "<light_purple>數值品質</light_purple>", List.of("<white>★</white><gray> 優良擲骰（前 30%）</gray>", "<yellow>★★</yellow><gray> 卓越擲骰（前 15%）</gray>", "<gold>★★★</gold><gray> 極致擲骰（前 5%）</gray>", "<dark_gray>星等按各詞條實際範圍計算。</dark_gray>")));
      inventory.setItem(31, this.menuItem(Material.BARRIER, "<red>關閉</red>", List.of("<gray>所有欄位物品會安全退回背包。</gray>")));
   }

   private ItemStack placeholder() {
      ItemStack item = this.menuItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<aqua>鑑定欄位</aqua>", List.of("<gray>點擊下方背包中的裝備放入。</gray>"));
      item.editMeta((meta) -> meta.getPersistentDataContainer().set(this.markerKey, PersistentDataType.BYTE, (byte)1));
      return item;
   }

   private boolean isPlaceholder(ItemStack item) {
      return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(this.markerKey, PersistentDataType.BYTE);
   }

   private ItemStack menuItem(Material material, String name, List<String> loreLines) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.messages.text(name));
      List<Component> lore = loreLines.stream().map((line) -> (Component)(line.isEmpty() ? Component.empty() : this.messages.text(line))).toList();
      meta.lore(lore);
      item.setItemMeta(meta);
      return item;
   }

   private void giveOrDrop(Player player, ItemStack item) {
      Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack[]{item});

      for(ItemStack leftover : leftovers.values()) {
         player.getWorld().dropItemNaturally(player.getLocation(), leftover);
      }

   }
}
