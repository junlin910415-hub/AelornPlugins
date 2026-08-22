package com.xuzhihuanjing.rpgcore.combat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;

public final class HudNotificationService {
   private final long defaultDurationMillis;
   private final Map<UUID, Notification> notifications = new ConcurrentHashMap();

   public HudNotificationService(long defaultDurationMillis) {
      if (defaultDurationMillis <= 0L) {
         throw new IllegalArgumentException("HUD notification duration must be positive");
      } else {
         this.defaultDurationMillis = defaultDurationMillis;
      }
   }

   public void show(UUID playerId, Component message) {
      this.show(playerId, message, this.defaultDurationMillis, System.currentTimeMillis());
   }

   void show(UUID playerId, Component message, long durationMillis, long nowMillis) {
      this.notifications.put(playerId, new Notification(message, nowMillis + Math.max(1L, durationMillis)));
   }

   public Optional<Component> current(UUID playerId, long nowMillis) {
      Notification notification = (Notification)this.notifications.get(playerId);
      if (notification == null) {
         return Optional.empty();
      } else if (nowMillis >= notification.expiresAtMillis()) {
         this.notifications.remove(playerId, notification);
         return Optional.empty();
      } else {
         return Optional.of(notification.message());
      }
   }

   public void clear(UUID playerId) {
      this.notifications.remove(playerId);
   }

   public void clearAll() {
      this.notifications.clear();
   }

   private static record Notification(Component message, long expiresAtMillis) {
   }
}
