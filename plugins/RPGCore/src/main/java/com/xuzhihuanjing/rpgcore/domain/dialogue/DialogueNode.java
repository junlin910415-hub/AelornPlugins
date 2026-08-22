package com.xuzhihuanjing.rpgcore.domain.dialogue;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 對話樹的一個節點:先播完所有台詞,再決定去向。
 *
 * <p>去向有三種,依序判斷:{@code choices} 非空則等待玩家選擇;
 * 否則 {@code next} 非 null 則自動前往;都沒有就結束對話。
 *
 * @param id       節點 id
 * @param lines    此節點的台詞,依序播放
 * @param choices  玩家選項;空表示不需要選擇
 * @param next     自動前往的下一個節點
 * @param grantQuestProgress 播完此節點時是否回報 TALK_TO_NPC 任務進度。
 *                 讓設計者決定「講到哪一句才算完成對話目標」,而不是一互動就完成
 */
public record DialogueNode(String id, List<DialogueLine> lines, List<DialogueChoice> choices,
                           @Nullable String next, boolean grantQuestProgress) {

    public DialogueNode {
        lines = lines == null ? List.of() : List.copyOf(lines);
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    public boolean waitsForChoice() {
        return !choices.isEmpty();
    }

    /** 沒有選項也沒有下一個節點,代表對話在此結束。 */
    public boolean terminal() {
        return choices.isEmpty() && (next == null || next.isBlank());
    }
}
