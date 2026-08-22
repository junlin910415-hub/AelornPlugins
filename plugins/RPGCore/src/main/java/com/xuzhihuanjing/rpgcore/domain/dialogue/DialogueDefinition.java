package com.xuzhihuanjing.rpgcore.domain.dialogue;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 一段完整的 NPC 對話。
 *
 * @param id            對話 id
 * @param npcId         觸發此對話的 MythicMobs NPC id,對應 QuestService.recordNpcInteraction 的參數
 * @param speaker       預設說話者名稱(MiniMessage)
 * @param startNode     起始節點 id
 * @param nodes         節點表
 * @param lockMovement  播放期間是否鎖住玩家移動(Wynncraft 式演出)
 * @param skippable     是否允許玩家中途跳過
 * @param requiredQuest 僅在玩家持有此任務時觸發;null 表示無條件
 */
public record DialogueDefinition(String id, String npcId, String speaker, String startNode,
                                 Map<String, DialogueNode> nodes, boolean lockMovement,
                                 boolean skippable, @Nullable String requiredQuest) {

    public DialogueDefinition {
        nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
    }

    public @Nullable DialogueNode node(String nodeId) {
        return nodeId == null ? null : nodes.get(nodeId);
    }

    public @Nullable DialogueNode start() {
        return node(startNode);
    }
}
