package tw.linsy.aelorn.rpgcore.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 一個技能腳本 —— 有序的步驟串。
 *
 * <p>技能的「華麗」不是靠單一大特效，而是靠<b>節奏</b>：
 * 前搖讓對手看得見、爆發在正確的時間點出現、餘波把畫面收乾淨。
 * 步驟加上延遲就足以表達這一切，不需要為每個技能寫一支 Java 方法。</p>
 *
 * <h2>設定寫法</h2>
 * <pre>
 * skills:
 *   flame_cleave:
 *     name: '&amp;c烈焰橫掃'
 *     tier: FLASHY              # SIMPLE / FLASHY / ULTIMATE / SIGNATURE
 *     cooldown-seconds: 8
 *     cast-seconds: 0.6         # 吟唱時間，0 = 瞬發
 *     costs:                    # 施放代價，代號對應 reagents.yml
 *       mana: 24
 *       rage: 20
 *     description: '橫掃前方敵人並附加灼燒。'
 *     steps:
 *       - delay: 0
 *         mechanic: SOUND
 *         sound: ENTITY_BLAZE_SHOOT
 *         amount: 1.0
 *       - delay: 0
 *         mechanic: PARTICLE
 *         targeter: SELF
 *         particle: FLAME
 *         amount: 30
 *         spread: 1.2
 *       - delay: 8               # 前搖 8 tick，對手有反應窗口
 *         mechanic: DAMAGE
 *         targeter: CONE
 *         radius: 4.5
 *         angle: 120
 *         max-targets: 5
 *         amount: 1.6            # 攻擊力的 1.6 倍
 *       - delay: 8
 *         mechanic: AURA
 *         targeter: CONE
 *         radius: 4.5
 *         angle: 120
 *         aura: searing_burn
 *         amount: 1              # 層數
 * </pre>
 *
 * @param id 技能代號
 * @param displayName 顯示名稱
 * @param tier 技能層級
 * @param cooldownSeconds 冷卻秒數
 * @param castSeconds 吟唱秒數；0 為瞬發
 * @param description 給玩家看的說明
 * @param costs 施放代價：資源代號對應消耗量，代號須存在於 {@code reagents.yml}
 * @param steps 步驟串，依 {@code delay} 排序
 */
public record SkillScript(
        String id,
        String displayName,
        Tier tier,
        double cooldownSeconds,
        double castSeconds,
        String description,
        Map<String, Double> costs,
        List<Step> steps) {

    /** 單一資源的消耗上限，防止設定檔手滑寫出永遠放不出來的技能。 */
    private static final double COST_LIMIT = 100_000.0;

    public SkillScript {
        id = normalize(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        tier = tier == null ? Tier.SIMPLE : tier;
        cooldownSeconds = clamp(cooldownSeconds, 0, 3600);
        castSeconds = clamp(castSeconds, 0, 30);
        description = description == null ? "" : description.trim();
        costs = costs == null ? Map.of() : Map.copyOf(costs);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** 由設定區段建立；步驟解析失敗時該步驟會被跳過並記在回報清單裡。 */
    public static SkillScript from(String id, ConfigurationSection section, List<String> problems) {
        if (section == null) {
            return null;
        }
        List<Step> steps = new ArrayList<>();
        List<?> rawSteps = section.getList("steps");
        if (rawSteps != null) {
            int index = 0;
            for (Object raw : rawSteps) {
                index++;
                Step step = Step.from(raw, id + " 第 " + index + " 步", problems);
                if (step != null) {
                    steps.add(step);
                }
            }
        }
        if (steps.isEmpty()) {
            problems.add("技能「" + id + "」沒有任何有效步驟，已略過。");
            return null;
        }
        // 依延遲排序,讓執行端可以線性推進
        steps.sort((left, right) -> Long.compare(left.delayTicks(), right.delayTicks()));

        return new SkillScript(
                id,
                section.getString("name", id),
                Tier.of(section.getString("tier")),
                section.getDouble("cooldown-seconds", 0),
                section.getDouble("cast-seconds", 0),
                section.getString("description", ""),
                parseCosts(id, section.getConfigurationSection("costs"), problems),
                steps);
    }

    /**
     * 解析施放代價。
     *
     * <p>這裡<b>不</b>檢查資源代號是否真的存在於 {@code reagents.yml} ——
     * 技能載入時資源可能還沒載入，而且兩份設定檔互相驗證會讓載入順序變成隱藏規則。
     * 代號打錯的後果由施放時的檢查回報，玩家會看到「沒有這種資源」而不是無聲失效。</p>
     */
    private static Map<String, Double> parseCosts(String id, ConfigurationSection section,
                                                  List<String> problems) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> costs = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            double amount = section.getDouble(key, 0);
            if (!Double.isFinite(amount) || amount <= 0) {
                problems.add("技能「" + id + "」的消耗「" + key + "」不是正數，已略過。");
                continue;
            }
            costs.put(normalize(key), Math.min(COST_LIMIT, amount));
        }
        return costs;
    }

    /** 是否需要吟唱。 */
    public boolean requiresCast() {
        return castSeconds > 0;
    }

    /** 是否需要付出資源。 */
    public boolean hasCosts() {
        return !costs.isEmpty();
    }

    /** 整個技能演完需要幾 tick。 */
    public long totalTicks() {
        return steps.stream().mapToLong(Step::delayTicks).max().orElse(0L);
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

    /**
     * 技能層級 —— 決定它在戰鬥中的定位。
     *
     * <p>層級本身不改變數值，只是給企劃與 Lore 一個一致的分類，
     * 讓「這是小技能還是大招」在設定檔與物品說明上都講得清楚。</p>
     */
    public enum Tier {
        /** 簡單：低耗、短冷卻、瞬發，用來填輸出空檔。 */
        SIMPLE("&f簡易"),
        /** 華麗：中耗、有前搖、帶控場或附加狀態，是戰鬥的主力。 */
        FLASHY("&b精妙"),
        /** 大招：高耗、長冷卻、高爆發，決定戰局的一擊。 */
        ULTIMATE("&6奧義"),
        /** 專武：綁定特定武器，是那把武器存在的理由。 */
        SIGNATURE("&d專武");

        private final String label;

        Tier(String label) {
            this.label = label;
        }

        /** Lore 與選單用的統一標籤。 */
        public String label() {
            return label;
        }

        public static Tier of(String name) {
            if (name == null || name.isBlank()) {
                return SIMPLE;
            }
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return SIMPLE;
            }
        }
    }

    /**
     * 單一步驟。
     *
     * @param delayTicks 從施放起算延遲幾 tick 執行
     * @param targeter 目標選擇器
     * @param targetParams 選擇器參數
     * @param mechanic 機制
     * @param mechanicParams 機制參數
     */
    public record Step(
            long delayTicks,
            SkillTargeter targeter,
            SkillTargeter.Params targetParams,
            SkillMechanic mechanic,
            SkillMechanic.Params mechanicParams) {

        @SuppressWarnings("unchecked")
        static Step from(Object raw, String label, List<String> problems) {
            if (!(raw instanceof java.util.Map<?, ?> map)) {
                problems.add(label + "：格式不是設定區塊，已略過。");
                return null;
            }
            java.util.Map<String, Object> values = (java.util.Map<String, Object>) map;

            SkillMechanic mechanic = SkillMechanic.of(string(values, "mechanic"));
            if (mechanic == null) {
                problems.add(label + "：無法辨識的機制「" + string(values, "mechanic") + "」，已略過。");
                return null;
            }

            SkillTargeter targeter = SkillTargeter.of(string(values, "targeter"));
            SkillTargeter.Params targetParams = new SkillTargeter.Params(
                    number(values, "radius", 4.0),
                    number(values, "range", 8.0),
                    number(values, "angle", 90.0),
                    (int) number(values, "max-targets", 0));

            // 文字參數依機制取用不同的鍵,設定檔才讀得懂在寫什麼
            String text = firstNonBlank(
                    string(values, "aura"),
                    string(values, "particle"),
                    string(values, "sound"),
                    string(values, "potion"),
                    string(values, "damage-kind"),
                    string(values, "text"));

            SkillMechanic.Params mechanicParams = new SkillMechanic.Params(
                    number(values, "amount", 1.0),
                    text,
                    number(values, "spread", 0.0));

            long delay = (long) Math.max(0, number(values, "delay", 0));
            return new Step(delay, targeter, targetParams, mechanic, mechanicParams);
        }

        private static String string(java.util.Map<String, Object> values, String key) {
            Object value = values.get(key);
            return value == null ? "" : String.valueOf(value);
        }

        private static double number(java.util.Map<String, Object> values, String key, double fallback) {
            Object value = values.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        private static String firstNonBlank(String... candidates) {
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return "";
        }
    }
}
