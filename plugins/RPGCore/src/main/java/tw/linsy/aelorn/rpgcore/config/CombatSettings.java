package tw.linsy.aelorn.rpgcore.config;

import org.bukkit.configuration.file.FileConfiguration;

public record CombatSettings(long comboTimeoutMillis, double manaRegenerationPerSecond, double damageToMinecraftScale, double defenseConstantBase, double defenseConstantPerLevel, double maximumMitigation, double healthToMinecraftScale, double criticalDamageMultiplier, double abilityCriticalEfficiency, double dodgeDamageRetained, double damageVariance, double baseHealthRegenerationPerSecond, double minimumMinecraftDamage) {
   public static CombatSettings from(FileConfiguration config) {
      CombatSettings settings = new CombatSettings(config.getLong("combat.combo-timeout-ms", 900L), config.getDouble("combat.mana-regeneration-per-second", (double)3.0F), config.getDouble("combat.damage-to-minecraft-scale", 0.22), config.getDouble("combat.defense-constant-base", (double)80.0F), config.getDouble("combat.defense-constant-per-level", (double)4.0F), config.getDouble("combat.maximum-mitigation", (double)0.75F), config.getDouble("combat.health-to-minecraft-scale", 0.2), config.getDouble("combat.critical-damage-multiplier", (double)1.75F), config.getDouble("combat.ability-critical-efficiency", 0.7), config.getDouble("combat.dodge-damage-retained", 0.15), config.getDouble("combat.damage-variance", 0.08), config.getDouble("combat.base-health-regeneration-per-second", 0.45), config.getDouble("combat.minimum-minecraft-damage", 0.05));
      if (settings.comboTimeoutMillis >= 300L && settings.comboTimeoutMillis <= 2000L) {
         if (!(settings.manaRegenerationPerSecond <= (double)0.0F) && !(settings.damageToMinecraftScale <= (double)0.0F) && !(settings.defenseConstantBase <= (double)0.0F) && !(settings.defenseConstantPerLevel < (double)0.0F) && !(settings.maximumMitigation <= (double)0.0F) && !(settings.maximumMitigation > 0.9) && !(settings.healthToMinecraftScale <= (double)0.0F) && !(settings.criticalDamageMultiplier < (double)1.0F) && !(settings.criticalDamageMultiplier > (double)3.0F) && !(settings.abilityCriticalEfficiency < (double)0.0F) && !(settings.abilityCriticalEfficiency > (double)1.0F) && !(settings.dodgeDamageRetained < (double)0.0F) && !(settings.dodgeDamageRetained > (double)0.5F) && !(settings.damageVariance < (double)0.0F) && !(settings.damageVariance > (double)0.25F) && !(settings.baseHealthRegenerationPerSecond < (double)0.0F) && !(settings.minimumMinecraftDamage < (double)0.0F) && !(settings.minimumMinecraftDamage > (double)1.0F)) {
            return settings;
         } else {
            throw new IllegalArgumentException("Combat settings contain an invalid numeric value");
         }
      } else {
         throw new IllegalArgumentException("combat.combo-timeout-ms must be between 300 and 2000");
      }
   }
}
