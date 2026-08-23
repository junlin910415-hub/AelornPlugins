package com.xuzhihuanjing.rpgcore.integration.nexo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

final class AeloriaHudGlyphProvider implements HudGlyphProvider {
   private static final Key GUI_FONT = Key.key("rpgcore_hud", "gui");
   private static final Key INTERFACE_FONT = Key.key("rpgcore_hud", "interface");
   private static final Key SPACE_FONT = Key.key("rpgcore_hud", "space");
   private static final int SPACE_CENTER_CODEPOINT = 851968;
   private static final Map<String, Glyph> GLYPHS = Map.of(
      "rpgcore_content_book_gui", new Glyph(844037, GUI_FONT),
      "rpgcore_character_selector_gui", new Glyph(844032, GUI_FONT),
      "rpgcore_key_f", new Glyph(844288, INTERFACE_FONT)
   );

   AeloriaHudGlyphProvider(Logger logger, Collection<String> configuredGlyphs) {
      List<String> missing = configuredGlyphs.stream().filter(id -> !GLYPHS.containsKey(id)).toList();
      if (missing.isEmpty()) {
         logger.info("Aeloria interface integration enabled with " + configuredGlyphs.size() + " glyphs.");
      } else {
         logger.warning("Aeloria interface glyphs are not registered: " + String.join(", ", missing) + ". Text fallbacks will be used.");
      }
   }

   @Override
   public Optional<Component> glyph(Player player, String glyphId) {
      Glyph glyph = GLYPHS.get(glyphId);
      return glyph == null
         ? Optional.empty()
         : Optional.of(Component.text(codePoint(glyph.codePoint())).font(glyph.font()));
   }

   @Override
   public Component shift(int pixels) {
      if (pixels < -8192 || pixels > 8192) {
         return Component.empty();
      }
      return Component.text(codePoint(SPACE_CENTER_CODEPOINT + pixels)).font(SPACE_FONT);
   }

   private static String codePoint(int value) {
      return new String(Character.toChars(value));
   }

   private record Glyph(int codePoint, Key font) {
   }
}

