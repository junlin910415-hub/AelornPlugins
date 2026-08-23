package tw.linsy.aelorn.mmoitems;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

final class MythicDropService {
   private final MMOItemsPlugin plugin;
   private final Map<String, List<DropRule>> rules = new LinkedHashMap();
   private boolean hooked;

   MythicDropService(MMOItemsPlugin var1) {
      this.plugin = var1;
      this.reload();
      this.hook();
   }

   void reload() {
      this.rules.clear();
      YamlConfiguration var1 = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "mythic-drops.yml"));
      ConfigurationSection var2 = var1.getConfigurationSection("mobs");
      if (var2 != null) {
         for(String var4 : var2.getKeys(false)) {
            ConfigurationSection var5 = var2.getConfigurationSection(var4 + ".drops");
            if (var5 != null) {
               ArrayList var6 = new ArrayList();

               for(String var8 : var5.getKeys(false)) {
                  ConfigurationSection var9 = var5.getConfigurationSection(var8);
                  if (var9 != null) {
                     var6.add(new DropRule(var9.getString("type", "MATERIAL"), var9.getString("item", var8), var9.getString("tier", "COMMON"), Math.max(1, var9.getInt("level", 1)), Math.max((double)0.0F, Math.min((double)100.0F, var9.getDouble("chance", (double)100.0F))), Math.max(1, var9.getInt("amount-min", 1)), Math.max(1, var9.getInt("amount-max", 1))));
                  }
               }

               if (!var6.isEmpty()) {
                  this.rules.put(var4.toUpperCase(Locale.ROOT), List.copyOf(var6));
               }
            }
         }
      }

   }

   private void hook() {
      if (!this.hooked && this.plugin.getConfig().getBoolean("mythic-drops.enabled", true)) {
         try {
            Class var1 = Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent");
            Class var2 = var1.asSubclass(Event.class);
            Listener var3 = new Listener() {
            };
            Bukkit.getPluginManager().registerEvent(var2, var3, EventPriority.MONITOR, (var1x, var2x) -> this.handle(var2x), this.plugin, true);
            this.hooked = true;
            this.plugin.getLogger().info("已掛接 MythicMobs 掉落事件，共 " + this.rules.size() + " 種生物規則。");
         } catch (ReflectiveOperationException | RuntimeException | LinkageError var4) {
            this.plugin.getLogger().warning("MythicMobs 掉落掛勾未啟用：" + var4.getClass().getSimpleName());
         }
      }

   }

   private void handle(Event var1) {
      String var3 = this.resolveMobId(var1);
      List var4 = (List)this.rules.get(var3);
      Entity var2;
      if (var4 != null && !var4.isEmpty() && (var2 = this.resolveEntity(var1)) != null) {
         Location var5 = var2.getLocation().clone();
         Bukkit.getRegionScheduler().execute(this.plugin, var5.getWorld(), var5.getBlockX() >> 4, var5.getBlockZ() >> 4, () -> this.rollDrops(var5, var4));
      }

   }

   private void rollDrops(Location var1, List<DropRule> var2) {
      for(DropRule var4 : var2) {
         if (!(ThreadLocalRandom.current().nextDouble((double)100.0F) > var4.chance())) {
            int var5 = Math.max(var4.minAmount(), var4.maxAmount());
            int var6 = ThreadLocalRandom.current().nextInt(var4.minAmount(), var5 + 1);
            ItemStack var7 = this.plugin.serviceCreateItem(var4.type(), var4.itemId(), var4.level(), var6, var4.tier());
            if (var7 != null) {
               var1.getWorld().dropItemNaturally(var1, var7);
            }
         }
      }

   }

   private String resolveMobId(Event var1) {
      Object var2 = this.invoke(var1, "getMob");
      Object var3 = this.invoke(var2, "getType");
      Object var4 = this.invoke(var3, "getInternalName");
      if (var4 == null) {
         var4 = this.invoke(var3, "getId");
      }

      if (var4 == null) {
         var4 = this.invoke(var2, "getMobType");
      }

      return var4 == null ? "" : var4.toString().toUpperCase(Locale.ROOT);
   }

   private Entity resolveEntity(Event var1) {
      Object var3 = this.invoke(var1, "getEntity");
      if (var3 instanceof Entity var8) {
         return var8;
      } else {
         Object var4 = this.invoke(var1, "getMob");
         Object var5 = this.invoke(var4, "getEntity");
         Object var6 = this.invoke(var5, "getBukkitEntity");
         Entity var2;
         Entity var7 = var6 instanceof Entity ? (var2 = (Entity)var6) : null;
         return var7;
      }
   }

   private Object invoke(Object var1, String var2) {
      if (var1 == null) {
         return null;
      } else {
         try {
            Method var3 = var1.getClass().getMethod(var2);
            return var3.invoke(var1);
         } catch (RuntimeException | ReflectiveOperationException var4) {
            return null;
         }
      }
   }

   private static record DropRule(String type, String itemId, String tier, int level, double chance, int minAmount, int maxAmount) {
      private DropRule(String type, String itemId, String tier, int level, double chance, int minAmount, int maxAmount) {
         type = type.toUpperCase(Locale.ROOT);
         itemId = itemId.toUpperCase(Locale.ROOT);
         tier = tier.toUpperCase(Locale.ROOT);
         this.type = type;
         this.itemId = itemId;
         this.tier = tier;
         this.level = level;
         this.chance = chance;
         this.minAmount = minAmount;
         this.maxAmount = maxAmount;
      }
   }
}
