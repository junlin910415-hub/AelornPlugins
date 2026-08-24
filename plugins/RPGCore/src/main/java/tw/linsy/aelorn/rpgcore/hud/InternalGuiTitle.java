package tw.linsy.aelorn.rpgcore.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

public final class InternalGuiTitle {
   private static final Key GUI_FONT = Key.key("rpgcore_hud", "gui");
   private static final Key SPACE_FONT = Key.key("rpgcore_hud", "space");
   private static final int SPACE_CENTER_CODEPOINT = 851968;
   private static final int MAIN_MENU_CODEPOINT = 844032;
   private static final int ITEM_BROWSER_CODEPOINT = 844033;
   private static final int ITEM_EDITOR_CODEPOINT = 844034;
   private static final int PARTY_MENU_CODEPOINT = 844035;
   private static final int QUEST_JOURNAL_CODEPOINT = 844036;
   private static final int CONTENT_BOOK_CODEPOINT = 844037;
   private static final int CHARACTER_MENU_CODEPOINT = 844038;
   private static final int ABILITY_TREE_CODEPOINT = 844039;
   private static final int PROFESSION_MENU_CODEPOINT = 844040;
   private static final int IDENTIFICATION_CODEPOINT = 844041;
   private static final int SKILL_CRYSTAL_CODEPOINT = 844042;
   private static final int CHARACTER_PROFILE_CODEPOINT = 844043;
   private static final int GUI_WIDTH = 256;
   private static final int LEADING_SHIFT = -18;
   private static final ShadowColor NO_SHADOW = ShadowColor.shadowColor(0);
   private static final TextColor TITLE_COLOR = TextColor.color(16043373);

   private InternalGuiTitle() {
   }

   public static Component mainMenu() {
      return compose(844032, "角色總覽");
   }

   public static Component profile() {
      return compose(844043, "角色檔案");
   }

   public static Component itemBrowser() {
      return compose(844033, "裝備圖鑑");
   }

   public static Component itemEditor() {
      return compose(844034, "物品工坊");
   }

   public static Component party() {
      return compose(844035, "冒險隊伍");
   }

   public static Component questJournal() {
      return compose(844036, "冒險日誌");
   }

   public static Component contentBook() {
      return compose(844037, "內容書");
   }

   public static Component character(String label) {
      return compose(844038, label);
   }

   public static Component abilityTree() {
      return compose(844039, "能力樹");
   }

   public static Component profession() {
      return compose(844040, "生活技能");
   }

   public static Component identification() {
      return compose(844041, "裝備鑑定");
   }

   public static Component skillCrystal() {
      return compose(844042, "技能水晶");
   }

   static Component compose(int codePoint, String label) {
      TextComponent.Builder title = Component.text();
      appendSpace(title, -18);
      title.append(((TextComponent)Component.text(glyph(codePoint)).font(GUI_FONT)).shadowColor(NO_SHADOW));
      appendSpace(title, -238);
      title.append(((TextComponent)Component.text(label).color(TITLE_COLOR)).shadowColor(ShadowColor.shadowColor(-1342177280)));
      return title.build();
   }

   private static void appendSpace(TextComponent.Builder output, int pixels) {
      output.append(((TextComponent)Component.text(glyph(851968 + pixels)).font(SPACE_FONT)).shadowColor(NO_SHADOW));
   }

   private static String glyph(int codePoint) {
      return new String(Character.toChars(codePoint));
   }
}
