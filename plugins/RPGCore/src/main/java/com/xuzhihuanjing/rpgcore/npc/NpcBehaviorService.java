package com.xuzhihuanjing.rpgcore.npc;

import com.xuzhihuanjing.rpgcore.config.NpcRegistry;
import com.xuzhihuanjing.rpgcore.domain.npc.NpcDefinition;
import com.xuzhihuanjing.rpgcore.integration.citizens.CitizensBridge;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * 驅動 NPC 的移動行為:巡邏、護送、戰鬥協助。
 *
 * <h2>Folia 執行緒模型</h2>
 * 每個 NPC 的行為 tick 掛在**該 NPC 實體自己的 EntityScheduler** 上。NPC 走動跨越區域時
 * Folia 會把 task 一起搬過去,所以巡邏路線可以橫跨多個區域而不會出現跨區域存取。
 * 這也表示行為迴圈裡只能碰這個 NPC 與它附近的實體——不能去動別的區域。
 *
 * <p>tick 間隔刻意設得比較長(預設 20 tick):Citizens 的 Navigator 自己會逐 tick 尋路,
 * 這裡只負責「下一個目標是什麼」,不需要每 tick 介入。
 */
public final class NpcBehaviorService {

    /** 行為決策間隔。Navigator 自己會處理逐 tick 的移動。 */
    private static final long DECISION_INTERVAL_TICKS = 20L;
    /** 巡邏點抵達判定半徑。 */
    private static final double WAYPOINT_REACH_DISTANCE = 2.0D;
    /** 護送終點判定半徑。 */
    private static final double ESCORT_REACH_DISTANCE = 4.0D;

    private final Plugin plugin;
    private final CitizensBridge citizens;
    private final NpcRegistry registry;
    private final com.xuzhihuanjing.rpgcore.quest.QuestService questService;
    private final QuestIndicatorService indicators;

    private final Map<UUID, BehaviorState> states = new ConcurrentHashMap<>();

    public NpcBehaviorService(Plugin plugin, CitizensBridge citizens, NpcRegistry registry,
                              com.xuzhihuanjing.rpgcore.quest.QuestService questService,
                              QuestIndicatorService indicators) {
        this.plugin = plugin;
        this.citizens = citizens;
        this.registry = registry;
        this.questService = questService;
        this.indicators = indicators;
    }

    /**
     * 開始驅動一個 NPC。生成時呼叫;重複呼叫是安全的。
     *
     * <p>STATIONARY 的 NPC 完全不排程——沒有行為就不該有成本。
     */
    public void attach(NPC npc) {
        if (!citizens.available() || npc == null || !npc.isSpawned()) {
            return;
        }
        NpcDefinition definition = registry.find(citizens.keyOf(npc)).orElse(null);
        // 任務 NPC 即使不移動也要排程,因為頭頂指示符需要依玩家狀態更新
        if (definition == null || (!definition.behavior().needsTicking() && !definition.givesQuests())) {
            return;
        }
        Entity entity = npc.getEntity();
        if (entity == null) {
            return;
        }
        UUID entityId = entity.getUniqueId();
        if (states.containsKey(entityId)) {
            return;
        }

        indicators.createMarkers(entity, definition);
        BehaviorState state = new BehaviorState(definition);
        state.home = entity.getLocation().clone();
        states.put(entityId, state);
        state.task = entity.getScheduler().runAtFixedRate(plugin,
            task -> tick(npc, state),
            () -> states.remove(entityId),
            DECISION_INTERVAL_TICKS, DECISION_INTERVAL_TICKS);
    }

    public void detach(NPC npc) {
        if (npc == null) {
            return;
        }
        Entity entity = npc.getEntity();
        if (entity != null) {
            cancel(states.remove(entity.getUniqueId()));
            indicators.removeMarkers(entity);
        }
    }

    /** 在 NPC 自己的區域執行緒上執行。 */
    private void tick(NPC npc, BehaviorState state) {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        switch (state.definition.behavior().type()) {
            case PATROL -> tickPatrol(npc, state);
            case ESCORT -> tickEscort(npc, state);
            case COMBAT_ALLY -> tickCombatAlly(npc, state);
            case WANDER -> tickWander(npc, state);
            default -> {
            }
        }
        if (state.definition.givesQuests()) {
            indicators.refresh(npc.getEntity(), state.definition);
        }
    }

    /**
     * 以生成點為圓心隨機漫步,中間穿插停頓。
     *
     * <p>停頓很重要:一直走的公民看起來像機器人,偶爾站著才像在生活。
     */
    private void tickWander(NPC npc, BehaviorState state) {
        if (npc.getNavigator().isNavigating()) {
            return;
        }
        if (state.idleRemaining > 0) {
            state.idleRemaining--;
            return;
        }
        Location home = state.home;
        if (home == null || home.getWorld() == null) {
            return;
        }
        double radius = state.definition.behavior().wanderRadius();
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double distance = ThreadLocalRandom.current().nextDouble(radius * 0.3D, radius);
        Location target = home.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
        // 對齊地面,避免目標點懸空或埋在地下
        target.setY(home.getWorld().getHighestBlockYAt(target) + 1);

        if (npc.getNavigator().canNavigateTo(target)) {
            navigateTo(npc, state, target);
        }
        // idleTicks 是「決策週期」數,不是遊戲 tick
        int idle = state.definition.behavior().idleTicks();
        state.idleRemaining = idle <= 0 ? 0 : ThreadLocalRandom.current().nextInt(1, Math.max(2, idle / 20 + 1));
    }

    private void tickPatrol(NPC npc, BehaviorState state) {
        List<NpcDefinition.Waypoint> waypoints = state.definition.behavior().waypoints();
        if (waypoints.isEmpty()) {
            return;
        }
        Location current = npc.getEntity().getLocation();
        Location target = waypoints.get(state.waypointIndex).toLocation();
        if (target == null) {
            // 世界沒載入,跳過這個點而不是卡住整條路線
            state.waypointIndex = (state.waypointIndex + 1) % waypoints.size();
            return;
        }
        if (sameWorld(current, target) && current.distanceSquared(target) <= WAYPOINT_REACH_DISTANCE * WAYPOINT_REACH_DISTANCE) {
            state.waypointIndex = (state.waypointIndex + 1) % waypoints.size();
            target = waypoints.get(state.waypointIndex).toLocation();
            if (target == null) {
                return;
            }
        }
        if (!npc.getNavigator().isNavigating()) {
            navigateTo(npc, state, target);
        }
    }

    private void tickEscort(NPC npc, BehaviorState state) {
        Player escortee = state.escortee == null ? null : plugin.getServer().getPlayer(state.escortee);
        if (escortee == null || !escortee.isOnline()) {
            npc.getNavigator().cancelNavigation();
            return;
        }
        Location npcLocation = npc.getEntity().getLocation();

        NpcDefinition.Waypoint destination = state.definition.behavior().escortTarget();
        Location target = destination == null ? null : destination.toLocation();
        if (target != null && sameWorld(npcLocation, target)
            && npcLocation.distanceSquared(target) <= ESCORT_REACH_DISTANCE * ESCORT_REACH_DISTANCE) {
            npc.getNavigator().cancelNavigation();
            // escortArrived 兼作「已回報」旗標,避免每個 tick 重複計數
            if (!state.escortArrived) {
                state.escortArrived = true;
                String npcKey = state.definition.key();
                escortee.getScheduler().run(plugin,
                    task -> questService.recordEscortArrival(escortee, npcKey), null);
            }
            return;
        }

        // 玩家落後太多就等他,否則朝終點前進
        double followRange = state.definition.behavior().followRange();
        if (sameWorld(npcLocation, escortee.getLocation())
            && npcLocation.distanceSquared(escortee.getLocation()) > followRange * followRange) {
            navigateTo(npc, state, escortee.getLocation());
        } else if (target != null && !npc.getNavigator().isNavigating()) {
            navigateTo(npc, state, target);
        }
    }

    private void tickCombatAlly(NPC npc, BehaviorState state) {
        Player owner = state.escortee == null ? null : plugin.getServer().getPlayer(state.escortee);
        if (owner == null || !owner.isOnline()) {
            npc.getNavigator().cancelNavigation();
            return;
        }
        Location npcLocation = npc.getEntity().getLocation();
        if (!sameWorld(npcLocation, owner.getLocation())) {
            return;
        }

        LivingEntity hostile = nearestHostile(npc, owner, state.definition.behavior().targetRange());
        if (hostile != null) {
            // aggressive = true 讓 Citizens 用攻擊型尋路
            npc.getNavigator().setTarget(hostile, true);
            npc.getNavigator().getLocalParameters().speedModifier((float) state.definition.behavior().speed());
            return;
        }

        double followRange = state.definition.behavior().followRange();
        if (npcLocation.distanceSquared(owner.getLocation()) > followRange * followRange) {
            navigateTo(npc, state, owner.getLocation());
        }
    }

    /**
     * 找出離主人最近的敵對生物。
     *
     * <p>只掃 NPC 自己周圍——`getNearbyEntities` 在 Folia 上僅能安全存取本區域,
     * 這也順便把索敵範圍限制在合理值。
     */
    private @Nullable LivingEntity nearestHostile(NPC npc, Player owner, double range) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity nearby : npc.getEntity().getNearbyEntities(range, range / 2, range)) {
            if (!(nearby instanceof Mob mob) || mob.isDead()) {
                continue;
            }
            if (citizens.isNpc(mob)) {
                continue;
            }
            double distance = mob.getLocation().distanceSquared(owner.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        return best;
    }

    private void navigateTo(NPC npc, BehaviorState state, Location target) {
        npc.getNavigator().setTarget(target);
        npc.getNavigator().getLocalParameters().speedModifier((float) state.definition.behavior().speed());
    }

    private static boolean sameWorld(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }

    /** 指派護送/同伴的對象玩家。 */
    public void assignOwner(NPC npc, UUID playerId) {
        if (npc == null || npc.getEntity() == null) {
            return;
        }
        BehaviorState state = states.get(npc.getEntity().getUniqueId());
        if (state != null) {
            state.escortee = playerId;
            state.escortArrived = false;
        }
    }

    /** 護送 NPC 是否已抵達終點,供 ESCORT_NPC 目標判定。 */
    public boolean escortArrived(NPC npc) {
        if (npc == null || npc.getEntity() == null) {
            return false;
        }
        BehaviorState state = states.get(npc.getEntity().getUniqueId());
        return state != null && state.escortArrived;
    }

    public void shutdown() {
        for (UUID id : List.copyOf(states.keySet())) {
            cancel(states.remove(id));
        }
    }

    public int activeBehaviors() {
        return states.size();
    }

    private static void cancel(@Nullable BehaviorState state) {
        if (state != null && state.task != null && !state.task.isCancelled()) {
            state.task.cancel();
        }
    }

    /** 單一 NPC 的行為狀態;只由該 NPC 的區域執行緒讀寫。 */
    private static final class BehaviorState {
        private final NpcDefinition definition;
        private int waypointIndex;
        private @Nullable Location home;
        private int idleRemaining;
        private volatile @Nullable UUID escortee;
        private volatile boolean escortArrived;
        private @Nullable ScheduledTask task;

        private BehaviorState(NpcDefinition definition) {
            this.definition = definition;
        }
    }
}
