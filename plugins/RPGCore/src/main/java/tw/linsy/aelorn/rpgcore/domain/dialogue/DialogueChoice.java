package tw.linsy.aelorn.rpgcore.domain.dialogue;

import org.jetbrains.annotations.Nullable;

/**
 * 玩家在對話節點可選的一個分支。
 *
 * @param text            選項文字(MiniMessage)
 * @param next            選擇後前往的節點 id
 * @param minimumLevel    需要的角色等級,0 表示不限制
 * @param requiredQuest   需要已完成的任務 id;null 表示不限制
 * @param hiddenWhenLocked 條件不符時隱藏而非顯示為鎖定。
 *                        劇情選項通常隱藏比較自然,任務選項顯示鎖定比較好懂
 */
public record DialogueChoice(String text, String next, int minimumLevel,
                             @Nullable String requiredQuest, boolean hiddenWhenLocked) {

    public DialogueChoice {
        text = text == null ? "" : text;
        minimumLevel = Math.max(0, minimumLevel);
    }

    public boolean hasRequirement() {
        return minimumLevel > 0 || (requiredQuest != null && !requiredQuest.isBlank());
    }
}
