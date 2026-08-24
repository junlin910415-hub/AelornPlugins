package tw.linsy.aelorn.rpgcore.domain.discovery;

import java.util.List;
import java.util.Objects;

public record DiscoveryDefinition(String id, String displayName, String description, DiscoveryCategory category, String iconMaterial, int minimumLevel, String world, double x, double y, double z, double radius, long rewardExperience, List<String> prerequisites, List<String> requiredQuests, boolean hiddenUntilDiscovered) {
   public DiscoveryDefinition(String id, String displayName, String description, DiscoveryCategory category, String iconMaterial, int minimumLevel, String world, double x, double y, double z, double radius, long rewardExperience, List<String> prerequisites, List<String> requiredQuests, boolean hiddenUntilDiscovered) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(description, "description");
      Objects.requireNonNull(category, "category");
      Objects.requireNonNull(iconMaterial, "iconMaterial");
      Objects.requireNonNull(world, "world");
      prerequisites = List.copyOf(prerequisites);
      requiredQuests = List.copyOf(requiredQuests);
      this.id = id;
      this.displayName = displayName;
      this.description = description;
      this.category = category;
      this.iconMaterial = iconMaterial;
      this.minimumLevel = minimumLevel;
      this.world = world;
      this.x = x;
      this.y = y;
      this.z = z;
      this.radius = radius;
      this.rewardExperience = rewardExperience;
      this.prerequisites = prerequisites;
      this.requiredQuests = requiredQuests;
      this.hiddenUntilDiscovered = hiddenUntilDiscovered;
   }
}
