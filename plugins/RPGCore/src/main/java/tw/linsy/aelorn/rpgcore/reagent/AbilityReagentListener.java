package tw.linsy.aelorn.rpgcore.reagent;

import tw.linsy.aelorn.rpgcore.api.event.RpgAbilityCastEvent;
import java.util.Map;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 把資源消耗接進技能施放。
 *
 * <p>刻意<b>不</b>去改動 {@code AbilityCastService}——那條路徑同時管著冷卻、
 * 法力、事件與執行，動它風險高且沒有必要。
 * {@code RpgAbilityCastEvent} 本來就是可取消的擴充點，
 * 而且核心在事件被取消時<b>會自動退還法力</b>，
 * 因此只要在這裡攔下來，就能得到完全正確的「全有或全無」語意。</p>
 *
 * <h2>扣除順序</h2>
 * <p>「全有或全無」的結算由 {@link ReagentCostGate} 負責，
 * 與武器技能共用同一份實作 —— 兩條路徑對「資源不夠時該怎麼辦」
 * 不該有兩種答案。</p>
 */
public final class AbilityReagentListener implements Listener {

    private final ReagentCostGate gate;
    private final AbilityReagentCosts costs;
    private final BiConsumer<Player, String> notifier;

    /**
     * @param reagents 資源服務
     * @param costs 技能消耗表
     * @param notifier 提示玩家的方式；可為 {@code null} 代表不提示
     */
    public AbilityReagentListener(ReagentService reagents,
                                  AbilityReagentCosts costs,
                                  BiConsumer<Player, String> notifier) {
        this.gate = new ReagentCostGate(reagents);
        this.costs = costs;
        this.notifier = notifier == null ? (player, message) -> { } : notifier;
    }

    /**
     * 在技能實際執行前結算額外資源。
     *
     * <p>用 {@code NORMAL} 優先度：晚於可能取消施放的權限／區域檢查，
     * 早於只做紀錄的 {@code MONITOR} 監聽器。已被其他插件取消的施放直接跳過，
     * 否則會扣了資源卻沒放技能。</p>
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCast(RpgAbilityCastEvent event) {
        if (costs.isEmpty()) {
            return;
        }
        Map<String, Double> required = costs.forAbility(event.ability().id());
        if (required.isEmpty()) {
            return;
        }

        Player player = event.player();
        String problem = gate.charge(player, event.character().classId(), required);
        if (problem != null) {
            event.setCancelled(true);
            notifier.accept(player, problem);
        }
    }
}
