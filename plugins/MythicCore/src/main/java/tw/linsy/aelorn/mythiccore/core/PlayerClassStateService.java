package tw.linsy.aelorn.mythiccore.core;

import java.io.File;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import tw.linsy.aelorn.mythiccore.api.ClassStageProfile;
import tw.linsy.aelorn.mythiccore.api.PlayerClassState;
import tw.linsy.aelorn.mythiccore.api.StatSnapshot;

public final class PlayerClassStateService {
   public static final String SOURCE_EVENT = "RPGCORE_EVENT";
   public static final String SOURCE_FILE = "RPGCORE_FILE";
   private final Plugin plugin;
   private final StageResolver stageResolver;
   private final ConcurrentHashMap<UUID, PlayerClassState> states = new ConcurrentHashMap();
   private final ConcurrentHashMap<String, Method> methodCache = new ConcurrentHashMap();
   private volatile boolean profileSyncEnabled = true;
   private volatile String rpgDataFolder = "RPGCore";
   private volatile int maxLevel = 1000;

   public PlayerClassStateService(Plugin var1, StageResolver var2) {
      this.plugin = var1;
      this.stageResolver = var2;
   }

   public void reload(FileConfiguration var1) {
      this.profileSyncEnabled = var1.getBoolean("integration.rpgcore.player-profile-sync", true);
      String var2 = var1.getString("integration.rpgcore.data-folder", "RPGCore");
      this.rpgDataFolder = var2 != null && !var2.isBlank() ? var2 : "RPGCore";
      this.maxLevel = Math.max(1, Math.min(10000, var1.getInt("safety.max-item-level", 1000)));
   }

   public PlayerClassState state(UUID var1) {
      return var1 == null ? null : (PlayerClassState)this.states.get(var1);
   }

   public void remove(UUID var1) {
      this.states.remove(var1);
   }

   public void clear() {
      this.states.clear();
   }

   public void captureFromEvent(Event var1) {
      Object var2 = this.invokeNoArgs(var1, "player");
      Object var3 = this.invokeNoArgs(var1, "character");
      if (var2 instanceof Player var4) {
         if (var3 != null) {
            String var5 = objectString(this.invokeNoArgs(var3, "classId"));
            String var6 = objectString(this.invokeNoArgs(var3, "name"));
            int var7 = toInt(this.invokeNoArgs(var3, "level"), 1);
            Map var8 = readSkillPoints(this.invokeNoArgs(var3, "skillPoints"));
            this.states.put(var4.getUniqueId(), this.createState(var5, var6, var7, var8, "RPGCORE_EVENT"));
            return;
         }
      }

   }

   public void loadProfileAsync(Player var1) {
      if (this.profileSyncEnabled) {
         UUID var2 = var1.getUniqueId();
         File var3 = new File(new File(this.plugin.getDataFolder().getParentFile(), this.rpgDataFolder + "/player-data"), String.valueOf(var2) + ".yml");
         Bukkit.getAsyncScheduler().runNow(this.plugin, (var3x) -> {
            if (var3.isFile()) {
               YamlConfiguration var4 = YamlConfiguration.loadConfiguration(var3);
               int var5 = var4.getInt("active-slot", -1);
               ConfigurationSection var6 = var4.getConfigurationSection("characters." + var5);
               if (var6 != null) {
                  Map var7 = readIntegerMap(var6.getConfigurationSection("skill-points"));
                  PlayerClassState var8 = this.createState(var6.getString("class", ""), var6.getString("name", ""), var6.getInt("level", 1), var7, "RPGCORE_FILE");
                  if (!var8.classId().isBlank()) {
                     this.states.merge(var2, var8, (var0, var1) -> "RPGCORE_EVENT".equals(var0.source()) ? var0 : var1);
                  }
               }
            }
         });
      }
   }

   public PlayerClassState createState(String var1, String var2, int var3, Map<String, Integer> var4, String var5) {
      String var6 = StatRegistry.normalizeClassId(var1);
      int var7 = Math.max(1, Math.min(this.maxLevel, var3));
      ClassStageProfile var8 = this.stageResolver.stageAt(var6, var7);
      return new PlayerClassState(var6, var2, var7, var8 == null ? "" : var8.id(), var8 == null ? "" : var8.displayName(), var4, var5, System.currentTimeMillis());
   }

   public String itemUseFailure(Player var1, ItemDataService.ItemCombatData var2) {
      if (var2 != null && !var2.isEmpty()) {
         PlayerClassState var3 = (PlayerClassState)this.states.get(var1.getUniqueId());
         int var4 = var3 == null ? Math.max(1, var1.getLevel()) : var3.level();
         int var5 = var2.requiredLevel();
         if (var5 > var4 && !var1.hasPermission("mmoitems.bypass.level")) {
            return "等級不足";
         } else {
            String var6 = var2.requiredClasses();
            if (!var6.isBlank() && !var1.hasPermission("mmoitems.bypass.class")) {
               if (var3 != null && !var3.classId().isBlank()) {
                  for(String var10 : var6.split("[,;/|]+")) {
                     if (StatRegistry.normalizeClassId(var10).equals(var3.classId())) {
                        return "";
                     }
                  }

                  return "職業不符";
               } else {
                  return "尚未載入職業";
               }
            } else {
               return "";
            }
         }
      } else {
         return "";
      }
   }

   private Object invokeNoArgs(Object var1, String var2) {
      if (var1 == null) {
         return null;
      } else {
         String var10000 = var1.getClass().getName();
         String var3 = var10000 + "#" + var2;
         Method var4 = (Method)this.methodCache.get(var3);
         if (var4 == null) {
            try {
               var4 = var1.getClass().getMethod(var2);
               this.methodCache.putIfAbsent(var3, var4);
            } catch (RuntimeException | ReflectiveOperationException var7) {
               return null;
            }
         }

         try {
            return var4.invoke(var1);
         } catch (RuntimeException | ReflectiveOperationException var6) {
            return null;
         }
      }
   }

   private static Map<String, Integer> readSkillPoints(Object var0) {
      if (var0 instanceof Map var1) {
         if (!var1.isEmpty()) {
            LinkedHashMap var2 = new LinkedHashMap();
            var1.forEach((var1x, var2x) -> {
               if (var1x != null && var2x instanceof Number var3) {
                  var2.put(StatSnapshot.normalize(var1x.toString()), Math.max(0, var3.intValue()));
               }

            });
            return var2;
         }
      }

      return Map.of();
   }

   private static Map<String, Integer> readIntegerMap(ConfigurationSection var0) {
      if (var0 == null) {
         return Map.of();
      } else {
         LinkedHashMap var1 = new LinkedHashMap();

         for(String var3 : var0.getKeys(false)) {
            var1.put(StatSnapshot.normalize(var3), var0.getInt(var3));
         }

         return var1;
      }
   }

   private static int toInt(Object var0, int var1) {
      int var10000;
      if (var0 instanceof Number var2) {
         var10000 = var2.intValue();
      } else {
         var10000 = var1;
      }

      return var10000;
   }

   private static String objectString(Object var0) {
      return var0 == null ? "" : var0.toString();
   }

   @FunctionalInterface
   public interface StageResolver {
      ClassStageProfile stageAt(String var1, int var2);
   }
}
