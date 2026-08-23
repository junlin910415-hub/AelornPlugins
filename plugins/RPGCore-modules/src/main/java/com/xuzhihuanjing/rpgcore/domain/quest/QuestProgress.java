package com.xuzhihuanjing.rpgcore.domain.quest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 玩家在單一任務上的進度。
 *
 * <p>{@code stageIndex} 是後加的欄位。既有存檔沒有這個值,反序列化時會落在 0,
 * 剛好等同「第一階段」——單階段任務因此完全不受影響。
 */
public record QuestProgress(QuestStatus status, Map<String, Integer> objectiveProgress, int stageIndex) {

    public QuestProgress {
        Objects.requireNonNull(status, "status");
        objectiveProgress = Map.copyOf(new LinkedHashMap<>(objectiveProgress));
        if (objectiveProgress.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("Quest objective progress cannot be negative");
        }
        stageIndex = Math.max(0, stageIndex);
    }

    /** 舊建構子:沒有階段資訊即為第一階段。 */
    public QuestProgress(QuestStatus status, Map<String, Integer> objectiveProgress) {
        this(status, objectiveProgress, 0);
    }

    public static QuestProgress active() {
        return new QuestProgress(QuestStatus.ACTIVE, Map.of(), 0);
    }

    public QuestProgress withObjective(String objectiveId, int amount) {
        Map<String, Integer> updated = new LinkedHashMap<>(this.objectiveProgress);
        updated.put(objectiveId, Math.max(0, amount));
        return new QuestProgress(this.status, updated, this.stageIndex);
    }

    /**
     * 推進到下一階段。
     *
     * <p>已完成階段的目標進度不清除——日誌要能回顧走過的步驟,而且目標 id
     * 在任務內唯一,不會互相干擾。
     */
    public QuestProgress advanceStage() {
        return new QuestProgress(this.status, this.objectiveProgress, this.stageIndex + 1);
    }

    public QuestProgress completed() {
        return new QuestProgress(QuestStatus.COMPLETED, this.objectiveProgress, this.stageIndex);
    }
}
