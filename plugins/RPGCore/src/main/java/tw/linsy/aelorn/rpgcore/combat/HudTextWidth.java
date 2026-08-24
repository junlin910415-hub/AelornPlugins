package tw.linsy.aelorn.rpgcore.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class HudTextWidth {
   private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

   private HudTextWidth() {
   }

   static int estimate(Component component) {
      return estimate(PLAIN.serialize(component));
   }

   static Component fit(Component component, int maximumWidth) {
      if (maximumWidth <= 0) {
         return Component.empty();
      } else {
         String text = PLAIN.serialize(component);
         if (estimate(text) <= maximumWidth) {
            return component;
         } else {
            int ellipsisWidth = codePointWidth(8230);
            if (ellipsisWidth > maximumWidth) {
               return Component.empty();
            } else {
               StringBuilder fitted = new StringBuilder();
               int width = 0;

               int nextWidth;
               for(int offset = 0; offset < text.length(); width = nextWidth) {
                  int codePoint = text.codePointAt(offset);
                  offset += Character.charCount(codePoint);
                  nextWidth = width + codePointWidth(codePoint);
                  if (nextWidth + ellipsisWidth > maximumWidth) {
                     break;
                  }

                  fitted.appendCodePoint(codePoint);
               }

               fitted.append('…');
               return Component.text(fitted.toString()).style(component.style());
            }
         }
      }
   }

   private static int estimate(String text) {
      int width = 0;

      int codePoint;
      for(int offset = 0; offset < text.length(); width += codePointWidth(codePoint)) {
         codePoint = text.codePointAt(offset);
         offset += Character.charCount(codePoint);
      }

      return Math.max(0, width - (text.isEmpty() ? 0 : 1));
   }

   private static int codePointWidth(int codePoint) {
      return codePoint == 32 ? 4 : (codePoint < 128 ? asciiWidth(codePoint) : 9);
   }

   private static int asciiWidth(int codePoint) {
      byte var10000;
      switch (codePoint) {
         case 33:
         case 39:
         case 44:
         case 46:
         case 58:
         case 59:
         case 96:
         case 105:
         case 108:
         case 124:
            var10000 = 2;
            break;
         case 40:
         case 41:
         case 73:
         case 91:
         case 93:
         case 116:
            var10000 = 4;
            break;
         case 60:
         case 62:
         case 102:
         case 107:
            var10000 = 5;
            break;
         default:
            var10000 = 6;
      }

      return var10000;
   }
}
