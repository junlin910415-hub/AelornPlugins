package com.xuzhihuanjing.rpgcore.reagent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 技能的額外資源消耗表。
 *
 * <p>既有的 {@code AbilityDefinition} 只認得法力一種消耗，
 * 要讓戰士花怒氣、盜賊花精力與連擊點，最省事的做法不是改動
 * 那個被十幾處引用的 record，而是另外讀一張表。</p>
 *
 * <p>設定寫在 {@code abilities.yml} 既有技能區段底下，
 * 沒寫的技能維持原本只扣法力的行為，完全向下相容：</p>
 *
 * <pre>
 * abilities:
 *   vanguard_charge:
 *     class: vanguard
 *     mana: 18.0
 *     reagent-costs:        # ← 新增，可省略
 *       rage: 25            # 額外消耗 25 點怒氣
 *
 *   rogue_eviscerate:
 *     class: rogue
 *     mana: 0.0
 *     reagent-costs:
 *       stamina: 30
 *       combo: 3            # 消耗三點連擊點
 * </pre>
 */
public final class AbilityReagentCosts {

    /** 單一技能單一資源的消耗上限，防止設定檔手滑寫出無法施放的技能。 */
    private static final double COST_LIMIT = 100_000.0;

    private final Map<String, Map<String, Double>> costs = new LinkedHashMap<>();

    /**
     * 從 {@code abilities.yml} 的根區段載入。
     *
     * @param root 設定檔根區段；為 {@code null} 時視為沒有任何額外消耗
     * @return 有額外消耗的技能數量
     */
    public int load(ConfigurationSection root) {
        costs.clear();
        if (root == null) {
            return 0;
        }
        ConfigurationSection abilities = root.getConfigurationSection("abilities");
        if (abilities == null) {
            return 0;
        }

        for (String abilityId : abilities.getKeys(false)) {
            ConfigurationSection ability = abilities.getConfigurationSection(abilityId);
            if (ability == null) {
                continue;
            }
            ConfigurationSection section = ability.getConfigurationSection("reagent-costs");
            if (section == null) {
                section = ability.getConfigurationSection("reagent_costs");
            }
            if (section == null) {
                continue;
            }

            Map<String, Double> perAbility = new LinkedHashMap<>();
            for (String reagentId : section.getKeys(false)) {
                double amount = section.getDouble(reagentId, 0);
                if (Double.isFinite(amount) && amount > 0) {
                    perAbility.put(normalize(reagentId), Math.min(COST_LIMIT, amount));
                }
            }
            if (!perAbility.isEmpty()) {
                costs.put(normalize(abilityId), Map.copyOf(perAbility));
            }
        }
        return costs.size();
    }

    /**
     * 取得某技能的額外資源消耗。
     *
     * @return 資源代號對應消耗量；沒有設定時回傳空 Map
     */
    public Map<String, Double> forAbility(String abilityId) {
        return costs.getOrDefault(normalize(abilityId), Map.of());
    }

    /** 是否有任何技能設定了額外消耗。 */
    public boolean isEmpty() {
        return costs.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
