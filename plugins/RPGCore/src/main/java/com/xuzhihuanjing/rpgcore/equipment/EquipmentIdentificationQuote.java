package com.xuzhihuanjing.rpgcore.equipment;

public record EquipmentIdentificationQuote(Status status, Action action, String templateId, String itemName, int level, EquipmentRarity rarity, int completedRolls, int cost) {
   public boolean ready() {
      return this.status == EquipmentIdentificationQuote.Status.READY;
   }

   public static enum Status {
      READY,
      NOT_EQUIPMENT,
      UNKNOWN_TEMPLATE,
      INVALID_DATA,
      REROLL_LIMIT;
   }

   public static enum Action {
      IDENTIFY,
      REROLL;
   }
}
