package tw.linsy.aelorn.rpgcore.equipment;

import tw.linsy.aelorn.rpgcore.config.IdentificationSettings;

public final class IdentificationCostFormula {
   private final IdentificationSettings settings;

   public IdentificationCostFormula(IdentificationSettings settings) {
      this.settings = settings;
   }

   public int calculate(int itemLevel, EquipmentRarity rarity, EquipmentSlotType slotType, int completedRolls) {
      double levelCost = this.settings.baseCost() + this.settings.levelFactor() * Math.pow((double)Math.max(1, itemLevel), this.settings.levelExponent());
      double itemCost = levelCost * (Double)this.settings.rarityMultipliers().get(rarity) * (Double)this.settings.slotMultipliers().get(slotType);
      double rerollCost = itemCost * Math.pow(this.settings.rerollMultiplier(), (double)Math.max(0, completedRolls));
      return Double.isFinite(rerollCost) && !(rerollCost >= (double)this.settings.maximumCost()) ? Math.max(1, (int)Math.ceil(rerollCost)) : this.settings.maximumCost();
   }
}
