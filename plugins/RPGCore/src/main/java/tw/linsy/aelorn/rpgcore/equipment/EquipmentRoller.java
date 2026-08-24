package tw.linsy.aelorn.rpgcore.equipment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class EquipmentRoller {
   private EquipmentRoller() {
   }

   public static Map<EquipmentStatType, Integer> rollStats(EquipmentTemplate template, int level, EquipmentRarity rarity, long seed) {
      return roll(template, level, rarity, seed).stats();
   }

   public static EquipmentRoll roll(EquipmentTemplate template, int level, EquipmentRarity rarity, long seed) {
      SplittableRandom random = new SplittableRandom(seed);
      Map<EquipmentStatType, Integer> stats = new EnumMap(EquipmentStatType.class);
      Map<EquipmentStatType, Integer> minima = new EnumMap(EquipmentStatType.class);
      Map<EquipmentStatType, Integer> maxima = new EnumMap(EquipmentStatType.class);
      double levelMultiplier = (double)1.0F + (double)Math.max(0, level - template.minimumLevel()) * 0.035;

      for(EquipmentStatRange range : template.baseStats().values()) {
         rollRange(range, random, levelMultiplier, rarity, stats, minima, maxima);
      }

      List<EquipmentStatRange> candidates = new ArrayList(template.affixes());
      int maximumAffixes = Math.min(template.maximumAffixes(), candidates.size());
      int minimumAffixes = Math.min(template.minimumAffixes(), maximumAffixes);
      int affixCount = minimumAffixes;
      if (maximumAffixes > minimumAffixes) {
         affixCount = minimumAffixes + random.nextInt(maximumAffixes - minimumAffixes + 1);
      }

      for(int index = 0; index < affixCount && !candidates.isEmpty(); ++index) {
         EquipmentStatRange range = (EquipmentStatRange)candidates.remove(random.nextInt(candidates.size()));
         rollRange(range, random, levelMultiplier, rarity, stats, minima, maxima);
      }

      Map<EquipmentStatType, Double> qualities = new EnumMap(EquipmentStatType.class);

      for(Map.Entry<EquipmentStatType, Integer> entry : stats.entrySet()) {
         int minimum = (Integer)minima.get(entry.getKey());
         int maximum = (Integer)maxima.get(entry.getKey());
         double quality = maximum == minimum ? (double)1.0F : (double)((Integer)entry.getValue() - minimum) / (double)(maximum - minimum);
         qualities.put((EquipmentStatType)entry.getKey(), Math.max((double)0.0F, Math.min((double)1.0F, quality)));
      }

      return new EquipmentRoll(stats, qualities);
   }

   private static void rollRange(EquipmentStatRange range, SplittableRandom random, double levelMultiplier, EquipmentRarity rarity, Map<EquipmentStatType, Integer> stats, Map<EquipmentStatType, Integer> minima, Map<EquipmentStatType, Integer> maxima) {
      double rarityMultiplier = rarity.statMultiplier();
      stats.merge(range.type(), range.roll(random, levelMultiplier, rarityMultiplier), Integer::sum);
      minima.merge(range.type(), range.scaledMinimum(levelMultiplier, rarityMultiplier), Integer::sum);
      maxima.merge(range.type(), range.scaledMaximum(levelMultiplier, rarityMultiplier), Integer::sum);
   }
}
