package com.xuzhihuanjing.rpgcore.skill;

import com.xuzhihuanjing.rpgcore.reagent.ReagentType;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 技能消耗的迴歸測試 —— 把兩份設定檔對起來看。
 *
 * <p>{@code skills.yml} 的 {@code costs} 只是字串鍵，代號打錯不會讓插件起不來，
 * 而是等玩家按下右鍵、看到「你的職業無法使用這個技能所需的資源」才會發現。
 * 這一關在建置時就把 {@code skills.yml} 的每個消耗代號拿去
 * {@code reagents.yml} 對一次，錯字當場擋下。</p>
 *
 * <p>另外檢查一條設計約束：武器技能是「拿起武器就能打」，
 * 若消耗了有 {@code classes} 限制的資源，其他職業會完全用不了那把武器。
 * 真要這樣設計時，請連同這裡的白名單一起改，讓它是個明示的決定。</p>
 */
public final class SkillCostsTest {

    /** 允許使用職業限定資源的技能；預設為空，代表所有武器技能都不限職業。 */
    private static final Set<String> CLASS_LOCKED_SKILLS = Set.of();

    private SkillCostsTest() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected the skills.yml and reagents.yml paths");
        }
        ConfigurationSection skillRoot = load(arguments[0]);
        ConfigurationSection reagentRoot = load(arguments[1]);

        Map<String, ReagentType> reagents = readReagents(reagentRoot);
        require(!reagents.isEmpty(), "reagents.yml did not yield any reagent definitions");

        List<String> problems = new ArrayList<>();
        Set<String> costed = new LinkedHashSet<>();
        ConfigurationSection skills = skillRoot.getConfigurationSection("skills");
        require(skills != null, "skills.yml is missing the skills section");

        for (String key : skills.getKeys(false)) {
            SkillScript script = SkillScript.from(key, skills.getConfigurationSection(key), problems);
            require(script != null, "skill failed to parse: " + key);
            if (!script.hasCosts()) {
                continue;
            }
            costed.add(script.id());
            for (Map.Entry<String, Double> cost : script.costs().entrySet()) {
                ReagentType type = reagents.get(cost.getKey());
                require(type != null,
                        "skill " + script.id() + " costs an unknown reagent: " + cost.getKey());
                require(cost.getValue() > 0,
                        "skill " + script.id() + " has a non-positive cost for " + cost.getKey());
                require(cost.getValue() <= type.maxAt(1),
                        "skill " + script.id() + " costs more " + cost.getKey()
                                + " than a level 1 character can ever hold");
                require(type.classes().isEmpty() || CLASS_LOCKED_SKILLS.contains(script.id()),
                        "skill " + script.id() + " costs class-restricted reagent "
                                + cost.getKey() + "; other classes could not use the weapon");
            }
        }

        require(problems.isEmpty(), "skills.yml reported parse problems: " + problems);
        require(!costed.isEmpty(), "no skill declares any cost; the costs field is still unwired");
        System.out.println("SkillCostsTest PASS (" + costed.size() + " costed skills)");
    }

    private static Map<String, ReagentType> readReagents(ConfigurationSection root) {
        ConfigurationSection types = root.getConfigurationSection("types");
        ConfigurationSection source = types == null ? root : types;
        Map<String, ReagentType> reagents = new java.util.LinkedHashMap<>();
        for (String key : source.getKeys(false)) {
            ConfigurationSection entry = source.getConfigurationSection(key);
            if (entry != null) {
                ReagentType type = ReagentType.from(key, entry);
                reagents.put(type.id(), type);
            }
        }
        return reagents;
    }

    private static ConfigurationSection load(String path) {
        File file = new File(path);
        if (!file.isFile()) {
            throw new IllegalArgumentException("Configuration file is missing: " + path);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
