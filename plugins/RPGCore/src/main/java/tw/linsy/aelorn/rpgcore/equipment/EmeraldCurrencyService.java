package tw.linsy.aelorn.rpgcore.equipment;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class EmeraldCurrencyService {
   public int balance(Player player) {
      int total = 0;

      for(ItemStack item : player.getInventory().getStorageContents()) {
         if (this.isPlain(item, Material.EMERALD)) {
            total += item.getAmount();
         } else if (this.isPlain(item, Material.EMERALD_BLOCK)) {
            total += item.getAmount() * 9;
         }
      }

      return total;
   }

   public boolean withdraw(Player player, int amount) {
      if (amount <= 0) {
         return true;
      } else if (this.balance(player) < amount) {
         return false;
      } else {
         PlayerInventory inventory = player.getInventory();
         int looseAvailable = this.count(inventory, Material.EMERALD);
         int looseToRemove = Math.min(amount, looseAvailable);
         int remainder = amount - looseToRemove;
         int blocksToRemove = (remainder + 8) / 9;
         int change = blocksToRemove * 9 - remainder;
         this.remove(inventory, Material.EMERALD, looseToRemove);
         this.remove(inventory, Material.EMERALD_BLOCK, blocksToRemove);
         if (change > 0) {
            this.deposit(player, change);
         }

         return true;
      }
   }

   public void deposit(Player player, int amount) {
      if (amount > 0) {
         Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack[]{new ItemStack(Material.EMERALD, amount)});

         for(ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
         }

      }
   }

   private int count(PlayerInventory inventory, Material material) {
      int count = 0;

      for(ItemStack item : inventory.getStorageContents()) {
         if (this.isPlain(item, material)) {
            count += item.getAmount();
         }
      }

      return count;
   }

   private void remove(PlayerInventory inventory, Material material, int amount) {
      int remaining = amount;
      ItemStack[] contents = inventory.getStorageContents();

      for(int slot = 0; slot < contents.length && remaining > 0; ++slot) {
         ItemStack item = contents[slot];
         if (this.isPlain(item, material)) {
            int removed = Math.min(remaining, item.getAmount());
            remaining -= removed;
            if (removed == item.getAmount()) {
               inventory.setItem(slot, (ItemStack)null);
            } else {
               item.setAmount(item.getAmount() - removed);
               inventory.setItem(slot, item);
            }
         }
      }

      if (remaining != 0) {
         throw new IllegalStateException("Currency balance changed during withdrawal");
      }
   }

   private boolean isPlain(ItemStack item, Material material) {
      return item != null && item.getType() == material && !item.hasItemMeta();
   }
}
