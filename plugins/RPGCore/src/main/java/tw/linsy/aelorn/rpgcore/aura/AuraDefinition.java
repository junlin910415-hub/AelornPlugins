package tw.linsy.aelorn.rpgcore.aura;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 增益／減益的定義 —— 祝福、詛咒、中毒、狂暴都由這份設定描述。
 *
 * <h2>設定檔範例</h2>
 * <pre>
 * auras:
 *   battle_fury:
 *     name: "戰意高昂"
 *     icon: "⚔"
 *     color: "&amp;c"
 *     beneficial: true
 *     duration-seconds: 12
 *     max-stacks: 5
 *     stack-rule: STACK          # 疊層並刷新時間
 *     description: "每層提升 6% 攻擊力，最多五層。"
 *     modifiers:
 *       attack-power:
 *         percent: 6            # 每層 +6%
 *
 *   deep_poison:
 *     name: "劇毒"
 *     icon: "☠"
 *     color: "&amp;2"
 *     beneficial: false
 *     duration-seconds: 8
 *     max-stacks: 3
 *     stack-rule: STACK
 *     description: "每秒受到毒素傷害，可疊三層。"
 *     periodic:
 *       interval-seconds: 1
 *       damage: 4              # 每層每次 4 點
 * </pre>
 *
 * <p>屬性修飾分為 {@code flat}（加值）與 {@code percent}（百分比）兩段，
 * 一律先加總所有加值、再套用百分比，避免多個效果互乘導致數值失控。</p>
 */
public record AuraDefinition(
        String id,
        String displayName,
        String icon,
        String color,
        String description,
        boolean beneficial,
        double durationSeconds,
        int maxStacks,
        StackRule stackRule,
        Map<String, Modifier> modifiers,
        Periodic periodic) {

    /** 持續時間上限（秒），避免設定檔寫出實質永久的效果。 */
    private static final double MAX_DURATION = 86_400.0;
    /** 疊層上限。 */
    private static final int MAX_STACK_LIMIT = 999;

    public AuraDefinition {
        id = normalize(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        icon = icon == null ? "" : icon.trim();
        color = color == null || color.isBlank() ? "&f" : color.trim();
        description = description == null ? "" : description.trim();
        durationSeconds = clamp(durationSeconds, 0, MAX_DURATION);
        maxStacks = (int) clamp(maxStacks, 1, MAX_STACK_LIMIT);
        stackRule = stackRule == null ? StackRule.REFRESH : stackRule;
        modifiers = modifiers == null ? Map.of() : Map.copyOf(modifiers);
    }

    /** 由設定區段建立。 */
    public static AuraDefinition from(String id, ConfigurationSection section) {
        if (section == null) {
            return new AuraDefinition(id, id, "", "&f", "", true, 10, 1,
                    StackRule.REFRESH, Map.of(), null);
        }

        Map<String, Modifier> modifiers = new LinkedHashMap<>();
        ConfigurationSection modifierSection = section.getConfigurationSection("modifiers");
        if (modifierSection != null) {
            for (String key : modifierSection.getKeys(false)) {
                Modifier modifier = Modifier.from(modifierSection, key);
                if (modifier != null && !modifier.isEmpty()) {
                    modifiers.put(normalize(key), modifier);
                }
            }
        }

        return new AuraDefinition(
                id,
                section.getString("name", id),
                section.getString("icon", ""),
                section.getString("color", "&f"),
                section.getString("description", ""),
                section.getBoolean("beneficial", true),
                section.getDouble("duration-seconds", 10),
                section.getInt("max-stacks", 1),
                StackRule.of(section.getString("stack-rule")),
                modifiers,
                Periodic.from(section.getConfigurationSection("periodic")));
    }

    /** 是否為永久效果（持續時間 0 代表不自動到期）。 */
    public boolean permanent() {
        return durationSeconds <= 0;
    }

    /** 是否可疊層。 */
    public boolean stackable() {
        return maxStacks > 1 && stackRule == StackRule.STACK;
    }

    /** 是否有週期效果。 */
    public boolean hasPeriodic() {
        return periodic != null && periodic.enabled();
    }

    /**
     * 組出統一格式的顯示標籤，例如 {@code &c⚔ 戰意高昂 ×3}。
     *
     * @param stacks 目前疊層數
     */
    public String format(int stacks) {
        String prefix = icon.isEmpty() ? "" : icon + " ";
        String suffix = stacks > 1 ? " ×" + stacks : "";
        return color + prefix + displayName + suffix;
    }

    /** 組出含剩餘秒數的顯示標籤，例如 {@code &c⚔ 戰意高昂 ×3 (8秒)}。 */
    public String format(int stacks, double remainingSeconds) {
        if (permanent()) {
            return format(stacks);
        }
        return format(stacks) + " &7(" + (long) Math.ceil(Math.max(0, remainingSeconds)) + "秒)";
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

    // ------------------------------------------------------------------
    // 巢狀型別
    // ------------------------------------------------------------------

    /** 重複施加同一效果時的處理方式。 */
    public enum StackRule {
        /** 刷新持續時間，不增加層數。 */
        REFRESH,
        /** 疊加層數並刷新持續時間。 */
        STACK,
        /** 已存在時直接忽略，讓原本的效果自然走完。 */
        IGNORE;

        /** 由設定檔字串解析；無法辨識時退回 {@link #REFRESH}。 */
        public static StackRule of(String name) {
            if (name == null || name.isBlank()) {
                return REFRESH;
            }
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return REFRESH;
            }
        }
    }

    /**
     * 單一屬性的修飾量。
     *
     * @param flat 每層的加值
     * @param percent 每層的百分比加成（以 100 為基準，6 代表 +6%）
     */
    public record Modifier(double flat, double percent) {

        public boolean isEmpty() {
            return flat == 0 && percent == 0;
        }

        static Modifier from(ConfigurationSection parent, String key) {
            if (parent.isConfigurationSection(key)) {
                ConfigurationSection section = parent.getConfigurationSection(key);
                if (section == null) {
                    return null;
                }
                return new Modifier(
                        section.getDouble("flat", section.getDouble("value", 0)),
                        section.getDouble("percent", 0));
            }
            // 純數字寫法一律視為加值
            if (parent.isDouble(key) || parent.isInt(key) || parent.isLong(key)) {
                return new Modifier(parent.getDouble(key), 0);
            }
            return null;
        }
    }

    /**
     * 週期效果 —— 每隔一段時間觸發一次的傷害或治療。
     *
     * @param intervalSeconds 觸發間隔（秒）
     * @param damage 每層每次造成的傷害
     * @param healing 每層每次回復的生命
     */
    public record Periodic(double intervalSeconds, double damage, double healing) {

        public boolean enabled() {
            return intervalSeconds > 0 && (damage != 0 || healing != 0);
        }

        static Periodic from(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            return new Periodic(
                    Math.max(0.05, section.getDouble("interval-seconds", 1)),
                    section.getDouble("damage", 0),
                    section.getDouble("healing", 0));
        }
    }
}
