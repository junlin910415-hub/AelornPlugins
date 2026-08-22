package tw.linsy.aelorn.quest;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * RPGCore quests.yml 的唯讀快照。
 *
 * 只在啟動與 /aelornquest reload 時載入一次；之後全部從記憶體讀，
 * 因為 placeholder 每 tick 都會被 TAB 呼叫，不能碰磁碟。
 */
public final class QuestCatalog {

    /** 單一任務目標的定義。 */
    public record Objective(String id, String description, int requiredAmount) {
    }

    /** 單一任務的定義；displayName 已去除 MiniMessage 標籤。 */
    public record Quest(String id, String displayName, String description,
                        String category, int minimumLevel, List<Objective> objectives) {
    }

    private volatile Map<String, Quest> quests = Map.of();

    /** 回傳載入的任務數；檔案不存在時回傳 0 並保留舊資料。 */
    public int load(File questsFile) {
        if (!questsFile.isFile()) {
            return 0;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(questsFile);
        ConfigurationSection root = yaml.getConfigurationSection("quests");
        if (root == null) {
            return 0;
        }
        Map<String, Quest> loaded = new LinkedHashMap<>();
        for (String questId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(questId);
            if (section == null) {
                continue;
            }
            List<Objective> objectives = new ArrayList<>();
            ConfigurationSection objectiveRoot = section.getConfigurationSection("objectives");
            if (objectiveRoot != null) {
                for (String objectiveId : objectiveRoot.getKeys(false)) {
                    ConfigurationSection objective = objectiveRoot.getConfigurationSection(objectiveId);
                    if (objective == null) {
                        continue;
                    }
                    objectives.add(new Objective(
                            objectiveId,
                            stripTags(objective.getString("description", objectiveId)),
                            Math.max(1, objective.getInt("amount", 1))));
                }
            }
            loaded.put(questId, new Quest(
                    questId,
                    stripTags(section.getString("display-name", questId)),
                    stripTags(section.getString("description", "")),
                    section.getString("category", ""),
                    section.getInt("minimum-level", 1),
                    List.copyOf(objectives)));
        }
        this.quests = Map.copyOf(loaded);
        return quests.size();
    }

    public Quest quest(String questId) {
        return questId == null ? null : quests.get(questId);
    }

    public int size() {
        return quests.size();
    }

    /** RPGCore 的顯示名稱帶 MiniMessage 標籤與 & 色碼；記分板的顏色由 TAB 設定決定。 */
    static String stripTags(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("<[^>]+>", "")
                .replaceAll("(?i)[&§][0-9A-FK-ORX]", "")
                .trim();
    }
}
