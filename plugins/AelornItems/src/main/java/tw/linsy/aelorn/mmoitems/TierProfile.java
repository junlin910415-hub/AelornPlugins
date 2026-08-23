package tw.linsy.aelorn.mmoitems;

import java.util.Locale;
import java.util.Random;
import org.bukkit.configuration.ConfigurationSection;
import tw.linsy.aelorn.mmoitems.forge.StatFormula;

record TierProfile(String id, String display, String tooltip, DeconstructionProfile deconstruction, double statMultiplier, int qualityMin, int qualityMax, int maxAffixes, double chance, StatFormula affixCapacity) {
   static TierProfile from(String var0, ConfigurationSection var1) {
      TierDefaults var2 = TierProfile.TierDefaults.forId(var0);
      return new TierProfile(var0.toUpperCase(Locale.ROOT), var1.getString("name", var2.display()), var1.getString("tooltip", ""), DeconstructionProfile.from(var1.getConfigurationSection("deconstruct-item")), var1.getDouble("stat-multiplier", var2.statMultiplier()), Math.max(1, var1.getInt("quality.min", var2.qualityMin())), Math.min(100, var1.getInt("quality.max", var2.qualityMax())), Math.max(0, var1.getInt("max-affixes", var2.maxAffixes())), Math.max((double)0.0F, var1.getDouble("generation.chance", var1.getDouble("chance", var2.chance()))), StatFormula.parse(var1, "affix-capacity"));
   }

   boolean usesAffixBudget() {
      return this.affixCapacity != null;
   }

   double rollAffixCapacity(int var1, Random var2) {
      return this.affixCapacity == null ? (double)0.0F : this.affixCapacity.roll(var1, var2);
   }

   boolean canDeconstruct() {
      return this.deconstruction != null && this.deconstruction.hasDrops();
   }

   int rollQuality(Random var1) {
      int var2 = Math.min(this.qualityMin, this.qualityMax);
      int var3 = Math.max(this.qualityMin, this.qualityMax);
      return var2 + var1.nextInt(Math.max(1, var3 - var2 + 1));
   }

   private static record TierDefaults(String display, double statMultiplier, int qualityMin, int qualityMax, int maxAffixes, double chance) {
      private static TierDefaults forId(String var0) {
         TierDefaults var10000;
         switch (var0.toUpperCase(Locale.ROOT)) {
            case "STARTER":
               var10000 = new TierDefaults("&7新人測試", (double)1.0F, 50, 50, 0, (double)0.0F);
               break;
            case "UNCOMMON":
               var10000 = new TierDefaults("&a優良", 1.05, 52, 82, 2, 0.26);
               break;
            case "RARE":
               var10000 = new TierDefaults("&9稀有", 1.1, 58, 86, 2, (double)0.25F);
               break;
            case "EPIC":
            case "VERY_RARE":
               var10000 = new TierDefaults("&5史詩", 1.2, 68, 93, 3, 0.12);
               break;
            case "LEGENDARY":
               var10000 = new TierDefaults("&6傳說", 1.33, 78, 98, 4, 0.055);
               break;
            case "VAST":
               var10000 = new TierDefaults("&b浩瀚", 1.48, 88, 100, 5, 0.025);
               break;
            case "MYTHIC":
            case "MYTHICAL":
               var10000 = new TierDefaults("&c神話", 1.62, 92, 100, 6, 0.01);
               break;
            default:
               var10000 = new TierDefaults("&f凡品", (double)1.0F, 45, 78, 1, 0.55);
         }

         return var10000;
      }
   }
}
