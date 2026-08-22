package com.xuzhihuanjing.rpgcore.domain.ability;

public record AbilityModifiers(double powerBonus, double manaReduction, double cooldownReduction) {
   public static AbilityModifiers none() {
      return new AbilityModifiers((double)0.0F, (double)0.0F, (double)0.0F);
   }
}
