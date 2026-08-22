package com.xuzhihuanjing.rpgcore.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class CharacterProfileMenuHolder implements InventoryHolder {
   private final UUID ownerId;
   private final UUID characterId;
   private Inventory inventory;

   public CharacterProfileMenuHolder(UUID ownerId, UUID characterId) {
      this.ownerId = ownerId;
      this.characterId = characterId;
   }

   public UUID ownerId() {
      return this.ownerId;
   }

   public UUID characterId() {
      return this.characterId;
   }

   public void inventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }
}
