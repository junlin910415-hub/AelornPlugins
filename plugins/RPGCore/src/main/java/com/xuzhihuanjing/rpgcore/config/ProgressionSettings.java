package com.xuzhihuanjing.rpgcore.config;

import org.bukkit.configuration.file.FileConfiguration;

public record ProgressionSettings(int maximumLevel, double experienceBase, double experienceLinear, double experiencePower, double experienceQuadratic, double monsterHealthGrowth, double monsterDamageGrowth, double monsterExperienceGrowth) {
   public static ProgressionSettings from(FileConfiguration config) {
      ProgressionSettings settings = new ProgressionSettings(config.getInt("progression.maximum-level", 120), config.getDouble("progression.curve.base", (double)80.0F), config.getDouble("progression.curve.linear", (double)22.0F), config.getDouble("progression.curve.power", 1.65), config.getDouble("progression.curve.quadratic", (double)3.5F), config.getDouble("progression.monster-scaling.health-per-level", 0.065), config.getDouble("progression.monster-scaling.damage-per-level", 0.045), config.getDouble("progression.monster-scaling.experience-per-level", 0.055));
      if (settings.maximumLevel >= 20 && settings.maximumLevel <= 500 && !(settings.experienceBase <= (double)0.0F) && !(settings.experienceLinear < (double)0.0F) && !(settings.experiencePower < (double)1.0F) && !(settings.experiencePower > (double)3.0F) && !(settings.experienceQuadratic < (double)0.0F) && !(settings.monsterHealthGrowth <= (double)0.0F) && !(settings.monsterHealthGrowth > (double)0.25F) && !(settings.monsterDamageGrowth <= (double)0.0F) && !(settings.monsterDamageGrowth > (double)0.25F) && !(settings.monsterExperienceGrowth <= (double)0.0F) && !(settings.monsterExperienceGrowth > (double)0.25F)) {
         return settings;
      } else {
         throw new IllegalArgumentException("Progression settings contain an invalid numeric value");
      }
   }
}
