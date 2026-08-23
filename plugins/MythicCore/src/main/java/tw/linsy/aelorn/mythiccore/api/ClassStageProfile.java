package tw.linsy.aelorn.mythiccore.api;

import java.util.Map;

public record ClassStageProfile(String id, String displayName, int minimumLevel, Map<String, Double> multipliers, int bonusAbilityPoints) {
   public ClassStageProfile(String id, String displayName, int minimumLevel, Map<String, Double> multipliers, int bonusAbilityPoints) {
      id = StatSnapshot.normalize(id);
      displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : id;
      minimumLevel = Math.max(1, minimumLevel);
      multipliers = Map.copyOf(multipliers == null ? Map.of() : multipliers);
      bonusAbilityPoints = Math.max(0, bonusAbilityPoints);
      this.id = id;
      this.displayName = displayName;
      this.minimumLevel = minimumLevel;
      this.multipliers = multipliers;
      this.bonusAbilityPoints = bonusAbilityPoints;
   }

   public double multiplier(String var1) {
      double var2 = (Double)this.multipliers.getOrDefault(StatSnapshot.normalize(var1), (double)1.0F);
      return Double.isFinite(var2) ? Math.max(0.1, Math.min((double)3.0F, var2)) : (double)1.0F;
   }
}
