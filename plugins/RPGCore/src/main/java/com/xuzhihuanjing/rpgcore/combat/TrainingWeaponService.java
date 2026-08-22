package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.config.ClassRegistry;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.classes.CharacterClassDefinition;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class TrainingWeaponService {
   private final ClassRegistry classRegistry;
   private final MessageBundle messages;
   private final NamespacedKey markerKey;
   private final NamespacedKey characterKey;
   private final NamespacedKey classKey;

   public TrainingWeaponService(Plugin plugin, ClassRegistry classRegistry, MessageBundle messages) {
      this.classRegistry = classRegistry;
      this.messages = messages;
      this.markerKey = new NamespacedKey(plugin, "training_weapon");
      this.characterKey = new NamespacedKey(plugin, "character_id");
      this.classKey = new NamespacedKey(plugin, "class_id");
   }

   public void ensure(Player player, CharacterProfile character) {
      this.removeTrainingWeapons(player);
      CharacterClassDefinition definition = (CharacterClassDefinition)this.classRegistry.find(character.classId()).orElseThrow(() -> new IllegalArgumentException("Unknown class: " + character.classId()));
      Material material = Material.matchMaterial(definition.castingMaterial());
      if (material == null) {
         throw new IllegalArgumentException("Unknown casting material: " + definition.castingMaterial());
      } else {
         ItemStack item = new ItemStack(material);
         ItemMeta meta = item.getItemMeta();
         meta.displayName(this.messages.text(definition.displayName() + " <white>訓練武器</white>"));
         meta.lore(List.of(this.messages.text("<gray>武器類型：</gray><white>" + definition.weapon() + "</white>"), this.messages.text("<gray>以右鍵開始輸入三鍵技能連擊。</gray>"), this.messages.text("<dark_gray>綁定角色：</dark_gray><white>" + character.name() + "</white>")));
         meta.setUnbreakable(true);
         meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE});
         PersistentDataContainer data = meta.getPersistentDataContainer();
         data.set(this.markerKey, PersistentDataType.BYTE, (byte)1);
         data.set(this.characterKey, PersistentDataType.STRING, character.id().toString());
         data.set(this.classKey, PersistentDataType.STRING, character.classId());
         item.setItemMeta(meta);
         player.getInventory().addItem(new ItemStack[]{item}).values().forEach((leftover) -> {
            Item dropped = player.getWorld().dropItem(player.getLocation(), leftover);
            dropped.setOwner(player.getUniqueId());
            dropped.setPickupDelay(0);
         });
      }
   }

   public boolean isActiveWeapon(Player player, ItemStack item, CharacterProfile character) {
      if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
         PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
         return data.has(this.markerKey, PersistentDataType.BYTE) && character.id().toString().equals(data.get(this.characterKey, PersistentDataType.STRING)) && character.classId().equals(data.get(this.classKey, PersistentDataType.STRING));
      } else {
         return false;
      }
   }

   public boolean isTrainingWeapon(ItemStack item) {
      return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(this.markerKey, PersistentDataType.BYTE);
   }

   private void removeTrainingWeapons(Player player) {
      ItemStack[] contents = player.getInventory().getContents();

      for(int index = 0; index < contents.length; ++index) {
         if (this.isTrainingWeapon(contents[index])) {
            player.getInventory().setItem(index, (ItemStack)null);
         }
      }

   }
}
