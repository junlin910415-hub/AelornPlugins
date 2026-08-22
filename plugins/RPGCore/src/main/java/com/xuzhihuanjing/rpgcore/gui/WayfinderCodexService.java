package com.xuzhihuanjing.rpgcore.gui;

import com.xuzhihuanjing.rpgcore.config.InterfaceSettings;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.integration.nexo.CustomItemProvider;
import com.xuzhihuanjing.rpgcore.integration.nexo.HudGlyphProvider;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class WayfinderCodexService {
   private final MessageBundle messages;
   private final CustomItemProvider customItems;
   private final HudGlyphProvider glyphs;
   private final InterfaceSettings settings;
   private final NamespacedKey markerKey;
   private final NamespacedKey characterKey;

   public WayfinderCodexService(Plugin plugin, MessageBundle messages, CustomItemProvider customItems, HudGlyphProvider glyphs, InterfaceSettings settings) {
      this.messages = messages;
      this.customItems = customItems;
      this.glyphs = glyphs;
      this.settings = settings;
      this.markerKey = new NamespacedKey(plugin, "wayfinder_codex");
      this.characterKey = new NamespacedKey(plugin, "character_id");
   }

   public void ensure(Player player, CharacterProfile character) {
      PlayerInventory inventory = player.getInventory();
      this.removeCodices(inventory);
      int slot = this.settings.contentBookHotbarSlot();
      ItemStack displaced = inventory.getItem(slot);
      if (!this.isEmpty(displaced)) {
         int destination = this.firstEmptyStorageSlot(inventory, slot);
         if (destination >= 0) {
            inventory.setItem(destination, displaced);
         } else {
            Item dropped = player.getWorld().dropItem(player.getLocation(), displaced.clone());
            dropped.setOwner(player.getUniqueId());
            dropped.setPickupDelay(0);
            player.sendMessage(this.messages.message("content-book-slot-cleared", MessageBundle.value("slot", Integer.toString(slot + 1))));
         }
      }

      inventory.setItem(slot, this.create(player, character));
   }

   public boolean isCodex(ItemStack item) {
      return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(this.markerKey, PersistentDataType.BYTE);
   }

   public boolean hasCodex(Player player) {
      PlayerInventory inventory = player.getInventory();

      for(ItemStack item : inventory.getStorageContents()) {
         if (this.isCodex(item)) {
            return true;
         }
      }

      return this.isCodex(inventory.getItemInOffHand());
   }

   private ItemStack create(Player player, CharacterProfile character) {
      ItemStack item = (ItemStack)this.customItems.build(this.settings.contentBookItemId()).orElseGet(() -> new ItemStack(Material.KNOWLEDGE_BOOK));
      ItemMeta meta = item.getItemMeta();
      Component fKey = this.fKey(player);
      meta.displayName(this.messages.text("<gold><bold>旅圖冊</bold></gold>"));
      meta.lore(List.of(this.messages.text("<gray>角色：</gray><white>" + character.name() + "</white>"), this.messages.text("<dark_gray>角色目錄 / 任務 / 技能 / 生活技能</dark_gray>"), this.messages.text(""), this.messages.text("<yellow>右鍵</yellow><gray> 或 </gray>").append(fKey).append(this.messages.text("<yellow> 開啟功能選單</yellow>")), this.glyphs.shift(24).append(this.messages.text("<gold>•</gold><dark_gray> · · </dark_gray>")).append(fKey).append(this.messages.text("<gray> 目錄</gray>")), this.messages.text("<dark_gray>固定於快捷欄第 " + (this.settings.contentBookHotbarSlot() + 1) + " 格</dark_gray>")));
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      PersistentDataContainer data = meta.getPersistentDataContainer();
      data.set(this.markerKey, PersistentDataType.BYTE, (byte)1);
      data.set(this.characterKey, PersistentDataType.STRING, character.id().toString());
      item.setItemMeta(meta);
      return item;
   }

   private Component fKey(Player player) {
      return (Component)this.glyphs.glyph(player, "rpgcore_key_f").orElseGet(() -> Component.text("[F]", NamedTextColor.WHITE));
   }

   private void removeCodices(PlayerInventory inventory) {
      ItemStack[] storage = inventory.getStorageContents();

      for(int slot = 0; slot < storage.length; ++slot) {
         if (this.isCodex(storage[slot])) {
            inventory.setItem(slot, (ItemStack)null);
         }
      }

      if (this.isCodex(inventory.getItemInOffHand())) {
         inventory.setItemInOffHand((ItemStack)null);
      }

   }

   private int firstEmptyStorageSlot(PlayerInventory inventory, int reservedSlot) {
      ItemStack[] storage = inventory.getStorageContents();

      for(int slot = 0; slot < storage.length; ++slot) {
         if (slot != reservedSlot && this.isEmpty(storage[slot])) {
            return slot;
         }
      }

      return -1;
   }

   private boolean isEmpty(ItemStack item) {
      return item == null || item.getType().isAir();
   }
}
