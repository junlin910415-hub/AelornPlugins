package tw.linsy.aelorn.discordbridge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter keyed by sender id.
 *
 * Buckets for senders that have gone quiet are pruned periodically so the map
 * does not grow without bound on a long-lived server.
 */
final class WindowRateLimiter {

    private static final long PRUNE_INTERVAL_MILLIS = 60_000L;
    private static final int PRUNE_MIN_SIZE = 128;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile int limit;
    private volatile long windowMillis;
    private volatile long nextPruneAt;

    WindowRateLimiter(int maxMessages, int windowSeconds) {
        reconfigure(maxMessages, windowSeconds);
    }

    void reconfigure(int maxMessages, int windowSeconds) {
        this.limit = Math.max(1, maxMessages);
        this.windowMillis = Math.max(1L, windowSeconds) * 1000L;
        this.buckets.clear();
    }

    Decision tryAcquire(String key) {
        return tryAcquire(key, System.currentTimeMillis());
    }

    Decision tryAcquire(String key, long now) {
        pruneIfDue(now);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        synchronized (bucket.timestamps) {
            bucket.lastAccess = now;
            long windowStart = now - windowMillis;
            Deque<Long> timestamps = bucket.timestamps;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= limit) {
                long retryAfter = Math.max(1L, timestamps.peekFirst() + windowMillis - now);
                return new Decision(false, retryAfter);
            }
            timestamps.addLast(now);
            return new Decision(true, 0L);
        }
    }

    int trackedKeys() {
        return buckets.size();
    }

    private void pruneIfDue(long now) {
        if (now < nextPruneAt || buckets.size() < PRUNE_MIN_SIZE) {
            return;
        }
        nextPruneAt = now + PRUNE_INTERVAL_MILLIS;
        long staleBefore = now - Math.max(windowMillis * 2, PRUNE_INTERVAL_MILLIS);
        buckets.values().removeIf(bucket -> bucket.lastAccess < staleBefore);
    }

    record Decision(boolean allowed, long retryAfterMillis) {
        long retryAfterSeconds() {
            return Math.max(1L, (retryAfterMillis + 999L) / 1000L);
        }
    }

    private static final class Bucket {
        private final Deque<Long> timestamps = new ArrayDeque<>();
        private volatile long lastAccess = System.currentTimeMillis();
    }
}
