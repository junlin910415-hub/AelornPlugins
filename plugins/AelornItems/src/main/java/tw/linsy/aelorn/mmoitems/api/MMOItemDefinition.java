package tw.linsy.aelorn.mmoitems.api;

import java.util.List;
import java.util.Map;

public record MMOItemDefinition(String type, String id, String displayName, String category, String tier, String requiredClass, int requiredLevel, Map<String, Integer> skillRequirements, List<String> questRequirements, String majorIdentification, boolean weapon, Map<String, Double> baseStats) {
   public MMOItemDefinition(String type, String id, String displayName, String category, String tier, String requiredClass, int requiredLevel, Map<String, Integer> skillRequirements, List<String> questRequirements, String majorIdentification, boolean weapon, Map<String, Double> baseStats) {
      skillRequirements = Map.copyOf(skillRequirements);
      questRequirements = List.copyOf(questRequirements);
      majorIdentification = majorIdentification == null ? "" : majorIdentification;
      baseStats = Map.copyOf(baseStats);
      this.type = type;
      this.id = id;
      this.displayName = displayName;
      this.category = category;
      this.tier = tier;
      this.requiredClass = requiredClass;
      this.requiredLevel = requiredLevel;
      this.skillRequirements = skillRequirements;
      this.questRequirements = questRequirements;
      this.majorIdentification = majorIdentification;
      this.weapon = weapon;
      this.baseStats = baseStats;
   }

   public MMOItemDefinition(String var1, String var2, String var3, String var4, String var5, String var6, int var7, boolean var8, Map<String, Double> var9) {
      this(var1, var2, var3, var4, var5, var6, var7, Map.of(), List.of(), "", var8, var9);
   }
}
