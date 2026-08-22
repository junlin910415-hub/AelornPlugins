package com.xuzhihuanjing.rpgcore.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(int baseCharacterSlots, int maximumCharacterSlots, boolean openSelectorOnFirstJoin, long deletionGraceMinutes, int backupLimit, String storageDirectory) {
   public static final int SCHEMA_VERSION = 15;

   public static PluginSettings from(FileConfiguration config) {
      if (config.getInt("schema-version", -1) != 15) {
         throw new IllegalArgumentException("Unsupported config.yml schema-version");
      } else {
         int baseSlots = config.getInt("characters.base-slots", 3);
         int maximumSlots = config.getInt("characters.maximum-slots", 9);
         if (baseSlots >= 1 && baseSlots <= 9) {
            if (maximumSlots >= baseSlots && maximumSlots <= 9) {
               String storageType = config.getString("storage.type", "yaml");
               if (!"yaml".equalsIgnoreCase(storageType)) {
                  throw new IllegalArgumentException("Phase 1 supports storage.type: yaml only");
               } else {
                  String directory = config.getString("storage.directory", "player-data");
                  if (directory != null && !directory.isBlank() && !directory.contains("..")) {
                     return new PluginSettings(baseSlots, maximumSlots, config.getBoolean("characters.open-selector-on-first-join", true), Math.max(1L, config.getLong("characters.deletion-grace-minutes", 10L)), Math.max(1, Math.min(10, config.getInt("characters.backup-limit", 5))), directory);
                  } else {
                     throw new IllegalArgumentException("storage.directory is invalid");
                  }
               }
            } else {
               throw new IllegalArgumentException("characters.maximum-slots must be between base-slots and 9");
            }
         } else {
            throw new IllegalArgumentException("characters.base-slots must be between 1 and 9");
         }
      }
   }
}
