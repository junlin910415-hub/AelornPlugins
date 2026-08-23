package tw.linsy.aelorn.mythiccore.combat;

import java.util.Locale;

public record CombatResult(double rawDamage, double finalDamage, boolean critical, double reduction, String outcome) {
   public String indicator() {
      String var1 = this.outcome != null && !this.outcome.isBlank() ? this.outcome : (this.critical ? "暴擊" : "傷害");
      return var1 + " " + format(this.finalDamage) + "  減傷 " + format(this.reduction * (double)100.0F) + "%";
   }

   private static String format(double var0) {
      return String.format(Locale.ROOT, "%.1f", var0);
   }
}
