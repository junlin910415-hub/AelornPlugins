package com.xuzhihuanjing.rpgcore.gui;

import java.util.Arrays;

public final class SlotLayout {
   private static final int[] CHARACTER_SLOTS = new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30};
   private static final int[] CLASS_SLOTS = new int[]{11, 12, 13, 14, 15};
   private static final int[] BACKUP_SLOTS = new int[]{10, 12, 14, 16, 29};

   private SlotLayout() {
   }

   public static int inventorySlotForCharacter(int characterSlot) {
      if (characterSlot >= 0 && characterSlot < CHARACTER_SLOTS.length) {
         return CHARACTER_SLOTS[characterSlot];
      } else {
         throw new IllegalArgumentException("Unknown character slot: " + characterSlot);
      }
   }

   public static int characterSlotAt(int inventorySlot, int maximumSlots) {
      for(int index = 0; index < Math.min(maximumSlots, CHARACTER_SLOTS.length); ++index) {
         if (CHARACTER_SLOTS[index] == inventorySlot) {
            return index;
         }
      }

      return -1;
   }

   public static int inventorySlotForClass(int classIndex) {
      if (classIndex >= 0 && classIndex < CLASS_SLOTS.length) {
         return CLASS_SLOTS[classIndex];
      } else {
         throw new IllegalArgumentException("Unknown class index: " + classIndex);
      }
   }

   public static int inventorySlotForBackup(int backupIndex) {
      if (backupIndex >= 0 && backupIndex < BACKUP_SLOTS.length) {
         return BACKUP_SLOTS[backupIndex];
      } else {
         throw new IllegalArgumentException("Unknown backup index: " + backupIndex);
      }
   }

   public static int backupIndexAt(int inventorySlot, int backupCount) {
      for(int index = 0; index < Math.min(backupCount, BACKUP_SLOTS.length); ++index) {
         if (BACKUP_SLOTS[index] == inventorySlot) {
            return index;
         }
      }

      return -1;
   }

   public static boolean isValid(int inventorySize, int maximumCharacterSlots, int classCount) {
      if (inventorySize > 0 && inventorySize % 9 == 0) {
         return Arrays.stream(CHARACTER_SLOTS, 0, maximumCharacterSlots).allMatch((slot) -> slot < inventorySize) && Arrays.stream(CLASS_SLOTS, 0, classCount).allMatch((slot) -> slot < inventorySize);
      } else {
         return false;
      }
   }
}
