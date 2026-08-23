package tw.linsy.aelorn.mmoitems;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

final class MMOValidationService {
   private final MMOItemsPlugin plugin;
   private final MythicCoreApi api;

   MMOValidationService(MMOItemsPlugin var1, MythicCoreApi var2) {
      this.plugin = var1;
      this.api = var2;
   }

   ValidationReport validate() {
      ValidationContext var1 = new ValidationContext();
      this.validateTemplates(var1);
      this.validateRecipes(var1);
      this.validateShops(var1);
      this.validateDrops(var1);
      this.validateForge(var1);
      return var1.report();
   }

   private void validateTemplates(ValidationContext var1) {
      List<ItemTemplate> var2 = this.plugin.serviceTemplates();
      if (var2.isEmpty()) {
         var1.error("沒有可用的物品模板");
      } else {
         for(ItemTemplate var4 : var2) {
            var1.check();
            String var8 = var4.type();
            String var9 = var8 + "." + var4.id();
            if (!this.plugin.serviceHasType(var4.type())) {
               var1.error(var9 + " 使用不存在的物品類型 " + var4.type());
            }

            if (!this.plugin.serviceHasTier(var4.tier())) {
               var1.error(var9 + " 使用不存在的階級 " + var4.tier());
            }

            if (var4.material() == Material.AIR || !var4.material().isItem()) {
               var1.error(var9 + " 使用無效原版材質");
            }

            if (!var4.setId().isBlank() && !this.plugin.serviceHasSet(var4.setId())) {
               var1.error(var9 + " 指向不存在的套裝 " + var4.setId());
            }

            if (!var4.upgradeTemplate().isBlank() && !this.plugin.serviceHasUpgrade(var4.upgradeTemplate())) {
               var1.error(var9 + " 指向不存在的強化模板 " + var4.upgradeTemplate());
            }

            for(String var13 : var4.requiredClass().split("[,;/|]+")) {
               if (!var13.isBlank() && this.api.classProfile(var13) == null) {
                  var1.error(var9 + " 使用不存在的需求職業 " + var13);
               }
            }

            var4.requiredSkills().forEach((var2x, var3) -> {
               if (var3 == null || var3 < 1 || var3 > 200) {
                  var1.error(var9 + " 的 " + var2x + " 穿戴需求必須介於 1 到 200");
               }

            });
            if (var4.requiredQuests().stream().anyMatch(String::isBlank)) {
               var1.error(var9 + " 含有空白的任務需求");
            }

            if (var4.majorIdentification().enabled() && var4.majorIdentification().displayName().isBlank()) {
               var1.error(var9 + " 的大型特性缺少顯示名稱");
            }

            if (!var4.itemModel().isBlank() && !var4.itemModel().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
               var1.error(var9 + " 的 item-model 格式無效");
            }

            if (var4.statsAtLevel(Math.max(1, var4.requiredLevel()), new Random(23063L)).entrySet().stream().anyMatch((var0) -> ((String)var0.getKey()).isBlank() || var0.getValue() == null || !Double.isFinite((Double)var0.getValue()))) {
               var1.error(var9 + " 產生了無效屬性");
            }

            ItemStack var6;
            if ((var6 = this.plugin.serviceCreateItem(var4.type(), var4.id(), Math.max(1, var4.requiredLevel()), 1, var4.tier())) != null) {
               String var10001 = this.api.readItemType(var6);
               if (var9.equalsIgnoreCase(var10001 + "." + this.api.readItemId(var6))) {
                  continue;
               }
            }

            var1.error(var9 + " 無法建立有效物品實例");
         }
      }

   }

   private void validateRecipes(ValidationContext var1) {
      YamlConfiguration var2 = this.load("recipes.yml");
      ConfigurationSection var3 = var2.getConfigurationSection("recipes");
      if (var3 == null) {
         var1.error("recipes.yml 缺少 recipes 區段");
      } else {
         for(String var5 : var3.getKeys(false)) {
            var1.check();
            ConfigurationSection var10 = var3.getConfigurationSection(var5);
            ConfigurationSection var9 = var10 == null ? null : var10.getConfigurationSection("result");
            if (var9 == null || !this.templateExists(var9.getString("type", ""), var9.getString("item", ""))) {
               var1.error("配方 " + var5 + " 的成品模板不存在");
            }

            long var7 = var10 == null ? -1L : var10.getLong("gold-cost", 0L);
            if (var7 < 0L) {
               var1.error("配方 " + var5 + " 的黃金費用不能為負數");
            }

            ConfigurationSection var6 = var10 == null ? null : var10.getConfigurationSection("ingredients");
            if (var6 != null && !var6.getKeys(false).isEmpty()) {
               for(String var16 : var6.getKeys(false)) {
                  ConfigurationSection var17 = var6.getConfigurationSection(var16);
                  if (var17 != null && var17.getInt("amount", 0) > 0) {
                     if (var17.getString("kind", "VANILLA").equalsIgnoreCase("MMOITEM") && !this.templateExists(var17.getString("type", ""), var17.getString("item", ""))) {
                        var1.error("配方 " + var5 + " 的 MMO 材料 " + var16 + " 不存在");
                     }

                     if (var17.getString("kind", "VANILLA").equalsIgnoreCase("VANILLA") && Material.matchMaterial(var17.getString("material", "")) == null) {
                        var1.error("配方 " + var5 + " 的原版材料 " + var16 + " 無效");
                     }
                  } else {
                     var1.error("配方 " + var5 + " 的材料 " + var16 + " 數量無效");
                  }
               }
            } else {
               var1.error("配方 " + var5 + " 沒有材料");
            }
         }
      }

   }

   private void validateShops(ValidationContext var1) {
      YamlConfiguration var2 = this.load("shops.yml");
      ConfigurationSection var3 = var2.getConfigurationSection("shops");
      if (var3 == null) {
         var1.error("shops.yml 缺少 shops 區段");
      } else {
         for(String var5 : var3.getKeys(false)) {
            ConfigurationSection var6 = var3.getConfigurationSection(var5 + ".entries");
            if (var6 == null) {
               var1.error("商店 " + var5 + " 沒有商品");
            } else {
               for(String var8 : var6.getKeys(false)) {
                  var1.check();
                  ConfigurationSection var9 = var6.getConfigurationSection(var8);
                  if (var9 != null && this.templateExists(var9.getString("type", ""), var9.getString("item", ""))) {
                     long var10 = var9.getLong("buy", -1L);
                     long var12 = var9.getLong("sell", -1L);
                     if (var10 >= 0L && var12 >= 0L) {
                        if (var10 > 0L && var12 >= var10) {
                           var1.error("商店商品 " + var5 + "." + var8 + " 的回收價不得高於或等於售價");
                        }
                     } else {
                        var1.error("商店商品 " + var5 + "." + var8 + " 價格不能為負數");
                     }
                  } else {
                     var1.error("商店商品 " + var5 + "." + var8 + " 的模板不存在");
                  }
               }
            }
         }
      }

   }

   private void validateDrops(ValidationContext var1) {
      YamlConfiguration var2 = this.load("mythic-drops.yml");
      ConfigurationSection var3 = var2.getConfigurationSection("mobs");
      if (var3 == null) {
         var1.error("mythic-drops.yml 缺少 mobs 區段");
      } else {
         for(String var5 : var3.getKeys(false)) {
            ConfigurationSection var6 = var3.getConfigurationSection(var5 + ".drops");
            if (var6 == null) {
               var1.warning("MythicMobs " + var5 + " 沒有 MMOItems 掉落");
            } else {
               for(String var8 : var6.getKeys(false)) {
                  var1.check();
                  ConfigurationSection var9 = var6.getConfigurationSection(var8);
                  if (var9 != null && this.templateExists(var9.getString("type", ""), var9.getString("item", ""))) {
                     double var10 = var9.getDouble("chance", (double)-1.0F);
                     int var12 = var9.getInt("amount-min", 0);
                     int var13 = var9.getInt("amount-max", 0);
                     if (!Double.isFinite(var10) || var10 < (double)0.0F || var10 > (double)100.0F) {
                        var1.error("掉落 " + var5 + "." + var8 + " 的機率必須介於 0 到 100");
                     }

                     if (var12 <= 0 || var13 < var12 || var13 > 64) {
                        var1.error("掉落 " + var5 + "." + var8 + " 的數量範圍無效");
                     }
                  } else {
                     var1.error("掉落 " + var5 + "." + var8 + " 的模板不存在");
                  }
               }
            }
         }
      }

   }

   private void validateForge(ValidationContext var1) {
      var1.check();
      double var2 = this.plugin.getConfig().getDouble("forge.base-success-chance", 0.92);
      double var4 = this.plugin.getConfig().getDouble("forge.level-penalty", 0.045);
      double var6 = this.plugin.getConfig().getDouble("forge.pity-per-failure", 0.035);
      if (!finiteRange(var2, (double)0.0F, (double)1.0F) || !finiteRange(var4, (double)0.0F, (double)0.25F) || !finiteRange(var6, (double)0.0F, (double)0.25F)) {
         var1.error("強化成功率、等級懲罰或保底設定超出安全範圍");
      }

      if (this.plugin.getConfig().getInt("upgrade.max-level", 0) <= 0) {
         var1.error("強化上限必須大於 0");
      }

   }

   private boolean templateExists(String var1, String var2) {
      return this.plugin.serviceTemplate(StatSnapshot.normalize(var1), StatSnapshot.normalize(var2)) != null;
   }

   private YamlConfiguration load(String var1) {
      return YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), var1));
   }

   private static boolean finiteRange(double var0, double var2, double var4) {
      return Double.isFinite(var0) && var0 >= var2 && var0 <= var4;
   }

   private static final class ValidationContext {
      private int checks;
      private final List<String> errors = new ArrayList();
      private final List<String> warnings = new ArrayList();

      private ValidationContext() {
      }

      void check() {
         ++this.checks;
      }

      void error(String var1) {
         this.errors.add(var1);
      }

      void warning(String var1) {
         this.warnings.add(var1);
      }

      ValidationReport report() {
         return new ValidationReport(this.checks, this.errors, this.warnings);
      }
   }

   static record ValidationReport(int checks, List<String> errors, List<String> warnings) {
      ValidationReport(int checks, List<String> errors, List<String> warnings) {
         checks = Math.max(0, checks);
         errors = List.copyOf(errors);
         warnings = List.copyOf(warnings);
         this.checks = checks;
         this.errors = errors;
         this.warnings = warnings;
      }

      boolean passed() {
         return this.errors.isEmpty();
      }

      String summary() {
         return String.format(Locale.ROOT, "檢查 %d 項，錯誤 %d，警告 %d", this.checks, this.errors.size(), this.warnings.size());
      }
   }
}
