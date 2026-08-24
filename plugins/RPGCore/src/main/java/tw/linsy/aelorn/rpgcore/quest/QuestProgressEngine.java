package tw.linsy.aelorn.rpgcore.quest;

import tw.linsy.aelorn.rpgcore.domain.quest.QuestDefinition;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestObjectiveDefinition;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestObjectiveType;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestProgress;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStage;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * 任務進度推進的純函式核心。
 *
 * <p>只比對**當前階段**的目標:後面階段的目標即使條件符合也不會被提前計數。
 * 這是多階段劇情的關鍵——玩家必須照順序走,不能在第一階段就把後面的怪先殺完。
 */
public final class QuestProgressEngine {

    private QuestProgressEngine() {
    }

    public static AdvanceResult advance(QuestDefinition quest, QuestProgress progress,
                                        QuestObjectiveType type, String target, int amount) {
        if (progress.status() != QuestStatus.ACTIVE || amount <= 0) {
            return new AdvanceResult(progress, List.of(), false, false, null);
        }

        QuestStage stage = quest.stage(progress.stageIndex());
        if (stage == null) {
            // 進度指向不存在的階段(設定被改過),當作完成收場而不是把玩家卡死
            return new AdvanceResult(progress.completed(), List.of(), true, false, null);
        }

        QuestProgress changed = progress;
        List<QuestObjectiveDefinition> updated = new ArrayList<>();
        for (QuestObjectiveDefinition objective : stage.objectives()) {
            if (objective.type() != type || !objective.target().equalsIgnoreCase(target)) {
                continue;
            }
            int before = changed.objectiveProgress().getOrDefault(objective.id(), 0);
            int after = Math.min(objective.requiredAmount(), before + amount);
            if (after > before) {
                changed = changed.withObjective(objective.id(), after);
                updated.add(objective);
            }
        }

        if (updated.isEmpty()) {
            return new AdvanceResult(progress, List.of(), false, false, null);
        }

        QuestProgress evaluated = changed;
        boolean stageComplete = stage.objectives().stream()
            .allMatch(objective -> evaluated.objectiveProgress().getOrDefault(objective.id(), 0)
                >= objective.requiredAmount());

        if (!stageComplete) {
            return new AdvanceResult(changed, updated, false, false, null);
        }

        // 本階段完成:還有下一階段就推進,沒有就結束整個任務
        boolean lastStage = progress.stageIndex() + 1 >= quest.stageCount();
        if (lastStage) {
            return new AdvanceResult(changed.completed(), updated, true, false, null);
        }
        QuestProgress advanced = changed.advanceStage();
        return new AdvanceResult(advanced, updated, false, true, quest.stage(advanced.stageIndex()));
    }

    /**
     * @param completed     整個任務完成
     * @param stageAdvanced 進入了下一階段(與 completed 互斥)
     * @param newStage      推進後的新階段,供通知顯示;未推進時為 null
     */
    public record AdvanceResult(QuestProgress progress, List<QuestObjectiveDefinition> updatedObjectives,
                                boolean completed, boolean stageAdvanced, QuestStage newStage) {
        public AdvanceResult {
            updatedObjectives = List.copyOf(updatedObjectives);
        }
    }
}
