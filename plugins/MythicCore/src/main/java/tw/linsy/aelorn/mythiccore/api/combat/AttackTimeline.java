package tw.linsy.aelorn.mythiccore.api.combat;

public record AttackTimeline(String profileId, String displayName, int comboStep, int maximumComboSteps, int windupTicks, int activeTicks, int recoveryTicks, int inputBufferTicks, int comboResetTicks, double damageMultiplier, double rangeMultiplier, boolean interruptible, double interruptDamagePercent) {
   public int activeStartTick() {
      return this.windupTicks;
   }

   public int recoveryStartTick() {
      return this.windupTicks + this.activeTicks;
   }

   public int totalTicks() {
      return this.windupTicks + this.activeTicks + this.recoveryTicks;
   }

   public AttackPhase phaseAt(int var1) {
      if (var1 >= 0 && var1 < this.totalTicks()) {
         if (var1 < this.activeStartTick()) {
            return AttackPhase.WINDUP;
         } else {
            return var1 < this.recoveryStartTick() ? AttackPhase.ACTIVE : AttackPhase.RECOVERY;
         }
      } else {
         return AttackPhase.READY;
      }
   }

   public boolean acceptsBufferedInput(int var1) {
      return var1 >= this.recoveryStartTick() && this.totalTicks() - var1 <= this.inputBufferTicks;
   }
}
