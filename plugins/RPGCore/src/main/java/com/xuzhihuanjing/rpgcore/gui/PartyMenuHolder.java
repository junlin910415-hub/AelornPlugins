package com.xuzhihuanjing.rpgcore.gui;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class PartyMenuHolder implements InventoryHolder {
   private final UUID ownerId;
   private final View view;
   private final int page;
   private final List<UUID> entries;
   private Inventory inventory;

   public PartyMenuHolder(UUID ownerId, View view, int page, List<UUID> entries) {
      this.ownerId = ownerId;
      this.view = view;
      this.page = page;
      this.entries = List.copyOf(entries);
   }

   public UUID ownerId() {
      return this.ownerId;
   }

   public View view() {
      return this.view;
   }

   public int page() {
      return this.page;
   }

   public List<UUID> entries() {
      return this.entries;
   }

   public void inventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }

   public static enum View {
      ROOT,
      INVITE,
      FINDER,
      INVITATIONS;
   }
}
