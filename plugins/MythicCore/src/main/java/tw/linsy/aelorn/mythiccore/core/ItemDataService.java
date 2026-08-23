package tw.linsy.aelorn.mythiccore.core;

import io.papermc.paper.persistence.PersistentDataContainerView;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

public final class ItemDataService {
   private static final String STAT_PREFIX = "stat_";
   private static final String TAG_PREFIX = "tag_";
   private static final double EPSILON = 1.0E-6;
   private final Plugin plugin;
   private final StatRegistry registry;
   private final String namespace;
   private final ConcurrentHashMap<String, NamespacedKey> keyCache = new ConcurrentHashMap();
   private volatile int maxItemLevel = 1000;

   public ItemDataService(Plugin var1, StatRegistry var2) {
      this.plugin = var1;
      this.registry = var2;
      this.namespace = (new NamespacedKey(var1, "ns")).getNamespace();
   }

   public void reload(FileConfiguration var1) {
      this.maxItemLevel = Math.max(1, Math.min(10000, var1.getInt("safety.max-item-level", 1000)));
   }

   public int maxItemLevel() {
      return this.maxItemLevel;
   }

   public void writeItemData(ItemMeta var1, String var2, String var3, String var4, int var5, Map<String, Double> var6) {
      PersistentDataContainer var7 = var1.getPersistentDataContainer();
      var7.set(this.key("source"), PersistentDataType.STRING, safe(var2));
      var7.set(this.key("item_type"), PersistentDataType.STRING, safe(var3).toUpperCase(Locale.ROOT));
      var7.set(this.key("item_id"), PersistentDataType.STRING, safe(var4).toUpperCase(Locale.ROOT));
      var7.set(this.key("item_level"), PersistentDataType.INTEGER, Math.max(0, Math.min(this.maxItemLevel, var5)));
      if (var6 != null) {
         for(Map.Entry var9 : var6.entrySet()) {
            if (var9.getKey() != null && var9.getValue() != null) {
               String var10 = this.registry.canonical((String)var9.getKey());
               double var11 = this.registry.sanitize(var10, (Double)var9.getValue());
               if (Math.abs(var11) > 1.0E-6) {
                  this.registry.registerKnownStat(var10);
                  var7.set(this.statKey(var10), PersistentDataType.DOUBLE, var11);
               }
            }
         }

      }
   }

   public void writeItemTags(ItemMeta var1, Map<String, String> var2) {
      if (var2 != null) {
         PersistentDataContainer var3 = var1.getPersistentDataContainer();

         for(Map.Entry var5 : var2.entrySet()) {
            if (var5.getKey() != null && var5.getValue() != null) {
               var3.set(this.tagKey((String)var5.getKey()), PersistentDataType.STRING, (String)var5.getValue());
            }
         }

      }
   }

   public Map<String, Double> readItemStats(ItemStack var1) {
      PersistentDataContainerView var2 = container(var1);
      LinkedHashMap var3 = new LinkedHashMap();
      if (var2 == null) {
         return var3;
      } else {
         for(NamespacedKey var5 : var2.getKeys()) {
            if (this.namespace.equals(var5.getNamespace()) && var5.getKey().startsWith("stat_")) {
               Double var6 = readDouble(var2, var5);
               if (var6 != null && !(Math.abs(var6) <= 1.0E-6)) {
                  String var7 = this.registry.canonical(var5.getKey().substring("stat_".length()));
                  double var8 = this.registry.sanitize(var7, var6);
                  if (Math.abs(var8) > 1.0E-6) {
                     var3.merge(var7, var8, (var2x, var3x) -> this.registry.sanitize(var7, var2x + var3x));
                  }
               }
            }
         }

         return var3;
      }
   }

   public String readItemTag(ItemStack var1, String var2) {
      PersistentDataContainerView var3 = container(var1);
      if (var3 == null) {
         return "";
      } else {
         String var4 = readString(var3, this.tagKey(var2));
         return var4 == null ? "" : var4;
      }
   }

   public String readItemId(ItemStack var1) {
      return this.readPlainString(var1, "item_id");
   }

   public String readItemType(ItemStack var1) {
      return this.readPlainString(var1, "item_type");
   }

   public int readItemLevel(ItemStack var1) {
      PersistentDataContainerView var2 = container(var1);
      if (var2 == null) {
         return 0;
      } else {
         Integer var3 = readInteger(var2, this.key("item_level"));
         return var3 == null ? 0 : Math.max(0, Math.min(this.maxItemLevel, var3));
      }
   }

   public ItemCombatData readCombatData(ItemStack var1) {
      PersistentDataContainerView var2 = container(var1);
      if (var2 == null) {
         return ItemDataService.ItemCombatData.EMPTY;
      } else {
         String var3 = "";
         String var4 = "";
         String var5 = "";
         int var6 = 0;
         LinkedHashMap var7 = new LinkedHashMap();
         LinkedHashMap var8 = new LinkedHashMap();

         for(NamespacedKey var10 : var2.getKeys()) {
            if (this.namespace.equals(var10.getNamespace())) {
               String var11 = var10.getKey();
               if (var11.startsWith("stat_")) {
                  Double var12 = readDouble(var2, var10);
                  if (var12 != null && Math.abs(var12) > 1.0E-6) {
                     String var13 = this.registry.canonical(var11.substring("stat_".length()));
                     double var14 = this.registry.sanitize(var13, var12);
                     if (Math.abs(var14) > 1.0E-6) {
                        var7.merge(var13, var14, (var2x, var3x) -> this.registry.sanitize(var13, var2x + var3x));
                     }
                  }
               } else if (var11.startsWith("tag_")) {
                  String var16 = readString(var2, var10);
                  if (var16 != null && !var16.isBlank()) {
                     var8.put(StatSnapshot.normalize(var11.substring("tag_".length())), var16);
                  }
               } else {
                  switch (var11) {
                     case "source":
                        var3 = orEmpty(readString(var2, var10));
                        break;
                     case "item_type":
                        var4 = orEmpty(readString(var2, var10));
                        break;
                     case "item_id":
                        var5 = orEmpty(readString(var2, var10));
                        break;
                     case "item_level":
                        Integer var18 = readInteger(var2, var10);
                        var6 = var18 == null ? 0 : Math.max(0, Math.min(this.maxItemLevel, var18));
                  }
               }
            }
         }

         if (var3.isEmpty() && var4.isEmpty() && var5.isEmpty() && var7.isEmpty() && var8.isEmpty()) {
            return ItemDataService.ItemCombatData.EMPTY;
         } else {
            return new ItemCombatData(var3, var4, var5, var6, var7, var8);
         }
      }
   }

   private String readPlainString(ItemStack var1, String var2) {
      PersistentDataContainerView var3 = container(var1);
      if (var3 == null) {
         return "";
      } else {
         String var4 = readString(var3, this.key(var2));
         return var4 == null ? "" : var4;
      }
   }

   private static PersistentDataContainerView container(ItemStack var0) {
      return var0 != null && !var0.getType().isAir() ? var0.getPersistentDataContainer() : null;
   }

   private NamespacedKey key(String var1) {
      NamespacedKey var2 = (NamespacedKey)this.keyCache.get(var1);
      return var2 != null ? var2 : (NamespacedKey)this.keyCache.computeIfAbsent(var1, (var1x) -> new NamespacedKey(this.plugin, var1x));
   }

   private NamespacedKey tagKey(String var1) {
      String var10001 = StatSnapshot.normalize(var1);
      return this.key("tag_" + var10001.toLowerCase(Locale.ROOT));
   }

   private NamespacedKey statKey(String var1) {
      return this.key("stat_" + var1.toLowerCase(Locale.ROOT));
   }

   private static Double readDouble(PersistentDataContainerView var0, NamespacedKey var1) {
      try {
         return (Double)var0.get(var1, PersistentDataType.DOUBLE);
      } catch (IllegalArgumentException var3) {
         return null;
      }
   }

   private static Integer readInteger(PersistentDataContainerView var0, NamespacedKey var1) {
      try {
         return (Integer)var0.get(var1, PersistentDataType.INTEGER);
      } catch (IllegalArgumentException var3) {
         return null;
      }
   }

   private static String readString(PersistentDataContainerView var0, NamespacedKey var1) {
      try {
         return (String)var0.get(var1, PersistentDataType.STRING);
      } catch (IllegalArgumentException var3) {
         return null;
      }
   }

   private static String safe(String var0) {
      return var0 == null ? "" : var0.trim();
   }

   private static String orEmpty(String var0) {
      return var0 == null ? "" : var0;
   }

   private static double parseDouble(String var0, double var1) {
      if (var0 != null && !var0.isBlank()) {
         try {
            double var3 = Double.parseDouble(var0.trim());
            return Double.isFinite(var3) ? var3 : var1;
         } catch (NumberFormatException var5) {
            return var1;
         }
      } else {
         return var1;
      }
   }

   public static record ItemCombatData(String source, String type, String id, int level, Map<String, Double> stats, Map<String, String> tags) {
      public static final ItemCombatData EMPTY = new ItemCombatData("", "", "", 0, Map.of(), Map.of());

      public String tag(String var1) {
         return (String)this.tags.getOrDefault(StatSnapshot.normalize(var1), "");
      }

      public int requiredLevel() {
         return (int)Math.ceil(Math.max((double)0.0F, ItemDataService.parseDouble(this.tag("REQUIRED_LEVEL"), (double)0.0F)));
      }

      public String requiredClasses() {
         return this.tag("REQUIRED_CLASS");
      }

      public String setId() {
         return this.tag("SET_ID");
      }

      public String onHitAbility() {
         return this.tag("ABILITY_ON_HIT").toUpperCase(Locale.ROOT);
      }

      public double abilityChance(double var1) {
         return ItemDataService.parseDouble(this.tag("ABILITY_CHANCE"), var1);
      }

      public double abilityPower(double var1) {
         return ItemDataService.parseDouble(this.tag("ABILITY_POWER"), var1);
      }

      public boolean isEmpty() {
         return this.id.isBlank() && this.type.isBlank() && this.stats.isEmpty() && this.tags.isEmpty();
      }
   }
}
