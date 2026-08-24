package tw.linsy.aelorn.rpgcore.config;

import org.bukkit.configuration.file.FileConfiguration;

public record InterfaceSettings(int contentBookHotbarSlot, String contentBookGuiGlyph, int contentBookGuiWidth, String contentBookItemId, String characterSelectorGuiGlyph, int characterSelectorGuiWidth) {
   public static InterfaceSettings from(FileConfiguration config) {
      InterfaceSettings settings = new InterfaceSettings(config.getInt("interface.content-book.hotbar-slot", 7), config.getString("interface.content-book.gui-glyph", "rpgcore_content_book_gui"), config.getInt("interface.content-book.gui-width", 205), config.getString("interface.content-book.item-id", "rpgcore_wayfinder_codex"), config.getString("interface.character-selector.gui-glyph", "rpgcore_character_selector_gui"), config.getInt("interface.character-selector.gui-width", 256));
      if (settings.contentBookHotbarSlot >= 0 && settings.contentBookHotbarSlot <= 8) {
         if (settings.contentBookGuiWidth >= 1 && settings.contentBookGuiWidth <= 256) {
            if (settings.characterSelectorGuiWidth >= 1 && settings.characterSelectorGuiWidth <= 512) {
               if (settings.contentBookGuiGlyph != null && !settings.contentBookGuiGlyph.isBlank() && settings.contentBookItemId != null && !settings.contentBookItemId.isBlank() && settings.characterSelectorGuiGlyph != null && !settings.characterSelectorGuiGlyph.isBlank()) {
                  return settings;
               } else {
                  throw new IllegalArgumentException("Interface asset IDs cannot be blank");
               }
            } else {
               throw new IllegalArgumentException("interface.character-selector.gui-width must be between 1 and 512");
            }
         } else {
            throw new IllegalArgumentException("interface.content-book.gui-width must be between 1 and 256");
         }
      } else {
         throw new IllegalArgumentException("interface.content-book.hotbar-slot must be between 0 and 8");
      }
   }
}
