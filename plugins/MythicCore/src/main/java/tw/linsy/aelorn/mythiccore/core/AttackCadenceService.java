package tw.linsy.aelorn.mythiccore.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import tw.linsy.aelorn.mythiccore.api.combat.AttackCadenceProfile;
import tw.linsy.aelorn.mythiccore.api.combat.AttackTimeline;

public final class AttackCadenceService {
   private static final String FILE_NAME = "attack-cadence.yml";
   private final JavaPlugin plugin;
   private volatile Map<String, AttackCadenceProfile> profiles = Map.of();

   public AttackCadenceService(JavaPlugin var1) {
      this.plugin = var1;
   }

   public void reload() {
      File var1 = new File(this.plugin.getDataFolder(), "attack-cadence.yml");
      if (!var1.isFile()) {
         this.plugin.saveResource("attack-cadence.yml", false);
      }

      this.mergeBundledDefaults(var1);
      YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);
      if (var2.getInt("schema-version", -1) != 1) {
         throw new IllegalArgumentException("attack-cadence.yml schema-version 必須是 1");
      } else {
         ConfigurationSection var3 = var2.getConfigurationSection("profiles");
         if (var3 == null) {
            throw new IllegalArgumentException("attack-cadence.yml 缺少 profiles");
         } else {
            LinkedHashMap var4 = new LinkedHashMap();

            for(String var6 : var3.getKeys(false)) {
               ConfigurationSection var7 = var3.getConfigurationSection(var6);
               if (var7 != null) {
                  AttackCadenceProfile var8 = new AttackCadenceProfile(var6, var7.getString("display-name", var6), var7.getInt("windup-ticks"), var7.getInt("active-ticks"), var7.getInt("recovery-ticks"), var7.getInt("input-buffer-ticks"), var7.getInt("combo-reset-ticks"), var7.getInt("maximum-combo-steps"), var7.getDouble("base-damage-multiplier"), var7.getDouble("combo-damage-step"), var7.getDouble("finisher-damage-bonus"), var7.getDouble("range-multiplier"), var7.getDouble("maximum-attack-speed-reduction"), var7.getDouble("minimum-timing-scale"), var7.getBoolean("interruptible"), var7.getDouble("interrupt-damage-percent"));
                  if (var4.put(var8.id(), var8) != null) {
                     throw new IllegalArgumentException("重複的攻擊節奏 ID: " + var8.id());
                  }
               }
            }

            if (var4.isEmpty()) {
               throw new IllegalArgumentException("attack-cadence.yml 至少需要一個攻擊節奏");
            } else {
               this.profiles = Map.copyOf(var4);
            }
         }
      }
   }

   public Map<String, AttackCadenceProfile> profiles() {
      return this.profiles;
   }

   public AttackCadenceProfile profile(String var1) {
      return (AttackCadenceProfile)this.profiles.get(normalize(var1));
   }

   public AttackTimeline timeline(String var1, int var2, double var3) {
      AttackCadenceProfile var5 = this.profile(var1);
      if (var5 == null) {
         throw new IllegalArgumentException("未知的攻擊節奏：" + var1);
      } else {
         return var5.timeline(var2, var3);
      }
   }

   private void mergeBundledDefaults(File var1) {
      try {
         InputStream var2 = this.plugin.getResource("attack-cadence.yml");

         try {
            if (var2 == null) {
               throw new IllegalStateException("JAR 缺少 attack-cadence.yml");
            }

            YamlConfiguration var3 = YamlConfiguration.loadConfiguration(new InputStreamReader(var2, StandardCharsets.UTF_8));
            YamlConfiguration var4 = YamlConfiguration.loadConfiguration(var1);
            int var5 = 0;

            for(Map.Entry var7 : var3.getValues(true).entrySet()) {
               if (!(var7.getValue() instanceof ConfigurationSection) && !var4.contains((String)var7.getKey())) {
                  var4.set((String)var7.getKey(), var7.getValue());
                  ++var5;
               }
            }

            if (var5 > 0) {
               var4.save(var1);
               this.plugin.getLogger().info("攻擊節奏設定已補入 " + var5 + " 個新欄位。");
            }
         } catch (Throwable var9) {
            if (var2 != null) {
               try {
                  var2.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (var2 != null) {
            var2.close();
         }

      } catch (IOException var10) {
         throw new IllegalStateException("無法更新 attack-cadence.yml", var10);
      }
   }

   private static String normalize(String var0) {
      return var0 == null ? "" : var0.trim().toLowerCase(Locale.ROOT).replace('_', '-');
   }
}
