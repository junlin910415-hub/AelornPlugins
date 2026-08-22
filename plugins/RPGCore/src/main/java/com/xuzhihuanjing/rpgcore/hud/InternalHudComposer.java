package com.xuzhihuanjing.rpgcore.hud;

import com.xuzhihuanjing.rpgcore.combat.CombatHudSnapshot;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

final class InternalHudComposer {
   static final Key IMAGE_FONT = Key.key("rpgcore_hud", "hud_rpgcore_status_image");
   static final Key VALUE_FONT = Key.key("rpgcore_hud", "hud_rpgcore_status_text_values");
   static final Key CLASS_FONT = Key.key("rpgcore_hud", "hud_rpgcore_status_text_class");
   static final Key NOTIFICATION_FONT = Key.key("rpgcore_hud", "hud_rpgcore_status_text_notification");
   static final Key SPACE_FONT = Key.key("rpgcore_hud", "space");
   private static final int SPACE_CENTER_CODEPOINT = 851968;
   private static final int IMAGE_NEGATIVE_ONE_CODEPOINT = 843777;
   private static final int FRAME_FIRST_CODEPOINT = 843778;
   private static final int HEALTH_BASE_CODEPOINT = 843781;
   private static final int MANA_BASE_CODEPOINT = 843813;
   private static final int STAMINA_BASE_CODEPOINT = 843845;
   private static final int AIR_BASE_CODEPOINT = 843870;
   private static final int STAMINA_ICON_CODEPOINT = 843896;
   private static final int AIR_ICON_CODEPOINT = 843897;
   private static final int COMBO_CLICK_CODEPOINT = 843903;
   private static final int COMBO_EMPTY_CODEPOINT = 843904;
   private static final int COMBO_ARROW_CODEPOINT = 843905;
   private static final int LAYOUT_HALF_WIDTH = 146;
   private static final ShadowColor NO_SHADOW = ShadowColor.shadowColor(0);
   private static final ShadowColor TEXT_SHADOW = ShadowColor.shadowColor(-1073741824);
   private static final TextColor WHITE = TextColor.color(16777215);
   private static final TextColor HEALTH = TextColor.color(16734572);
   private static final TextColor MANA = TextColor.color(5627903);
   private static final TextColor SEPARATOR = TextColor.color(5723991);
   private static final TextColor CLASS = TextColor.color(15913091);
   private static final TextColor NOTIFICATION = TextColor.color(16769946);
   private static final Map<String, Integer> CLASS_CODEPOINTS = Map.of("vanguard", 843898, "ranger", 843899, "shadowblade", 843900, "arcanist", 843901, "warden", 843902);
   private final int maximumNotificationCodePoints;

   InternalHudComposer(int maximumNotificationCodePoints) {
      this.maximumNotificationCodePoints = maximumNotificationCodePoints;
   }

   Component compose(CombatHudSnapshot snapshot, InternalHudVisualState visual) {
      TextComponent.Builder output = Component.text();
      this.appendSpace(output, -1);
      this.appendSpace(output, -146);
      this.appendImage(output, 4, 843778 + visual.frame(), 292);
      this.appendImage(output, 30, 843781 + visual.healthStep(), this.splitWidth(96, visual.healthStep(), 32));
      this.appendImage(output, 174, 843813 + visual.manaStep(), this.splitWidth(96, visual.manaStep(), 32));
      int movementCodePoint = snapshot.underwater() ? 843870 + visual.movementStep() : 843845 + visual.movementStep();
      this.appendImage(output, 112, movementCodePoint, this.splitWidth(76, visual.movementStep(), 25));
      this.appendImage(output, 94, snapshot.underwater() ? 843897 : 843896, 16);
      this.appendImage(output, 142, (Integer)CLASS_CODEPOINTS.getOrDefault(snapshot.classId(), (Integer)CLASS_CODEPOINTS.get("vanguard")), 16);
      if (snapshot.comboActive()) {
         int[] clickPositions = new int[]{121, 145, 169};
         int[] arrowPositions = new int[]{139, 163};

         for(int index = 0; index < clickPositions.length; ++index) {
            int codePoint = index < snapshot.combo().size() ? 843903 : 843904;
            this.appendImage(output, clickPositions[index], codePoint, 16);
         }

         for(int position : arrowPositions) {
            this.appendImage(output, position, 843905, 16);
         }
      }

      this.appendValue(output, 79, HudNumberFormat.compact(snapshot.currentHealth()), HudNumberFormat.compact(snapshot.maximumHealth()), HEALTH);
      this.appendValue(output, 221, HudNumberFormat.compact(snapshot.currentMana()), HudNumberFormat.compact(snapshot.maximumMana()), MANA);
      this.appendCenteredText(output, 150, Integer.toString(snapshot.level()), VALUE_FONT, 11, WHITE);
      this.appendCenteredText(output, 150, snapshot.className(), CLASS_FONT, 9, CLASS);
      if (snapshot.notificationActive()) {
         this.appendCenteredText(output, 150, this.truncate(snapshot.notification(), this.maximumNotificationCodePoints), NOTIFICATION_FONT, 10, NOTIFICATION);
      }

      this.appendSpace(output, 146);
      return output.build();
   }

   static int listenerStep(double ratio, int steps) {
      double clamped = Math.max((double)0.0F, Math.min((double)1.0F, ratio));
      return 1 + (int)Math.round(clamped * (double)(steps - 1));
   }

   private void appendValue(TextComponent.Builder output, int center, String current, String maximum, TextColor maximumColor) {
      TextComponent.Builder value = (TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)((TextComponent.Builder)Component.text().font(VALUE_FONT)).shadowColor(TEXT_SHADOW)).append(Component.text(current, WHITE))).append(Component.text("/", SEPARATOR))).append(Component.text(maximum, maximumColor));
      int width = this.textWidth(current + "/" + maximum, 11);
      this.appendOverlay(output, center - width / 2, value.build(), width);
   }

   private void appendCenteredText(TextComponent.Builder output, int center, String text, Key font, int glyphWidth, TextColor color) {
      if (text != null && !text.isBlank()) {
         Component component = ((TextComponent)((TextComponent)Component.text(text).font(font)).color(color)).shadowColor(TEXT_SHADOW);
         int width = this.textWidth(text, glyphWidth);
         this.appendOverlay(output, center - width / 2, component, width);
      }
   }

   private void appendImage(TextComponent.Builder output, int pixel, int codePoint, int width) {
      String var10000 = glyph(codePoint);
      Component image = ((TextComponent)((TextComponent)Component.text(var10000 + glyph(843777)).font(IMAGE_FONT)).color(WHITE)).shadowColor(NO_SHADOW);
      this.appendOverlay(output, pixel, image, width);
   }

   private void appendOverlay(TextComponent.Builder output, int pixel, Component component, int width) {
      this.appendSpace(output, pixel);
      output.append(component);
      this.appendSpace(output, -pixel - width);
   }

   private void appendSpace(TextComponent.Builder output, int pixels) {
      if (pixels != 0) {
         if (pixels >= -8192 && pixels <= 8192) {
            output.append(((TextComponent)Component.text(glyph(851968 + pixels)).font(SPACE_FONT)).shadowColor(NO_SHADOW));
         } else {
            throw new IllegalArgumentException("HUD space is outside the bundled font range");
         }
      }
   }

   private int splitWidth(int fullWidth, int step, int steps) {
      return Math.max(1, fullWidth * step / steps);
   }

   private int textWidth(String text, int glyphWidth) {
      int width = 0;

      int codePoint;
      for(int offset = 0; offset < text.length(); width += codePoint == 32 ? 4 : glyphWidth) {
         codePoint = text.codePointAt(offset);
         offset += Character.charCount(codePoint);
      }

      return width;
   }

   private String truncate(String value, int maximumCodePoints) {
      String singleLine = value.replace('\n', ' ').replace('\r', ' ').trim();
      int count = singleLine.codePointCount(0, singleLine.length());
      if (count <= maximumCodePoints) {
         return singleLine;
      } else {
         int keep = Math.max(1, maximumCodePoints - 1);
         int end = singleLine.offsetByCodePoints(0, keep);
         String var10000 = singleLine.substring(0, end);
         return var10000 + "…";
      }
   }

   private static String glyph(int codePoint) {
      return new String(Character.toChars(codePoint));
   }
}
