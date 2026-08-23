package tw.linsy.aelorn.mmoitems;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;

final class RpgGuiTitle {
   private static final Key GUI_FONT = Key.key("rpgcore_hud", "gui");
   private static final Key INTERFACE_FONT = Key.key("rpgcore_hud", "interface");
   private static final Key SPACE_FONT = Key.key("rpgcore_hud", "space");
   private static final int SPACE_CENTER_CODEPOINT = 851968;
   private static final int ITEM_BROWSER_CODEPOINT = 844033;
   private static final int ITEM_EDITOR_CODEPOINT = 844034;
   private static final int KEY_F_CODEPOINT = 844288;
   private static final int GUI_WIDTH = 256;
   private static final int LEADING_SHIFT = -48;
   private static final int CONTAINER_WIDTH = 176;
   private static final int TITLE_ORIGIN_X = 8;
   private static final ShadowColor NO_SHADOW = ShadowColor.shadowColor(0);
   private static final TextColor TITLE_COLOR = TextColor.color(16043373);

   private RpgGuiTitle() {
   }

   static Component browser(MMOItemsPlugin var0, String var1) {
      return title(var0, 844033, var1);
   }

   static Component editor(MMOItemsPlugin var0, String var1) {
      return title(var0, 844034, var1);
   }

   static Component keyF(MMOItemsPlugin var0) {
      return !texturesAvailable(var0) ? ((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)Component.text().append((TextComponent)Component.text("[").color(NamedTextColor.DARK_GRAY))).append((TextComponent)Component.text("F").color(NamedTextColor.WHITE))).append((TextComponent)Component.text("]").color(NamedTextColor.DARK_GRAY))).build() : ((TextComponent)Component.text(glyph(844288)).font(INTERFACE_FONT)).shadowColor(NO_SHADOW);
   }

   private static boolean texturesAvailable(MMOItemsPlugin var0) {
      if (!var0.getConfig().getBoolean("gui.internal-textures.enabled", true)) {
         return false;
      } else {
         String var1 = var0.getConfig().getString("gui.internal-textures.provider-plugin", "RPGCore");
         return var1 == null || var1.isBlank() || Bukkit.getPluginManager().isPluginEnabled(var1);
      }
   }

   private static Component title(MMOItemsPlugin var0, int var1, String var2) {
      if (!texturesAvailable(var0)) {
         return Component.text(var2, NamedTextColor.DARK_GRAY);
      } else {
         int var3 = Math.max(-256, Math.min(64, var0.getConfig().getInt("gui.internal-textures.leading-shift", -48)));
         int var4 = -8 + Math.max(0, (176 - textWidth(var2)) / 2) + var0.getConfig().getInt("gui.internal-textures.title-offset", 0);
         TextComponent.Builder var5 = Component.text();
         appendSpace(var5, var3);
         var5.append(((TextComponent)Component.text(glyph(var1)).font(GUI_FONT)).shadowColor(NO_SHADOW));
         appendSpace(var5, -(var3 + 256) + var4);
         var5.append(((TextComponent)Component.text(var2).color(TITLE_COLOR)).shadowColor(ShadowColor.shadowColor(-1342177280)));
         return var5.build();
      }
   }

   private static int textWidth(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         int var1 = 0;
         int var2 = 0;

         while(var2 < var0.length()) {
            int var3 = var0.codePointAt(var2);
            var2 += Character.charCount(var3);
            if (var3 == 32) {
               var1 += 4;
            } else if (var3 >= 4352 && var3 <= 65500) {
               var1 += 9;
            } else {
               var1 += 6;
            }
         }

         return var1;
      } else {
         return 0;
      }
   }

   private static void appendSpace(TextComponent.Builder var0, int var1) {
      var0.append(((TextComponent)Component.text(glyph(851968 + var1)).font(SPACE_FONT)).shadowColor(NO_SHADOW));
   }

   private static String glyph(int var0) {
      return new String(Character.toChars(var0));
   }
}
