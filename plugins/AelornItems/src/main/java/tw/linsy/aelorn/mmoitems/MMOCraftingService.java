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

final class MMOCraftingService implements Listener {
   private static final int[] CONTENT = new int[]{10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 37, 39, 41, 43};
   private final MMOItemsPlugin plugin;
   private final MythicCoreApi api;
   private final GoldCurrencyService currency;
   private List<Recipe> recipes = List.of();

   MMOCraftingService(MMOItemsPlugin var1, MythicCoreApi var2, GoldCurrencyService var3) {
      this.plugin = var1;
      this.api = var2;
      this.currency = var3;
      Bukkit.getPluginManager().registerEvents(this, var1);
      this.reload();
   }

   void reload() {
      YamlConfiguration var1 = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "recipes.yml"));
      ConfigurationSection var2 = var1.getConfigurationSection("recipes");
      ArrayList<Recipe> var3 = new ArrayList<>();
      if (var2 != null) {
         for(String var5 : var2.getKeys(false)) {
            ConfigurationSection var7 = var2.getConfigurationSection(var5);
            ConfigurationSection var6;
            if (var7 != null && (var6 = var7.getConfigurationSection("result")) != null) {
               ArrayList var8 = new ArrayList();
               ConfigurationSection var9 = var7.getConfigurationSection("ingredients");
               if (var9 != null) {
                  for(String var11 : var9.getKeys(false)) {
                     ConfigurationSection var12 = var9.getConfigurationSection(var11);
                     if (var12 != null) {
                        var8.add(MMOCraftingService.Ingredient.from(var11, var12));
                     }
                  }
               }

               var3.add(new Recipe(var5, var7.getString("name", var5), var6.getString("type", "MATERIAL"), var6.getString("item", var5), var6.getString("tier", "COMMON"), Math.max(1, var6.getInt("level", 1)), Math.max(1, var6.getInt("amount", 1)), Math.max(0L, var7.getLong("gold-cost", 0L)), List.copyOf(var8), var7.getInt("order", var3.size())));
            }
         }
      }

      this.recipes = var3.stream().filter((var0) -> !var0.ingredients().isEmpty()).sorted(Comparator.comparingInt(Recipe::order).thenComparing(Recipe::id)).toList();
   }

   void open(Player var1, int var2) {
      int var5 = Math.max(0, (this.recipes.size() - 1) / CONTENT.length);
      int var6 = Math.max(0, Math.min(var5, var2));
      CraftHolder var7 = new CraftHolder(var6);
      Inventory var4;
      var7.inventory = var4 = Bukkit.createInventory(var7, 54, Component.text("王國工藝台", NamedTextColor.DARK_GRAY));

      int var3;
      for(int var8 = 0; var8 < CONTENT.length && (var3 = var6 * CONTENT.length + var8) < this.recipes.size(); ++var8) {
         Recipe var9 = (Recipe)this.recipes.get(var3);
         ItemStack var10 = this.plugin.serviceCreateItem(var9.resultType(), var9.resultId(), var9.level(), var9.amount(), var9.tier());
         if (var10 != null) {
            ItemMeta var11 = var10.getItemMeta();
            ArrayList var12 = var11.hasLore() ? new ArrayList(var11.lore()) : new ArrayList();
            var12.add(Component.empty());
            var12.add(line("需要材料", NamedTextColor.AQUA));
            var9.ingredients().forEach((var1x) -> var12.add(line("・" + var1x.display() + " × " + var1x.amount(), NamedTextColor.GRAY)));
            if (var9.goldCost() > 0L) {
               var12.add(line("・黃金 × " + var9.goldCost(), NamedTextColor.GOLD));
            }

            var12.add(line("左鍵製作，Shift 製作 5 次", NamedTextColor.GREEN));
            var11.lore(var12);
            var10.setItemMeta(var11);
            var4.setItem(CONTENT[var8], var10);
         }
      }

      var4.setItem(45, icon(Material.BARRIER, "關閉"));
      var4.setItem(46, icon(Material.ARROW, "上一頁"));
      var4.setItem(49, icon(Material.CRAFTING_TABLE, "可用配方：" + this.recipes.size()));
      var4.setItem(53, icon(Material.ARROW, "下一頁"));
      var1.openInventory(var4);
   }

   @EventHandler
   public void onClick(InventoryClickEvent var1) {
      InventoryHolder var2 = var1.getView().getTopInventory().getHolder();
      if (var2 instanceof CraftHolder var3) {
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

            int var7 = this.contentIndex(var6);
            int var8 = var3.page * CONTENT.length + var7;
            if (var7 >= 0 && var8 < this.recipes.size()) {
               this.craft(var5, (Recipe)this.recipes.get(var8), var1.isShiftClick() ? 5 : 1);
               this.open(var5, var3.page);
               return;
            }

            return;
         }
      }

   }

   @EventHandler
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof CraftHolder) {
         var1.setCancelled(true);
      }

   }

   private void craft(Player var1, Recipe var2, int var3) {
      ItemStack var4 = this.plugin.serviceCreateItem(var2.resultType(), var2.resultId(), var2.level(), var2.amount(), var2.tier());
      if (var4 == null) {
         var1.sendMessage(Component.text("配方結果不存在，請通知管理員。", NamedTextColor.RED));
      } else {
         int var5 = var3;

         for(Ingredient var7 : var2.ingredients()) {
            var5 = Math.min(var5, this.count(var1, var7) / var7.amount());
         }

         if (var2.goldCost() > 0L) {
            var5 = Math.min(var5, (int)Math.min(2147483647L, this.currency.available(var1) / var2.goldCost()));
         }

         if (var5 <= 0) {
            var1.sendMessage(Component.text("材料或黃金不足。", NamedTextColor.RED));
         } else {
            ArrayList<ItemStack> var11 = new ArrayList<>(var5);

            for(int var12 = 0; var12 < var5; ++var12) {
               ItemStack var8 = this.plugin.serviceCreateItem(var2.resultType(), var2.resultId(), var2.level(), var2.amount(), var2.tier());
               if (var8 == null) {
                  var1.sendMessage(Component.text("配方成品資料無效，製作已取消且不會消耗材料。", NamedTextColor.RED));
                  return;
               }

               var11.add(var8);
            }

            long var13 = safeMultiply(var2.goldCost(), var5);
            if (var13 > 0L && !this.currency.debit(var1, var13)) {
               var1.sendMessage(Component.text("黃金不足。", NamedTextColor.RED));
            } else {
               for(Ingredient var10 : var2.ingredients()) {
                  this.consume(var1, var10, var10.amount() * var5);
               }

               var11.forEach((var2x) -> this.plugin.serviceGiveOrDrop(var1, var2x));
               var1.sendMessage(Component.text("完成「" + var2.displayName() + "」× " + var5 + "。", NamedTextColor.GREEN));
            }
         }
      }

   }

   private int count(Player var1, Ingredient var2) {
      int var3 = 0;

      for(ItemStack var7 : var1.getInventory().getStorageContents()) {
         if (var2.matches(var7, this.api)) {
            var3 += var7.getAmount();
         }
      }

      return var3;
   }

   private void consume(Player var1, Ingredient var2, int var3) {
      ItemStack[] var4 = var1.getInventory().getStorageContents();
      int var5 = var3;

      for(int var6 = 0; var6 < var4.length && var5 > 0; ++var6) {
         ItemStack var7 = var4[var6];
         if (var2.matches(var7, this.api)) {
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
   }

   private int contentIndex(int var1) {
      for(int var2 = 0; var2 < CONTENT.length; ++var2) {
         if (CONTENT[var2] == var1) {
            return var2;
         }
      }

      return -1;
   }

   private static long safeMultiply(long var0, int var2) {
      if (var0 > 0L && var2 > 0) {
         return var0 > Long.MAX_VALUE / (long)var2 ? Long.MAX_VALUE : var0 * (long)var2;
      } else {
         return 0L;
      }
   }

   private static ItemStack icon(Material var0, String var1) {
      ItemStack var2 = new ItemStack(var0);
      ItemMeta var3 = var2.getItemMeta();
      var3.displayName(line(var1, NamedTextColor.WHITE));
      var2.setItemMeta(var3);
      return var2;
   }

   private static Component line(String var0, NamedTextColor var1) {
      return Component.text(var0, var1).decoration(TextDecoration.ITALIC, false);
   }

   private static record Ingredient(String id, Kind kind, Material material, String type, String itemId, int amount, String display) {
      static Ingredient from(String var0, ConfigurationSection var1) {
         Kind var2;
         try {
            var2 = MMOCraftingService.Kind.valueOf(var1.getString("kind", "VANILLA").toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var4) {
            var2 = MMOCraftingService.Kind.VANILLA;
         }

         Material var3 = Material.matchMaterial(var1.getString("material", var1.getString("item", "STONE")));
         return new Ingredient(var0, var2, var3 == null ? Material.STONE : var3, var1.getString("type", "MATERIAL").toUpperCase(Locale.ROOT), var1.getString("item", var0).toUpperCase(Locale.ROOT), Math.max(1, var1.getInt("amount", 1)), var1.getString("display", var0));
      }

      boolean matches(ItemStack var1, MythicCoreApi var2) {
         if (var1 != null && var1.getType() != Material.AIR) {
            return this.kind == MMOCraftingService.Kind.VANILLA ? var1.getType() == this.material : this.type.equalsIgnoreCase(var2.readItemType(var1)) && this.itemId.equalsIgnoreCase(var2.readItemId(var1));
         } else {
            return false;
         }
      }
   }

   private static record Recipe(String id, String displayName, String resultType, String resultId, String tier, int level, int amount, long goldCost, List<Ingredient> ingredients, int order) {
      private Recipe(String id, String displayName, String resultType, String resultId, String tier, int level, int amount, long goldCost, List<Ingredient> ingredients, int order) {
         id = id.toUpperCase(Locale.ROOT);
         resultType = resultType.toUpperCase(Locale.ROOT);
         resultId = resultId.toUpperCase(Locale.ROOT);
         tier = tier.toUpperCase(Locale.ROOT);
         this.id = id;
         this.displayName = displayName;
         this.resultType = resultType;
         this.resultId = resultId;
         this.tier = tier;
         this.level = level;
         this.amount = amount;
         this.goldCost = goldCost;
         this.ingredients = ingredients;
         this.order = order;
      }
   }

   private static final class CraftHolder implements InventoryHolder {
      private final int page;
      private Inventory inventory;

      private CraftHolder(int var1) {
         this.page = var1;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static enum Kind {
      VANILLA,
      MMOITEM;

      private Kind() {
      }
   }
}
