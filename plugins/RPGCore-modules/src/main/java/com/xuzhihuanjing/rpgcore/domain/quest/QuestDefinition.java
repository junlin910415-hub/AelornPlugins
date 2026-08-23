package com.xuzhihuanjing.rpgcore.domain.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一個任務的完整定義。
 *
 * <p>任務由一到多個 {@link QuestStage} 組成。schema 3 的扁平 {@code objectives}
 * 會被包成單一隱含階段,所以舊任務不必改寫就能繼續運作;{@link #objectives()}
 * 仍然回傳全部階段的目標總和,既有的驗證與統計程式碼因此不受影響。
 *
 * @param rewardExperience 保留欄位,等同 {@code reward().experience()};既有呼叫端沿用即可
 */
public record QuestDefinition(String id, String displayName, String description, QuestCategory category,
                              String iconMaterial, int minimumLevel, List<String> prerequisites,
                              long rewardExperience, List<QuestObjectiveDefinition> objectives,
                              List<QuestStage> stages, QuestReward reward) {

    public QuestDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(iconMaterial, "iconMaterial");
        prerequisites = List.copyOf(prerequisites);
        objectives = List.copyOf(objectives);
        stages = List.copyOf(stages);
        reward = reward == null ? QuestReward.NONE : reward;
    }

    /** schema 3 的建構方式:扁平目標 + 只有經驗的獎勵。 */
    public QuestDefinition(String id, String displayName, String description, QuestCategory category,
                           String iconMaterial, int minimumLevel, List<String> prerequisites,
                           long rewardExperience, List<QuestObjectiveDefinition> objectives) {
        this(id, displayName, description, category, iconMaterial, minimumLevel, prerequisites,
            rewardExperience, objectives,
            List.of(QuestStage.implicit(description, objectives)),
            QuestReward.experienceOnly(rewardExperience));
    }

    /** 多階段建構:objectives 由各階段彙總,呼叫端不必自己攤平。 */
    public static QuestDefinition staged(String id, String displayName, String description,
                                         QuestCategory category, String iconMaterial, int minimumLevel,
                                         List<String> prerequisites, List<QuestStage> stages,
                                         QuestReward reward) {
        List<QuestObjectiveDefinition> flattened = new ArrayList<>();
        for (QuestStage stage : stages) {
            flattened.addAll(stage.objectives());
        }
        QuestReward effective = reward == null ? QuestReward.NONE : reward;
        return new QuestDefinition(id, displayName, description, category, iconMaterial, minimumLevel,
            prerequisites, effective.experience(), flattened, stages, effective);
    }

    public int stageCount() {
        return stages.size();
    }

    public boolean multiStage() {
        return stages.size() > 1;
    }

    /** 超出範圍時回傳 null,呼叫端據此判斷已走完所有階段。 */
    public QuestStage stage(int index) {
        return index >= 0 && index < stages.size() ? stages.get(index) : null;
    }

    /** 玩家目前該做的階段;進度已走完時回傳最後一階段,供日誌顯示。 */
    public QuestStage stageFor(QuestProgress progress) {
        if (stages.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(stages.size() - 1, progress.stageIndex()));
        return stages.get(index);
    }
}
