package com.xuzhihuanjing.rpgcore.config;

import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;

public record HudSettings(long updateIntervalTicks, long notificationDurationMillis, Renderer renderer, boolean internalEnabled, boolean waitForResourcePack, boolean installPackOnStartup, boolean regeneratePackOnChange, int animationFrameTicks, double healthSmoothing, double manaSmoothing, double movementSmoothing, int maximumNotificationCodePoints, boolean showProgressionOnExperienceBar, boolean aeloriaAssetsEnabled, boolean sidebarEnabled, long sidebarUpdateIntervalTicks, String sidebarTitle) {
   public static HudSettings from(FileConfiguration config) {
      String configuredRenderer = config.getString("hud.renderer", "internal");

      Renderer renderer;
      try {
         renderer = HudSettings.Renderer.valueOf(configuredRenderer.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var4) {
         throw new IllegalArgumentException("hud.renderer must be one of: aeloriahud, internal, native");
      }

      HudSettings settings = new HudSettings(config.getLong("hud.update-interval-ticks", 2L), config.getLong("hud.notification-duration-ms", 1200L), renderer, config.getBoolean("hud.internal.enabled", true), config.getBoolean("hud.internal.wait-for-resource-pack", true), config.getBoolean("hud.internal.install-pack-on-startup", true), config.getBoolean("hud.internal.regenerate-pack-on-change", true), config.getInt("hud.internal.animation-frame-ticks", 4), config.getDouble("hud.internal.smoothing.health", 0.65), config.getDouble("hud.internal.smoothing.mana", 0.65), config.getDouble("hud.internal.smoothing.movement", 0.55), config.getInt("hud.internal.maximum-notification-codepoints", 26), config.getBoolean("hud.native.show-progression-on-experience-bar", true), config.getBoolean("hud.aeloria-assets.enabled", true), config.getBoolean("hud.sidebar.enabled", true), config.getLong("hud.sidebar.update-interval-ticks", 10L), config.getString("hud.sidebar.title", "<gold><bold>RPGCORE</bold></gold>"));
      if (settings.updateIntervalTicks >= 1L && settings.updateIntervalTicks <= 20L) {
         if (settings.notificationDurationMillis >= 300L && settings.notificationDurationMillis <= 5000L) {
            if (settings.renderer == HudSettings.Renderer.INTERNAL && !settings.internalEnabled) {
               throw new IllegalArgumentException("hud.internal.enabled must be true when hud.renderer is internal");
            } else if (settings.animationFrameTicks >= 1 && settings.animationFrameTicks <= 40) {
               validateSmoothing("health", settings.healthSmoothing);
               validateSmoothing("mana", settings.manaSmoothing);
               validateSmoothing("movement", settings.movementSmoothing);
               if (settings.maximumNotificationCodePoints >= 8 && settings.maximumNotificationCodePoints <= 64) {
                  if (settings.sidebarUpdateIntervalTicks >= 2L && settings.sidebarUpdateIntervalTicks <= 100L) {
                     if (settings.sidebarTitle != null && !settings.sidebarTitle.isBlank()) {
                        return settings;
                     } else {
                        throw new IllegalArgumentException("hud.sidebar.title cannot be blank");
                     }
                  } else {
                     throw new IllegalArgumentException("hud.sidebar.update-interval-ticks must be between 2 and 100");
                  }
               } else {
                  throw new IllegalArgumentException("hud.internal.maximum-notification-codepoints must be between 8 and 64");
               }
            } else {
               throw new IllegalArgumentException("hud.internal.animation-frame-ticks must be between 1 and 40");
            }
         } else {
            throw new IllegalArgumentException("hud.notification-duration-ms must be between 300 and 5000");
         }
      } else {
         throw new IllegalArgumentException("hud.update-interval-ticks must be between 1 and 20");
      }
   }

   public Set<String> requiredExternalPlugins() {
      if (this.renderer == HudSettings.Renderer.AELORIAHUD) {
         return Set.of("AeloriaHUD", "Nexo");
      } else {
         return this.renderer == HudSettings.Renderer.INTERNAL && this.internalEnabled ? Set.of("AeloriaHUD", "Nexo") : Set.of();
      }
   }

   private static void validateSmoothing(String name, double value) {
      if (!Double.isFinite(value) || value <= (double)0.0F || value > (double)1.0F) {
         throw new IllegalArgumentException("hud.internal.smoothing." + name + " must be greater than 0 and at most 1");
      }
   }

   public static enum Renderer {
      AELORIAHUD,
      INTERNAL,
      NATIVE;
   }
}
