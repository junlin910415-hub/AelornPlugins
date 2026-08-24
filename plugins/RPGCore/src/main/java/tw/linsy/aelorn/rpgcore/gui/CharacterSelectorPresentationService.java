package tw.linsy.aelorn.rpgcore.gui;

import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public final class CharacterSelectorPresentationService {
   private final RpgScheduler scheduler;
   private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap();

   public CharacterSelectorPresentationService(RpgScheduler scheduler) {
      this.scheduler = scheduler;
   }

   public void start(Player player, boolean musicEnabled) {
      this.stop(player);
      AtomicLong frame = new AtomicLong();
      ScheduledTask task = this.scheduler.runEntityAtFixedRate(player, (ignored) -> this.animate(player, musicEnabled, frame.getAndIncrement()), () -> this.tasks.remove(player.getUniqueId()), 1L, 10L);
      if (task != null) {
         this.tasks.put(player.getUniqueId(), task);
      }

   }

   public void stop(Player player) {
      this.scheduler.cancel((ScheduledTask)this.tasks.remove(player.getUniqueId()));
      player.stopSound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MUSIC);
   }

   public void shutdown() {
      var var10000 = this.tasks.values();
      RpgScheduler var10001 = this.scheduler;
      Objects.requireNonNull(var10001);
      var10000.forEach(var10001::cancel);
      this.tasks.clear();
   }

   private void animate(Player player, boolean musicEnabled, long frame) {
      if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof CharacterSelectorHolder)) {
         this.stop(player);
      } else {
         double angle = (double)frame * Math.PI / (double)8.0F;
         player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(Math.cos(angle) * 0.7, (double)1.0F, Math.sin(angle) * 0.7), 1, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F);
         if (musicEnabled && frame % 8L == 0L) {
            float pitch = (float)(0.85 + (double)(frame / 8L % 4L) * 0.08);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MUSIC, 0.18F, pitch);
         }

      }
   }
}
