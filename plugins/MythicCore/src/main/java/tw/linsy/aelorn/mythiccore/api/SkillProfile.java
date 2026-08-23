package tw.linsy.aelorn.mythiccore.api;

import java.util.List;

public record SkillProfile(String id, String classId, String archetypeId, String displayName, String elementId, String effect, List<String> combo, int requiredLevel, double manaCost, double cooldownSeconds, double coefficient, double flatPower, double radius, double range, int durationTicks, int maxHits, String description) {
   public SkillProfile(String id, String classId, String archetypeId, String displayName, String elementId, String effect, List<String> combo, int requiredLevel, double manaCost, double cooldownSeconds, double coefficient, double flatPower, double radius, double range, int durationTicks, int maxHits, String description) {
      id = StatSnapshot.normalize(id);
      classId = StatSnapshot.normalize(classId);
      archetypeId = StatSnapshot.normalize(archetypeId);
      displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : id;
      elementId = StatSnapshot.normalize(elementId);
      effect = StatSnapshot.normalize(effect);
      combo = List.copyOf(combo == null ? List.of() : combo.stream().filter((var0) -> var0 != null && !var0.isBlank()).map((var0) -> var0.trim().toUpperCase()).toList());
      requiredLevel = Math.max(1, requiredLevel);
      manaCost = Math.max((double)0.0F, finiteOr(manaCost, (double)0.0F));
      cooldownSeconds = Math.max((double)0.0F, finiteOr(cooldownSeconds, (double)0.0F));
      coefficient = Math.max((double)0.0F, Math.min((double)3.0F, finiteOr(coefficient, (double)0.0F)));
      flatPower = Math.max((double)0.0F, finiteOr(flatPower, (double)0.0F));
      radius = Math.max((double)0.0F, finiteOr(radius, (double)0.0F));
      range = Math.max((double)0.0F, finiteOr(range, (double)0.0F));
      durationTicks = Math.max(0, durationTicks);
      maxHits = Math.max(1, maxHits);
      description = description == null ? "" : description.trim();
      this.id = id;
      this.classId = classId;
      this.archetypeId = archetypeId;
      this.displayName = displayName;
      this.elementId = elementId;
      this.effect = effect;
      this.combo = combo;
      this.requiredLevel = requiredLevel;
      this.manaCost = manaCost;
      this.cooldownSeconds = cooldownSeconds;
      this.coefficient = coefficient;
      this.flatPower = flatPower;
      this.radius = radius;
      this.range = range;
      this.durationTicks = durationTicks;
      this.maxHits = maxHits;
      this.description = description;
   }

   public double balancedPower(int var1, double var2, double var4, double var6) {
      double var8 = (double)Math.max(1, var1);
      double var10 = (double)1.0F + Math.min(0.68, Math.log1p(var8) / (double)11.0F);
      double var12 = Math.max((double)0.0F, this.coefficient) * 0.28 + 0.32;
      double var14 = Math.max((double)0.0F, var2) * var12;
      double var16 = Math.max((double)0.0F, var4) * 0.42;
      double var18 = Math.max((double)0.0F, var6);
      var18 = Math.max(0.35, Math.min(1.55, var18));
      int var20 = Math.max(1, this.maxHits);
      double var21 = (double)1.0F - Math.min(0.32, (double)(var20 - 1) / (double)10.0F);
      double var23 = this.flatPower + var14 + var16;
      return Math.max((double)0.0F, var23 * var10 * var18 * var21 / (double)var20);
   }

   public double balancedPower(int var1, int var2, double var3, double var5, double var7) {
      int var9 = Math.max(1, Math.min(100, var2));
      double var10 = (double)1.0F + Math.min(0.35, (double)(var9 - 1) * 0.012);
      return this.balancedPower(var1, var3, var5, var7) * var10;
   }

   private static double finiteOr(double var0, double var2) {
      return Double.isFinite(var0) ? var0 : var2;
   }
}
