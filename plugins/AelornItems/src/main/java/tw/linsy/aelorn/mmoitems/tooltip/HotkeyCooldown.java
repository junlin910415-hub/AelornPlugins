package tw.linsy.aelorn.mmoitems.tooltip;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HotkeyCooldown {
   private final ConcurrentHashMap<UUID, Long> lastAcceptedNanos = new ConcurrentHashMap();
   private final long cooldownNanos;

   public HotkeyCooldown(long var1) {
      this.cooldownNanos = Math.max(0L, var1);
   }

   public boolean claim(UUID var1) {
      return this.claim(var1, System.nanoTime());
   }

   boolean claim(UUID var1, long var2) {
      if (var1 == null) {
         return false;
      } else if (this.cooldownNanos <= 0L) {
         return true;
      } else {
         AtomicBoolean var4 = new AtomicBoolean(false);
         this.lastAcceptedNanos.compute(var1, (var4x, var5) -> {
            if (var5 != null && var2 - var5 < this.cooldownNanos) {
               return var5;
            } else {
               var4.set(true);
               return var2;
            }
         });
         return var4.get();
      }
   }

   public void forget(UUID var1) {
      if (var1 != null) {
         this.lastAcceptedNanos.remove(var1);
      }

   }
}
