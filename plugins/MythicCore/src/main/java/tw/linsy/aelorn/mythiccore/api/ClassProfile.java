package tw.linsy.aelorn.mythiccore.api;

import java.util.List;
import java.util.Map;

public record ClassProfile(String id, String displayName, String secondJobName, List<String> advancedJobs, String role, String weapon, Map<String, Double> baseStats, Map<String, Double> scaling, Map<String, Integer> ratings, List<ArchetypeProfile> archetypes) {
   public ClassProfile(String id, String displayName, String secondJobName, List<String> advancedJobs, String role, String weapon, Map<String, Double> baseStats, Map<String, Double> scaling, Map<String, Integer> ratings, List<ArchetypeProfile> archetypes) {
      id = StatSnapshot.normalize(id);
      displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : id;
      secondJobName = secondJobName == null ? "" : secondJobName.trim();
      advancedJobs = List.copyOf((advancedJobs == null ? List.of() : advancedJobs).stream().map((var0) -> var0 == null ? "" : var0.trim()).filter((var0) -> !var0.isBlank()).toList());
      role = role == null ? "" : role.trim();
      weapon = weapon == null ? "" : weapon.trim();
      baseStats = Map.copyOf(baseStats == null ? Map.of() : baseStats);
      scaling = Map.copyOf(scaling == null ? Map.of() : scaling);
      ratings = Map.copyOf(ratings == null ? Map.of() : ratings);
      archetypes = List.copyOf(archetypes == null ? List.of() : archetypes);
      this.id = id;
      this.displayName = displayName;
      this.secondJobName = secondJobName;
      this.advancedJobs = advancedJobs;
      this.role = role;
      this.weapon = weapon;
      this.baseStats = baseStats;
      this.scaling = scaling;
      this.ratings = ratings;
      this.archetypes = archetypes;
   }
}
