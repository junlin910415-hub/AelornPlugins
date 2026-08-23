package tw.linsy.aelorn.mythiccore.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

public final class StatSnapshotService {
   private final StatRegistry registry;
   private final ItemDataService itemData;
   private final PlayerClassStateService classStates;
   private final ConcurrentHashMap<UUID, CachedSnapshot> cache = new ConcurrentHashMap();
   private volatile long cacheTtlNanos = 100000000L;
   private static final NamespacedKey ACCESSORY_STATS_KEY = new NamespacedKey("aelorn", "accessory_stats");

   public StatSnapshotService(StatRegistry var1, ItemDataService var2, PlayerClassStateService var3) {
      this.registry = var1;
      this.itemData = var2;
      this.classStates = var3;
   }

   public void reload(FileConfiguration var1) {
      long var2 = Math.max(0L, Math.min(1000L, var1.getLong("performance.snapshot-cache-ms", 100L)));
      this.cacheTtlNanos = var2 * 1000000L;
      this.cache.clear();
   }

   public StatSnapshot snapshot(LivingEntity var1) {
      if (var1 == null) {
         return new StatSnapshot(this.registry.baseStats());
      } else {
         long var3 = this.cacheTtlNanos;
         UUID var5 = var1.getUniqueId();
         Object var2;
         if (var3 > 0L && (var2 = this.cache.get(var5)) != null && System.nanoTime() - ((CachedSnapshot)var2).atNanos() < var3) {
            return ((CachedSnapshot)var2).snapshot();
         } else {
            StatSnapshot var8 = this.build(var1);
            if (var3 > 0L) {
               this.cache.put(var5, new CachedSnapshot(System.nanoTime(), var8));
               if (this.cache.size() > 2048) {
                  long var6 = System.nanoTime();
                  this.cache.entrySet().removeIf((var4) -> var6 - ((CachedSnapshot)var4.getValue()).atNanos() >= var3);
               }
            }

            return var8;
         }
      }
   }

   public void invalidate(UUID var1) {
      if (var1 != null) {
         this.cache.remove(var1);
      }

   }

   public void clearCache() {
      this.cache.clear();
   }

   private StatSnapshot build(LivingEntity var1) {
      LinkedHashMap var2 = new LinkedHashMap(this.registry.baseStats());
      this.mergeAttribute(var2, var1, Attribute.MAX_HEALTH, "MAX_HEALTH");
      this.mergeAttribute(var2, var1, Attribute.MOVEMENT_SPEED, "MOVEMENT_SPEED");
      LinkedHashMap var3 = new LinkedHashMap();
      EntityEquipment var4 = var1.getEquipment();
      if (var4 != null) {
         Player var10000;
         if (var1 instanceof Player) {
            Player var6 = (Player)var1;
            var10000 = var6;
         } else {
            var10000 = null;
         }

         Player var5 = var10000;
         this.mergeItem(var2, var3, var4.getItemInMainHand(), var5);
         this.mergeItem(var2, var3, var4.getItemInOffHand(), var5);

         for(ItemStack var9 : var4.getArmorContents()) {
            this.mergeItem(var2, var3, var9, var5);
         }
      }

      this.mergeAccessoryStats(var2, var1);
      this.applySetBonuses(var2, var3);
      this.registry.sanitizeAll(var2);
      return new StatSnapshot(var2);
   }

   private void mergeAccessoryStats(Map<String, Double> var1, LivingEntity var2) {
      if (var2 instanceof Player var3) {
         String var4 = (String)var3.getPersistentDataContainer().get(ACCESSORY_STATS_KEY, PersistentDataType.STRING);
         if (var4 != null && !var4.isBlank()) {
            LinkedHashMap var5 = new LinkedHashMap();

            for(String var9 : var4.split(";")) {
               int var10 = var9.indexOf(58);
               if (var10 > 0 && var10 < var9.length() - 1) {
                  try {
                     double var11 = Double.parseDouble(var9.substring(var10 + 1));
                     if (Double.isFinite(var11) && Math.abs(var11) > 1.0E-6) {
                        var5.merge(var9.substring(0, var10), var11, Double::sum);
                     }
                  } catch (NumberFormatException var13) {
                  }
               }
            }

            if (!var5.isEmpty()) {
               this.registry.mergeInto(var1, var5);
            }

         }
      }
   }

   private void mergeAttribute(Map<String, Double> var1, LivingEntity var2, Attribute var3, String var4) {
      AttributeInstance var5 = var2.getAttribute(var3);
      if (var5 != null) {
         var1.merge(var4, this.registry.sanitize(var4, var5.getValue()), Double::sum);
      }

   }

   private void mergeItem(Map<String, Double> var1, Map<String, Integer> var2, ItemStack var3, Player var4) {
      ItemDataService.ItemCombatData var5 = this.itemData.readCombatData(var3);
      if (!var5.isEmpty()) {
         if (var4 == null || this.classStates.itemUseFailure(var4, var5).isBlank()) {
            this.registry.mergeInto(var1, var5.stats());
            String var6 = var5.setId();
            if (!var6.isBlank()) {
               var2.merge(StatSnapshot.normalize(var6), 1, Integer::sum);
            }

         }
      }
   }

   private void applySetBonuses(Map<String, Double> var1, Map<String, Integer> var2) {
      for(Map.Entry var4 : var2.entrySet()) {
         Map var5 = this.registry.setBonusesFor((String)var4.getKey());

         for(Map.Entry var7 : var5.entrySet()) {
            if ((Integer)var4.getValue() >= (Integer)var7.getKey()) {
               this.registry.mergeInto(var1, (Map)var7.getValue());
            }
         }
      }

   }

   private static record CachedSnapshot(long atNanos, StatSnapshot snapshot) {
   }
}
