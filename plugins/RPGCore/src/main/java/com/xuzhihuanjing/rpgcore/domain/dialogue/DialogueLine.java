package com.xuzhihuanjing.rpgcore.domain.dialogue;

import org.jetbrains.annotations.Nullable;

/**
 * 對話中的一句台詞。
 *
 * @param text        MiniMessage 內容,與 quests.yml 的文字格式一致
 * @param speaker     覆寫此句的說話者;null 表示沿用對話定義的預設說話者
 * @param typewriter  是否以逐字方式在動作列呈現。整段對話都逐字會太慢,
 *                    所以由每一句自行決定,通常只用在關鍵台詞
 * @param pauseTicks  這句顯示完之後、進入下一句之前的停頓
 */
public record DialogueLine(String text, @Nullable String speaker, boolean typewriter, int pauseTicks) {

    /** 停頓上限,避免設定錯誤把玩家鎖在對話裡太久。 */
    public static final int MAX_PAUSE_TICKS = 200;

    public DialogueLine {
        text = text == null ? "" : text;
        pauseTicks = Math.max(0, Math.min(MAX_PAUSE_TICKS, pauseTicks));
    }

    public String speakerOr(String fallback) {
        return speaker == null || speaker.isBlank() ? fallback : speaker;
    }
}
