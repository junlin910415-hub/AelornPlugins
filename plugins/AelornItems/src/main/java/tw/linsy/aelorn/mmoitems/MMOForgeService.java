package tw.linsy.aelorn.mmoitems;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;

final class MMOForgeService implements Listener {
   private static final int ITEM_SLOT = 20;
   private static final int CATALYST_SLOT = 24;
   private final MMOItemsPlugin plugin;
   private final MythicCoreApi api;
   private final GoldCurrencyService currency;
   private final NamespacedKey pityKey;

   MMOForgeService(MMOItemsPlugin var1, MythicCoreApi var2, GoldCurrencyService var3) {
      this.plugin = var1;
      this.api = var2;
      this.currency = var3;
      this.pityKey = new NamespacedKey("mmoitems", "forge_pity");
      Bukkit.getPluginManager().registerEvents(this, var1);
   }

   void open(Player var1) {
      ForgeHolder var3 = new ForgeHolder();
      Inventory var2;
      var3.inventory = var2 = Bukkit.createInventory(var3, 54, Component.text("皇家強化台", NamedTextColor.DARK_GRAY));
      var2.setItem(18, icon(Material.ANVIL, "裝備欄", List.of(line("點擊背包中的 MMOItems 裝備放入", NamedTextColor.GRAY))));
      var2.setItem(26, icon(Material.AMETHYST_SHARD, "催化欄", List.of(line("強化石可提高成功率", NamedTextColor.GRAY))));
      var2.setItem(31, icon(Material.BOOK, "強化資訊", List.of(line("請先放入裝備", NamedTextColor.GRAY))));
      var2.setItem(40, icon(Material.SMITHING_TABLE, "開始強化", List.of(line("每次只提升一級", NamedTextColor.GRAY))));
      var2.setItem(49, icon(Material.BARRIER, "關閉", List.of()));
      var1.openInventory(var2);
   }

   void shutdown() {
      for(Player var2 : Bukkit.getOnlinePlayers()) {
         if (var2.getOpenInventory().getTopInventory().getHolder() instanceof ForgeHolder) {
            var2.closeInventory();
         }
      }

   }

   @EventHandler
   public void onClick(InventoryClickEvent var1) {
      InventoryHolder var2 = var1.getView().getTopInventory().getHolder();
      if (var2 instanceof ForgeHolder var3) {
         HumanEntity var4 = var1.getWhoClicked();
         if (var4 instanceof Player var5) {
            var1.setCancelled(true);
            int var6 = var1.getRawSlot();
            if (var6 == 49) {
               var5.closeInventory();
               return;
            }

            if (var6 == 40) {
               this.attempt(var5, var3);
               return;
            }

            if (var6 != 20 && var6 != 24) {
               if (var6 < var3.inventory.getSize()) {
                  return;
               }

               ItemStack var12 = var1.getCurrentItem();
               if (var12 != null && var12.getType() != Material.AIR) {
                  int var8 = this.plugin.serviceIsManaged(var12) ? 20 : (this.isCatalyst(var12) ? 24 : -1);
                  if (var8 >= 0 && var3.inventory.getItem(var8) == null) {
                     int var9 = var8 == 20 ? 1 : var12.getAmount();
                     ItemStack var10 = var12.clone();
                     var10.setAmount(var9);
                     var3.inventory.setItem(var8, var10);
                     if (var9 >= var12.getAmount()) {
                        var1.getClickedInventory().setItem(var1.getSlot(), (ItemStack)null);
                     } else {
                        ItemStack var11 = var12.clone();
                        var11.setAmount(var12.getAmount() - var9);
                        var1.getClickedInventory().setItem(var1.getSlot(), var11);
                     }

                     this.updateInfo(var5, var3);
                     return;
                  }

                  var5.sendMessage(Component.text(var8 < 0 ? "這件物品不能放入強化台。" : "目標欄位已有物品。", NamedTextColor.RED));
                  return;
               }

               return;
            }

            ItemStack var7 = var3.inventory.getItem(var6);
            if (var7 != null && var7.getType() != Material.AIR) {
               var3.inventory.setItem(var6, (ItemStack)null);
               this.plugin.serviceGiveOrDrop(var5, var7);
               this.updateInfo(var5, var3);
            }

            return;
         }
      }

   }

   @EventHandler
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof ForgeHolder) {
         var1.setCancelled(true);
      }

   }

   @EventHandler
   public void onClose(InventoryCloseEvent var1) {
      InventoryHolder var2 = var1.getInventory().getHolder();
      if (var2 instanceof ForgeHolder var3) {
         HumanEntity var4 = var1.getPlayer();
         if (var4 instanceof Player var5) {
            this.returnSlot(var5, var3.inventory, 20);
            this.returnSlot(var5, var3.inventory, 24);
            return;
         }
      }

   }

   private void attempt(Player var1, ForgeHolder var2) {
      ItemStack var3 = var2.inventory.getItem(20);
      if (!this.plugin.serviceIsManaged(var3)) {
         var1.sendMessage(Component.text("請先放入 MMOItems 裝備。", NamedTextColor.RED));
      } else if (!this.api.readItemTag(var3, "soulbound_uuid").isBlank() && !this.api.readItemTag(var3, "soulbound_uuid").equalsIgnoreCase(var1.getUniqueId().toString())) {
         var1.sendMessage(Component.text("不能強化其他玩家綁定的裝備。", NamedTextColor.RED));
      } else {
         int var5 = parseInt(this.api.readItemTag(var3, "upgrade_level"), 0);
         if (var5 >= Math.max(1, this.plugin.getConfig().getInt("upgrade.max-level", 20))) {
            var1.sendMessage(Component.text("這件裝備已達強化上限。", NamedTextColor.YELLOW));
         } else {
            ItemStack var6 = var2.inventory.getItem(24);
            int var7 = this.pity(var1);
            long var8 = this.forgeCost(var3, var5 + 1);
            double var10 = Math.max((double)0.25F, Math.min(0.98, this.plugin.getConfig().getDouble("forge.base-success-chance", 0.92) - (double)var5 * this.plugin.getConfig().getDouble("forge.level-penalty", 0.045) + (this.isCatalyst(var6) ? this.plugin.getConfig().getDouble("forge.catalyst-bonus", 0.1) : (double)0.0F) + (double)var7 * this.plugin.getConfig().getDouble("forge.pity-per-failure", 0.035)));
            ItemStack var12 = this.plugin.serviceRebuildUpgrade(var3, var5 + 1);
            if (var12 == null) {
               var1.sendMessage(Component.text("裝備資料無法重建，強化已取消且不會扣款。", NamedTextColor.RED));
            } else if (!this.currency.debit(var1, var8)) {
               var1.sendMessage(Component.text("黃金不足，需要 " + var8 + "。", NamedTextColor.RED));
            } else {
               this.consumeCatalyst(var2.inventory);
               if (ThreadLocalRandom.current().nextDouble() <= var10) {
                  var2.inventory.setItem(20, var12);
                  this.setPity(var1, 0);
                  var1.sendMessage(Component.text("強化成功：+" + (var5 + 1), NamedTextColor.GREEN));
               } else {
                  this.setPity(var1, Math.min(10, var7 + 1));
                  var1.sendMessage(Component.text("強化未成功，裝備沒有損壞；下次成功率已提高。", NamedTextColor.YELLOW));
               }

               this.updateInfo(var1, var2);
            }
         }
      }

   }

   private void updateInfo(Player var1, ForgeHolder var2) {
      ItemStack var3 = var2.inventory.getItem(20);
      if (!this.plugin.serviceIsManaged(var3)) {
         var2.inventory.setItem(31, icon(Material.BOOK, "強化資訊", List.of(line("請先放入裝備", NamedTextColor.GRAY))));
      } else {
         int var4 = parseInt(this.api.readItemTag(var3, "upgrade_level"), 0);
         long var5 = this.forgeCost(var3, var4 + 1);
         double var7 = Math.max((double)0.25F, Math.min(0.98, this.plugin.getConfig().getDouble("forge.base-success-chance", 0.92) - (double)var4 * this.plugin.getConfig().getDouble("forge.level-penalty", 0.045) + (this.isCatalyst(var2.inventory.getItem(24)) ? this.plugin.getConfig().getDouble("forge.catalyst-bonus", 0.1) : (double)0.0F) + (double)this.pity(var1) * this.plugin.getConfig().getDouble("forge.pity-per-failure", 0.035)));
         Inventory var9 = var2.inventory;
         Material var10 = Material.WRITABLE_BOOK;
         Component var11 = line("目前：+" + var4 + " → +" + (var4 + 1), NamedTextColor.WHITE);
         Component var12 = line("費用：" + var5 + " 黃金", NamedTextColor.GOLD);
         Locale var13 = Locale.ROOT;
         var9.setItem(31, icon(var10, "強化資訊", List.of(var11, var12, line("成功率：" + String.format(var13, "%.1f%%", var7 * (double)100.0F), NamedTextColor.AQUA), line("保底層數：" + this.pity(var1), NamedTextColor.GRAY))));
      }

   }

   private long forgeCost(ItemStack var1, int var2) {
      double var3 = parseDouble(this.api.readItemTag(var1, "buy_price"), (double)0.0F);
      double var5 = this.plugin.getConfig().getDouble("forge.base-cost", (double)12.0F) + (double)(var2 * var2) * this.plugin.getConfig().getDouble("forge.level-cost-factor", (double)3.0F) + var3 * this.plugin.getConfig().getDouble("forge.item-value-factor", 0.025);
      return Math.max(1L, Math.min(10000000L, Math.round(var5)));
   }

   private boolean isCatalyst(ItemStack var1) {
      return var1 != null && var1.getType() != Material.AIR && ("UPGRADE_STONE".equalsIgnoreCase(this.api.readItemType(var1)) || !this.api.readItemTag(var1, "material_id").isBlank());
   }

   private void consumeCatalyst(Inventory var1) {
      ItemStack var2 = var1.getItem(24);
      if (this.isCatalyst(var2)) {
         if (var2.getAmount() <= 1) {
            var1.setItem(24, (ItemStack)null);
         } else {
            var2.setAmount(var2.getAmount() - 1);
            var1.setItem(24, var2);
         }
      }

   }

   private int pity(Player var1) {
      Integer var2 = (Integer)var1.getPersistentDataContainer().get(this.pityKey, PersistentDataType.INTEGER);
      return var2 == null ? 0 : Math.max(0, Math.min(10, var2));
   }

   private void setPity(Player var1, int var2) {
      var1.getPersistentDataContainer().set(this.pityKey, PersistentDataType.INTEGER, Math.max(0, Math.min(10, var2)));
   }

   private void returnSlot(Player var1, Inventory var2, int var3) {
      ItemStack var4 = var2.getItem(var3);
      if (var4 != null && var4.getType() != Material.AIR) {
         var2.setItem(var3, (ItemStack)null);
         this.plugin.serviceGiveOrDrop(var1, var4);
      }

   }

   private static ItemStack icon(Material var0, String var1, List<Component> var2) {
      ItemStack var3 = new ItemStack(var0);
      ItemMeta var4 = var3.getItemMeta();
      var4.displayName(line(var1, NamedTextColor.WHITE));
      var4.lore(var2);
      var3.setItemMeta(var4);
      return var3;
   }

   private static Component line(String var0, NamedTextColor var1) {
      return Component.text(var0, var1).decoration(TextDecoration.ITALIC, false);
   }

   private static int parseInt(String var0, int var1) {
      try {
         return Integer.parseInt(var0);
      } catch (RuntimeException var3) {
         return var1;
      }
   }

   private static double parseDouble(String var0, double var1) {
      try {
         return Double.parseDouble(var0);
      } catch (RuntimeException var4) {
         return var1;
      }
   }

   private static final class ForgeHolder implements InventoryHolder {
      private Inventory inventory;

      private ForgeHolder() {
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
