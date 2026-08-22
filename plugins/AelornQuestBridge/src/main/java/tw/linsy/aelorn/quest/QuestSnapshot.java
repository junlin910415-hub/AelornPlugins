package tw.linsy.aelorn.quest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 單一玩家的任務狀態快照，由背景排程更新，placeholder 只讀不算。
 *
 * 資料來源是 RPGCore 的存檔（唯讀）：
 *   characters.&lt;slot&gt;.tracked-quest
 *   characters.&lt;slot&gt;.quests.&lt;questId&gt;.status
 *   characters.&lt;slot&gt;.quests.&lt;questId&gt;.objectives.&lt;objectiveId&gt;
 */
public record QuestSnapshot(
        boolean active,
        String questId,
        String questName,
        String questDescription,
        String category,
        int minimumLevel,
        List<String> pendingObjectives,
        int objectivesDone,
        int objectivesTotal,
        int acceptedCount,
        int completedCount) {

    public static final QuestSnapshot EMPTY = new QuestSnapshot(
            false, "", "", "", "", 0, List.of(), 0, 0, 0, 0);

    public QuestSnapshot {
        pendingObjectives = List.copyOf(pendingObjectives);
    }

    /** 第 index 個未完成目標（1 起算）；沒有就回傳空字串。 */
    public String objectiveLine(int index) {
        int zeroBased = index - 1;
        return zeroBased >= 0 && zeroBased < pendingObjectives.size()
                ? pendingObjectives.get(zeroBased)
                : "";
    }

    public String progressText() {
        return objectivesTotal <= 0 ? "" : objectivesDone + "/" + objectivesTotal;
    }

    /**
     * 從 RPGCore 存檔讀出當前角色的任務狀態。
     * 這個方法會碰磁碟，必須在非同步排程中呼叫。
     */
    public static QuestSnapshot read(File playerDataFolder, UUID playerId, QuestCatalog catalog) {
        File file = new File(playerDataFolder, playerId + ".yml");
        if (!file.isFile()) {
            return EMPTY;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int slot = yaml.getInt("active-slot", -1);
        String prefix = "characters." + slot + ".";
        ConfigurationSection character = yaml.getConfigurationSection("characters." + slot);
        if (character == null) {
            return EMPTY;
        }

        int accepted = 0;
        int completed = 0;
        ConfigurationSection questRoot = yaml.getConfigurationSection(prefix + "quests");
        if (questRoot != null) {
            for (String questId : questRoot.getKeys(false)) {
                accepted++;
                if ("COMPLETED".equalsIgnoreCase(
                        questRoot.getString(questId + ".status", ""))) {
                    completed++;
                }
            }
        }

        String trackedId = character.getString("tracked-quest", "");
        if (trackedId == null || trackedId.isBlank()) {
            return new QuestSnapshot(false, "", "", "", "", 0, List.of(), 0, 0,
                    accepted, completed);
        }
        QuestCatalog.Quest quest = catalog.quest(trackedId);
        if (quest == null) {
            return new QuestSnapshot(false, trackedId, trackedId, "", "", 0, List.of(), 0, 0,
                    accepted, completed);
        }

        ConfigurationSection objectiveSection =
                yaml.getConfigurationSection(prefix + "quests." + trackedId + ".objectives");
        List<String> pending = new ArrayList<>();
        int done = 0;
        for (QuestCatalog.Objective objective : quest.objectives()) {
            int current = objectiveSection == null
                    ? 0
                    : Math.max(0, objectiveSection.getInt(objective.id(), 0));
            if (current >= objective.requiredAmount()) {
                done++;
                continue;
            }
            pending.add(objective.description()
                    + " " + current + "/" + objective.requiredAmount());
        }

        return new QuestSnapshot(
                true,
                quest.id(),
                quest.displayName(),
                quest.description(),
                quest.category(),
                quest.minimumLevel(),
                pending,
                done,
                quest.objectives().size(),
                accepted,
                completed);
    }
}
