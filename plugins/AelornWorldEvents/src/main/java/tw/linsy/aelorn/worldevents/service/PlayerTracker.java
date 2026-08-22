package tw.linsy.aelorn.worldevents.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;

/**
 * Last known horizontal position of every online player.
 *
 * Folia will not let one thread read another player's {@code getLocation()},
 * so "who is near this point" cannot be answered by walking the online player
 * list. The movement listener publishes each player's position here instead,
 * and broadcasts read from this snapshot.
 *
 * <p>Position and throttle timestamp live in the same record on purpose: the
 * previous version kept two parallel maps and wrote to both on every
 * block-crossing movement, doubling the work on the hottest path in the plugin.
 */
public final class PlayerTracker {

    private final Map<UUID, Position> positions = new ConcurrentHashMap<>();

    /**
     * Records where the player is and reports whether enough time has passed to
     * run another proximity check.
     *
     * @return {@code true} when the caller should perform the check
     */
    public boolean updateAndShouldCheck(UUID playerId, Location location, long nowMillis,
                                        long intervalMillis) {
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        Position previous = positions.get(playerId);
        boolean due = previous == null || nowMillis - previous.checkedAt() >= intervalMillis;
        positions.put(playerId, new Position(world, location.getX(), location.getZ(),
            due ? nowMillis : previous.checkedAt()));
        return due;
    }

    /** Runs {@code action} for every tracked player within {@code radius} of a point. */
    public void forEachWithin(String world, double x, double z, double radius,
                              java.util.function.Consumer<UUID> action) {
        double radiusSquared = radius * radius;
        positions.forEach((playerId, position) -> {
            if (position.world().equals(world) && position.distanceSquared(x, z) <= radiusSquared) {
                action.accept(playerId);
            }
        });
    }

    public void forget(UUID playerId) {
        positions.remove(playerId);
    }

    public void clear() {
        positions.clear();
    }

    public int tracked() {
        return positions.size();
    }

    private record Position(String world, double x, double z, long checkedAt) {

        double distanceSquared(double otherX, double otherZ) {
            double deltaX = x - otherX;
            double deltaZ = z - otherZ;
            return deltaX * deltaX + deltaZ * deltaZ;
        }
    }
}
