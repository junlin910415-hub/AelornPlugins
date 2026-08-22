package com.xuzhihuanjing.rpgcore.hud;

import com.xuzhihuanjing.rpgcore.combat.CombatHudSnapshot;
import dev.aeloria.hud.api.AeloriaHudService;
import dev.aeloria.hud.api.HudStatKeys;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Publishes RPGCore's authoritative combat values into AeloriaHUD.
 *
 * <p>AeloriaHUD owns rendering and resource-pack dispatch. RPGCore only supplies
 * data, which avoids a second action-bar/boss-bar renderer fighting for the same
 * client UI surface.</p>
 */
public final class AeloriaCombatHudRenderer implements CombatHudRenderer {
   private final AeloriaHudService hudService;
   private final Duration notificationDuration;
   private final Map<UUID, PublishedState> published = new ConcurrentHashMap<>();

   public AeloriaCombatHudRenderer(AeloriaHudService hudService, long notificationDurationMillis) {
      this.hudService = Objects.requireNonNull(hudService, "hudService");
      this.notificationDuration = Duration.ofMillis(notificationDurationMillis);
   }

   @Override
   public void render(Player player, CombatHudSnapshot snapshot) {
      Objects.requireNonNull(player, "player");
      Objects.requireNonNull(snapshot, "snapshot");
      UUID playerId = player.getUniqueId();
      if (!snapshot.active() || !this.hudService.isVisible(playerId)) {
         this.hide(player);
         return;
      }

      PublishedState next = PublishedState.from(snapshot);
      PublishedState previous = this.published.put(playerId, next);
      if (previous == null || previous.currentHealth != next.currentHealth || previous.maximumHealth != next.maximumHealth) {
         this.hudService.setStat(playerId, HudStatKeys.HEALTH, next.currentHealth, next.maximumHealth);
      }
      if (previous == null || previous.currentMana != next.currentMana || previous.maximumMana != next.maximumMana) {
         this.hudService.setStat(playerId, HudStatKeys.MANA, next.currentMana, next.maximumMana);
      }
      if (previous == null || previous.currentExperience != next.currentExperience || previous.requiredExperience != next.requiredExperience) {
         this.hudService.setStat(playerId, HudStatKeys.EXPERIENCE, next.currentExperience, next.requiredExperience);
      }
      if (!next.notification.isBlank() && (previous == null || !next.notification.equals(previous.notification))) {
         this.hudService.pushNotification(playerId, Component.text(next.notification), this.notificationDuration);
      }
   }

   @Override
   public void hide(Player player) {
      this.clear(player.getUniqueId());
   }

   @Override
   public boolean isEnabled(Player player) {
      return this.hudService.isVisible(player.getUniqueId());
   }

   @Override
   public void setEnabled(Player player, boolean enabled) {
      UUID playerId = player.getUniqueId();
      if (!enabled) {
         this.clear(playerId);
      }
      this.hudService.setVisible(playerId, enabled);
   }

   @Override
   public Status status(Player player) {
      UUID playerId = player.getUniqueId();
      return new Status(
         "aeloriahud",
         this.hudService.isVisible(playerId),
         this.published.containsKey(playerId),
         this.hudService.activeResourcePack().isPresent() ? "published" : "unavailable"
      );
   }

   @Override
   public void close() {
      for (UUID playerId : this.published.keySet()) {
         this.clear(playerId);
      }
      this.published.clear();
   }

   private void clear(UUID playerId) {
      if (this.published.remove(playerId) == null) {
         return;
      }
      this.hudService.clearStat(playerId, HudStatKeys.HEALTH);
      this.hudService.clearStat(playerId, HudStatKeys.MANA);
      this.hudService.clearStat(playerId, HudStatKeys.EXPERIENCE);
   }

   private record PublishedState(
      int currentHealth,
      int maximumHealth,
      int currentMana,
      int maximumMana,
      long currentExperience,
      long requiredExperience,
      String notification
   ) {
      private static PublishedState from(CombatHudSnapshot snapshot) {
         return new PublishedState(
            snapshot.currentHealth(),
            snapshot.maximumHealth(),
            snapshot.currentMana(),
            snapshot.maximumMana(),
            snapshot.currentLevelExperience(),
            Math.max(1L, snapshot.requiredLevelExperience()),
            snapshot.notification()
         );
      }
   }
}
