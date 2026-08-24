package tw.linsy.aelorn.rpgcore.domain.combat;

public record DamageResult(double rawDamage, double finalRpgDamage, double minecraftDamage, double mitigation, boolean critical, boolean dodged, DamageKind kind) {
   public double blockedDamage() {
      return Math.max((double)0.0F, this.rawDamage - this.finalRpgDamage);
   }
}
