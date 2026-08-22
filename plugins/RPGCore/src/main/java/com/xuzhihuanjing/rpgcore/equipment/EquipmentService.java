package com.xuzhihuanjing.rpgcore.equipment;

import com.xuzhihuanjing.rpgcore.config.EquipmentRegistry;
import com.xuzhihuanjing.rpgcore.config.IdentificationSettings;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestProgress;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestStatus;
import com.xuzhihuanjing.rpgcore.domain.stats.PrimarySkill;
import com.xuzhihuanjing.rpgcore.integration.mmoitems.MmoItemsBridge;
import com.xuzhihuanjing.rpgcore.integration.nexo.CustomItemProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EquipmentService {
   private static final String STATE_UNIDENTIFIED = "unidentified";
   private static final String STATE_IDENTIFIED = "identified";
   private static final double DEFAULT_HITS_PER_SECOND = (double)1.5F;
   private final JavaPlugin plugin;
   private final EquipmentRegistry registry;
   private final CustomItemProvider customItems;
   private final IdentificationSettings identificationSettings;
   private final MmoItemsBridge mmoItems;
   private final IdentificationCostFormula costFormula;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();
   private final NamespacedKey stateKey;
   private final NamespacedKey templateKey;
   private final NamespacedKey levelKey;
   private final NamespacedKey rarityKey;
   private final NamespacedKey seedKey;
   private final NamespacedKey instanceIdKey;
   private final NamespacedKey rollCountKey;

   public EquipmentService(JavaPlugin plugin, EquipmentRegistry registry, CustomItemProvider customItems, IdentificationSettings identificationSettings, MmoItemsBridge mmoItems) {
      this.plugin = plugin;
      this.registry = registry;
      this.customItems = customItems;
      this.identificationSettings = identificationSettings;
      this.mmoItems = mmoItems;
      this.costFormula = new IdentificationCostFormula(identificationSettings);
      this.stateKey = new NamespacedKey(plugin, "equipment_state");
      this.templateKey = new NamespacedKey(plugin, "equipment_template");
      this.levelKey = new NamespacedKey(plugin, "equipment_level");
      this.rarityKey = new NamespacedKey(plugin, "equipment_rarity");
      this.seedKey = new NamespacedKey(plugin, "equipment_seed");
      this.instanceIdKey = new NamespacedKey(plugin, "equipment_instance_id");
      this.rollCountKey = new NamespacedKey(plugin, "equipment_roll_count");
   }

   public Optional<ItemStack> createUnidentified(MonsterEquipmentDropDefinition drop, int sourceLevel) {
      return this.registry.find(drop.templateId()).map((template) -> this.createUnidentified(template, drop, sourceLevel));
   }

   public EquipmentBonuses bonuses(Player player) {
      return this.bonuses(player, (CharacterProfile)null);
   }

   public EquipmentBonuses bonuses(Player player, CharacterProfile character) {
      PlayerInventory inventory = player.getInventory();
      EnumMap<EquipmentStatType, Integer> totals = new EnumMap(EquipmentStatType.class);
      this.addAllBonuses(totals, inventory.getItemInMainHand(), character);
      this.addAllBonuses(totals, inventory.getItemInOffHand(), character);

      for(ItemStack armor : inventory.getArmorContents()) {
         this.addAllBonuses(totals, armor, character);
      }

      return new EquipmentBonuses(totals);
   }

   public int bonus(Player player, EquipmentStatType type) {
      return this.bonuses(player).value(type);
   }

   public EquipmentRequirementResult requirements(ItemStack item, CharacterProfile character) {
      if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
         PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
         if (!"identified".equals(data.get(this.stateKey, PersistentDataType.STRING))) {
            return (EquipmentRequirementResult)this.mmoItems.inspect(item).map((identity) -> character == null ? EquipmentRequirementResult.denied("角色資料尚未載入") : this.requirements(identity, character)).orElseGet(EquipmentRequirementResult::allowed);
         } else if (character == null) {
            return EquipmentRequirementResult.denied("角色資料尚未載入");
         } else {
            String templateId = (String)data.get(this.templateKey, PersistentDataType.STRING);
            Integer itemLevel = (Integer)data.get(this.levelKey, PersistentDataType.INTEGER);
            EquipmentTemplate template = templateId == null ? null : (EquipmentTemplate)this.registry.find(templateId).orElse(null);
            return template != null && itemLevel != null ? this.requirements(template, itemLevel, character) : EquipmentRequirementResult.denied("裝備資料不完整");
         }
      } else {
         return EquipmentRequirementResult.allowed();
      }
   }

   public EquipmentIdentificationQuote quote(ItemStack item) {
      if (item != null && !item.getType().isAir() && item.getAmount() == 1 && item.hasItemMeta()) {
         PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
         String state = (String)data.get(this.stateKey, PersistentDataType.STRING);
         if (!"unidentified".equals(state) && !"identified".equals(state)) {
            return this.invalidQuote(EquipmentIdentificationQuote.Status.NOT_EQUIPMENT, "");
         } else {
            String templateId = (String)data.get(this.templateKey, PersistentDataType.STRING);
            Integer level = (Integer)data.get(this.levelKey, PersistentDataType.INTEGER);
            String rarityId = (String)data.get(this.rarityKey, PersistentDataType.STRING);
            Long seed = (Long)data.get(this.seedKey, PersistentDataType.LONG);
            Integer storedRolls = (Integer)data.get(this.rollCountKey, PersistentDataType.INTEGER);
            int completedRolls = storedRolls == null ? ("identified".equals(state) ? 1 : 0) : storedRolls;
            if (templateId != null && level != null && level >= 1 && rarityId != null && seed != null && completedRolls >= 0) {
               EquipmentTemplate template = (EquipmentTemplate)this.registry.find(templateId).orElse(null);
               if (template == null) {
                  return this.invalidQuote(EquipmentIdentificationQuote.Status.UNKNOWN_TEMPLATE, templateId);
               } else {
                  EquipmentRarity rarity;
                  try {
                     rarity = EquipmentRarity.valueOf(rarityId);
                  } catch (NullPointerException | IllegalArgumentException var14) {
                     return this.invalidQuote(EquipmentIdentificationQuote.Status.INVALID_DATA, template.id());
                  }

                  EquipmentIdentificationQuote.Action action = "unidentified".equals(state) ? EquipmentIdentificationQuote.Action.IDENTIFY : EquipmentIdentificationQuote.Action.REROLL;
                  if (action == EquipmentIdentificationQuote.Action.REROLL && completedRolls > this.identificationSettings.maximumRerolls()) {
                     return new EquipmentIdentificationQuote(EquipmentIdentificationQuote.Status.REROLL_LIMIT, action, template.id(), this.miniMessage.stripTags(template.displayName()), level, rarity, completedRolls, 0);
                  } else {
                     int cost = this.costFormula.calculate(level, rarity, template.slotType(), completedRolls);
                     return new EquipmentIdentificationQuote(EquipmentIdentificationQuote.Status.READY, action, template.id(), this.miniMessage.stripTags(template.displayName()), level, rarity, completedRolls, cost);
                  }
               }
            } else {
               return this.invalidQuote(EquipmentIdentificationQuote.Status.INVALID_DATA, "");
            }
         }
      } else {
         return this.invalidQuote(EquipmentIdentificationQuote.Status.NOT_EQUIPMENT, "");
      }
   }

   public EquipmentIdentifyResult identify(ItemStack item, CharacterProfile character) {
      EquipmentIdentificationQuote quote = this.quote(item);
      if (!quote.ready()) {
         return this.failedResult(quote);
      } else {
         EquipmentTemplate template = (EquipmentTemplate)this.registry.find(quote.templateId()).orElseThrow();
         PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
         Long storedSeed = (Long)data.get(this.seedKey, PersistentDataType.LONG);
         if (storedSeed == null) {
            return this.failedResult(this.invalidQuote(EquipmentIdentificationQuote.Status.INVALID_DATA, template.id()));
         } else {
            long seed = quote.action() == EquipmentIdentificationQuote.Action.REROLL ? ThreadLocalRandom.current().nextLong() : storedSeed;
            int completedRolls = quote.completedRolls() + 1;
            EquipmentRoll roll = EquipmentRoller.roll(template, quote.level(), quote.rarity(), seed);
            this.renderIdentified(item, template, quote.level(), quote.rarity(), roll, character, seed, completedRolls);
            return new EquipmentIdentifyResult(EquipmentIdentifyResult.Status.SUCCESS, quote.itemName(), quote.level(), quote.rarity(), quote.action(), quote.cost(), completedRolls);
         }
      }
   }

   private EquipmentIdentifyResult failedResult(EquipmentIdentificationQuote quote) {
      EquipmentIdentifyResult.Status var10000;
      switch (quote.status()) {
         case NOT_EQUIPMENT -> var10000 = EquipmentIdentifyResult.Status.NOT_EQUIPMENT;
         case UNKNOWN_TEMPLATE -> var10000 = EquipmentIdentifyResult.Status.UNKNOWN_TEMPLATE;
         case INVALID_DATA -> var10000 = EquipmentIdentifyResult.Status.INVALID_DATA;
         case REROLL_LIMIT -> var10000 = EquipmentIdentifyResult.Status.REROLL_LIMIT;
         case READY -> throw new IllegalArgumentException("A ready quote is not a failed result");
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      EquipmentIdentifyResult.Status status = var10000;
      return new EquipmentIdentifyResult(status, quote.itemName(), quote.level(), quote.rarity(), quote.action(), 0, quote.completedRolls());
   }

   private EquipmentIdentificationQuote invalidQuote(EquipmentIdentificationQuote.Status status, String templateId) {
      return new EquipmentIdentificationQuote(status, EquipmentIdentificationQuote.Action.IDENTIFY, templateId, templateId, 0, EquipmentRarity.COMMON, 0, 0);
   }

   private ItemStack createUnidentified(EquipmentTemplate template, MonsterEquipmentDropDefinition drop, int sourceLevel) {
      int level = Math.max(template.minimumLevel(), Math.min(template.maximumLevel(), sourceLevel + ThreadLocalRandom.current().nextInt(drop.minimumLevelOffset(), drop.maximumLevelOffset() + 1)));
      EquipmentRarity rarity = this.chooseRarity(drop.rarityWeights().isEmpty() ? template.rarityWeights() : drop.rarityWeights());
      long seed = ThreadLocalRandom.current().nextLong();
      ItemStack item = this.baseItem(template);
      item.editMeta((meta) -> {
         meta.customName(this.miniMessage.deserialize("<gray>未鑑定的" + template.slotType().displayName() + "</gray>"));
         meta.lore(this.unidentifiedLore(template, level, rarity));
         meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE});
         meta.setMaxStackSize(1);
         PersistentDataContainer data = meta.getPersistentDataContainer();
         data.set(this.stateKey, PersistentDataType.STRING, "unidentified");
         data.set(this.templateKey, PersistentDataType.STRING, template.id());
         data.set(this.levelKey, PersistentDataType.INTEGER, level);
         data.set(this.rarityKey, PersistentDataType.STRING, rarity.name());
         data.set(this.seedKey, PersistentDataType.LONG, seed);
         data.set(this.instanceIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
         data.set(this.rollCountKey, PersistentDataType.INTEGER, 0);
      });
      return item;
   }

   private ItemStack baseItem(EquipmentTemplate template) {
      if (!template.customItem().isBlank()) {
         Optional<ItemStack> custom = this.customItems.build(template.customItem());
         if (custom.isPresent()) {
            ItemStack item = (ItemStack)custom.get();
            item.setAmount(1);
            return item;
         }
      }

      Material material = Material.matchMaterial(template.material());
      return new ItemStack(material == null ? Material.STONE : material);
   }

   private EquipmentRarity chooseRarity(Map<EquipmentRarity, Double> weights) {
      double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
      double cursor = ThreadLocalRandom.current().nextDouble(total);

      for(Map.Entry<EquipmentRarity, Double> entry : weights.entrySet()) {
         cursor -= (Double)entry.getValue();
         if (cursor <= (double)0.0F) {
            return (EquipmentRarity)entry.getKey();
         }
      }

      return (EquipmentRarity)weights.keySet().stream().min(Comparator.comparing(Enum::ordinal)).orElse(EquipmentRarity.COMMON);
   }

   private void renderIdentified(ItemStack item, EquipmentTemplate template, int level, EquipmentRarity rarity, EquipmentRoll roll, CharacterProfile character, long seed, int completedRolls) {
      item.editMeta((meta) -> {
         meta.customName(this.miniMessage.deserialize(template.displayName()));
         meta.lore(this.identifiedLore(template, level, rarity, roll, character, completedRolls));
         meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE});
         meta.setMaxStackSize(1);
         PersistentDataContainer data = meta.getPersistentDataContainer();
         data.set(this.stateKey, PersistentDataType.STRING, "identified");
         data.set(this.templateKey, PersistentDataType.STRING, template.id());
         data.set(this.levelKey, PersistentDataType.INTEGER, level);
         data.set(this.rarityKey, PersistentDataType.STRING, rarity.name());
         data.set(this.seedKey, PersistentDataType.LONG, seed);
         data.set(this.rollCountKey, PersistentDataType.INTEGER, completedRolls);
         if (!data.has(this.instanceIdKey, PersistentDataType.STRING)) {
            data.set(this.instanceIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
         }

         for(EquipmentStatType type : EquipmentStatType.values()) {
            data.remove(this.statKey(type));
            data.remove(this.qualityKey(type));
         }

         for(Map.Entry<EquipmentStatType, Integer> entry : roll.stats().entrySet()) {
            data.set(this.statKey((EquipmentStatType)entry.getKey()), PersistentDataType.INTEGER, (Integer)entry.getValue());
            int basisPoints = (int)Math.round((Double)roll.qualities().getOrDefault(entry.getKey(), (double)0.0F) * (double)10000.0F);
            data.set(this.qualityKey((EquipmentStatType)entry.getKey()), PersistentDataType.INTEGER, basisPoints);
         }

      });
   }

   private NamespacedKey statKey(EquipmentStatType type) {
      return new NamespacedKey(this.plugin, "equipment_stat_" + type.id());
   }

   private NamespacedKey qualityKey(EquipmentStatType type) {
      return new NamespacedKey(this.plugin, "equipment_quality_" + type.id());
   }

   private void addBonuses(EnumMap<EquipmentStatType, Integer> totals, ItemStack item, CharacterProfile character) {
      if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
         PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
         if ("identified".equals(data.get(this.stateKey, PersistentDataType.STRING))) {
            if (character != null) {
               String templateId = (String)data.get(this.templateKey, PersistentDataType.STRING);
               Integer itemLevel = (Integer)data.get(this.levelKey, PersistentDataType.INTEGER);
               EquipmentTemplate template = templateId == null ? null : (EquipmentTemplate)this.registry.find(templateId).orElse(null);
               if (template == null || itemLevel == null || !this.requirements(template, itemLevel, character).usable()) {
                  return;
               }
            }

            for(EquipmentStatType type : EquipmentStatType.values()) {
               Integer value = (Integer)data.get(this.statKey(type), PersistentDataType.INTEGER);
               if (value != null && value != 0) {
                  totals.merge(type, value, Integer::sum);
               }
            }

         }
      }
   }

   private void addAllBonuses(EnumMap<EquipmentStatType, Integer> totals, ItemStack item, CharacterProfile character) {
      this.addBonuses(totals, item, character);
      this.mmoItems.inspect(item).filter((identity) -> character == null || this.requirements(identity, character).usable()).ifPresent((identity) -> MmoItemsEquipmentMapper.merge(totals, identity.stats()));
   }

   private EquipmentRequirementResult requirements(EquipmentTemplate template, int itemLevel, CharacterProfile character) {
      if (itemLevel > character.level()) {
         return EquipmentRequirementResult.denied("戰鬥等級不足，需要 " + itemLevel + " 級");
      } else if (!template.classRequirements().isEmpty() && template.classRequirements().stream().noneMatch((required) -> required.equalsIgnoreCase(character.classId()))) {
         String var10000 = this.classRequirements(template);
         return EquipmentRequirementResult.denied("職業不符，需要 " + var10000);
      } else {
         EquipmentRequirementResult skillResult = this.skillRequirements(template.skillRequirements(), character);
         return !skillResult.usable() ? skillResult : this.questRequirements(template.questRequirements(), character);
      }
   }

   private EquipmentRequirementResult requirements(MmoItemsBridge.Identity identity, CharacterProfile character) {
      if (identity.requiredLevel() > character.level()) {
         return EquipmentRequirementResult.denied("戰鬥等級不足，需要 " + identity.requiredLevel() + " 級");
      } else if (!MmoItemsEquipmentMapper.meetsRequirements(identity.requiredClass(), identity.requiredLevel(), character.classId(), character.level())) {
         String var10000 = identity.requiredClass();
         return EquipmentRequirementResult.denied("職業不符，需要 " + var10000.replace('|', '/'));
      } else {
         Map<PrimarySkill, Integer> requiredSkills = new EnumMap(PrimarySkill.class);
         identity.skillRequirements().forEach((id, value) -> PrimarySkill.parse(id).ifPresent((skill) -> requiredSkills.put(skill, Math.max(0, value))));
         EquipmentRequirementResult skillResult = this.skillRequirements(requiredSkills, character);
         return !skillResult.usable() ? skillResult : this.questRequirements(identity.questRequirements(), character);
      }
   }

   private EquipmentRequirementResult skillRequirements(Map<PrimarySkill, Integer> requirements, CharacterProfile character) {
      for(PrimarySkill skill : PrimarySkill.values()) {
         int required = (Integer)requirements.getOrDefault(skill, 0);
         int current = (Integer)character.skillPoints().getOrDefault(skill, 0);
         if (required > current) {
            String var10000 = skill.displayName();
            return EquipmentRequirementResult.denied(var10000 + "不足，需要 " + required + " 點");
         }
      }

      return EquipmentRequirementResult.allowed();
   }

   private EquipmentRequirementResult questRequirements(Iterable<String> requirements, CharacterProfile character) {
      for(String questId : requirements) {
         boolean completed = character.questProgress().entrySet().stream().anyMatch((entry) -> ((String)entry.getKey()).equalsIgnoreCase(questId) && ((QuestProgress)entry.getValue()).status() == QuestStatus.COMPLETED);
         if (!completed) {
            return EquipmentRequirementResult.denied("尚未完成任務 " + questId);
         }
      }

      return EquipmentRequirementResult.allowed();
   }

   private List<Component> unidentifiedLore(EquipmentTemplate template, int level, EquipmentRarity rarity) {
      List<Component> lore = new ArrayList();
      String var10002 = this.itemFamily(template);
      lore.add(this.line("<gray>未鑑定的 " + var10002 + "</gray>"));
      lore.add(this.line("<dark_gray>鑑定後會揭露完整數值與品質。</dark_gray>"));
      lore.add(Component.empty());
      var10002 = template.slotType().displayName();
      lore.add(this.line("<gray>類型：</gray><white>" + var10002 + "</white><dark_gray> · </dark_gray><white>" + this.itemFamily(template) + "</white>"));
      lore.add(this.line("<gray>戰鬥等級：</gray><white>" + level + "</white>"));
      lore.add(this.line("<gray>稀有度：</gray>" + rarity.displayName()));
      int var8 = this.costFormula.calculate(level, rarity, template.slotType(), 0);
      lore.add(this.line("<gray>鑑定費用：</gray><green>" + var8 + " 綠寶石</green>"));
      if (!template.classRequirements().isEmpty()) {
         String var9 = this.classRequirements(template);
         lore.add(this.line("<yellow>◆</yellow> <gray>職業類型：</gray><white>" + var9 + "</white>"));
      }

      template.skillRequirements().forEach((skill, required) -> {
         String skillName = skill.displayName();
         lore.add(this.line("<yellow>◆</yellow> <gray>" + skillName + "需求：</gray><white>" + required + "</white>"));
      });

      for(String questId : template.questRequirements()) {
         lore.add(this.line("<yellow>◆</yellow> <gray>任務前置：</gray><white>" + questId + "</white>"));
      }

      lore.add(Component.empty());
      lore.add(this.line("<yellow>交給城鎮中的鑑定師處理</yellow>"));
      lore.add(this.line("<dark_gray>指令鑑定已關閉，只能透過專用 NPC。</dark_gray>"));
      return lore;
   }

   private List<Component> identifiedLore(EquipmentTemplate template, int level, EquipmentRarity rarity, EquipmentRoll roll, CharacterProfile character, int completedRolls) {
      List<Component> lore = new ArrayList();
      this.addPreviewBlock(lore, template, roll);
      lore.add(this.line("<dark_gray>────────────────────</dark_gray>"));
      lore.add(this.line("<gray>稀有度：</gray>" + rarity.displayName()));
      String var10002 = this.itemFamily(template);
      lore.add(this.line("<gray>類型：</gray><white>" + var10002 + "</white><dark_gray> · </dark_gray><white>" + template.slotType().displayName() + "</white>"));
      lore.add(this.line("<gray>物品等級：</gray><white>" + level + "</white><gray>　品鑑：</gray><white>" + this.qualityPercent(roll) + "%</white>"));
      lore.add(this.line("<gray>鑑定次數：</gray><white>" + completedRolls + "</white>"));
      lore.add(Component.empty());
      lore.add(this.line(this.requirementLine("職業類型", template.classRequirements().isEmpty() || template.classRequirements().stream().anyMatch((required) -> required.equalsIgnoreCase(character.classId())), template.classRequirements().isEmpty() ? "不限職業" : this.classRequirements(template))));
      lore.add(this.line(this.requirementLine("戰鬥等級", character.level() >= level, Integer.toString(level))));
      template.skillRequirements().forEach((skill, required) -> lore.add(this.line(this.requirementLine(skill.displayName(), (Integer)character.skillPoints().getOrDefault(skill, 0) >= required, Integer.toString(required)))));

      for(String questId : template.questRequirements()) {
         boolean completed = character.questProgress().entrySet().stream().anyMatch((entry) -> ((String)entry.getKey()).equalsIgnoreCase(questId) && ((QuestProgress)entry.getValue()).status() == QuestStatus.COMPLETED);
         lore.add(this.line(this.requirementLine("任務前置", completed, questId)));
      }

      if (!template.classRequirements().isEmpty()) {
         boolean usable = template.classRequirements().stream().anyMatch((required) -> required.equalsIgnoreCase(character.classId()));
         if (!usable) {
            lore.add(this.line("<red>✘</red> <gray>目前角色：</gray><red>不符合裝備職業</red>"));
         }
      }

      if (template.majorIdentification().enabled()) {
         lore.add(Component.empty());
         lore.add(this.line("<light_purple>大型特性：</light_purple><white>" + template.majorIdentification().displayName() + "</white>"));
         template.majorIdentification().description().forEach((description) -> lore.add(this.line("<gray>" + description + "</gray>")));
      }

      lore.add(Component.empty());
      var10002 = String.format(Locale.US, "%.2f", this.equipmentScore(roll, rarity));
      lore.add(this.line("<gold>裝備評分：</gold><white>" + var10002 + "</white> <dark_gray>[" + this.ratingBox(this.averageQuality(roll)) + "]</dark_gray>"));
      lore.add(this.line("<dark_gray>────────────────────</dark_gray>"));
      roll.stats().entrySet().stream().sorted(Comparator.comparingInt((entry) -> ((EquipmentStatType)entry.getKey()).ordinal())).forEach((entry) -> lore.add(this.line(this.statLine((EquipmentStatType)entry.getKey(), (Integer)entry.getValue(), (Double)roll.qualities().getOrDefault(entry.getKey(), (double)0.0F)))));
      lore.add(Component.empty());
      lore.add(this.line("<gold>•</gold><dark_gray> · · · </dark_gray><white>[F]</white> <gray>目錄</gray>"));
      return lore;
   }

   private void addPreviewBlock(List<Component> lore, EquipmentTemplate template, EquipmentRoll roll) {
      if (template.slotType() == EquipmentSlotType.WEAPON) {
         int rawAttack = (Integer)roll.stats().getOrDefault(EquipmentStatType.ATTACK, 0) + (Integer)roll.stats().getOrDefault(EquipmentStatType.MAIN_ATTACK_DAMAGE, 0) + (Integer)roll.stats().getOrDefault(EquipmentStatType.MAGIC_DAMAGE, 0);
         double damageMultiplier = (double)1.0F + (double)((Integer)roll.stats().getOrDefault(EquipmentStatType.MAIN_ATTACK_DAMAGE_PERCENT, 0) + (Integer)roll.stats().getOrDefault(EquipmentStatType.ALL_DAMAGE, 0) + (Integer)roll.stats().getOrDefault(EquipmentStatType.PHYSICAL_DAMAGE, 0)) / (double)100.0F;
         int attack = Math.max(1, (int)Math.round((double)rawAttack * Math.max((double)0.0F, damageMultiplier)));
         int minimumDamage = Math.max(1, (int)Math.floor((double)attack * 0.85));
         int maximumDamage = Math.max(minimumDamage, (int)Math.ceil((double)attack * 1.15));
         double hitsPerSecond = (double)1.5F * ((double)1.0F + (double)(Integer)roll.stats().getOrDefault(EquipmentStatType.ATTACK_SPEED, 0) / (double)100.0F) + (double)(Integer)roll.stats().getOrDefault(EquipmentStatType.ATTACK_SPEED_TIER, 0) * 0.15;
         hitsPerSecond = Math.max(0.4, Math.min((double)6.0F, hitsPerSecond));
         int dps = (int)Math.round((double)attack * hitsPerSecond);
         lore.add(this.line("<white>" + dps + "</white> <gray>DPS</gray>"));
         String var16 = this.attackSpeedName(hitsPerSecond);
         lore.add(this.line("<dark_gray>攻擊速度：</dark_gray><white>" + var16 + "</white> <dark_gray>(每秒 " + String.format(Locale.US, "%.1f", hitsPerSecond) + " 次)</dark_gray>"));
         lore.add(this.line("<gold>傷害範圍：</gold><white>" + minimumDamage + "-" + maximumDamage + "</white>"));
      } else {
         int health = (Integer)roll.stats().getOrDefault(EquipmentStatType.HEALTH, 0);
         int defense = (Integer)roll.stats().getOrDefault(EquipmentStatType.DEFENSE, 0) + (Integer)roll.stats().getOrDefault(EquipmentStatType.DEFENCE, 0);
         int resistance = (Integer)roll.stats().getOrDefault(EquipmentStatType.RESISTANCE, 0);
         lore.add(this.line("<white>" + Math.max(0, health) + "</white> <gray>生命</gray>"));
         int var10002 = Math.max(0, defense);
         lore.add(this.line("<dark_gray>防禦：</dark_gray><white>" + var10002 + "</white><dark_gray> · 抗性：</dark_gray><white>" + Math.max(0, resistance) + "</white>"));
      }
   }

   private String statLine(EquipmentStatType type, int value, double quality) {
      String sign = value >= 0 ? "+" : "";
      String var10000 = type.displayName();
      return "<green>✔</green> <gray>" + var10000 + "</gray><dark_gray>：</dark_gray><green>" + sign + value + type.suffix() + "</green>" + this.qualityStars(quality);
   }

   private String qualityStars(double quality) {
      if (quality >= 0.95) {
         return " <gold>★★★</gold>";
      } else if (quality >= 0.85) {
         return " <yellow>★★</yellow>";
      } else {
         return quality >= 0.7 ? " <white>★</white>" : "";
      }
   }

   private String classRequirements(EquipmentTemplate template) {
      return String.join(" / ", template.classRequirements());
   }

   private String requirementLine(String label, boolean met, String value) {
      return (met ? "<green>✔</green> " : "<red>✘</red> ") + "<gray>" + label + "：</gray><white>" + value + "</white>";
   }

   private int qualityPercent(EquipmentRoll roll) {
      return (int)Math.round(this.averageQuality(roll) * (double)100.0F);
   }

   private double averageQuality(EquipmentRoll roll) {
      return roll.qualities().isEmpty() ? (double)1.0F : roll.qualities().values().stream().mapToDouble(Double::doubleValue).average().orElse((double)1.0F);
   }

   private double equipmentScore(EquipmentRoll roll, EquipmentRarity rarity) {
      double statWeight = roll.stats().entrySet().stream().mapToDouble((entry) -> (double)Math.abs((Integer)entry.getValue()) * this.statWeight((EquipmentStatType)entry.getKey())).sum();
      return Math.max((double)1.0F, statWeight * ((double)0.75F + this.averageQuality(roll) * (double)0.5F) * rarity.statMultiplier() / (double)10.0F);
   }

   private double statWeight(EquipmentStatType type) {
      double var10000;
      switch (type) {
         case ATTACK:
         case MAIN_ATTACK_DAMAGE:
         case SPELL_DAMAGE:
         case MAGIC_DAMAGE:
         case SKILL_DAMAGE:
         case ABILITY_DAMAGE:
            var10000 = 1.45;
            break;
         case MAIN_ATTACK_DAMAGE_PERCENT:
         case SPELL_DAMAGE_PERCENT:
         case ALL_DAMAGE:
         case PVE_DAMAGE:
         case PVP_DAMAGE:
         case PROJECTILE_DAMAGE:
         case PHYSICAL_DAMAGE:
         case CRITICAL_CHANCE:
         case CRITICAL_POWER:
            var10000 = 1.2;
            break;
         case HEALTH:
            var10000 = 0.38;
            break;
         case HEALTH_PERCENT:
         case DEFENSE:
         case ARMOR:
         case ARMOR_TOUGHNESS:
         case DEFENCE:
         case RESISTANCE:
         case ELEMENTAL_RESISTANCE:
         case ELEMENTAL_RESISTANCE_PERCENT:
         case DAMAGE_REDUCTION:
         case PHYSICAL_DAMAGE_REDUCTION:
         case MAGIC_DAMAGE_REDUCTION:
         case PROJECTILE_DAMAGE_REDUCTION:
         case PVE_DAMAGE_REDUCTION:
         case PVP_DAMAGE_REDUCTION:
            var10000 = 1.05;
            break;
         case SPEED:
         case ATTACK_SPEED:
         case ATTACK_SPEED_TIER:
         case RANGE:
         case KNOCKBACK:
         case PROJECTILE_VELOCITY:
         case SPRINT:
         case SPRINT_REGEN:
         case JUMP_HEIGHT:
            var10000 = 0.8;
            break;
         case MANA:
         case MANA_REGEN:
         case STAMINA:
         case STAMINA_REGEN:
         case LIFE_STEAL:
         case MANA_STEAL:
         case SPELL_VAMPIRISM:
         case HEALING_EFFICIENCY:
            var10000 = 1.2;
            break;
         case STRENGTH:
         case DEXTERITY:
         case INTELLIGENCE:
         case AGILITY:
            var10000 = (double)1.0F;
            break;
         case HEALTH_REGEN:
         case HEALTH_REGEN_PERCENT:
            var10000 = 0.9;
            break;
         default:
            var10000 = 0.72;
      }

      return var10000;
   }

   private String attackSpeedName(double hitsPerSecond) {
      if (hitsPerSecond < 0.8) {
         return "極慢";
      } else if (hitsPerSecond < 1.2) {
         return "慢";
      } else if (hitsPerSecond < 1.8) {
         return "普通";
      } else if (hitsPerSecond < (double)2.5F) {
         return "快";
      } else {
         return hitsPerSecond < (double)3.5F ? "極快" : "神速";
      }
   }

   private String ratingBox(double quality) {
      if (quality >= 0.92) {
         return "◆";
      } else {
         return quality >= (double)0.75F ? "◇" : "□";
      }
   }

   private String itemFamily(EquipmentTemplate template) {
      String material = template.material().toUpperCase(Locale.ROOT);
      if (material.contains("SWORD")) {
         return "劍";
      } else if (material.contains("AXE")) {
         return "斧";
      } else if (material.contains("BOW")) {
         return "弓";
      } else if (material.contains("TRIDENT")) {
         return "槍";
      } else {
         return !material.contains("HOE") && !material.contains("STICK") && !material.contains("ROD") ? template.slotType().displayName() : "法器";
      }
   }

   private Component line(String value) {
      return this.miniMessage.deserialize(value);
   }
}
