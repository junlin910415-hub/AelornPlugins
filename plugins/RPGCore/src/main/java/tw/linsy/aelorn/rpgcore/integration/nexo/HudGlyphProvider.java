package tw.linsy.aelorn.rpgcore.integration.nexo;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface HudGlyphProvider {
   Optional<Component> glyph(Player player, String glyphId);

   default Component shift(int pixels) {
      return Component.empty();
   }

   static HudGlyphProvider empty() {
      return (player, glyphId) -> Optional.empty();
   }
}
