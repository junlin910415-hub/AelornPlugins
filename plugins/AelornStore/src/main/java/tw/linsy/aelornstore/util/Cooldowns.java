package tw.linsy.aelornstore.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player click and command throttling.
 *
 * This is not politeness — it is the first line of defence against a
 * double-submitted purchase. The database transaction is what actually makes a
 * purchase safe, but rejecting the second click here means the player never sees
 * two confirmation screens for one item.
 *
 * <p>Stale entries are swept opportunistically rather than on a timer, so an
 * unbounded player population cannot grow the map forever on a long-running
 * server.
 */
public final class Cooldowns {

    private static final int SWEEP_THRESHOLD = 256;
    private static final long SWEEP_INTERVAL_MILLIS = 60_000L;

    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private volatile long lastSweep;

    /**
     * Records an attempt and reports whether it is allowed.
     * A window of zero disables throttling entirely.
     */
    public boolean tryUse(UUID playerId, long windowMillis) {
        long now = System.currentTimeMillis();
        maybeSweep(now, windowMillis);
        if (windowMillis <= 0) {
            return true;
        }
        Long previous = lastUse.get(playerId);
        if (previous != null && now - previous < windowMillis) {
            return false;
        }
        lastUse.put(playerId, now);
        return true;
    }

    public void clear(UUID playerId) {
        lastUse.remove(playerId);
    }

    private void maybeSweep(long now, long windowMillis) {
        if (lastUse.size() < SWEEP_THRESHOLD || now - lastSweep < SWEEP_INTERVAL_MILLIS) {
            return;
        }
        lastSweep = now;
        long cutoff = now - Math.max(windowMillis, SWEEP_INTERVAL_MILLIS);
        lastUse.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
