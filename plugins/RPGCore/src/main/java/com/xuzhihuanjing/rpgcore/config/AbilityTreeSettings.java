package com.xuzhihuanjing.rpgcore.config;

import org.bukkit.configuration.file.FileConfiguration;

public record AbilityTreeSettings(double safeZoneRadius) {
   public static AbilityTreeSettings from(FileConfiguration config) {
      double radius = config.getDouble("ability-tree.safe-zone-radius", (double)48.0F);
      if (!(radius < (double)4.0F) && !(radius > (double)512.0F)) {
         return new AbilityTreeSettings(radius);
      } else {
         throw new IllegalArgumentException("ability-tree.safe-zone-radius must be between 4 and 512");
      }
   }
}
