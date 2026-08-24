package tw.linsy.aelorn.rpgcore.aura;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 增益減益的屬性安全閘 —— 決定狀態效果最多能把數值推到哪裡。
 *
 * <h2>為什麼要有這一層</h2>
 * <p>疊層是會失控的。四層碎甲加上兩層灼燒可以把防禦推到負值，
 * 三層遲滯能讓玩家完全動不了，堆滿爆擊裝再吃一個增益就變成 100% 必爆。
 * 這些都不是「數值很高」的問題，而是<b>遊戲直接壞掉</b>。</p>
 *
 * <p>因此每一項屬性都要有下限與上限。重點是這些數字必須由企劃調整，
 * 而不是埋在程式碼裡——平衡一旦要動，不該每次都得重新編譯部署。</p>
 *
 * <h2>設定寫法（{@code auras.yml} 的 {@code limits} 區段）</h2>
 * <pre>
 * limits:
 *   minimum-health: 1.0            # 生命上限的下限，避免減益把血條歸零
 *   minimum-mana: 1.0
 *   minimum-speed: 20.0            # 移動速度下限，避免疊滿減速後完全不能動
 *   maximum-speed: 400.0           # 移動速度上限，避免加速到穿牆
 *   maximum-critical-chance: 0.95  # 爆擊率上限（0~1）
 *   maximum-dodge-chance: 0.90     # 閃避率上限（0~1）
 *   maximum-spell-cost-reduction: 0.90
 *   maximum-attack-power: 0.0      # 0 代表不設上限
 *   maximum-defense: 0.0
 * </pre>
 *
 * <p>上限填 {@code 0} 或負數一律視為「不設限」，讓不想管的欄位可以直接省略。</p>
 *
 * @param minimumHealth 生命上限的下限
 * @param minimumMana 法力上限的下限
 * @param minimumSpeed 移動速度下限
 * @param maximumSpeed 移動速度上限；0 代表不設限
 * @param maximumCriticalChance 爆擊率上限（0~1）
 * @param maximumDodgeChance 閃避率上限（0~1）
 * @param maximumSpellCostReduction 法力消耗減免上限（0~1）
 * @param maximumAttackPower 攻擊力上限；0 代表不設限
 * @param maximumDefense 防禦力上限；0 代表不設限
 */
public record AuraLimits(
        double minimumHealth,
        double minimumMana,
        double minimumSpeed,
        double maximumSpeed,
        double maximumCriticalChance,
        double maximumDodgeChance,
        double maximumSpellCostReduction,
        double maximumAttackPower,
        double maximumDefense) {

    /**
     * 保守的預設值 —— 與設定檔缺漏時的行為一致。
     *
     * <p>這些數字只是「不讓遊戲壞掉」的底線，不是平衡建議值。
     * 真正的平衡請在 {@code auras.yml} 調整。</p>
     */
    public static final AuraLimits DEFAULTS =
            new AuraLimits(1.0, 1.0, 20.0, 0.0, 0.95, 0.90, 0.90, 0.0, 0.0);

    public AuraLimits {
        minimumHealth = atLeast(minimumHealth, 0);
        minimumMana = atLeast(minimumMana, 0);
        minimumSpeed = atLeast(minimumSpeed, 0);
        maximumSpeed = atLeast(maximumSpeed, 0);
        maximumCriticalChance = ratio(maximumCriticalChance, 0.95);
        maximumDodgeChance = ratio(maximumDodgeChance, 0.90);
        maximumSpellCostReduction = ratio(maximumSpellCostReduction, 0.90);
        maximumAttackPower = atLeast(maximumAttackPower, 0);
        maximumDefense = atLeast(maximumDefense, 0);
    }

    /** 從 {@code auras.yml} 的 {@code limits} 區段載入；缺漏時採用預設值。 */
    public static AuraLimits from(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        return new AuraLimits(
                section.getDouble("minimum-health", DEFAULTS.minimumHealth()),
                section.getDouble("minimum-mana", DEFAULTS.minimumMana()),
                section.getDouble("minimum-speed", DEFAULTS.minimumSpeed()),
                section.getDouble("maximum-speed", DEFAULTS.maximumSpeed()),
                section.getDouble("maximum-critical-chance", DEFAULTS.maximumCriticalChance()),
                section.getDouble("maximum-dodge-chance", DEFAULTS.maximumDodgeChance()),
                section.getDouble("maximum-spell-cost-reduction", DEFAULTS.maximumSpellCostReduction()),
                section.getDouble("maximum-attack-power", DEFAULTS.maximumAttackPower()),
                section.getDouble("maximum-defense", DEFAULTS.maximumDefense()));
    }

    /**
     * 夾在下限與上限之間。
     *
     * @param upper 上限；{@code 0} 或負數代表不設限
     */
    static double clamp(double value, double lower, double upper) {
        if (!Double.isFinite(value)) {
            return lower;
        }
        double result = Math.max(lower, value);
        return upper > 0 ? Math.min(upper, result) : result;
    }

    private static double atLeast(double value, double floor) {
        return Double.isFinite(value) ? Math.max(floor, value) : floor;
    }

    private static double ratio(double value, double fallback) {
        if (!Double.isFinite(value) || value < 0) {
            return fallback;
        }
        return Math.min(1.0, value);
    }
}
