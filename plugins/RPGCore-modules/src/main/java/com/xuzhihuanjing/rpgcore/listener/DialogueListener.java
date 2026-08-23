package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.dialogue.DialogueService;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * 對話演出期間的玩家行為約束。
 *
 * <p>全部事件都在玩家自己的區域執行緒上觸發,與 {@link DialogueService} 的
 * EntityScheduler 是同一條執行緒,所以可以直接讀 session 狀態。
 */
public final class DialogueListener implements Listener {

    private final DialogueService dialogueService;

    public DialogueListener(DialogueService dialogueService) {
        this.dialogueService = dialogueService;
    }

    /**
     * 鎖定移動但**不鎖視角**——玩家仍可轉頭看 NPC,這是 Wynncraft 的演出感覺;
     * 完全凍結會讓人以為卡住了。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!dialogueService.movementLocked(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // 只比對座標,忽略 yaw/pitch
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        Location anchor = dialogueService.anchorOf(event.getPlayer().getUniqueId());
        Location target = anchor == null ? from : anchor;
        // 保留玩家目前的視角方向
        event.setTo(new Location(target.getWorld(), target.getX(), target.getY(), target.getZ(),
            to.getYaw(), to.getPitch()));
    }

    /** 蹲下 = 跳過對話,不需要額外指令或介面。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && dialogueService.skippable(event.getPlayer().getUniqueId())) {
            dialogueService.skip(event.getPlayer().getUniqueId());
        }
    }

    /** 演出期間不該被打斷,否則玩家會在動不了的狀態下被打死。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player
            && dialogueService.movementLocked(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        dialogueService.endSession(event.getPlayer().getUniqueId(), false);
    }
}
