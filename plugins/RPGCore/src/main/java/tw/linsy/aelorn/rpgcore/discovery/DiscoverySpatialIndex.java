package tw.linsy.aelorn.rpgcore.discovery;

import tw.linsy.aelorn.rpgcore.domain.discovery.DiscoveryDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DiscoverySpatialIndex {
   public static final int CELL_SIZE = 64;
   private final Map<Cell, List<DiscoveryDefinition>> cells;

   public DiscoverySpatialIndex(Collection<DiscoveryDefinition> definitions) {
      Map<Cell, List<DiscoveryDefinition>> indexed = new LinkedHashMap();

      for(DiscoveryDefinition definition : definitions) {
         int minimumX = cell(definition.x() - definition.radius());
         int maximumX = cell(definition.x() + definition.radius());
         int minimumZ = cell(definition.z() - definition.radius());
         int maximumZ = cell(definition.z() + definition.radius());

         for(int x = minimumX; x <= maximumX; ++x) {
            for(int z = minimumZ; z <= maximumZ; ++z) {
               ((List)indexed.computeIfAbsent(new Cell(normalize(definition.world()), x, z), (ignored) -> new ArrayList())).add(definition);
            }
         }
      }

      Map<Cell, List<DiscoveryDefinition>> immutable = new LinkedHashMap();
      indexed.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
      this.cells = Map.copyOf(immutable);
   }

   public List<DiscoveryDefinition> candidates(String world, double x, double z) {
      if (world != null && Double.isFinite(x) && Double.isFinite(z)) {
         Set<DiscoveryDefinition> matches = new LinkedHashSet((Collection)this.cells.getOrDefault(new Cell(normalize(world), cell(x), cell(z)), List.of()));
         return List.copyOf(matches);
      } else {
         return List.of();
      }
   }

   private static int cell(double coordinate) {
      return (int)Math.floor(coordinate / (double)64.0F);
   }

   private static String normalize(String world) {
      return world.toLowerCase(Locale.ROOT);
   }

   private static record Cell(String world, int x, int z) {
   }
}
