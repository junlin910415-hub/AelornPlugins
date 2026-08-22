package tw.linsy.aelorn.worlds;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a landing spot a player can actually stand in.
 *
 * A configured spawn point drifts out of date as builders edit the world — the
 * coordinate ends up inside a wall, over a lava pool, or floating. Rather than
 * dropping the player there, this scans outward for the nearest position that
 * satisfies three conditions: two blocks of clear space for the body and head, a
 * solid floor, and no hazard listed in {@code transfer.safe-landing}.
 *
 * <p>Which blocks count as hazards is configuration, not code, so a server with
 * custom damaging blocks can extend the lists without a rebuild.
 *
 * <p>All methods read blocks, so they must run on the region thread owning the
 * location — i.e. after {@code getChunkAtAsync} has completed.
 */
final class SafeSpawnLocator {

    private final GlobalSettings.SafeLandingOptions options;

    SafeSpawnLocator(GlobalSettings.SafeLandingOptions options) {
        this.options = options;
    }

    /**
     * Returns a safe location at or near {@code origin}, preserving the original
     * yaw/pitch, or {@code null} if nothing suitable was found in range.
     */
    @Nullable Location findSafeLanding(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        Block safe = search(world.getBlockAt(origin));
        if (safe == null) {
            return null;
        }
        // Centre the player on the block and keep the configured facing.
        return new Location(world, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
            origin.getYaw(), origin.getPitch());
    }

    boolean isSafe(Location location) {
        World world = location.getWorld();
        return world != null && isSafeBlock(world.getBlockAt(location));
    }

    /**
     * Expanding search: the exact block first, then each vertical offset in
     * increasing distance (below before above, since falling a little is safer
     * than being teleported into a ceiling), and within each layer a growing
     * square ring around the origin column.
     */
    private @Nullable Block search(Block start) {
        if (isSafeBlock(start)) {
            return start;
        }
        int horizontalRadius = options.horizontalRadius();
        int verticalRadius = options.verticalRadius();
        for (int distance = 1; distance <= Math.max(horizontalRadius, verticalRadius); distance++) {
            if (distance <= verticalRadius) {
                Block below = start.getRelative(0, -distance, 0);
                if (isSafeBlock(below)) {
                    return below;
                }
                Block above = start.getRelative(0, distance, 0);
                if (isSafeBlock(above)) {
                    return above;
                }
            }
            if (distance <= horizontalRadius) {
                Block ring = searchRing(start, distance, Math.min(distance, verticalRadius));
                if (ring != null) {
                    return ring;
                }
            }
        }
        return null;
    }

    /** Walks the perimeter of a square ring at radius {@code r}, sampling each vertical offset. */
    private @Nullable Block searchRing(Block centre, int r, int verticalRadius) {
        for (int offset = -r; offset <= r; offset++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                Block candidate = firstSafe(centre, dy,
                    new int[][]{{offset, -r}, {offset, r}, {-r, offset}, {r, offset}});
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private @Nullable Block firstSafe(Block centre, int dy, int[][] offsets) {
        for (int[] offset : offsets) {
            Block candidate = centre.getRelative(offset[0], dy, offset[1]);
            if (isSafeBlock(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** {@code block} is where the player's feet go. */
    private boolean isSafeBlock(Block block) {
        World world = block.getWorld();
        if (block.getY() <= world.getMinHeight() || block.getY() >= world.getMaxHeight() - 1) {
            return false;
        }
        return !blocksBody(block)
            && !blocksBody(block.getRelative(0, 1, 0))
            && isStandableFloor(block.getRelative(0, -1, 0));
    }

    /** Solid blocks suffocate; the configured hazard list covers fire, lava and friends. */
    private boolean blocksBody(Block block) {
        Material type = block.getType();
        return type.isSolid() || options.unsafeBody().contains(type);
    }

    private boolean isStandableFloor(Block block) {
        Material type = block.getType();
        if (options.unsafeFloor().contains(type) || !type.isSolid()) {
            return false;
        }
        // A solid floor under two blocks of water is still a drowning trap.
        return !options.deepWaterUnsafe() || !isSubmerged(block.getRelative(0, 1, 0));
    }

    private static boolean isSubmerged(Block block) {
        return block.getType() == Material.WATER
            && block.getRelative(0, 1, 0).getType() == Material.WATER;
    }
}
