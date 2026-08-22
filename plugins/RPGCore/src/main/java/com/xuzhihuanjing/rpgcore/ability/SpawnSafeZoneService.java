package com.xuzhihuanjing.rpgcore.ability;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class SpawnSafeZoneService {
   private final double radiusSquared;

   public SpawnSafeZoneService(double radius) {
      this.radiusSquared = radius * radius;
   }

   public boolean isSafe(Player player) {
      if (player.hasPermission("rpgcore.admin")) {
         return true;
      } else {
         Location spawn = player.getWorld().getSpawnLocation();
         return player.getLocation().distanceSquared(spawn) <= this.radiusSquared;
      }
   }
}
