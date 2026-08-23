package com.xuzhihuanjing.rpgcore.integration.nexo;

import com.xuzhihuanjing.rpgcore.config.HudSettings;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class HudGlyphProviders {
   private HudGlyphProviders() {
   }

   public static HudGlyphProvider create(PluginManager pluginManager, HudSettings settings, Logger logger) {
      return create(pluginManager, settings, List.of(), logger);
   }

   public static HudGlyphProvider create(PluginManager pluginManager, HudSettings settings, Collection<String> additionalGlyphs, Logger logger) {
      if (!settings.aeloriaAssetsEnabled()) {
         logger.info("Aeloria interface glyph integration is disabled.");
         return HudGlyphProvider.empty();
      }

      Plugin aeloriaHud = pluginManager.getPlugin("AeloriaHUD");
      if (aeloriaHud == null || !aeloriaHud.isEnabled()) {
         logger.warning("AeloriaHUD was not detected; RPGCore menus will use text fallbacks.");
         return HudGlyphProvider.empty();
      }

      List<String> glyphs = additionalGlyphs.stream()
         .filter(id -> id != null && !id.isBlank())
         .distinct()
         .toList();
      return new AeloriaHudGlyphProvider(logger, glyphs);
   }
}

