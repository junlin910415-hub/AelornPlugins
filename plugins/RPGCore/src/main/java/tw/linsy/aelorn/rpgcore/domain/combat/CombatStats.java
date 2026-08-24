package tw.linsy.aelorn.rpgcore.domain.combat;

public record CombatStats(double maximumHealth, double maximumMana, double attackPower, double defense, double resistance, double speed, double damageTakenMultiplier, double basicAttackMultiplier, int strengthPoints, int dexterityPoints, int intelligencePoints, int defencePoints, int agilityPoints, double criticalChance, double spellCostReduction, double dodgeChance, double knockbackBonus, double healthRegeneration, double manaRegeneration) {
   public CombatStats(double maximumHealth, double maximumMana, double attackPower, double defense, double resistance, double speed, double damageTakenMultiplier, double basicAttackMultiplier) {
      this(maximumHealth, maximumMana, attackPower, defense, resistance, speed, damageTakenMultiplier, basicAttackMultiplier, 0, 0, 0, 0, 0, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F);
   }
}
