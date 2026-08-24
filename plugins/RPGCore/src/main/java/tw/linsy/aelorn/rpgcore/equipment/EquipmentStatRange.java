package tw.linsy.aelorn.rpgcore.equipment;

import java.util.SplittableRandom;

public record EquipmentStatRange(EquipmentStatType type, int minimum, int maximum) {
   public EquipmentStatRange {
      if (minimum > maximum) {
         throw new IllegalArgumentException("Stat range minimum cannot exceed maximum");
      }
   }

   public int roll(SplittableRandom random, double levelMultiplier, double rarityMultiplier) {
      int scaledMinimum = this.scaledMinimum(levelMultiplier, rarityMultiplier);
      int scaledMaximum = this.scaledMaximum(levelMultiplier, rarityMultiplier);
      return scaledMinimum == scaledMaximum ? scaledMinimum : random.nextInt(scaledMinimum, scaledMaximum + 1);
   }

   public int scaledMinimum(double levelMultiplier, double rarityMultiplier) {
      return this.scale(this.minimum, levelMultiplier, rarityMultiplier);
   }

   public int scaledMaximum(double levelMultiplier, double rarityMultiplier) {
      return Math.max(this.scaledMinimum(levelMultiplier, rarityMultiplier), this.scale(this.maximum, levelMultiplier, rarityMultiplier));
   }

   private int scale(int value, double levelMultiplier, double rarityMultiplier) {
      return (int)Math.round((double)value * levelMultiplier * rarityMultiplier);
   }
}
