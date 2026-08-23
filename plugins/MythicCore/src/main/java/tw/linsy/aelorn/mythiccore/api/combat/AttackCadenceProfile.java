package tw.linsy.aelorn.mythiccore.api.combat;

import java.util.Locale;

public record AttackCadenceProfile(String id, String displayName, int windupTicks, int activeTicks, int recoveryTicks, int inputBufferTicks, int comboResetTicks, int maximumComboSteps, double baseDamageMultiplier, double comboDamageStep, double finisherDamageBonus, double rangeMultiplier, double maximumAttackSpeedReduction, double minimumTimingScale, boolean interruptible, double interruptDamagePercent) {
   private static final double ATTACK_SPEED_SOFT_CAP = (double)160.0F;

   public AttackCadenceProfile(String id, String displayName, int windupTicks, int activeTicks, int recoveryTicks, int inputBufferTicks, int comboResetTicks, int maximumComboSteps, double baseDamageMultiplier, double comboDamageStep, double finisherDamageBonus, double rangeMultiplier, double maximumAttackSpeedReduction, double minimumTimingScale, boolean interruptible, double interruptDamagePercent) {
      id = normalize(id);
      displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : id;
      require(!id.isBlank(), "id must not be blank");
      require(windupTicks >= 1 && windupTicks <= 80, "windup-ticks must be between 1 and 80");
      require(activeTicks >= 1 && activeTicks <= 20, "active-ticks must be between 1 and 20");
      require(recoveryTicks >= 1 && recoveryTicks <= 80, "recovery-ticks must be between 1 and 80");
      require(inputBufferTicks >= 0 && inputBufferTicks <= recoveryTicks, "input-buffer-ticks must fit inside recovery");
      require(comboResetTicks >= 1 && comboResetTicks <= 200, "combo-reset-ticks must be between 1 and 200");
      require(maximumComboSteps >= 1 && maximumComboSteps <= 8, "maximum-combo-steps must be between 1 and 8");
      require(finiteBetween(baseDamageMultiplier, 0.1, (double)3.0F), "base-damage-multiplier must be between 0.1 and 3.0");
      require(finiteBetween(comboDamageStep, (double)0.0F, (double)0.25F), "combo-damage-step must be between 0.0 and 0.25");
      require(finiteBetween(finisherDamageBonus, (double)0.0F, (double)0.5F), "finisher-damage-bonus must be between 0.0 and 0.5");
      require(finiteBetween(rangeMultiplier, (double)0.5F, (double)1.5F), "range-multiplier must be between 0.5 and 1.5");
      require(finiteBetween(maximumAttackSpeedReduction, (double)0.0F, 0.4), "maximum-attack-speed-reduction must be between 0.0 and 0.4");
      require(finiteBetween(minimumTimingScale, 0.6, (double)1.0F), "minimum-timing-scale must be between 0.6 and 1.0");
      require(finiteBetween(interruptDamagePercent, (double)0.0F, (double)100.0F), "interrupt-damage-percent must be between 0.0 and 100.0");
      require(!interruptible || interruptDamagePercent > (double)0.0F, "interruptible profiles require a positive interrupt threshold");
      this.id = id;
      this.displayName = displayName;
      this.windupTicks = windupTicks;
      this.activeTicks = activeTicks;
      this.recoveryTicks = recoveryTicks;
      this.inputBufferTicks = inputBufferTicks;
      this.comboResetTicks = comboResetTicks;
      this.maximumComboSteps = maximumComboSteps;
      this.baseDamageMultiplier = baseDamageMultiplier;
      this.comboDamageStep = comboDamageStep;
      this.finisherDamageBonus = finisherDamageBonus;
      this.rangeMultiplier = rangeMultiplier;
      this.maximumAttackSpeedReduction = maximumAttackSpeedReduction;
      this.minimumTimingScale = minimumTimingScale;
      this.interruptible = interruptible;
      this.interruptDamagePercent = interruptDamagePercent;
   }

   public AttackTimeline timeline(int var1, double var2) {
      int var4 = Math.max(1, Math.min(this.maximumComboSteps, var1));
      double var5 = Double.isFinite(var2) ? Math.max((double)0.0F, Math.min((double)5000.0F, var2)) : (double)0.0F;
      double var7 = this.maximumAttackSpeedReduction * var5 / (var5 + (double)160.0F);
      double var9 = Math.max(this.minimumTimingScale, (double)1.0F - var7);
      int var11 = scaledTicks(this.windupTicks, var9);
      int var12 = scaledTicks(this.recoveryTicks, var9);
      int var13 = this.inputBufferTicks == 0 ? 0 : Math.min(var12, Math.max(1, scaledTicks(this.inputBufferTicks, var9)));
      double var14 = this.baseDamageMultiplier + (double)(var4 - 1) * this.comboDamageStep;
      if (this.maximumComboSteps > 1 && var4 == this.maximumComboSteps) {
         var14 += this.finisherDamageBonus;
      }

      return new AttackTimeline(this.id, this.displayName, var4, this.maximumComboSteps, var11, this.activeTicks, var12, var13, this.comboResetTicks, var14, this.rangeMultiplier, this.interruptible, this.interruptDamagePercent);
   }

   private static int scaledTicks(int var0, double var1) {
      return Math.max(1, (int)Math.round((double)var0 * var1));
   }

   private static boolean finiteBetween(double var0, double var2, double var4) {
      return Double.isFinite(var0) && var0 >= var2 && var0 <= var4;
   }

   private static void require(boolean var0, String var1) {
      if (!var0) {
         throw new IllegalArgumentException(var1);
      }
   }

   private static String normalize(String var0) {
      return var0 == null ? "" : var0.trim().toLowerCase(Locale.ROOT).replace('_', '-').replaceAll("[^a-z0-9.-]", "-").replaceAll("-+", "-");
   }
}
