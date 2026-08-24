package tw.linsy.aelorn.rpgcore.skill;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 施放代價的轉接 —— 讓技能套件不必直接依賴資源服務。
 *
 * <p>技能只知道「這個技能要花掉哪些東西」，至於那些東西存在哪裡、
 * 怎麼扣、扣不動時該說什麼，全部由實作端決定。
 * {@code skills.yml} 因此可以寫任何 {@code reagents.yml} 有的資源，
 * 新增一種資源不必回頭改技能程式碼。</p>
 *
 * <h2>為什麼要分成檢查與扣除兩步</h2>
 * <p>有吟唱的技能會先讀條再執行。若在讀條前就扣款，被打斷的玩家等於白付；
 * 若到執行才第一次檢查，玩家會先看到讀條跑完才被告知資源不夠。
 * 因此：讀條前用 {@link #check} 問「付得起嗎」，
 * 真正要放技能的那一刻才用 {@link #charge} 扣款。</p>
 */
public interface SkillCostGate {

    /**
     * 檢查付不付得起，不扣除任何東西。
     *
     * @param costs 資源代號對應消耗量
     * @return 付得起回傳 {@code null}；否則回傳給玩家看的原因（含 &amp; 色碼）
     */
    String check(Player caster, Map<String, Double> costs);

    /**
     * 實際扣除，語意為「全有或全無」：任何一項扣不動，已扣的都要退回去。
     *
     * @param costs 資源代號對應消耗量
     * @return 扣除成功回傳 {@code null}；否則回傳給玩家看的原因（含 &amp; 色碼）
     */
    String charge(Player caster, Map<String, Double> costs);

    /**
     * 組出消耗的簡短描述，供施放提示與選單使用。
     *
     * @return 例如 {@code &b✦ 24}；沒有消耗時回傳空字串
     */
    String describe(Map<String, Double> costs);
}
