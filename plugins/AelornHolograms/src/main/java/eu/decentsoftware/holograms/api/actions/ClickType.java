package eu.decentsoftware.holograms.api.actions;

import org.bukkit.event.block.Action;

public enum ClickType {
   LEFT,
   RIGHT,
   SHIFT_LEFT,
   SHIFT_RIGHT;

   public static ClickType fromAction(Action var0, boolean var1) {
      boolean var2 = var0 == Action.LEFT_CLICK_AIR || var0 == Action.LEFT_CLICK_BLOCK;
      if (var2) {
         return var1 ? SHIFT_LEFT : LEFT;
      } else {
         return var1 ? SHIFT_RIGHT : RIGHT;
      }
   }

   public static ClickType fromString(String var0) {
      if (var0 == null) {
         return RIGHT;
      } else {
         try {
            return valueOf(var0.trim().toUpperCase().replace('-', '_'));
         } catch (IllegalArgumentException var2) {
            return RIGHT;
         }
      }
   }
}
