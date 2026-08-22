package com.xuzhihuanjing.rpgcore.monster;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class ContributionLedger {
   private final Map<UUID, Map<UUID, Contribution>> ledgers = new ConcurrentHashMap();

   public void record(UUID monsterId, UUID playerId, double damage, long nowMillis) {
      if (!(damage <= (double)0.0F)) {
         this.ledgers.computeIfAbsent(monsterId, (ignored) -> new ConcurrentHashMap<>()).compute(playerId, (ignored, current) -> current == null ? new Contribution(damage, nowMillis) : current.add(damage, nowMillis));
      }
   }

   public Map<UUID, Double> settle(UUID monsterId, long nowMillis, long windowMillis) {
      Map<UUID, Contribution> contributions = this.ledgers.remove(monsterId);
      if (contributions != null && !contributions.isEmpty()) {
         double total = contributions.values().stream().filter((value) -> nowMillis - value.lastHitMillis() <= windowMillis).mapToDouble(Contribution::damage).sum();
         if (total <= (double)0.0F) {
            return Map.of();
         } else {
            Map<UUID, Double> shares = new LinkedHashMap();
            contributions.entrySet().stream().filter((entry) -> nowMillis - ((Contribution)entry.getValue()).lastHitMillis() <= windowMillis).sorted(Entry.<UUID, Contribution>comparingByValue(Comparator.comparingDouble(Contribution::damage)).reversed()).forEach((entry) -> shares.put((UUID)entry.getKey(), ((Contribution)entry.getValue()).damage() / total));
            return shares;
         }
      } else {
         return Map.of();
      }
   }

   public void clear(UUID monsterId) {
      this.ledgers.remove(monsterId);
   }

   public void clearAll() {
      this.ledgers.clear();
   }

   private static record Contribution(double damage, long lastHitMillis) {
      private Contribution add(double amount, long nowMillis) {
         return new Contribution(this.damage + amount, nowMillis);
      }
   }
}
