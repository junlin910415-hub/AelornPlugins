package com.xuzhihuanjing.rpgcore.equipment;

public enum EquipmentSlotType {
   WEAPON("武器"),
   ARMOR("護甲"),
   ACCESSORY("飾品");

   private final String displayName;

   private EquipmentSlotType(String displayName) {
      this.displayName = displayName;
   }

   public String displayName() {
      return this.displayName;
   }
}
