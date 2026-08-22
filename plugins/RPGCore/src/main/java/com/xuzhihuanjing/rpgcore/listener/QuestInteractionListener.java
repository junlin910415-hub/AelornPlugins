package com.xuzhihuanjing.rpgcore.listener;

import com.xuzhihuanjing.rpgcore.dialogue.DialogueService;
import com.xuzhihuanjing.rpgcore.integration.mmoitems.MmoItemsBridge;
import com.xuzhihuanjing.rpgcore.monster.MythicMobsBridge;
import com.xuzhihuanjing.rpgcore.quest.QuestService;
import java.util.Objects;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class QuestInteractionListener implements Listener {
   private final QuestService questService;
   private final MmoItemsBridge mmoItems;
   private final MythicMobsBridge mythicMobs;
   private final DialogueService dialogueService;

   public QuestInteractionListener(QuestService questService, MmoItemsBridge mmoItems, MythicMobsBridge mythicMobs, DialogueService dialogueService) {
      this.questService = (QuestService)Objects.requireNonNull(questService, "questService");
      this.mmoItems = (MmoItemsBridge)Objects.requireNonNull(mmoItems, "mmoItems");
      this.mythicMobs = (MythicMobsBridge)Objects.requireNonNull(mythicMobs, "mythicMobs");
      this.dialogueService = (DialogueService)Objects.requireNonNull(dialogueService, "dialogueService");
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onPickup(EntityPickupItemEvent event) {
      LivingEntity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         this.mmoItems.inspect(event.getItem().getItemStack()).ifPresent((identity) -> this.questService.recordItemPickup(player, identity.type(), identity.id(), event.getItem().getItemStack().getAmount()));
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = false
   )
   public void onNpcInteract(PlayerInteractEntityEvent event) {
      if (event.getHand() == EquipmentSlot.HAND) {
         this.mythicMobs.mythicMobId(event.getRightClicked()).ifPresent((id) -> {
            // 交付先於對話:玩家帶著任務物品來找 NPC 時,應該先收下東西
            this.questService.recordDelivery(event.getPlayer(), id);
            if (!this.dialogueService.startForNpc(event.getPlayer(), id)) {
               this.questService.recordNpcInteraction(event.getPlayer(), id);
            }
         });
      }
   }
}
