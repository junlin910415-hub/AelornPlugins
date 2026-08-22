package com.xuzhihuanjing.rpgcore.config;

import com.xuzhihuanjing.rpgcore.domain.discovery.DiscoveryDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestObjectiveType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContentGraphValidator {
   private ContentGraphValidator() {
   }

   public static void validate(QuestRegistry quests, DiscoveryRegistry discoveries) {
      List<String> errors = new ArrayList();
      Map<String, List<String>> dependencies = new HashMap();

      for(QuestDefinition quest : quests.all()) {
         String node = questNode(quest.id());
         List<String> edges = new ArrayList();

         for(String prerequisite : quest.prerequisites()) {
            edges.add(questNode(prerequisite));
         }

         quest.objectives().stream().filter((objective) -> objective.type() == QuestObjectiveType.DISCOVER_LOCATION).forEach((objective) -> edges.add(discoveryNode(objective.target())));
         dependencies.put(node, List.copyOf(edges));
      }

      for(DiscoveryDefinition discovery : discoveries.all()) {
         String node = discoveryNode(discovery.id());
         List<String> edges = new ArrayList();

         for(String prerequisite : discovery.prerequisites()) {
            edges.add(discoveryNode(prerequisite));
         }

         for(String questId : discovery.requiredQuests()) {
            if (quests.find(questId).isEmpty()) {
               String var10001 = discovery.id();
               errors.add("Discovery " + var10001 + " references unknown required quest " + questId);
            }

            edges.add(questNode(questId));
         }

         dependencies.put(node, List.copyOf(edges));
      }

      Set<String> visiting = new HashSet();
      Set<String> visited = new HashSet();

      for(String node : dependencies.keySet()) {
         detectCycle(node, dependencies, visiting, visited, errors);
      }

      if (!errors.isEmpty()) {
         throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
      }
   }

   private static void detectCycle(String node, Map<String, List<String>> dependencies, Set<String> visiting, Set<String> visited, List<String> errors) {
      if (!visited.contains(node) && dependencies.containsKey(node)) {
         if (!visiting.add(node)) {
            errors.add("Content dependency cycle detected at " + node);
         } else {
            for(String dependency : dependencies.get(node)) {
               detectCycle(dependency, dependencies, visiting, visited, errors);
            }

            visiting.remove(node);
            visited.add(node);
         }
      }
   }

   private static String questNode(String id) {
      return "quest:" + id;
   }

   private static String discoveryNode(String id) {
      return "discovery:" + id;
   }
}
