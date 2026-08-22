package tw.linsy.aelorn.worldevents.model;

/**
 * One placed world event: where it is, what it summons, and how often.
 *
 * Pure data. Everything that decides *whether* it may fire lives in the
 * coordinator; this record only answers questions about itself.
 *
 * @param cooldownMillis how long after firing before this node may fire again
 * @param activationRadius horizontal distance a player must come within
 */
public record EventNode(
    String id,
    String displayName,
    String region,
    String encounter,
    String world,
    double x,
    double y,
    double z,
    int level,
    long cooldownMillis,
    double activationRadius) {

    /** Squared to keep the proximity check off {@code Math.sqrt} — it runs per movement. */
    public double horizontalDistanceSquared(double otherX, double otherZ) {
        double deltaX = x - otherX;
        double deltaZ = z - otherZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    public boolean isWithinActivationRange(double otherX, double otherZ) {
        return horizontalDistanceSquared(otherX, otherZ) <= activationRadius * activationRadius;
    }
}
