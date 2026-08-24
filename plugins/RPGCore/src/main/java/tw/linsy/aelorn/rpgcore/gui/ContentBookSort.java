package tw.linsy.aelorn.rpgcore.gui;

public enum ContentBookSort {
   RECOMMENDED("推薦順序"),
   LEVEL_ASCENDING("等級由低至高"),
   LEVEL_DESCENDING("等級由高至低");

   private final String displayName;

   private ContentBookSort(String displayName) {
      this.displayName = displayName;
   }

   public String displayName() {
      return this.displayName;
   }

   public ContentBookSort cycle(int direction) {
      ContentBookSort[] values = values();
      return values[Math.floorMod(this.ordinal() + direction, values.length)];
   }
}
