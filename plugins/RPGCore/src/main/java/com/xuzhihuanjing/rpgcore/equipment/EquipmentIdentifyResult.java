package com.xuzhihuanjing.rpgcore.equipment;

public record EquipmentIdentifyResult(Status status, String itemName, int level, EquipmentRarity rarity, EquipmentIdentificationQuote.Action action, int cost, int completedRolls) {
   public static enum Status {
      SUCCESS,
      NOT_EQUIPMENT,
      UNKNOWN_TEMPLATE,
      INVALID_DATA,
      REROLL_LIMIT;
   }
}
