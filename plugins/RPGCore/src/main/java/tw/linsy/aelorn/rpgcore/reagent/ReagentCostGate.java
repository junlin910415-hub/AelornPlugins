package tw.linsy.aelorn.rpgcore.reagent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 資源結算的單一實作 —— 「全有或全無」的扣款語意就只寫在這裡一次。
 *
 * <p>技能路徑有兩條：{@code abilities.yml} 的職業技能走
 * {@link AbilityReagentListener}，{@code skills.yml} 的武器技能走技能引擎。
 * 兩條路徑要的東西完全一樣：先確認每一種資源都夠、全夠才逐一扣、
 * 扣到一半失敗就退回去。這段邏輯抄第二份的那一刻，兩邊就會開始各自長歪，
 * 於是抽成本類別，由兩條路徑共用。</p>
 *
 * <h2>為什麼先檢查再扣</h2>
 * <p>省略檢查直接扣的話，「怒氣夠但精力不夠」的技能會白白吃掉玩家的怒氣
 * 卻沒有放出技能 —— 這是最容易被玩家抓到的那種 bug。</p>
 */
public final class ReagentCostGate {

    /** 浮點數比較的容差；沒有它，剛好等於消耗量的資源會被判定為不足。 */
    private static final double EPSILON = 1.0E-9;

    private final ReagentService reagents;

    public ReagentCostGate(ReagentService reagents) {
        this.reagents = reagents;
    }

    /**
     * 檢查付不付得起，不動用任何資源。
     *
     * @param classId 玩家目前的職業代號，用來判斷這些資源他有沒有
     * @return 付得起回傳 {@code null}；否則回傳給玩家看的原因
     */
    public String check(Player player, String classId, Map<String, Double> costs) {
        for (Map.Entry<String, Double> entry : costs.entrySet()) {
            ReagentType type = reagents.type(entry.getKey());
            if (type == null || !type.appliesTo(classId)) {
                return "&c你的職業無法使用這個技能所需的資源。";
            }
            double available = reagents.value(player, entry.getKey());
            if (available + EPSILON < entry.getValue()) {
                return shortfallMessage(type, available, entry.getValue());
            }
        }
        return null;
    }

    /**
     * 扣除資源；任何一項扣不動，已扣的都會退回去。
     *
     * @return 扣除成功回傳 {@code null}；否則回傳給玩家看的原因
     */
    public String charge(Player player, String classId, Map<String, Double> costs) {
        String problem = check(player, classId, costs);
        if (problem != null) {
            return problem;
        }

        // 回捲清單延後配置：絕大多數技能只消耗一種資源，而且幾乎不會扣到一半失敗，
        // 沒必要每次施放都先開一個註定用不到的 ArrayList。
        List<Map.Entry<String, Double>> spent = null;
        for (Map.Entry<String, Double> entry : costs.entrySet()) {
            ReagentSpendResult result = reagents.spend(player, entry.getKey(), entry.getValue());
            if (result.succeeded()) {
                if (spent == null) {
                    spent = new ArrayList<>(costs.size());
                }
                spent.add(entry);
                continue;
            }
            // 兩次檢查之間資源被別的來源吃掉了，回捲並回報
            if (spent != null) {
                spent.forEach(done -> reagents.restore(player, done.getKey(), done.getValue()));
            }
            return "&c" + result.describe(reagents.type(entry.getKey()));
        }
        return null;
    }

    /**
     * 組出消耗的簡短描述，例如 {@code &b✦ 24 &8+ &c✸ 20}。
     *
     * <p>符號與顏色都取自 {@code reagents.yml}，新增一種資源不必回頭改這裡。</p>
     */
    public String describe(Map<String, Double> costs) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Double> entry : costs.entrySet()) {
            if (!text.isEmpty()) {
                text.append(" &8+ ");
            }
            ReagentType type = reagents.type(entry.getKey());
            String label = type == null ? entry.getKey()
                    : (type.symbol().isBlank() ? type.displayName() : type.symbol());
            text.append(type == null ? "&f" : type.color())
                    .append(label)
                    .append(' ')
                    .append((long) Math.ceil(entry.getValue()));
        }
        return text.toString();
    }

    /** 統一格式的資源不足提示：講清楚還差多少，而不是只說「不夠」。 */
    private String shortfallMessage(ReagentType type, double available, double required) {
        long shortfall = (long) Math.ceil(required - available);
        return type.color() + type.displayName() + "&c不足，還差 &f" + shortfall
                + type.color() + "（目前 " + (long) Math.floor(available)
                + "／需要 " + (long) Math.ceil(required) + "）";
    }
}
