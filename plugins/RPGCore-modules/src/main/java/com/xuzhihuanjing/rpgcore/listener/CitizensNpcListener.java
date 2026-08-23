package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.config.MessageBundle;
import com.xuzhihuanjing.rpgcore.config.NpcRegistry;
import com.xuzhihuanjing.rpgcore.config.QuestRegistry;
import com.xuzhihuanjing.rpgcore.dialogue.DialogueService;
import com.xuzhihuanjing.rpgcore.domain.character.CharacterProfile;
import com.xuzhihuanjing.rpgcore.domain.npc.NpcDefinition;
import com.xuzhihuanjing.rpgcore.domain.quest.QuestDefinition;
import com.xuzhihuanjing.rpgcore.integration.citizens.CitizensBridge;
import com.xuzhihuanjing.rpgcore.npc.NpcBehaviorService;
import com.xuzhihuanjing.rpgcore.quest.QuestService;
import com.xuzhihuanjing.rpgcore.service.CharacterService;
import java.util.Objects;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Citizens NPC 與任務系統的接點。
 *
 * <p>右鍵一個 NPC 時的處理順序刻意設計成:**交付 → 對話 → 接任務 → 談話進度**。
 * 玩家帶著任務物品來找 NPC 時應該先收下東西;有劇情就播劇情;都沒有才考慮給新任務。
 */
public final class CitizensNpcListener implements Listener {

    private final CitizensBridge citizens;
    private final NpcRegistry npcRegistry;
    private final QuestRegistry questRegistry;
    private final QuestService questService;
    private final DialogueService dialogueService;
    private final NpcBehaviorService behaviorService;
    private final CharacterService characterService;
    private final MessageBundle messages;

    public CitizensNpcListener(CitizensBridge citizens, NpcRegistry npcRegistry, QuestRegistry questRegistry,
                               QuestService questService, DialogueService dialogueService,
                               NpcBehaviorService behaviorService, CharacterService characterService,
                               MessageBundle messages) {
        this.citizens = Objects.requireNonNull(citizens, "citizens");
        this.npcRegistry = Objects.requireNonNull(npcRegistry, "npcRegistry");
        this.questRegistry = Objects.requireNonNull(questRegistry, "questRegistry");
        this.questService = Objects.requireNonNull(questService, "questService");
        this.dialogueService = Objects.requireNonNull(dialogueService, "dialogueService");
        this.behaviorService = Objects.requireNonNull(behaviorService, "behaviorService");
        this.characterService = Objects.requireNonNull(characterService, "characterService");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        Player player = event.getClicker();
        String key = citizens.keyOf(npc);
        NpcDefinition definition = npcRegistry.find(key).orElse(null);
        if (definition == null) {
            return;
        }

        // 1) 先收下玩家帶來的任務物品
        questService.recordDelivery(player, key);

        // 2) 有劇情就交給對話引擎;對話會自行決定何時回報談話進度
        if (dialogueService.startForNpc(player, key)) {
            return;
        }

        // 3) 沒有劇情才考慮給新任務
        if (definition.givesQuests() && offerQuest(player, definition)) {
            return;
        }

        // 4) 都沒有就當作普通談話
        questService.recordNpcInteraction(player, key);
    }

    /**
     * 提供這個 NPC 名下第一個可接的任務。
     *
     * @return true 表示已接下任務
     */
    private boolean offerQuest(Player player, NpcDefinition definition) {
        CharacterProfile character = characterService.activeCharacter(player.getUniqueId()).orElse(null);
        if (character == null) {
            return false;
        }
        for (String questId : definition.quests()) {
            QuestDefinition quest = questRegistry.find(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            if (questService.availability(character, quest) != QuestService.Availability.AVAILABLE) {
                continue;
            }
            QuestService.AcceptResult result = questService.accept(player, quest);
            if (result == QuestService.AcceptResult.ACCEPTED) {
                player.sendMessage(messages.message("quest-accepted-from-npc",
                    MessageBundle.value("npc", definition.displayName()),
                    MessageBundle.value("quest", quest.displayName())));
                return true;
            }
        }
        return false;
    }

    /** NPC 生成時掛上行為;Citizens 會在世界載入、區塊載入時觸發。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(NPCSpawnEvent event) {
        behaviorService.attach(event.getNPC());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDespawn(NPCDespawnEvent event) {
        behaviorService.detach(event.getNPC());
    }
}
