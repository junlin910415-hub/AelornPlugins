package com.xuzhihuanjing.rpgcore.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 武器與技能的綁定表 —— 決定「哪把武器有哪些技能」。
 *
 * <p>綁定寫在設定檔而不是塞進每件物品的 NBT，理由是可維護性：
 * 想調整全體長劍的技能組時，改一行設定即可，
 * 不必回頭重生成所有玩家手上已經存在的劍。</p>
 *
 * <h2>查找順序</h2>
 * <ol>
 *   <li>先找 {@code items} 底下的<b>特定物品代號</b>（專武用）</li>
 *   <li>再找 {@code types} 底下的<b>物品類型</b>（整類武器的通用技能組）</li>
 * </ol>
 *
 * <p>特定物品優先，因此專武可以覆寫掉它所屬類型的預設技能組。</p>
 *
 * <h2>設定寫法</h2>
 * <pre>
 * bindings:
 *   types:
 *     SWORD:
 *       primary: rift_slash        # 右鍵
 *       secondary: flame_cleave    # 潛行 + 右鍵
 *   items:
 *     ABYSSAL_BLADE:
 *       primary: shadow_lunge
 *       secondary: abyssal_maw
 * </pre>
 */
public final class WeaponSkillBinding {

    private final Map<String, Slots> byItem = new LinkedHashMap<>();
    private final Map<String, Slots> byType = new LinkedHashMap<>();

    /**
     * 載入綁定表。
     *
     * @param section {@code weapon-skills.yml} 根區段
     * @return 載入的綁定筆數
     */
    public int load(ConfigurationSection section) {
        byItem.clear();
        byType.clear();
        if (section == null) {
            return 0;
        }
        ConfigurationSection root = section.getConfigurationSection("bindings");
        if (root == null) {
            root = section;
        }
        read(root.getConfigurationSection("types"), byType);
        read(root.getConfigurationSection("items"), byItem);
        return byItem.size() + byType.size();
    }

    private static void read(ConfigurationSection source, Map<String, Slots> target) {
        if (source == null) {
            return;
        }
        for (String key : source.getKeys(false)) {
            ConfigurationSection entry = source.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Slots slots = new Slots(
                    normalize(entry.getString("primary", "")),
                    normalize(entry.getString("secondary", "")));
            if (slots.hasAny()) {
                target.put(normalize(key), slots);
            }
        }
    }

    /**
     * 查出某件武器的技能組。
     *
     * @param itemId 物品代號（專武用）
     * @param itemType 物品類型
     * @return 綁定；沒有任何綁定時回傳 {@code null}
     */
    public Slots resolve(String itemId, String itemType) {
        Slots specific = byItem.get(normalize(itemId));
        if (specific != null) {
            return specific;
        }
        return byType.get(normalize(itemType));
    }

    /** 全部綁定，供指令與文件輸出。 */
    public Map<String, Slots> allByType() {
        return Map.copyOf(byType);
    }

    /** 專武綁定。 */
    public Map<String, Slots> allByItem() {
        return Map.copyOf(byItem);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    /**
     * 一把武器的兩個技能欄位。
     *
     * <p>只給兩格是刻意的：右鍵與潛行右鍵是玩家不用學就會的操作。
     * 欄位一多就得做快捷列與綁鍵介面，那是另一個層級的工程，
     * 而且對「拿起武器就能打」的體驗沒有幫助。</p>
     *
     * @param primary 右鍵觸發的技能代號
     * @param secondary 潛行 + 右鍵觸發的技能代號
     */
    public record Slots(String primary, String secondary) {

        public Slots {
            primary = primary == null ? "" : primary.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            secondary = secondary == null ? "" : secondary.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        }

        public boolean hasAny() {
            return !primary.isBlank() || !secondary.isBlank();
        }

        /** 依是否潛行取出對應的技能代號。 */
        public String forInput(boolean sneaking) {
            String chosen = sneaking ? secondary : primary;
            return chosen.isBlank() ? null : chosen;
        }

        /** 該欄位有沒有技能。 */
        public List<String> ids() {
            List<String> result = new java.util.ArrayList<>(2);
            if (!primary.isBlank()) {
                result.add(primary);
            }
            if (!secondary.isBlank()) {
                result.add(secondary);
            }
            return List.copyOf(result);
        }
    }
}
