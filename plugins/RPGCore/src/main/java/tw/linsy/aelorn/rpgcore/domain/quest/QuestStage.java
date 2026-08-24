package tw.linsy.aelorn.rpgcore.domain.quest;

import java.util.List;

/**
 * 任務的一個階段。
 *
 * <p>Wynncraft 式的任務是一連串會推進的步驟,每一步有自己的敘述與目標;
 * 玩家只看得到目前這一步,完成後才揭露下一步。這與「一次攤開所有目標」
 * 的做法差別很大,劇情感主要來自這裡。
 *
 * @param id          階段 id,用於進度記錄與除錯
 * @param description 目前步驟的敘述,會顯示在任務日誌與追蹤列
 * @param objectives  本階段要完成的目標
 */
public record QuestStage(String id, String description, List<QuestObjectiveDefinition> objectives) {

    public QuestStage {
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
    }

    /** 未定義 stages 的舊任務會被包成單一隱含階段,敘述沿用任務本身的描述。 */
    public static QuestStage implicit(String questDescription, List<QuestObjectiveDefinition> objectives) {
        return new QuestStage("main", questDescription, objectives);
    }
}
