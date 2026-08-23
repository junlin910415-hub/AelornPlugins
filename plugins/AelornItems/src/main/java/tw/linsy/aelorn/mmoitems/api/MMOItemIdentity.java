package tw.linsy.aelorn.mmoitems.api;

import java.util.List;
import java.util.Map;

public record MMOItemIdentity(String type, String id, String tier, int level, String requiredClass, int requiredLevel, Map<String, Integer> skillRequirements, List<String> questRequirements, String majorIdentification, Map<String, Double> stats) {
   public MMOItemIdentity(String type, String id, String tier, int level, String requiredClass, int requiredLevel, Map<String, Integer> skillRequirements, List<String> questRequirements, String majorIdentification, Map<String, Double> stats) {
      skillRequirements = Map.copyOf(skillRequirements);
      questRequirements = List.copyOf(questRequirements);
      majorIdentification = majorIdentification == null ? "" : majorIdentification;
      stats = Map.copyOf(stats);
      this.type = type;
      this.id = id;
      this.tier = tier;
      this.level = level;
      this.requiredClass = requiredClass;
      this.requiredLevel = requiredLevel;
      this.skillRequirements = skillRequirements;
      this.questRequirements = questRequirements;
      this.majorIdentification = majorIdentification;
      this.stats = stats;
   }

   public MMOItemIdentity(String var1, String var2, String var3, int var4, String var5, int var6, Map<String, Double> var7) {
      this(var1, var2, var3, var4, var5, var6, Map.of(), List.of(), "", var7);
   }
}
