package tw.linsy.aelorn.mmoitems;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;

final class MMOShopService implements Listener {
   private static final int[] CONTENT = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
   private final MMOItemsPlugin plugin;
   private final MythicCoreApi api;
   private final GoldCurrencyService currency;
   private List<ShopEntry> entries = List.of();

   MMOShopService(MMOItemsPlugin var1, MythicCoreApi var2, GoldCurrencyService var3) {
      this.plugin = var1;
      this.api = var2;
      this.currency = var3;
      Bukkit.getPluginManager().registerEvents(this, var1);
      this.reload();
   }

   void reload() {
      YamlConfiguration var1 = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "shops.yml"));
      ConfigurationSection var2 = var1.getConfigurationSection("shops");
      ArrayList var3 = new ArrayList();
      if (var2 != null) {
         for(String var5 : var2.getKeys(false)) {
            ConfigurationSection var6 = var2.getConfigurationSection(var5 + ".entries");
            if (var6 != null) {
               for(String var8 : var6.getKeys(false)) {
                  ConfigurationSection var9 = var6.getConfigurationSection(var8);
                  if (var9 != null) {
                     var3.add(new ShopEntry(var8, var9.getString("type", "MATERIAL"), var9.getString("item", var8), var9.getString("tier", "COMMON"), Math.max(1, var9.getInt("level", 1)), Math.max(1L, var9.getLong("buy", 1L)), Math.max(0L, var9.getLong("sell", 0L)), var9.getInt("order", var3.size())));
                  }
               }
            }
         }
      }

      this.entries = var3.stream().sorted(Comparator.comparingInt(ShopEntry::order).thenComparing(ShopEntry::id)).toList();
   }

   void open(Player var1, int var2) {
      int var5 = Math.max(0, (this.entries.size() - 1) / CONTENT.length);
      int var6 = Math.max(0, Math.min(var5, var2));
      ShopHolder var7 = new ShopHolder(var6);
      Inventory var4;
      var7.inventory = var4 = Bukkit.createInventory(var7, 54, Component.text("王國交易所", NamedTextColor.DARK_GRAY));

      int var3;
      for(int var8 = 0; var8 < CONTENT.length && (var3 = var6 * CONTENT.length + var8) < this.entries.size(); ++var8) {
         ShopEntry var9 = (ShopEntry)this.entries.get(var3);
         ItemStack var10 = this.plugin.serviceCreateItem(var9.type(), var9.itemId(), var9.level(), 1, var9.tier());
         if (var10 != null) {
            ItemMeta var11 = var10.getItemMeta();
            ArrayList var12 = var11.hasLore() ? new ArrayList(var11.lore()) : new ArrayList();
            var12.add(Component.empty());
            var12.add(line("左鍵購買：" + var9.buy() + " 黃金", NamedTextColor.GOLD));
            var12.add(line("右鍵出售：" + var9.sell() + " 黃金", NamedTextColor.YELLOW));
            var12.add(line("Shift 一次交易 5 件", NamedTextColor.DARK_GRAY));
            var11.lore(var12);
            var10.setItemMeta(var11);
            var4.setItem(CONTENT[var8], var10);
         }
      }

      var4.setItem(45, icon(Material.BARRIER, "關閉", List.of()));
      var4.setItem(46, icon(Material.ARROW, "上一頁", List.of()));
      var4.setItem(49, icon(Material.GOLD_INGOT, "黃金帳戶", this.currency.balanceLore(var1)));
      var4.setItem(53, icon(Material.ARROW, "下一頁", List.of()));
      var1.openInventory(var4);
   }

   @EventHandler
   public void onClick(InventoryClickEvent var1) {
      InventoryHolder var2 = var1.getView().getTopInventory().getHolder();
      if (var2 instanceof ShopHolder var3) {
         HumanEntity var4 = var1.getWhoClicked();
         if (var4 instanceof Player var5) {
            var1.setCancelled(true);
            int var6 = var1.getRawSlot();
            if (var6 == 45) {
               var5.closeInventory();
               return;
            }

            if (var6 == 46) {
               this.open(var5, var3.page - 1);
               return;
            }

            if (var6 == 53) {
               this.open(var5, var3.page + 1);
               return;
            }

            if (var6 == 49) {
               long var12 = this.currency.depositAll(var5);
               var5.sendMessage(Component.text("已存入 " + var12 + " 黃金。", NamedTextColor.GOLD));
               this.open(var5, var3.page);
               return;
            }

            int var7 = this.contentIndex(var6);
            int var8 = var3.page * CONTENT.length + var7;
            if (var7 >= 0 && var8 < this.entries.size()) {
               ShopEntry var10 = (ShopEntry)this.entries.get(var8);
               int var9 = var1.isShiftClick() ? 5 : 1;
               if (var1.isRightClick()) {
                  this.sell(var5, var10, var9);
               } else {
                  this.buy(var5, var10, var9);
               }

               this.open(var5, var3.page);
               return;
            }

            return;
         }
      }

   }

   @EventHandler
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof ShopHolder) {
         var1.setCancelled(true);
      }

   }

   private void buy(Player var1, ShopEntry var2, int var3) {
      long var4 = var2.buy() > Long.MAX_VALUE / (long)Math.max(1, var3) ? Long.MAX_VALUE : var2.buy() * (long)var3;
      ArrayList<ItemStack> var6 = new ArrayList<>(var3);

      for(int var7 = 0; var7 < var3; ++var7) {
         ItemStack var8 = this.plugin.serviceCreateItem(var2.type(), var2.itemId(), var2.level(), 1, var2.tier());
         if (var8 == null) {
            var1.sendMessage(Component.text("商品資料無效，交易已取消且不會扣款。", NamedTextColor.RED));
            return;
         }

         var6.add(var8);
      }

      if (!this.currency.debit(var1, var4)) {
         var1.sendMessage(Component.text("黃金不足，需要 " + var4 + "。", NamedTextColor.RED));
      } else {
         var6.forEach((var2x) -> this.plugin.serviceGiveOrDrop(var1, var2x));
         var1.sendMessage(Component.text("購買完成，支付 " + var4 + " 黃金。", NamedTextColor.GREEN));
      }

   }

   private void sell(Player var1, ShopEntry var2, int var3) {
      if (var2.sell() <= 0L) {
         var1.sendMessage(Component.text("這項物品不提供回收。", NamedTextColor.RED));
      } else {
         int var4 = (int)Math.min((long)var3, this.currency.creditCapacity(var1) / var2.sell());
         if (var4 <= 0) {
            var1.sendMessage(Component.text("黃金錢包已滿，無法回收物品。", NamedTextColor.RED));
         } else {
            int var5 = this.removeMatching(var1, var2, var4);
            if (var5 <= 0) {
               var1.sendMessage(Component.text("背包中沒有可出售且未綁定的對應物品。", NamedTextColor.RED));
            } else {
               long var6 = var2.sell() * (long)var5;
               long var8 = this.currency.credit(var1, var6);
               if (var8 != var6) {
                  this.plugin.getLogger().severe("商店回收入帳不完整：" + String.valueOf(var1.getUniqueId()) + " expected=" + var6 + " credited=" + var8);
               }

               var1.sendMessage(Component.text("出售 " + var5 + " 件物品，獲得 " + var6 + " 黃金。", NamedTextColor.GOLD));
            }
         }
      }

   }

   private int removeMatching(Player var1, ShopEntry var2, int var3) {
      ItemStack[] var4 = var1.getInventory().getStorageContents();
      int var5 = var3;

      for(int var6 = 0; var6 < var4.length && var5 > 0; ++var6) {
         ItemStack var7 = var4[var6];
         if (var7 != null && var7.getType() != Material.AIR && var2.type().equalsIgnoreCase(this.api.readItemType(var7)) && var2.itemId().equalsIgnoreCase(this.api.readItemId(var7)) && this.api.readItemTag(var7, "soulbound_uuid").isBlank()) {
            int var8 = Math.min(var5, var7.getAmount());
            var5 -= var8;
            if (var8 == var7.getAmount()) {
               var4[var6] = null;
            } else {
               ItemStack var9 = var7.clone();
               var9.setAmount(var7.getAmount() - var8);
               var4[var6] = var9;
            }
         }
      }

      var1.getInventory().setStorageContents(var4);
      return var3 - var5;
   }

   private int contentIndex(int var1) {
      for(int var2 = 0; var2 < CONTENT.length; ++var2) {
         if (CONTENT[var2] == var1) {
            return var2;
         }
      }

      return -1;
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

   private static record ShopEntry(String id, String type, String itemId, String tier, int level, long buy, long sell, int order) {
      private ShopEntry(String id, String type, String itemId, String tier, int level, long buy, long sell, int order) {
         id = id.toUpperCase(Locale.ROOT);
         type = type.toUpperCase(Locale.ROOT);
         itemId = itemId.toUpperCase(Locale.ROOT);
         tier = tier.toUpperCase(Locale.ROOT);
         this.id = id;
         this.type = type;
         this.itemId = itemId;
         this.tier = tier;
         this.level = level;
         this.buy = buy;
         this.sell = sell;
         this.order = order;
      }
   }

   private static final class ShopHolder implements InventoryHolder {
      private final int page;
      private Inventory inventory;

      private ShopHolder(int var1) {
         this.page = var1;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
