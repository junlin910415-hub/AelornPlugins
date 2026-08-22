package com.xuzhihuanjing.rpgcore.gui;

public enum ContentBookFilter {
   RECOMMENDED("推薦內容"),
   QUESTS("任務"),
   DISCOVERIES("探索發現");

   private final String displayName;

   private ContentBookFilter(String displayName) {
      this.displayName = displayName;
   }

   public String displayName() {
      return this.displayName;
   }

   public ContentBookFilter cycle(int direction) {
      ContentBookFilter[] values = values();
      return values[Math.floorMod(this.ordinal() + direction, values.length)];
   }
}
