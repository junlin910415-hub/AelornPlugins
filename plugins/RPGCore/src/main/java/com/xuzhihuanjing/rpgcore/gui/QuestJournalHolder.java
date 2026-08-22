package com.xuzhihuanjing.rpgcore.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class QuestJournalHolder implements InventoryHolder {
   private final UUID ownerId;
   private final UUID characterId;
   private final int page;
   private Inventory inventory;

   public QuestJournalHolder(UUID ownerId, UUID characterId, int page) {
      this.ownerId = ownerId;
      this.characterId = characterId;
      this.page = page;
   }

   public UUID ownerId() {
      return this.ownerId;
   }

   public UUID characterId() {
      return this.characterId;
   }

   public int page() {
      return this.page;
   }

   public void inventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }
}
