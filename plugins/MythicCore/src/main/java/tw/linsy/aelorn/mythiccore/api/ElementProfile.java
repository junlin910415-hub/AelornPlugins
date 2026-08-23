package tw.linsy.aelorn.mythiccore.api;

import java.util.List;

public record ElementProfile(String id, String displayName, List<String> damageStats, List<String> resistanceStats, double damageMultiplier, double softCap, double skillMultiplier) {
   public ElementProfile(String id, String displayName, List<String> damageStats, List<String> resistanceStats, double damageMultiplier, double softCap, double skillMultiplier) {
      id = StatSnapshot.normalize(id);
      displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : id;
      damageStats = List.copyOf(damageStats == null ? List.of() : damageStats.stream().map(StatSnapshot::normalize).filter((var0) -> !var0.isBlank()).distinct().toList());
      resistanceStats = List.copyOf(resistanceStats == null ? List.of() : resistanceStats.stream().map(StatSnapshot::normalize).filter((var0) -> !var0.isBlank()).distinct().toList());
      damageMultiplier = finiteOr(damageMultiplier, (double)1.0F);
      softCap = Math.max((double)1.0F, finiteOr(softCap, (double)120.0F));
      skillMultiplier = Math.max((double)0.0F, finiteOr(skillMultiplier, (double)1.0F));
      this.id = id;
      this.displayName = displayName;
      this.damageStats = damageStats;
      this.resistanceStats = resistanceStats;
      this.damageMultiplier = damageMultiplier;
      this.softCap = softCap;
      this.skillMultiplier = skillMultiplier;
   }

   private static double finiteOr(double var0, double var2) {
      return Double.isFinite(var0) ? var0 : var2;
   }
}
