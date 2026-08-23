package tw.linsy.aelorn.mythiccore.api;

public record SkillCastResult(boolean allowed, String reason, String skillId, String elementId, int skillLevel, int hits, double damagePerHit, double totalDamage, double manaCost, double cooldownSeconds) {
   public SkillCastResult(boolean allowed, String reason, String skillId, String elementId, int skillLevel, int hits, double damagePerHit, double totalDamage, double manaCost, double cooldownSeconds) {
      reason = reason == null ? "" : reason.trim();
      skillId = StatSnapshot.normalize(skillId);
      elementId = StatSnapshot.normalize(elementId);
      skillLevel = Math.max(1, skillLevel);
      hits = Math.max(0, hits);
      damagePerHit = finiteNonNegative(damagePerHit);
      totalDamage = finiteNonNegative(totalDamage);
      manaCost = finiteNonNegative(manaCost);
      cooldownSeconds = finiteNonNegative(cooldownSeconds);
      this.allowed = allowed;
      this.reason = reason;
      this.skillId = skillId;
      this.elementId = elementId;
      this.skillLevel = skillLevel;
      this.hits = hits;
      this.damagePerHit = damagePerHit;
      this.totalDamage = totalDamage;
      this.manaCost = manaCost;
      this.cooldownSeconds = cooldownSeconds;
   }

   public static SkillCastResult denied(String var0, String var1) {
      return new SkillCastResult(false, var1, var0, "", 1, 0, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F);
   }

   private static double finiteNonNegative(double var0) {
      return Double.isFinite(var0) ? Math.max((double)0.0F, var0) : (double)0.0F;
   }
}
