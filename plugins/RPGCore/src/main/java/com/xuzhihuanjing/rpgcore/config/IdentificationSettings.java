package com.xuzhihuanjing.rpgcore.config;

import com.xuzhihuanjing.rpgcore.equipment.EquipmentRarity;
import com.xuzhihuanjing.rpgcore.equipment.EquipmentSlotType;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

public record IdentificationSettings(int maximumBatchItems, int maximumRerolls, double baseCost, double levelFactor, double levelExponent, double rerollMultiplier, int maximumCost, Map<EquipmentRarity, Double> rarityMultipliers, Map<EquipmentSlotType, Double> slotMultipliers, String npcMythicMobId) {
   public IdentificationSettings(int maximumBatchItems, int maximumRerolls, double baseCost, double levelFactor, double levelExponent, double rerollMultiplier, int maximumCost, Map<EquipmentRarity, Double> rarityMultipliers, Map<EquipmentSlotType, Double> slotMultipliers, String npcMythicMobId) {
      rarityMultipliers = Map.copyOf(rarityMultipliers);
      slotMultipliers = Map.copyOf(slotMultipliers);
      if (npcMythicMobId != null && npcMythicMobId.matches("[A-Za-z0-9_-]+")) {
         if (maximumBatchItems >= 1 && maximumBatchItems <= 10 && maximumRerolls >= 0 && maximumRerolls <= 20 && !(baseCost < (double)0.0F) && !(levelFactor <= (double)0.0F) && !(levelExponent < (double)1.0F) && !(levelExponent > (double)3.0F) && !(rerollMultiplier < (double)1.0F) && !(rerollMultiplier > (double)20.0F) && maximumCost >= 1) {
            if (rarityMultipliers.size() == EquipmentRarity.values().length && slotMultipliers.size() == EquipmentSlotType.values().length && !rarityMultipliers.values().stream().anyMatch((value) -> value <= (double)0.0F) && !slotMultipliers.values().stream().anyMatch((value) -> value <= (double)0.0F)) {
               this.maximumBatchItems = maximumBatchItems;
               this.maximumRerolls = maximumRerolls;
               this.baseCost = baseCost;
               this.levelFactor = levelFactor;
               this.levelExponent = levelExponent;
               this.rerollMultiplier = rerollMultiplier;
               this.maximumCost = maximumCost;
               this.rarityMultipliers = rarityMultipliers;
               this.slotMultipliers = slotMultipliers;
               this.npcMythicMobId = npcMythicMobId;
            } else {
               throw new IllegalArgumentException("Identification multipliers must cover every rarity and slot type");
            }
         } else {
            throw new IllegalArgumentException("Identification settings contain an invalid numeric value");
         }
      } else {
         throw new IllegalArgumentException("Identification NPC MythicMob id is invalid");
      }
   }

   public static IdentificationSettings from(FileConfiguration config) {
      Map<EquipmentRarity, Double> rarityMultipliers = new EnumMap(EquipmentRarity.class);

      for(EquipmentRarity rarity : EquipmentRarity.values()) {
         rarityMultipliers.put(rarity, config.getDouble("identification.cost.rarity-multipliers." + rarity.id(), defaultRarityMultiplier(rarity)));
      }

      Map<EquipmentSlotType, Double> slotMultipliers = new EnumMap(EquipmentSlotType.class);

      for(EquipmentSlotType slotType : EquipmentSlotType.values()) {
         slotMultipliers.put(slotType, config.getDouble("identification.cost.slot-multipliers." + slotType.name().toLowerCase(Locale.ROOT), defaultSlotMultiplier(slotType)));
      }

      return new IdentificationSettings(config.getInt("identification.maximum-batch-items", 10), config.getInt("identification.maximum-rerolls", 5), config.getDouble("identification.cost.base", (double)4.0F), config.getDouble("identification.cost.level-factor", 1.35), config.getDouble("identification.cost.level-exponent", (double)1.25F), config.getDouble("identification.cost.reroll-multiplier", (double)5.0F), config.getInt("identification.cost.maximum", 1000000), rarityMultipliers, slotMultipliers, config.getString("identification.npc.mythic-mob-id", "RPGCore_Identifier"));
   }

   private static double defaultRarityMultiplier(EquipmentRarity rarity) {
      double var10000;
      switch (rarity) {
         case COMMON -> var10000 = (double)1.0F;
         case UNCOMMON -> var10000 = (double)1.25F;
         case RARE -> var10000 = 1.65;
         case EPIC -> var10000 = 2.35;
         case LEGENDARY -> var10000 = 2.8;
         case VAST -> var10000 = 3.2;
         case MYTHIC -> var10000 = (double)3.5F;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static double defaultSlotMultiplier(EquipmentSlotType slotType) {
      double var10000;
      switch (slotType) {
         case WEAPON -> var10000 = (double)1.0F;
         case ARMOR -> var10000 = 1.1;
         case ACCESSORY -> var10000 = 0.85;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }
}
