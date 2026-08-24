package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.api.event.RpgAbilityCastEvent;
import tw.linsy.aelorn.rpgcore.integration.mmoitems.MmoItemsBridge;
import tw.linsy.aelorn.rpgcore.quest.QuestService;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * USE_ITEM 與 CAST_ABILITY 兩種目標的觸發來源。
 *
 * <p>DELIVER_ITEM 走 {@link QuestInteractionListener}(需要 NPC 上下文),
 * REACH_LEVEL 由 QuestService 在接受任務與升級時直接檢查。
 */
public final class QuestObjectiveListener implements Listener {

    private final QuestService questService;
    private final MmoItemsBridge mmoItems;

    public QuestObjectiveListener(QuestService questService, MmoItemsBridge mmoItems) {
        this.questService = Objects.requireNonNull(questService, "questService");
        this.mmoItems = Objects.requireNonNull(mmoItems, "mmoItems");
    }

    /**
     * 右鍵使用物品。
     *
     * <p>只認主手,且只認右鍵——左鍵是攻擊,計進來會讓「使用道具」目標被普通戰鬥誤觸。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUseItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getItem() == null) {
            return;
        }
        mmoItems.inspect(event.getItem()).ifPresent(identity ->
            questService.recordItemUse(event.getPlayer(), identity.type(), identity.id()));
    }

    /** 技能施放。RPGCore 自己會在成功施放時觸發這個事件。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAbilityCast(RpgAbilityCastEvent event) {
        questService.recordAbilityCast(event.player(), event.ability().id());
    }
}
