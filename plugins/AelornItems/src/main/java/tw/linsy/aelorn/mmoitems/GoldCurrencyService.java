package tw.linsy.aelorn.mmoitems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;

final class GoldCurrencyService {
   private static final long MAX_WALLET = 1000000000000L;
   private final MMOItemsPlugin plugin;
   private final MythicCoreApi api;
   private final NamespacedKey walletKey;

   GoldCurrencyService(MMOItemsPlugin var1, MythicCoreApi var2) {
      this.plugin = var1;
      this.api = var2;
      this.walletKey = new NamespacedKey("mmoitems", "gold_wallet");
   }

   ItemStack createGold(int var1) {
      ItemStack var2 = new ItemStack(Material.GOLD_NUGGET, Math.max(1, Math.min(64, var1)));
      ItemMeta var3 = var2.getItemMeta();
      var3.displayName(Component.text("黃金", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
      var3.lore(List.of((TextComponent)Component.text("王國通用貨幣", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text("可存入錢包，用於商店、合成與強化。", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
      NamespacedKey var4 = NamespacedKey.fromString("mmoitems:gold_coin");
      if (var4 != null) {
         var3.setItemModel(var4);
      }

      var3.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      this.api.writeItemTags(var3, Map.of("currency_id", "GOLD", "currency_value", "1"));
      var2.setItemMeta(var3);
      return var2;
   }

   boolean isGold(ItemStack var1) {
      return var1 != null && var1.getType() != Material.AIR && "GOLD".equalsIgnoreCase(this.api.readItemTag(var1, "currency_id"));
   }

   long wallet(Player var1) {
      Long var2 = (Long)var1.getPersistentDataContainer().get(this.walletKey, PersistentDataType.LONG);
      return var2 == null ? 0L : Math.max(0L, var2);
   }

   long physical(Player var1) {
      long var2 = 0L;

      for(ItemStack var7 : var1.getInventory().getStorageContents()) {
         if (this.isGold(var7)) {
            var2 += (long)var7.getAmount();
         }
      }

      return var2;
   }

   long available(Player var1) {
      return Math.min(1000000000000L, this.wallet(var1) + this.physical(var1));
   }

   boolean debit(Player var1, long var2) {
      long var4 = this.normalizeAmount(var2);
      if (var4 > 0L && this.available(var1) >= var4) {
         long var6 = Math.min(this.wallet(var1), var4);
         this.setWallet(var1, this.wallet(var1) - var6);
         long var8 = var4 - var6;
         if (var8 > 0L) {
            this.removePhysical(var1, var8);
         }

         return true;
      } else {
         return false;
      }
   }

   long credit(Player var1, long var2) {
      long var4 = this.normalizeAmount(var2);
      long var6 = Math.min(var4, this.creditCapacity(var1));
      this.setWallet(var1, this.wallet(var1) + var6);
      return var6;
   }

   long creditCapacity(Player var1) {
      return Math.max(0L, 1000000000000L - this.wallet(var1));
   }

   long depositAll(Player var1) {
      long var2 = Math.min(this.physical(var1), this.creditCapacity(var1));
      if (var2 <= 0L) {
         return 0L;
      } else {
         this.removePhysical(var1, var2);
         this.setWallet(var1, this.wallet(var1) + var2);
         return var2;
      }
   }

   long withdrawItems(Player var1, long var2) {
      long var7 = Math.min(this.normalizeAmount(var2), this.wallet(var1));
      if (var7 <= 0L) {
         return 0L;
      } else {
         this.setWallet(var1, this.wallet(var1) - var7);
         long var9 = 0L;

         int var6;
         for(long var11 = var7; var11 > 0L; var11 -= (long)var6) {
            var6 = (int)Math.min(64L, var11);
            HashMap<Integer, ItemStack> var13 = var1.getInventory().addItem(new ItemStack[]{this.createGold(var6)});
            int var14 = var13.values().stream().mapToInt(ItemStack::getAmount).sum();
            var9 += (long)(var6 - var14);
            if (var14 > 0) {
               break;
            }
         }

         long var4;
         if ((var4 = var7 - var9) > 0L) {
            this.credit(var1, var4);
            var1.sendMessage(Component.text("背包空間不足，未領出的黃金已退回錢包。", NamedTextColor.YELLOW));
         }

         return var9;
      }
   }

   List<Component> balanceLore(Player var1) {
      ArrayList var2 = new ArrayList();
      var2.add(Component.text("錢包：" + this.wallet(var1) + " 黃金", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
      var2.add(Component.text("背包：" + this.physical(var1) + " 黃金", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
      var2.add(Component.text("可用總額：" + this.available(var1), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
      return var2;
   }

   private void removePhysical(Player var1, long var2) {
      long var4 = var2;
      ItemStack[] var6 = var1.getInventory().getStorageContents();

      for(int var7 = 0; var7 < var6.length && var4 > 0L; ++var7) {
         ItemStack var8 = var6[var7];
         if (this.isGold(var8)) {
            int var9 = (int)Math.min(var4, (long)var8.getAmount());
            var4 -= (long)var9;
            if (var9 >= var8.getAmount()) {
               var6[var7] = null;
            } else {
               ItemStack var10 = var8.clone();
               var10.setAmount(var8.getAmount() - var9);
               var6[var7] = var10;
            }
         }
      }

      var1.getInventory().setStorageContents(var6);
   }

   private void setWallet(Player var1, long var2) {
      var1.getPersistentDataContainer().set(this.walletKey, PersistentDataType.LONG, Math.max(0L, Math.min(1000000000000L, var2)));
   }

   private long normalizeAmount(long var1) {
      return Math.max(0L, Math.min(1000000000000L, var1));
   }
}
