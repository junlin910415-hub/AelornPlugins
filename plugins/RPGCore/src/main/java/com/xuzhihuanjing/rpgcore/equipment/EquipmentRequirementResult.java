package com.xuzhihuanjing.rpgcore.equipment;

public record EquipmentRequirementResult(boolean usable, String message) {
   public static EquipmentRequirementResult allowed() {
      return new EquipmentRequirementResult(true, "");
   }

   public static EquipmentRequirementResult denied(String message) {
      return new EquipmentRequirementResult(false, message);
   }
}
