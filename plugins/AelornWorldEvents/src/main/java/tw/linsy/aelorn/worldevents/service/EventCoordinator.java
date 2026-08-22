package tw.linsy.aelorn.worldevents.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tw.linsy.aelorn.worldevents.config.EventSettings;
import tw.linsy.aelorn.worldevents.model.EventNode;

/**
 * Decides whether a node may fire right now, and remembers that it did.
 *
 * This is the one piece of genuinely shared state in the plugin: proximity
 * checks arrive from many region threads at once, all asking about the same
 * global and per-region limits. Activation is rare and the critical section is
 * tiny, so a single lock is the right trade — the alternative is a lock-free
 * scheme whose correctness nobody can verify.
 */
public final class EventCoordinator {

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Object activationLock = new Object();

    private volatile EventSettings settings;

    public EventCoordinator(EventSettings settings) {
        this.settings = settings;
    }

    /** Keeps live state across a reload; only the limits change. */
    public void reconfigure(EventSettings settings) {
        this.settings = settings;
    }

    public Result tryActivate(EventNode node, long nowMillis) {
        EventSettings current = settings;
        synchronized (activationLock) {
            State state = states.computeIfAbsent(node.id(), ignored -> new State());
            if (state.activeUntil > nowMillis) {
                return Result.ALREADY_ACTIVE;
            }
            if (state.cooldownUntil > nowMillis) {
                return Result.COOLDOWN;
            }

            // One pass counts both limits; the previous version walked the map
            // twice and re-looked-up every key it had just iterated.
            int globalActive = 0;
            int regionActive = 0;
            for (State other : states.values()) {
                if (other.activeUntil <= nowMillis) {
                    continue;
                }
                globalActive++;
                if (node.region().equals(other.region)) {
                    regionActive++;
                }
            }
            if (globalActive >= current.maximumActiveGlobal()) {
                return Result.GLOBAL_LIMIT;
            }
            if (regionActive >= current.maximumActivePerRegion()) {
                return Result.REGION_LIMIT;
            }

            state.region = node.region();
            state.activeUntil = nowMillis + current.activeWindowMillis();
            state.cooldownUntil = nowMillis + node.cooldownMillis();
            state.started++;
            return Result.STARTED;
        }
    }

    /**
     * The encounter never actually started, so release the active slot and cut
     * the cooldown short — a node that failed to dispatch should retry soon
     * rather than sit out its full cooldown.
     */
    public void dispatchFailed(String nodeId, long nowMillis) {
        long retryAfter = nowMillis + settings.dispatchFailureCooldownMillis();
        synchronized (activationLock) {
            State state = states.get(nodeId);
            if (state == null) {
                return;
            }
            state.activeUntil = 0L;
            state.cooldownUntil = Math.min(state.cooldownUntil, retryAfter);
            state.failed++;
        }
    }

    /** Clears expired active windows so the limits reflect reality. */
    public void prune(long nowMillis) {
        synchronized (activationLock) {
            for (State state : states.values()) {
                if (state.activeUntil <= nowMillis) {
                    state.activeUntil = 0L;
                }
            }
        }
    }

    public Snapshot snapshot(long nowMillis) {
        synchronized (activationLock) {
            int active = 0;
            int coolingDown = 0;
            long started = 0L;
            long failed = 0L;
            Map<String, Integer> activeByRegion = new LinkedHashMap<>();
            for (State state : states.values()) {
                if (state.activeUntil > nowMillis) {
                    active++;
                    activeByRegion.merge(state.region, 1, Integer::sum);
                }
                if (state.cooldownUntil > nowMillis) {
                    coolingDown++;
                }
                started += state.started;
                failed += state.failed;
            }
            return new Snapshot(active, coolingDown, started, failed, Map.copyOf(activeByRegion));
        }
    }

    public void reset() {
        synchronized (activationLock) {
            states.clear();
        }
    }

    /** Outcome of an activation attempt; each constant names a key in messages.yml. */
    public enum Result {
        STARTED("event.result.started"),
        ALREADY_ACTIVE("event.result.already-active"),
        COOLDOWN("event.result.cooldown"),
        GLOBAL_LIMIT("event.result.global-limit"),
        REGION_LIMIT("event.result.region-limit");

        private final String messageKey;

        Result(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }

        public boolean started() {
            return this == STARTED;
        }
    }

    public record Snapshot(int active, int coolingDown, long started, long failed,
                           Map<String, Integer> activeByRegion) {
    }

    private static final class State {
        private String region = "";
        private long activeUntil;
        private long cooldownUntil;
        private long started;
        private long failed;
    }
}
