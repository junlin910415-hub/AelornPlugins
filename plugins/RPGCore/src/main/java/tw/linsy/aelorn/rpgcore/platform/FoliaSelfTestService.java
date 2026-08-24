package tw.linsy.aelorn.rpgcore.platform;

import tw.linsy.aelorn.rpgcore.config.MessageBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

public final class FoliaSelfTestService {
   private final RpgScheduler scheduler;
   private final Logger logger;
   private final MessageBundle messages;

   public FoliaSelfTestService(RpgScheduler scheduler, Logger logger, MessageBundle messages) {
      this.scheduler = scheduler;
      this.logger = logger;
      this.messages = messages;
   }

   public void run(CommandSender sender) {
      AtomicBoolean completed = new AtomicBoolean();
      this.scheduler.executeGlobal(() -> {
         World world = (World)Bukkit.getWorlds().stream().findFirst().orElse(null);
         if (world == null) {
            this.complete(sender, completed, false, "找不到可用世界");
         } else {
            Location location = world.getSpawnLocation().clone().add((double)0.5F, (double)2.0F, (double)0.5F);
            this.scheduler.executeRegion(location, () -> {
               ArmorStand probe = (ArmorStand)world.spawn(location, ArmorStand.class, (stand) -> {
                  stand.setVisible(false);
                  stand.setGravity(false);
                  stand.setMarker(true);
                  stand.customName(this.messages.text("<gray>RPGCore Folia Test</gray>"));
               });
               this.scheduler.runEntityLater(probe, () -> {
                  if (!Bukkit.isOwnedByCurrentRegion(probe)) {
                     probe.remove();
                     this.complete(sender, completed, false, "EntityScheduler 未取得實體所有權");
                  } else {
                     probe.setGlowing(true);
                     probe.setHealth(Math.max((double)1.0F, probe.getHealth() - (double)1.0F));
                     this.scheduler.runEntityLater(probe, () -> {
                        probe.remove();
                        this.complete(sender, completed, true, "Global、Region、Entity Scheduler 均通過");
                     }, () -> this.complete(sender, completed, false, "測試實體提前失效"), 2L);
                  }
               }, () -> this.complete(sender, completed, false, "測試實體無法進入 EntityScheduler"), 2L);
            });
         }
      });
      this.scheduler.runGlobalLater(() -> this.complete(sender, completed, false, "排程測試逾時"), 100L);
   }

   private void complete(CommandSender sender, AtomicBoolean completed, boolean success, String detail) {
      if (completed.compareAndSet(false, true)) {
         String result = success ? "PASS" : "FAIL";
         this.logger.info("Folia scheduler self-test " + result + ": " + detail);
         Component message = this.messages.text((success ? "<green>" : "<red>") + "Folia 排程自我測試 " + result + "：</" + (success ? "green>" : "red>") + "<white>" + detail + "</white>");
         if (sender instanceof Player) {
            Player player = (Player)sender;
            this.scheduler.executeEntity(player, () -> player.sendMessage(message), () -> {
            });
         } else {
            sender.sendMessage(message);
         }

      }
   }
}
