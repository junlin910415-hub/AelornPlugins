package tw.linsy.aelorn.worlds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Chunk-level observation and cleanup.
 *
 * Two independent parts:
 * <ul>
 *   <li><b>Metrics</b> — counts chunk loads/unloads per world and warns when a
 *       world genuinely thrashes chunks. Purely observational.</li>
 *   <li><b>Item cap</b> (opt-in, default off) — bounds the number of plain dropped
 *       items per chunk. Named items and items carrying persistent data (MMOItems,
 *       Nexo, AeloriaHUD, MythicCore gear) are never touched.</li>
 * </ul>
 *
 * <h2>Why the churn warning counts repeats rather than loads</h2>
 * The previous version warned on raw load count per window, which made it fire on
 * ordinary play: one player joining at view distance 10 loads about 441 chunks in a
 * few seconds, and pre-generation or a long elytra flight loads far more. Every one
 * of those is a chunk being loaded <em>once</em>, which costs exactly what it should.
 *
 * <p>What actually hurts is the same chunk cycling load → unload → load repeatedly:
 * a teleport loop, two chunk loaders pulling against each other, a spawn-chunk radius
 * fighting a border. So the window now counts, per chunk, how many times it loaded,
 * and the warning triggers on <b>redundant</b> loads — every load after a chunk's
 * first in the window. Exploration and joins produce zero of those no matter how many
 * chunks they touch; a thrash loop produces hundreds. The report also names the worst
 * offending chunk, which is normally enough to find the cause without further digging.
 *
 * <p>Every handler runs on the region thread owning the chunk, so chunk and entity
 * access here needs no extra scheduling.
 */
final class ChunkOptimizer implements Listener {

    private final AelornWorldsPlugin plugin;
    private final Map<UUID, WorldChunkMetrics> metrics = new ConcurrentHashMap<>();

    /** Cheap gate so the item handler costs one volatile read when the feature is off. */
    private volatile boolean itemCapActive;

    /** Safety valve: stop tracking new chunks once a world's counters reach this. */
    private volatile int maxTrackedChunks = 20_000;

    ChunkOptimizer(AelornWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Recomputed on every (re)load; clears counters that referenced the old config. */
    void reconfigure(WorldRegistry registry, GlobalSettings globals) {
        this.maxTrackedChunks = globals.chunk().maxTrackedChunks();
        this.itemCapActive = registry.anyItemCap();
        for (WorldChunkMetrics worldMetrics : metrics.values()) {
            worldMetrics.itemCounts.clear();
        }
    }

    // ChunkLoadEvent is not Cancellable, so ignoreCancelled would be meaningless here.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        GlobalSettings.ChunkOptions options = plugin.globals().chunk();
        if (!options.metricsEnabled()) {
            return;
        }
        World world = event.getWorld();
        WorldChunkMetrics worldMetrics = metricsFor(world);
        worldMetrics.loads.increment();
        if (event.isNewChunk()) {
            worldMetrics.generated.increment();
            // A freshly generated chunk cannot be a repeat by definition, and counting
            // it would make pre-generation look like thrash.
            worldMetrics.windowLoads.incrementAndGet();
        } else {
            Chunk chunk = event.getChunk();
            worldMetrics.observeLoad(packKey(chunk.getX(), chunk.getZ()), maxTrackedChunks);
        }
        closeWindowIfDue(world, worldMetrics, options, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        WorldChunkMetrics worldMetrics = metrics.get(event.getWorld().getUID());
        if (worldMetrics == null) {
            return;
        }
        if (plugin.globals().chunk().metricsEnabled()) {
            worldMetrics.unloads.increment();
        }
        // Always prune, even with metrics off, so counters cannot leak.
        Chunk chunk = event.getChunk();
        worldMetrics.itemCounts.remove(packKey(chunk.getX(), chunk.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!itemCapActive) {
            return;
        }
        Item item = event.getEntity();
        World world = item.getWorld();
        WorldProfile profile = plugin.registry().byWorld(world);
        if (profile == null || !profile.chunk().itemCapEnabled()) {
            return;
        }
        int cap = profile.chunk().maxItemsPerChunk();
        if (cap <= 0) {
            return;
        }

        Chunk chunk = item.getChunk();
        WorldChunkMetrics worldMetrics = metricsFor(world);
        long key = packKey(chunk.getX(), chunk.getZ());
        Map<Long, ItemCounter> counts = worldMetrics.itemCounts;
        ItemCounter counter = counts.get(key);
        if (counter == null) {
            if (counts.size() >= maxTrackedChunks) {
                // Refuse to grow rather than clearing: a full clear would drop every
                // world's accounting at once and make every chunk rescan on its next
                // item spawn, which is the opposite of what the valve is for.
                return;
            }
            counter = counts.computeIfAbsent(key, ignored -> new ItemCounter());
        }
        if (counter.count.incrementAndGet() < cap) {
            return;
        }
        // The estimate says we may be over the cap. Confirming means a full entity
        // scan of the chunk, so a chunk parked at its cap — a grinder, a mob farm —
        // must not pay for one on every single drop.
        long now = System.currentTimeMillis();
        if (!counter.claimScan(now, plugin.globals().chunk().itemCapRescanCooldownMillis())) {
            return;
        }
        enforceItemCap(chunk, cap, counter, worldMetrics, plugin.globals().chunk());
    }

    private static void enforceItemCap(Chunk chunk, int cap, ItemCounter counter,
                                       WorldChunkMetrics worldMetrics,
                                       GlobalSettings.ChunkOptions options) {
        List<Item> cullable = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item item && !isProtected(item, options)) {
                cullable.add(item);
            }
        }
        counter.count.set(cullable.size());
        if (cullable.size() <= cap) {
            return;
        }
        // Oldest first: those are closest to despawning anyway.
        cullable.sort(Comparator.comparingInt(Entity::getTicksLived).reversed());
        int removals = cullable.size() - cap;
        for (int index = 0; index < removals; index++) {
            cullable.get(index).remove();
        }
        counter.count.set(cap);
        worldMetrics.itemsCulled.add(removals);
    }

    private static boolean isProtected(Item item, GlobalSettings.ChunkOptions options) {
        if (options.protectNamed() && item.customName() != null) {
            return true;
        }
        ItemStack stack = item.getItemStack();
        if (!stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (options.protectNamed() && meta.hasDisplayName()) {
            return true;
        }
        return options.protectCustomData() && !meta.getPersistentDataContainer().getKeys().isEmpty();
    }

    /**
     * Closes the measurement window when it is due and reports if the world thrashed.
     *
     * <p>Exactly one thread closes a given window (the compare-and-set decides which);
     * everything else keeps counting straight into the next one.
     */
    private void closeWindowIfDue(World world, WorldChunkMetrics worldMetrics,
                                  GlobalSettings.ChunkOptions options, long now) {
        long windowStart = worldMetrics.windowStart.get();
        if (now - windowStart < options.churnWindowMillis()) {
            return;
        }
        if (!worldMetrics.windowStart.compareAndSet(windowStart, now)) {
            return;
        }
        WindowResult result = worldMetrics.closeWindow();
        worldMetrics.peakRedundantPerWindow =
            Math.max(worldMetrics.peakRedundantPerWindow, result.redundant());

        if (result.redundant() < options.churnWarnThreshold()) {
            return;
        }
        // Boot and a fresh world load both replay a large area at once. That is a
        // one-off cost, not thrash, and warning about it trains admins to ignore this.
        if (now - worldMetrics.observingSince < options.churnStartupGraceMillis()) {
            return;
        }
        long lastWarn = worldMetrics.lastWarnAt.get();
        if (now - lastWarn < options.churnWarnCooldownMillis()
            || !worldMetrics.lastWarnAt.compareAndSet(lastWarn, now)) {
            return;
        }
        warn(world, options, result, now - windowStart);
    }

    private void warn(World world, GlobalSettings.ChunkOptions options, WindowResult result,
                      long elapsedMillis) {
        StringBuilder message = new StringBuilder()
            .append("世界 ").append(world.getName())
            .append(" 在 ").append(elapsedMillis / 1000L).append(" 秒內重複載入區塊 ")
            .append(result.redundant()).append(" 次（門檻 ").append(options.churnWarnThreshold())
            .append("；同期共載入 ").append(result.loads())
            .append(" 個區塊，其中 ").append(result.repeatedChunks()).append(" 個載入超過一次）。");
        if (result.worstCount() > 1) {
            message.append("最頻繁的是區塊 ").append(result.worstX()).append(',').append(result.worstZ())
                .append("（").append(result.worstCount()).append(" 次，方塊座標約 ")
                .append(result.worstX() << 4).append(',').append(result.worstZ() << 4).append("）。");
        }
        message.append("單純探索、預生成或玩家上線只會讓每個區塊各載入一次，不會觸發這則警告；"
            + "會重複的通常是傳送迴圈、互相拉扯的區塊載入器，或 spawn-chunk-radius 與世界邊界打架。");
        plugin.getLogger().warning(message.toString());
    }

    private WorldChunkMetrics metricsFor(World world) {
        return metrics.computeIfAbsent(world.getUID(), ignored -> new WorldChunkMetrics());
    }

    /**
     * Restarts a world's grace period. Called when a world loads, because the replay
     * of its spawn area is the same one-off burst as a server boot.
     */
    void noteWorldLoaded(World world) {
        metricsFor(world).observingSince = System.currentTimeMillis();
    }

    /** Drops everything held for a world that is no longer loaded. */
    void forgetWorld(UUID worldId) {
        metrics.remove(worldId);
    }

    /** Read-only view for {@code /aw chunks}. */
    Snapshot snapshot(World world) {
        WorldChunkMetrics worldMetrics = metrics.get(world.getUID());
        if (worldMetrics == null) {
            return Snapshot.EMPTY;
        }
        return new Snapshot(worldMetrics.loads.sum(), worldMetrics.unloads.sum(),
            worldMetrics.generated.sum(), worldMetrics.itemsCulled.sum(),
            worldMetrics.peakRedundantPerWindow, worldMetrics.currentWindowRedundant());
    }

    void reset() {
        metrics.clear();
    }

    int trackedChunks() {
        int total = 0;
        for (WorldChunkMetrics worldMetrics : metrics.values()) {
            total += worldMetrics.itemCounts.size();
        }
        return total;
    }

    /** Chunk coordinates as one key; avoids allocating on the chunk-load path. */
    private static long packKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }

    record Snapshot(long loads, long unloads, long generated, long itemsCulled,
                    long peakRedundantPerWindow, long currentWindowRedundant) {

        static final Snapshot EMPTY = new Snapshot(0L, 0L, 0L, 0L, 0L, 0L);
    }

    /** What one closed window saw. */
    private record WindowResult(long loads, long redundant, int repeatedChunks,
                                int worstX, int worstZ, int worstCount) {
    }

    /**
     * Per-chunk dropped-item accounting.
     *
     * <p>{@code lastScanAt} is what keeps a chunk sitting at its cap from triggering a
     * full entity scan on every drop; the counter still rises in the meantime, so the
     * next scan after the cooldown sees the true figure.
     */
    private static final class ItemCounter {
        final AtomicInteger count = new AtomicInteger();
        private final AtomicLong lastScanAt = new AtomicLong();

        /** True for exactly one caller per cooldown period. */
        boolean claimScan(long now, long cooldownMillis) {
            long last = lastScanAt.get();
            return now - last >= cooldownMillis && lastScanAt.compareAndSet(last, now);
        }
    }

    private static final class WorldChunkMetrics {
        final LongAdder loads = new LongAdder();
        final LongAdder unloads = new LongAdder();
        final LongAdder generated = new LongAdder();
        final LongAdder itemsCulled = new LongAdder();
        final Map<Long, ItemCounter> itemCounts = new ConcurrentHashMap<>();

        /**
         * How many times each chunk loaded in the current window. Only chunks that
         * were read back from disk are counted; a newly generated chunk cannot repeat.
         */
        final Map<Long, AtomicInteger> windowChunkLoads = new ConcurrentHashMap<>();

        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        final AtomicLong windowLoads = new AtomicLong();
        final AtomicLong lastWarnAt = new AtomicLong();
        volatile long peakRedundantPerWindow;
        volatile long observingSince = System.currentTimeMillis();

        void observeLoad(long key, int trackLimit) {
            windowLoads.incrementAndGet();
            AtomicInteger counter = windowChunkLoads.get(key);
            if (counter == null) {
                if (windowChunkLoads.size() >= trackLimit) {
                    // Already past the tracking budget: the window will report what it
                    // has, which is by definition more than enough to trigger a warning.
                    return;
                }
                counter = windowChunkLoads.computeIfAbsent(key, ignored -> new AtomicInteger());
            }
            counter.incrementAndGet();
        }

        /** Totals the window and resets it. Only the thread that won the CAS calls this. */
        WindowResult closeWindow() {
            long loadCount = windowLoads.getAndSet(0L);
            long redundant = 0L;
            int repeated = 0;
            long worstKey = 0L;
            int worstCount = 0;
            for (Map.Entry<Long, AtomicInteger> entry : windowChunkLoads.entrySet()) {
                int times = entry.getValue().get();
                if (times < 2) {
                    continue;
                }
                repeated++;
                redundant += times - 1;
                if (times > worstCount) {
                    worstCount = times;
                    worstKey = entry.getKey();
                }
            }
            windowChunkLoads.clear();
            return new WindowResult(loadCount, redundant, repeated,
                unpackX(worstKey), unpackZ(worstKey), worstCount);
        }

        /** Redundant loads accumulated so far in the open window, for {@code /aw chunks}. */
        long currentWindowRedundant() {
            long redundant = 0L;
            for (AtomicInteger counter : windowChunkLoads.values()) {
                int times = counter.get();
                if (times > 1) {
                    redundant += times - 1;
                }
            }
            return redundant;
        }
    }
}
