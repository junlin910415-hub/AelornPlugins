package tw.linsy.aelorn.worlds;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;

/**
 * Counts what a world is actually carrying: entities per spawn category, dropped
 * items, tile entities and loaded chunks.
 *
 * Paper exposes {@code World#getEntityCount()} and friends, but those read shared
 * server state that Folia owns per region. This walks the loaded chunks through the
 * core's {@code Schedulers#forEachLoadedChunk} instead — slower to return, but
 * correct on Folia and accurate to the moment each region was visited.
 */
final class WorldHealthService {

    private final AelornWorldsPlugin plugin;

    WorldHealthService(AelornWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs a census and hands the result to {@code onComplete}, which may run on
     * any region thread.
     */
    void census(World world, Consumer<Report> onComplete) {
        Map<SpawnCategory, LongAdder> byCategory = new EnumMap<>(SpawnCategory.class);
        for (SpawnCategory category : SpawnCategory.values()) {
            byCategory.put(category, new LongAdder());
        }
        LongAdder entities = new LongAdder();
        LongAdder items = new LongAdder();
        LongAdder tileEntities = new LongAdder();
        LongAdder chunks = new LongAdder();
        LongAdder players = new LongAdder();

        plugin.schedulers().forEachLoadedChunk(world, chunk -> {
            chunks.increment();
            tileEntities.add(countTileEntities(chunk));
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Player) {
                    players.increment();
                    continue;
                }
                entities.increment();
                if (entity instanceof Item) {
                    items.increment();
                }
                byCategory.get(entity.getSpawnCategory()).increment();
            }
        }, () -> onComplete.accept(build(world, byCategory, entities, items, tileEntities, chunks, players)));
    }

    /** {@code false} skips the snapshot copy; we only need the count. */
    private static int countTileEntities(Chunk chunk) {
        return chunk.getTileEntities(false).length;
    }

    private static Report build(World world, Map<SpawnCategory, LongAdder> byCategory,
                                LongAdder entities, LongAdder items, LongAdder tileEntities,
                                LongAdder chunks, LongAdder players) {
        Map<SpawnCategory, Long> counts = new EnumMap<>(SpawnCategory.class);
        byCategory.forEach((category, adder) -> {
            long value = adder.sum();
            if (value > 0L) {
                counts.put(category, value);
            }
        });
        // Unmodifiable rather than Map.copyOf: the EnumMap's declaration order is
        // what makes the /wx health listing stable between runs.
        return new Report(world.getName(), entities.sum(), items.sum(), tileEntities.sum(),
            chunks.sum(), players.sum(), Collections.unmodifiableMap(counts));
    }

    /** Immutable census result; safe to hand between threads. */
    record Report(String worldName, long entities, long items, long tileEntities,
                  long chunks, long players, Map<SpawnCategory, Long> byCategory) {

        /** True when any tracked figure is over its configured warning threshold. */
        boolean isOverThreshold(GlobalSettings.HealthOptions thresholds) {
            return overEntities(thresholds) || overItems(thresholds)
                || overTileEntities(thresholds) || overChunks(thresholds);
        }

        boolean overEntities(GlobalSettings.HealthOptions thresholds) {
            return thresholds.warnEntities() > 0 && entities > thresholds.warnEntities();
        }

        boolean overItems(GlobalSettings.HealthOptions thresholds) {
            return thresholds.warnItems() > 0 && items > thresholds.warnItems();
        }

        boolean overTileEntities(GlobalSettings.HealthOptions thresholds) {
            return thresholds.warnTileEntities() > 0 && tileEntities > thresholds.warnTileEntities();
        }

        boolean overChunks(GlobalSettings.HealthOptions thresholds) {
            return thresholds.warnChunks() > 0 && chunks > thresholds.warnChunks();
        }

        /** Densest measure of load: entities carried per loaded chunk. */
        double entitiesPerChunk() {
            return chunks == 0L ? 0.0D : (double) entities / chunks;
        }
    }
}
