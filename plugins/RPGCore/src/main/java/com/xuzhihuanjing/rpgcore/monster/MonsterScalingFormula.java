package com.xuzhihuanjing.rpgcore.monster;

import com.xuzhihuanjing.rpgcore.config.ProgressionSettings;
import com.xuzhihuanjing.rpgcore.domain.monster.MonsterDefinition;
import com.xuzhihuanjing.rpgcore.domain.monster.ScaledMonsterStats;

public final class MonsterScalingFormula {
   private final ProgressionSettings settings;

   public MonsterScalingFormula(ProgressionSettings settings) {
      this.settings = settings;
   }

   public ScaledMonsterStats scale(MonsterDefinition definition, int requestedLevel) {
      int level = Math.max(definition.minimumLevel(), Math.min(definition.maximumLevel(), requestedLevel));
      int difference = level - definition.baseLevel();
      double healthFactor = Math.pow((double)1.0F + this.settings.monsterHealthGrowth(), (double)difference);
      double damageFactor = Math.pow((double)1.0F + this.settings.monsterDamageGrowth(), (double)difference);
      double experienceFactor = Math.pow((double)1.0F + this.settings.monsterExperienceGrowth(), (double)difference);
      return new ScaledMonsterStats(level, Math.max((double)1.0F, definition.baseHealth() * healthFactor), Math.max((double)0.0F, definition.baseDamage() * damageFactor), Math.max((double)0.0F, definition.baseDefense() * damageFactor), Math.max((double)0.0F, definition.baseResistance() * damageFactor), Math.max(1L, Math.round((double)definition.baseExperience() * experienceFactor)));
   }
}
