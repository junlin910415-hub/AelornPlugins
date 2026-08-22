package com.xuzhihuanjing.rpgcore.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ContentBookHolder implements InventoryHolder {
   private final UUID ownerId;
   private final UUID characterId;
   private final int page;
   private final ContentBookFilter filter;
   private final ContentBookSort sort;
   private Inventory inventory;

   public ContentBookHolder(UUID ownerId, UUID characterId, int page, ContentBookFilter filter, ContentBookSort sort) {
      this.ownerId = ownerId;
      this.characterId = characterId;
      this.page = page;
      this.filter = filter;
      this.sort = sort;
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

   public ContentBookFilter filter() {
      return this.filter;
   }

   public ContentBookSort sort() {
      return this.sort;
   }

   public void inventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }
}
