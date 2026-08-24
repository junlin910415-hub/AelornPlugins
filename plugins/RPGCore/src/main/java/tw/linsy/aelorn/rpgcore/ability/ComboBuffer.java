package tw.linsy.aelorn.rpgcore.ability;

import tw.linsy.aelorn.rpgcore.domain.ability.InputToken;
import java.util.ArrayList;
import java.util.List;

public final class ComboBuffer {
   private final long timeoutMillis;
   private final List<InputToken> tokens = new ArrayList(3);
   private long lastInputMillis = Long.MIN_VALUE;

   public ComboBuffer(long timeoutMillis) {
      if (timeoutMillis <= 0L) {
         throw new IllegalArgumentException("Combo timeout must be positive");
      } else {
         this.timeoutMillis = timeoutMillis;
      }
   }

   public synchronized List<InputToken> accept(InputToken token, long nowMillis) {
      this.expire(nowMillis);
      if (this.tokens.isEmpty() && token != InputToken.RIGHT) {
         return List.of();
      } else {
         if (this.tokens.size() == 3) {
            this.tokens.clear();
         }

         this.tokens.add(token);
         this.lastInputMillis = nowMillis;
         return this.snapshot();
      }
   }

   public synchronized boolean isPending(long nowMillis) {
      this.expire(nowMillis);
      return !this.tokens.isEmpty();
   }

   public synchronized List<InputToken> snapshot(long nowMillis) {
      this.expire(nowMillis);
      return this.snapshot();
   }

   public synchronized void restartWithRight(long nowMillis) {
      this.tokens.clear();
      this.tokens.add(InputToken.RIGHT);
      this.lastInputMillis = nowMillis;
   }

   public synchronized void clear() {
      this.tokens.clear();
      this.lastInputMillis = Long.MIN_VALUE;
   }

   private void expire(long nowMillis) {
      if (!this.tokens.isEmpty() && nowMillis - this.lastInputMillis > this.timeoutMillis) {
         this.clear();
      }

   }

   private List<InputToken> snapshot() {
      return List.copyOf(this.tokens);
   }
}
