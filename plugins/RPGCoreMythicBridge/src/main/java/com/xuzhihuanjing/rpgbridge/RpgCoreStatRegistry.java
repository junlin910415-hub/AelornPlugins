package com.xuzhihuanjing.rpgbridge;

import java.util.List;
import tw.linsy.aelorn.mythiccore.api.MythicCoreApi;

public final class RpgCoreStatRegistry {
   private static final List<String> KNOWN_STATS = List.of("maximum_health", "maximum_mana", "attack_power", "defense", "resistance", "speed", "ability_power", "spell_damage", "basic_attack_damage", "mana_regeneration", "mana_cost_reduction", "cooldown_reduction", "critical_chance", "critical_damage", "life_steal", "loot_bonus", "experience_bonus", "rpgcore_maximum_health", "rpgcore_maximum_mana", "rpgcore_attack_power", "rpgcore_defense", "rpgcore_resistance", "rpgcore_speed", "rpgcore_ability_power", "rpgcore_spell_damage");

   private RpgCoreStatRegistry() {
   }

   public static List<String> names() {
      return KNOWN_STATS;
   }

   public static int register(MythicCoreApi api) {
      api.registerKnownStats(KNOWN_STATS);
      return KNOWN_STATS.size();
   }
}
