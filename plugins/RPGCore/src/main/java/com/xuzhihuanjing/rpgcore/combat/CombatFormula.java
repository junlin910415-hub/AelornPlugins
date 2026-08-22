package com.xuzhihuanjing.rpgcore.combat;

import com.xuzhihuanjing.rpgcore.config.CombatSettings;
import com.xuzhihuanjing.rpgcore.domain.combat.CombatStats;

public final class CombatFormula {
   private final CombatSettings settings;

   public CombatFormula(CombatSettings settings) {
      this.settings = settings;
   }

   public double abilityPower(CombatStats caster, double coefficient, double flatPower) {
      return Math.max((double)0.0F, caster.attackPower() * coefficient + flatPower);
   }

   public double mitigation(double rating, int defenderLevel) {
      if (rating <= (double)0.0F) {
         return (double)0.0F;
      } else {
         double constant = this.settings.defenseConstantBase() + this.settings.defenseConstantPerLevel() * (double)Math.max(1, defenderLevel);
         return Math.min(this.settings.maximumMitigation(), rating / (rating + constant));
      }
   }

   public double afterMitigation(double rawDamage, double rating, int defenderLevel) {
      return Math.max((double)0.0F, rawDamage) * ((double)1.0F - this.mitigation(rating, defenderLevel));
   }

   public double criticalDamage(double damage, boolean ability) {
      double bonus = this.settings.criticalDamageMultiplier() - (double)1.0F;
      return Math.max((double)0.0F, damage) * ((double)1.0F + bonus * (ability ? this.settings.abilityCriticalEfficiency() : (double)1.0F));
   }

   public double dodgeRetainedDamage(double damage) {
      return Math.max((double)0.0F, damage) * this.settings.dodgeDamageRetained();
   }

   public double damageVariance() {
      return this.settings.damageVariance();
   }

   public double toMinecraftDamage(double rpgDamage) {
      return rpgDamage <= (double)0.0F ? (double)0.0F : Math.max(this.settings.minimumMinecraftDamage(), rpgDamage * this.settings.damageToMinecraftScale());
   }

   public double toMinecraftHealth(double rpgHealth) {
      return Math.max((double)1.0F, rpgHealth * this.settings.healthToMinecraftScale());
   }

   public double healthToMinecraftScale() {
      return this.settings.healthToMinecraftScale();
   }
}
