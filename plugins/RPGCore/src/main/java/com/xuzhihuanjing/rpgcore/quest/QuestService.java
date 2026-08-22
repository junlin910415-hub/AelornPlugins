package com.xuzhihuanjing.rpgcore.quest;

import com.xuzhihuanjing.rpgcore.combat.CharacterActivationService;
import com.xuzhihuanjing.rpgcore.combat.HudNotificationService;
import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.config.QuestRegistry;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestObjectiveDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestObjectiveType;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestProgress;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestReward;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestStatus;
import com.xuzhihuanjing.rpgcore.integration.mmoitems.MmoItemsBridge;
import com.xuzhihuanjing.rpgcore.domain.profession.ProfessionType;
import com.xuzhihuanjing.rpgcore.progression.ProfessionGainResult;
import com.xuzhihuanjing.rpgcore.progression.ProfessionService;
import com.xuzhihuanjing.rpgcore.progression.ProgressionResult;
import com.xuzhihuanjing.rpgcore.progression.ProgressionService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class QuestService {
   private final QuestRegistry registry;
   private final CharacterService characterService;
   private final ProgressionService progressionService;
   private final CharacterActivationService activationService;
   private final HudNotificationService notifications;
   private final MessageBundle messages;
   private final MmoItemsBridge mmoItems;
   private final ProfessionService professionService;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public QuestService(QuestRegistry registry, CharacterService characterService, ProgressionService progressionService, CharacterActivationService activationService, HudNotificationService notifications, MessageBundle messages, MmoItemsBridge mmoItems, ProfessionService professionService) {
      this.registry = (QuestRegistry)Objects.requireNonNull(registry, "registry");
      this.characterService = (CharacterService)Objects.requireNonNull(characterService, "characterService");
      this.progressionService = (ProgressionService)Objects.requireNonNull(progressionService, "progressionService");
      this.activationService = (CharacterActivationService)Objects.requireNonNull(activationService, "activationService");
      this.notifications = (HudNotificationService)Objects.requireNonNull(notifications, "notifications");
      this.messages = (MessageBundle)Objects.requireNonNull(messages, "messages");
      this.mmoItems = (MmoItemsBridge)Objects.requireNonNull(mmoItems, "mmoItems");
      this.professionService = professionService;
   }

   public Availability availability(CharacterProfile character, QuestDefinition quest) {
      return evaluateAvailability(character, quest);
   }

   public static Availability evaluateAvailability(CharacterProfile character, QuestDefinition quest) {
      QuestProgress progress = (QuestProgress)character.questProgress().get(quest.id());
      if (progress != null) {
         return progress.status() == QuestStatus.COMPLETED ? QuestService.Availability.COMPLETED : QuestService.Availability.ACTIVE;
      } else {
         return character.level() >= quest.minimumLevel() && prerequisitesComplete(character, quest) ? QuestService.Availability.AVAILABLE : QuestService.Availability.LOCKED;
      }
   }

   public AcceptResult accept(UUID ownerId, QuestDefinition quest) {
      return this.accept(ownerId, (Player)null, quest);
   }

   public AcceptResult accept(Player player, QuestDefinition quest) {
      return this.accept(player.getUniqueId(), player, quest);
   }

   private AcceptResult accept(UUID ownerId, Player player, QuestDefinition quest) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(ownerId).orElse(null);
      if (character == null) {
         return QuestService.AcceptResult.NO_ACTIVE_CHARACTER;
      } else {
         Availability availability = this.availability(character, quest);
         if (availability == QuestService.Availability.ACTIVE) {
            return QuestService.AcceptResult.ALREADY_ACTIVE;
         } else if (availability == QuestService.Availability.COMPLETED) {
            return QuestService.AcceptResult.ALREADY_COMPLETED;
         } else if (character.level() < quest.minimumLevel()) {
            return QuestService.AcceptResult.LEVEL_REQUIRED;
         } else if (!prerequisitesComplete(character, quest)) {
            return QuestService.AcceptResult.PREREQUISITE_REQUIRED;
         } else {
            Map<String, QuestProgress> updated = new LinkedHashMap(character.questProgress());
            QuestProgress initial = synchronizeDiscoveries(quest, QuestProgress.active(), character.discoveredLocations());
            if (player != null) {
               initial = this.synchronizeInventory(quest, initial, player);
            }

            initial = synchronizeLevel(quest, initial, character.level());
            // 接受當下可能已滿足前幾個階段(先前已探索/已持有物品/等級已達標),
            // 逐階段結算而不是直接判定整個任務完成
            initial = settleStages(quest, initial);
            boolean completedOnAccept = initial.status() == QuestStatus.COMPLETED;

            updated.put(quest.id(), initial);
            this.characterService.updateActiveCharacter(ownerId, (current) -> current.withQuestProgress(updated, completedOnAccept ? "" : quest.id()));
            if (completedOnAccept) {
               this.reward(ownerId, player, quest);
            }

            return QuestService.AcceptResult.ACCEPTED;
         }
      }
   }

   public boolean track(UUID ownerId, String questId) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(ownerId).orElse(null);
      if (character == null) {
         return false;
      } else {
         QuestProgress progress = (QuestProgress)character.questProgress().get(questId);
         if (progress != null && progress.status() == QuestStatus.ACTIVE) {
            String tracked = character.trackedQuestId().equals(questId) ? "" : questId;
            this.characterService.updateActiveCharacter(ownerId, (current) -> current.withQuestProgress(current.questProgress(), tracked));
            return true;
         } else {
            return false;
         }
      }
   }

   public void recordMonsterKill(Player player, String monsterId) {
      this.advance(player, QuestObjectiveType.KILL_MONSTER, monsterId, 1);
   }

   public void recordEncounterCompletion(Player player, String encounterId) {
      this.advance(player, QuestObjectiveType.COMPLETE_ENCOUNTER, encounterId, 1);
   }

   public void recordDiscovery(Player player, String discoveryId) {
      this.advance(player, QuestObjectiveType.DISCOVER_LOCATION, discoveryId, 1);
   }

   public void recordItemPickup(Player player, String type, String itemId, int amount) {
      if (type != null && itemId != null && amount > 0) {
         this.advance(player, QuestObjectiveType.COLLECT_ITEM, type + ":" + itemId, amount);
      }
   }

   public void recordNpcInteraction(Player player, String npcId) {
      if (npcId != null && !npcId.isBlank()) {
         this.advance(player, QuestObjectiveType.TALK_TO_NPC, npcId, 1);
      }
   }

   public void recordItemUse(Player player, String type, String itemId) {
      if (type != null && itemId != null) {
         this.advance(player, QuestObjectiveType.USE_ITEM, type + ":" + itemId, 1);
      }
   }

   /** 護送完成:NPC 抵達終點時由 NpcBehaviorService 觸發。 */
   public void recordEscortArrival(Player player, String npcKey) {
      if (npcKey != null && !npcKey.isBlank()) {
         this.advance(player, QuestObjectiveType.ESCORT_NPC, npcKey, 1);
      }
   }

   public void recordAbilityCast(Player player, String abilityId) {
      if (abilityId != null && !abilityId.isBlank()) {
         this.advance(player, QuestObjectiveType.CAST_ABILITY, abilityId, 1);
      }
   }

   /**
    * 等級型目標一次補滿:達到門檻就算完成,不是逐級累加。
    * 接受任務與升級後都會呼叫。
    */
   public void recordLevel(Player player, int level) {
      if (level > 0) {
         this.advance(player, QuestObjectiveType.REACH_LEVEL, Integer.toString(level), 1);
      }
   }

   /**
    * 向 NPC 交付物品。與其他目標不同,這會**消耗**玩家背包中的物品,
    * 所以只在真的有對應目標且數量足夠時才動手。
    *
    * @return 實際交付的物品數量,0 表示沒有可交付的目標
    */
   public int recordDelivery(Player player, String npcId) {
      CharacterProfile character = this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character == null || npcId == null || npcId.isBlank()) {
         return 0;
      }
      int delivered = 0;
      for (Map.Entry<String, QuestProgress> entry : character.questProgress().entrySet()) {
         QuestProgress progress = entry.getValue();
         QuestDefinition quest = this.registry.find(entry.getKey()).orElse(null);
         if (quest == null || progress.status() != QuestStatus.ACTIVE) {
            continue;
         }
         com.xuzhihuanjing.rpgcore.domain.quest.QuestStage stage = quest.stage(progress.stageIndex());
         if (stage == null) {
            continue;
         }
         for (QuestObjectiveDefinition objective : stage.objectives()) {
            if (objective.type() != QuestObjectiveType.DELIVER_ITEM) {
               continue;
            }
            String[] parts = objective.target().split("/", 2);
            if (parts.length != 2 || !parts[0].equalsIgnoreCase(npcId)) {
               continue;
            }
            int current = progress.objectiveProgress().getOrDefault(objective.id(), 0);
            int needed = objective.requiredAmount() - current;
            if (needed <= 0) {
               continue;
            }
            int taken = this.consumeItems(player, parts[1], needed);
            if (taken > 0) {
               this.advance(player, QuestObjectiveType.DELIVER_ITEM, objective.target(), taken);
               delivered += taken;
            }
         }
      }
      return delivered;
   }

   /** 從背包扣除指定的 MMOItems 物品,回傳實際扣除數量。 */
   private int consumeItems(Player player, String itemTarget, int wanted) {
      int remaining = wanted;
      ItemStack[] contents = player.getInventory().getContents();
      for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
         ItemStack item = contents[slot];
         if (item == null) {
            continue;
         }
         String target = this.mmoItems.inspect(item).map(identity -> identity.objectiveTarget()).orElse(null);
         if (target == null || !target.equalsIgnoreCase(itemTarget)) {
            continue;
         }
         int take = Math.min(remaining, item.getAmount());
         remaining -= take;
         if (take >= item.getAmount()) {
            player.getInventory().setItem(slot, null);
         } else {
            item.setAmount(item.getAmount() - take);
         }
      }
      return wanted - remaining;
   }

   public int objectiveProgress(CharacterProfile character, QuestDefinition quest, QuestObjectiveDefinition objective) {
      QuestProgress progress = (QuestProgress)character.questProgress().get(quest.id());
      return progress == null ? 0 : Math.min(objective.requiredAmount(), (Integer)progress.objectiveProgress().getOrDefault(objective.id(), 0));
   }

   public boolean objectivesComplete(CharacterProfile character, QuestDefinition quest) {
      QuestProgress progress = (QuestProgress)character.questProgress().get(quest.id());
      return progress != null && this.objectivesComplete(quest, progress);
   }

   private void advance(Player player, QuestObjectiveType type, String target, int amount) {
      CharacterProfile character = (CharacterProfile)this.characterService.activeCharacter(player.getUniqueId()).orElse(null);
      if (character != null) {
         Map<String, QuestProgress> updated = new LinkedHashMap(character.questProgress());
         List<ObjectiveUpdate> objectiveUpdates = new ArrayList();
         List<QuestDefinition> completed = new ArrayList();
         List<StageAdvance> stageAdvances = new ArrayList<>();

         for(Map.Entry<String, QuestProgress> entry : character.questProgress().entrySet()) {
            QuestProgress progress = (QuestProgress)entry.getValue();
            QuestDefinition quest = (QuestDefinition)this.registry.find((String)entry.getKey()).orElse(null);
            if (quest != null && progress.status() == QuestStatus.ACTIVE) {
               QuestProgressEngine.AdvanceResult result = QuestProgressEngine.advance(quest, progress, type, target, amount);

               for(QuestObjectiveDefinition objective : result.updatedObjectives()) {
                  int current = (Integer)result.progress().objectiveProgress().getOrDefault(objective.id(), 0);
                  objectiveUpdates.add(new ObjectiveUpdate(quest, objective, current));
               }

               if (result.completed()) {
                  completed.add(quest);
               }

               if (result.stageAdvanced() && result.newStage() != null) {
                  stageAdvances.add(new StageAdvance(quest, result.newStage(), result.progress().stageIndex()));
               }

               updated.put(quest.id(), result.progress());
            }
         }

         if (!objectiveUpdates.isEmpty()) {
            String tracked = character.trackedQuestId();
            boolean trackedCompleted = false;

            for(QuestDefinition quest : completed) {
               if (quest.id().equals(tracked)) {
                  trackedCompleted = true;
                  break;
               }
            }

            if (trackedCompleted) {
               tracked = "";
            }

            String trackedForUpdate = tracked;
            this.characterService.updateActiveCharacter(player.getUniqueId(), (currentx) -> currentx.withQuestProgress(updated, trackedForUpdate));

            for(ObjectiveUpdate update : objectiveUpdates) {
               this.notifications.show(player.getUniqueId(), this.messages.content("quest-progress", MessageBundle.value("quest", this.plain(update.quest().displayName())), MessageBundle.value("objective", update.objective().description()), MessageBundle.value("current", Integer.toString(update.current())), MessageBundle.value("required", Integer.toString(update.objective().requiredAmount()))));
            }

            for(StageAdvance advance : stageAdvances) {
               String questName = this.plain(advance.quest().displayName());
               this.notifications.show(player.getUniqueId(), this.messages.content("quest-stage-advance",
                  MessageBundle.value("quest", questName),
                  MessageBundle.value("stage", Integer.toString(advance.stageIndex() + 1)),
                  MessageBundle.value("total", Integer.toString(advance.quest().stageCount())),
                  MessageBundle.value("description", this.plain(advance.stage().description()))));
               player.sendMessage(this.messages.message("quest-stage-advance",
                  MessageBundle.value("quest", questName),
                  MessageBundle.value("stage", Integer.toString(advance.stageIndex() + 1)),
                  MessageBundle.value("total", Integer.toString(advance.quest().stageCount())),
                  MessageBundle.value("description", this.plain(advance.stage().description()))));
            }

            for(QuestDefinition quest : completed) {
               this.reward(player.getUniqueId(), player, quest);
            }

         }
      }
   }

   private void reward(UUID ownerId, Player player, QuestDefinition quest) {
      QuestReward reward = quest.reward();
      ProgressionResult result = this.progressionService.grantExperience(ownerId, reward.experience());
      if (player == null) {
         return;
      }

      String name = this.plain(quest.displayName());
      this.notifications.show(ownerId, this.messages.content("quest-complete-hud", MessageBundle.value("quest", name), MessageBundle.value("experience", Long.toString(result.awardedExperience()))));
      player.sendMessage(this.messages.message("quest-complete", MessageBundle.value("quest", name), MessageBundle.value("experience", Long.toString(result.awardedExperience()))));

      if (reward.hasItems()) {
         this.grantItems(player, reward);
      }
      if (reward.hasProfessionExperience()) {
         this.grantProfessionExperience(ownerId, player, reward);
      }

      if (result.leveledUp()) {
         player.sendMessage(this.messages.message("level-up", MessageBundle.value("level", Integer.toString(result.level()))));
         this.characterService.activeCharacter(ownerId).ifPresent((character) -> this.activationService.activate(player, character));
         // 升級可能達成 REACH_LEVEL 目標
         this.recordLevel(player, result.level());
      }
   }

   /**
    * 發放物品獎勵。背包滿的時候掉在腳邊而不是憑空消失——
    * 任務獎勵不見對玩家是嚴重問題,寧可掉在地上。
    */
   private void grantItems(Player player, QuestReward reward) {
      for (QuestReward.ItemReward itemReward : reward.items()) {
         ItemStack stack = new ItemStack(itemReward.material(), itemReward.amount());
         if (itemReward.displayName() != null && !itemReward.displayName().isBlank()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
               meta.displayName(this.miniMessage.deserialize(itemReward.displayName())
                  .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
               stack.setItemMeta(meta);
            }
         }
         Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
         for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
         }
      }
   }

   private void grantProfessionExperience(UUID ownerId, Player player, QuestReward reward) {
      if (this.professionService == null) {
         return;
      }
      reward.professionExperience().forEach((professionId, experience) -> {
         ProfessionType profession = ProfessionType.parse(professionId).orElse(null);
         if (profession == null || experience <= 0L) {
            return;
         }
         ProfessionGainResult gain = this.professionService.grantExperience(ownerId, profession, experience);
         player.sendMessage(this.messages.message("quest-profession-reward",
            MessageBundle.value("profession", profession.displayName()),
            MessageBundle.value("experience", Long.toString(gain.awardedExperience()))));
      });
   }

   /**
    * 連續推進已完成的階段,直到遇到未完成的階段或走完整個任務。
    *
    * <p>用於接受任務的當下:玩家可能早就滿足了前幾個階段的條件。
    */
   static QuestProgress settleStages(QuestDefinition quest, QuestProgress progress) {
      QuestProgress current = progress;
      while (current.status() == QuestStatus.ACTIVE) {
         com.xuzhihuanjing.rpgcore.domain.quest.QuestStage stage = quest.stage(current.stageIndex());
         if (stage == null) {
            return current.completed();
         }
         QuestProgress evaluated = current;
         boolean stageDone = !stage.objectives().isEmpty() && stage.objectives().stream()
            .allMatch((objective) -> evaluated.objectiveProgress().getOrDefault(objective.id(), 0)
               >= objective.requiredAmount());
         if (!stageDone) {
            return current;
         }
         if (current.stageIndex() + 1 >= quest.stageCount()) {
            return current.completed();
         }
         current = current.advanceStage();
      }
      return current;
   }

   /** 等級型目標在接受任務時就先結算,避免玩家等級早已足夠卻卡著不動。 */
   static QuestProgress synchronizeLevel(QuestDefinition quest, QuestProgress progress, int level) {
      QuestProgress result = progress;
      for (QuestObjectiveDefinition objective : quest.objectives()) {
         if (objective.type() != QuestObjectiveType.REACH_LEVEL) {
            continue;
         }
         try {
            if (level >= Integer.parseInt(objective.target())) {
               result = result.withObjective(objective.id(), objective.requiredAmount());
            }
         } catch (NumberFormatException invalidTarget) {
            // 載入時已驗證為數字,這裡只是防禦
         }
      }
      return result;
   }

   static QuestProgress synchronizeDiscoveries(QuestDefinition quest, QuestProgress progress, Set<String> discoveries) {
      QuestProgress synchronizedProgress = progress;

      for(QuestObjectiveDefinition objective : quest.objectives()) {
         if (objective.type() == QuestObjectiveType.DISCOVER_LOCATION && discoveries.contains(objective.target())) {
            synchronizedProgress = synchronizedProgress.withObjective(objective.id(), objective.requiredAmount());
         }
      }

      return synchronizedProgress;
   }

   private QuestProgress synchronizeInventory(QuestDefinition quest, QuestProgress progress, Player player) {
      Map<String, Integer> counts = new LinkedHashMap();

      for(ItemStack item : player.getInventory().getContents()) {
         this.mmoItems.inspect(item).ifPresent((identity) -> counts.merge(identity.objectiveTarget(), item.getAmount(), Integer::sum));
      }

      QuestProgress synchronizedProgress = progress;

      for(QuestObjectiveDefinition objective : quest.objectives()) {
         if (objective.type() == QuestObjectiveType.COLLECT_ITEM) {
            int amount = (Integer)counts.getOrDefault(objective.target().toUpperCase(Locale.ROOT), 0);
            if (amount > 0) {
               synchronizedProgress = synchronizedProgress.withObjective(objective.id(), Math.min(objective.requiredAmount(), amount));
            }
         }
      }

      return synchronizedProgress;
   }

   private static boolean prerequisitesComplete(CharacterProfile character, QuestDefinition quest) {
      return quest.prerequisites().stream().allMatch((id) -> {
         QuestProgress progress = (QuestProgress)character.questProgress().get(id);
         return progress != null && progress.status() == QuestStatus.COMPLETED;
      });
   }

   private boolean objectivesComplete(QuestDefinition quest, QuestProgress progress) {
      return quest.objectives().stream().allMatch((objective) -> (Integer)progress.objectiveProgress().getOrDefault(objective.id(), 0) >= objective.requiredAmount());
   }

   private String plain(String value) {
      return this.miniMessage.stripTags(value);
   }

   public static enum Availability {
      LOCKED,
      AVAILABLE,
      ACTIVE,
      COMPLETED;
   }

   public static enum AcceptResult {
      ACCEPTED,
      LEVEL_REQUIRED,
      PREREQUISITE_REQUIRED,
      ALREADY_ACTIVE,
      ALREADY_COMPLETED,
      NO_ACTIVE_CHARACTER;
   }

   /** 階段推進事件,供通知使用。 */
   private static record StageAdvance(QuestDefinition quest,
                                      com.xuzhihuanjing.rpgcore.domain.quest.QuestStage stage,
                                      int stageIndex) {
   }

   private static record ObjectiveUpdate(QuestDefinition quest, QuestObjectiveDefinition objective, int current) {
   }
}
