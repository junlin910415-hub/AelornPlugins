package tw.linsy.aelorn.rpgcore.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class CharacterBackupHolder implements CharacterSelectorHolder {
   private final UUID ownerId;
   private Inventory inventory;

   public CharacterBackupHolder(UUID ownerId) {
      this.ownerId = ownerId;
   }

   public UUID ownerId() {
      return this.ownerId;
   }

   public void inventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }
}
