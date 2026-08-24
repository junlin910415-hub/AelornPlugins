package tw.linsy.aelorn.rpgcore.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class ClassSelectionHolder implements CharacterSelectorHolder {
   private final UUID ownerId;
   private final int characterSlot;
   private Inventory inventory;

   public ClassSelectionHolder(UUID ownerId, int characterSlot) {
      this.ownerId = ownerId;
      this.characterSlot = characterSlot;
   }

   public UUID ownerId() {
      return this.ownerId;
   }

   public int characterSlot() {
      return this.characterSlot;
   }

   public void inventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }
}
