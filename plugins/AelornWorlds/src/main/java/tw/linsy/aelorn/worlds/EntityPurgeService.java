package tw.linsy.aelorn.worlds;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Tameable;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.lib.text.Messages;

/**
 * Removes surplus mobs from a world, on demand or on a schedule.
 *
 * What counts as "surplus" is entirely configuration: the target spawn categories,
 * how many of them may stay per chunk, and which entities are untouchable. The
 * protection defaults cover the things a purge must never eat on an RPG server —
 * named mobs, quest NPCs, and the tagged/PDC entities MythicMobs, Citizens and
 * ModelEngine rely on.
 *
 * <p>Work is spread across region threads by the core's
 * {@code Schedulers#forEachLoadedChunk}, so a purge of a large world never stalls a
 * single thread.
 */
final class EntityPurgeService {

    private final AelornWorldsPlugin plugin;

    /** Last completed auto purge per world, so intervals survive a config reload. */
    private final Map<UUID, Long> lastAutoPurgeAt = new ConcurrentHashMap<>();

    private volatile @Nullable ScheduledTask autoTask;

    /** Period the running task was started with, so a no-op reconfigure can skip. */
    private volatile long checkSeconds;

    EntityPurgeService(AelornWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Purges across regions and reports the total once every chunk has been
     * visited.
     *
     * @param categories categories to remove; empty falls back to the configured defaults
     * @param keepPerChunk how many matching entities may stay in each chunk
     * @param onComplete receives the removed count; may run on any region thread
     */
    void purge(World world, Set<SpawnCategory> categories, int keepPerChunk, IntConsumer onComplete) {
        GlobalSettings globals = plugin.globals();
        Set<SpawnCategory> targets = categories.isEmpty() ? globals.purge().defaultCategories() : categories;
        if (targets.isEmpty()) {
            onComplete.accept(0);
            return;
        }
        GlobalSettings.ProtectOptions protect = globals.purge().protect();
        LongAdder removed = new LongAdder();
        plugin.schedulers().forEachLoadedChunk(world,
            chunk -> removed.add(purgeChunk(chunk, targets, keepPerChunk, protect)),
            () -> onComplete.accept((int) Math.min(Integer.MAX_VALUE, removed.sum())));
    }

    /** Runs on the region thread owning the chunk. */
    private static int purgeChunk(Chunk chunk, Set<SpawnCategory> targets, int keepPerChunk,
                                  GlobalSettings.ProtectOptions protect) {
        List<Entity> candidates = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            if (shouldPurge(entity, targets, protect)) {
                candidates.add(entity);
            }
        }
        int removals = candidates.size() - keepPerChunk;
        if (removals <= 0) {
            return 0;
        }
        if (keepPerChunk > 0) {
            // Keep the youngest: those are the ones a player most likely just spawned in.
            candidates.sort(Comparator.comparingInt(Entity::getTicksLived).reversed());
        }
        for (int index = 0; index < removals; index++) {
            candidates.get(index).remove();
        }
        return removals;
    }

    private static boolean shouldPurge(Entity entity, Set<SpawnCategory> targets,
                                       GlobalSettings.ProtectOptions protect) {
        if (entity instanceof Player || !targets.contains(entity.getSpawnCategory())) {
            return false;
        }
        if (protect.entityTypes().contains(entity.getType())) {
            return false;
        }
        if (protect.named() && entity.customName() != null) {
            return false;
        }
        if (protect.persistentData() && !entity.getPersistentDataContainer().getKeys().isEmpty()) {
            return false;
        }
        if (protect.scoreboardTags() && !entity.getScoreboardTags().isEmpty()) {
            return false;
        }
        if (protect.tamed() && entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        return !protect.leashed() || !(entity instanceof LivingEntity living) || !living.isLeashed();
    }

    /**
     * (Re)starts the scheduled purge loop. One coarse async tick drives every
     * world, and each world tracks its own interval, so adding worlds does not add
     * timers.
     *
     * <p>Called again on every world load, so it must not restart a healthy timer:
     * doing so would reset the period each time a world appears at boot.
     */
    void reconfigure(WorldRegistry registry, GlobalSettings globals) {
        int targets = registry.autoPurgeTargets().size();
        long period = globals.purge().autoCheckSeconds();
        if (targets == 0) {
            cancelAutoTask();
            this.checkSeconds = 0L;
            return;
        }
        if (autoTask != null && period == checkSeconds) {
            return;
        }
        cancelAutoTask();
        this.checkSeconds = period;
        this.autoTask = plugin.schedulers()
            .asyncRepeating(this::tickAuto, period, period, TimeUnit.SECONDS);
        plugin.getLogger().info("自動實體清理已啟用：" + targets + " 個世界，每 " + period + " 秒檢查一次。");
    }

    /** Runs on an async thread; only reads immutable snapshots before dispatching. */
    private void tickAuto() {
        WorldRegistry registry = plugin.registry();
        long now = System.currentTimeMillis();
        for (WorldProfile profile : registry.autoPurgeTargets()) {
            World world = Bukkit.getWorld(profile.name());
            if (world == null) {
                continue;
            }
            WorldProfile.PurgeRules rules = profile.purge();
            long dueAfter = rules.intervalSeconds() * 1000L;
            // First sighting only starts the clock: purging every world moments
            // after a restart is the one time the server can least afford it.
            Long last = lastAutoPurgeAt.putIfAbsent(world.getUID(), now);
            if (last == null || now - last < dueAfter) {
                continue;
            }
            lastAutoPurgeAt.put(world.getUID(), now);
            purge(world, rules.categories(), rules.keepPerChunk(),
                removed -> reportAuto(profile, world, removed));
        }
    }

    private void reportAuto(WorldProfile profile, World world, int removed) {
        if (removed <= 0) {
            return;
        }
        plugin.getLogger().info("自動清理 " + profile.name() + "：移除 " + removed + " 個實體。");
        if (!profile.purge().announce()) {
            return;
        }
        Messages messages = plugin.messages();
        plugin.schedulers().global(() -> {
            for (Player player : world.getPlayers()) {
                plugin.schedulers().entity(player, () -> messages.send(player, "purge.auto-announce",
                    "world", profile.alias(), "count", removed), null);
            }
        });
    }

    /** Drops the interval clock for a world that is no longer loaded. */
    void forgetWorld(UUID worldId) {
        lastAutoPurgeAt.remove(worldId);
    }

    void shutdown() {
        cancelAutoTask();
        lastAutoPurgeAt.clear();
    }

    private void cancelAutoTask() {
        ScheduledTask running = this.autoTask;
        if (running != null) {
            running.cancel();
            this.autoTask = null;
        }
    }
}
