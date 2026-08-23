package com.xuzhihuanjing.rpgcore.reagent;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 一種資源的定義 —— 法力、怒氣、精力、連擊點都由這份設定描述。
 *
 * <h2>設定檔範例</h2>
 * <pre>
 * reagents:
 *   mana:
 *     name: "法力"
 *     symbol: "✦"
 *     color: "&amp;b"
 *     behavior: REGENERATE
 *     max:
 *       base: 100
 *       per-level: 5
 *     regen-per-second: 3.0
 *     combat-regen-multiplier: 0.4   # 戰鬥中回復速度打四折
 *     start-ratio: 1.0               # 上線時給滿
 *     order: 1
 *     classes: [mage, priest]        # 留空代表所有職業共用
 *
 *   rage:
 *     name: "怒氣"
 *     symbol: "✸"
 *     color: "&amp;c"
 *     behavior: DECAY
 *     max: { base: 100, per-level: 0 }
 *     decay-per-second: 5.0          # 脫戰每秒消退
 *     start-ratio: 0.0               # 上線時歸零
 *     order: 2
 *     classes: [warrior]
 * </pre>
 *
 * @param id 資源代號，全小寫
 * @param displayName 顯示名稱
 * @param symbol HUD 與 Lore 用的符號
 * @param color 顏色代碼（含 {@code &} 前綴）
 * @param behavior 行為模式
 * @param maxBase 上限基礎值
 * @param maxPerLevel 每級上限成長
 * @param regenPerSecond 每秒回復量
 * @param combatRegenMultiplier 戰鬥中的回復倍率
 * @param decayPerSecond 脫戰後每秒衰退量
 * @param startRatio 初始值佔上限的比例
 * @param order HUD 排序，數字小的排前面
 * @param classes 限定職業；空集合代表不限
 */
public record ReagentType(
        String id,
        String displayName,
        String symbol,
        String color,
        ReagentBehavior behavior,
        double maxBase,
        double maxPerLevel,
        double regenPerSecond,
        double combatRegenMultiplier,
        double decayPerSecond,
        double startRatio,
        int order,
        Set<String> classes) {

    /** 上限的安全天花板，避免設定檔手滑寫出無法顯示的數字。 */
    private static final double MAX_LIMIT = 1_000_000.0;

    public ReagentType {
        id = normalize(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        symbol = symbol == null ? "" : symbol.trim();
        color = color == null || color.isBlank() ? "&f" : color.trim();
        behavior = behavior == null ? ReagentBehavior.REGENERATE : behavior;
        maxBase = clamp(maxBase, 0, MAX_LIMIT);
        maxPerLevel = clamp(maxPerLevel, 0, MAX_LIMIT);
        regenPerSecond = clamp(regenPerSecond, 0, MAX_LIMIT);
        combatRegenMultiplier = clamp(combatRegenMultiplier, 0, 10);
        decayPerSecond = clamp(decayPerSecond, 0, MAX_LIMIT);
        startRatio = clamp(startRatio, 0, 1);
        classes = classes == null ? Set.of() : Set.copyOf(classes);
    }

    /**
     * 從設定區段建立。
     *
     * @param id 資源代號
     * @param section 該資源的設定區段
     */
    public static ReagentType from(String id, ConfigurationSection section) {
        if (section == null) {
            return defaults(id);
        }
        ConfigurationSection max = section.getConfigurationSection("max");
        double maxBase = max != null
                ? max.getDouble("base", 100)
                : section.getDouble("max-base", section.getDouble("max", 100));
        double maxPerLevel = max != null
                ? max.getDouble("per-level", max.getDouble("scale", 0))
                : section.getDouble("max-per-level", 0);

        List<String> rawClasses = section.getStringList("classes");
        Set<String> classes = rawClasses.stream()
                .map(ReagentType::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());

        return new ReagentType(
                id,
                section.getString("name", section.getString("display-name", id)),
                section.getString("symbol", ""),
                section.getString("color", "&f"),
                ReagentBehavior.of(section.getString("behavior")),
                maxBase,
                maxPerLevel,
                section.getDouble("regen-per-second", 0),
                section.getDouble("combat-regen-multiplier", 1.0),
                section.getDouble("decay-per-second", 0),
                section.getDouble("start-ratio", 1.0),
                section.getInt("order", 100),
                classes);
    }

    /** 建立一份保守的預設定義，用於設定檔缺漏時的降級處理。 */
    public static ReagentType defaults(String id) {
        return new ReagentType(id, id, "", "&f", ReagentBehavior.REGENERATE,
                100, 0, 0, 1.0, 0, 1.0, 100, Set.of());
    }

    /** 該等級的資源上限。 */
    public double maxAt(int level) {
        int safeLevel = Math.max(1, level);
        return clamp(maxBase + maxPerLevel * (safeLevel - 1), 1, MAX_LIMIT);
    }

    /** 該等級的初始值。 */
    public double startValueAt(int level) {
        return maxAt(level) * startRatio;
    }

    /** 是否對指定職業開放。 */
    public boolean appliesTo(String classId) {
        return classes.isEmpty() || classes.contains(normalize(classId));
    }

    /**
     * 組出統一格式的顯示字串，例如 {@code &b✦ 法力 84/120}。
     *
     * <p>全插件共用同一個排版，玩家在 HUD、選單、聊天欄看到的樣子才會一致。</p>
     */
    public String format(double current, double max) {
        String prefix = symbol.isEmpty() ? "" : symbol + " ";
        return color + prefix + displayName + " "
                + (long) Math.floor(current) + "/" + (long) Math.ceil(max);
    }

    /** 只回傳數值部分，供 HUD 這類已有標籤的場合使用。 */
    public String formatValue(double current, double max) {
        return color + (long) Math.floor(current) + "/" + (long) Math.ceil(max);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static double clamp(double value, double low, double high) {
        if (!Double.isFinite(value)) {
            return low;
        }
        return Math.max(low, Math.min(high, value));
    }
}
