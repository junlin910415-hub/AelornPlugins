package tw.linsy.aelorn.rpgcore.npc;

import tw.linsy.aelorn.rpgcore.config.QuestRegistry;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.npc.NpcDefinition;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestDefinition;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestProgress;
import tw.linsy.aelorn.rpgcore.domain.quest.QuestStatus;
import tw.linsy.aelorn.rpgcore.quest.QuestService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Wynncraft 式的頭頂任務指示符。
 *
 * <h2>為什麼需要三個實體</h2>
 * 指示符是**每個玩家不同**的:同一個 NPC 對 A 玩家是「有新任務」、對 B 玩家是「快去回報」、
 * 對 C 玩家什麼都不是。一個 TextDisplay 只能顯示一種文字,所以每個任務 NPC 生成三個標記
 * (每種狀態一個),全部 {@code setVisibleByDefault(false)},再依玩家狀態逐一 show/hide。
 *
 * <h2>Folia 執行緒模型</h2>
 * 更新掛在 **NPC 實體自己的 EntityScheduler** 上。從那裡呼叫
 * {@code npcEntity.getNearbyEntities(...)} 取得的玩家,**必然屬於同一個區域**,
 * 因此對他們呼叫 {@code showEntity}/{@code hideEntity} 是安全的;標記實體也生成在
 * NPC 位置,同屬一個區域。區域外的玩家收不到更新——但他們也遠到看不見標記。
 */
public final class QuestIndicatorService {

    /** 標記浮在 NPC 頭上的高度。 */
    private static final double MARKER_HEIGHT = 2.35D;
    /** 只對這個半徑內的玩家更新;超出視距就沒有意義。 */
    private static final double UPDATE_RADIUS = 28.0D;
    /** 標記本身的可視距離。 */
    private static final float MARKER_VIEW_RANGE = 32.0F;

    private final Plugin plugin;
    private final QuestRegistry questRegistry;
    private final QuestService questService;
    private final CharacterService characterService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /** NPC 實體 id → 該 NPC 的三個狀態標記。 */
    private final Map<UUID, Map<IndicatorState, UUID>> markers = new ConcurrentHashMap<>();

    public QuestIndicatorService(Plugin plugin, QuestRegistry questRegistry,
                                 QuestService questService, CharacterService characterService) {
        this.plugin = plugin;
        this.questRegistry = questRegistry;
        this.questService = questService;
        this.characterService = characterService;
    }

    /**
     * 為一個任務 NPC 建立標記。必須在該 NPC 的區域執行緒上呼叫。
     *
     * <p>只有真的會給任務的 NPC 才建立;純場景公民不需要,也不該付這個成本。
     */
    public void createMarkers(Entity npcEntity, NpcDefinition definition) {
        if (!definition.givesQuests() || markers.containsKey(npcEntity.getUniqueId())) {
            return;
        }
        Location base = npcEntity.getLocation().clone().add(0.0D, MARKER_HEIGHT, 0.0D);
        Map<IndicatorState, UUID> created = new EnumMap<>(IndicatorState.class);
        for (IndicatorState state : IndicatorState.values()) {
            if (state == IndicatorState.NONE) {
                continue;
            }
            TextDisplay display = spawnMarker(base, state);
            if (display != null) {
                created.put(state, display.getUniqueId());
            }
        }
        if (!created.isEmpty()) {
            markers.put(npcEntity.getUniqueId(), created);
        }
    }

    private @Nullable TextDisplay spawnMarker(Location base, IndicatorState state) {
        if (base.getWorld() == null) {
            return null;
        }
        try {
            return base.getWorld().spawn(base, TextDisplay.class, CreatureSpawnEvent.SpawnReason.CUSTOM, display -> {
                display.text(miniMessage.deserialize(state.symbol));
                display.setBillboard(Display.Billboard.CENTER);
                display.setViewRange(MARKER_VIEW_RANGE);
                display.setSeeThrough(false);
                display.setShadowRadius(0.0F);
                display.setShadowStrength(0.0F);
                display.setDefaultBackground(false);
                display.setPersistent(false);
                display.setGravity(false);
                display.setSilent(true);
                display.setInvulnerable(true);
                // 關鍵:預設對所有人隱藏,再依玩家狀態逐一開啟
                display.setVisibleByDefault(false);
            });
        } catch (RuntimeException spawnFailed) {
            plugin.getLogger().warning("無法生成任務指示符:" + spawnFailed.getMessage());
            return null;
        }
    }

    /**
     * 依附近玩家的任務狀態更新標記可見度。
     *
     * <p>必須在 NPC 的區域執行緒上呼叫(由 NpcBehaviorService 的行為 tick 帶動)。
     */
    public void refresh(Entity npcEntity, NpcDefinition definition) {
        Map<IndicatorState, UUID> npcMarkers = markers.get(npcEntity.getUniqueId());
        if (npcMarkers == null || npcMarkers.isEmpty()) {
            return;
        }
        // 標記不會自己跟著 NPC 走,巡邏型的任務 NPC 需要同步位置
        Location target = npcEntity.getLocation().clone().add(0.0D, MARKER_HEIGHT, 0.0D);

        for (Entity nearby : npcEntity.getNearbyEntities(UPDATE_RADIUS, UPDATE_RADIUS, UPDATE_RADIUS)) {
            if (!(nearby instanceof Player player)) {
                continue;
            }
            IndicatorState state = evaluate(player, definition);
            for (Map.Entry<IndicatorState, UUID> entry : npcMarkers.entrySet()) {
                Entity marker = resolve(npcEntity, entry.getValue());
                if (marker == null) {
                    continue;
                }
                if (entry.getKey() == state) {
                    player.showEntity(plugin, marker);
                } else {
                    player.hideEntity(plugin, marker);
                }
            }
        }

        for (UUID markerId : npcMarkers.values()) {
            Entity marker = resolve(npcEntity, markerId);
            if (marker != null && marker.getLocation().distanceSquared(target) > 0.01D) {
                marker.teleportAsync(target);
            }
        }
    }

    /**
     * 判斷玩家對這個 NPC 應該看到什麼。
     *
     * <p>優先序刻意是「可回報 > 可接取 > 進行中」:玩家最想知道的是哪裡能交任務。
     */
    private IndicatorState evaluate(Player player, NpcDefinition definition) {
        CharacterProfile character = characterService.activeCharacter(player.getUniqueId()).orElse(null);
        if (character == null) {
            return IndicatorState.NONE;
        }
        boolean anyAvailable = false;
        boolean anyInProgress = false;

        for (String questId : definition.quests()) {
            QuestDefinition quest = questRegistry.find(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            QuestProgress progress = character.questProgress().get(questId);
            if (progress != null && progress.status() == QuestStatus.ACTIVE) {
                // 目標都做完了但還沒結算 → 該回來找 NPC
                if (questService.objectivesComplete(character, quest)) {
                    return IndicatorState.TURN_IN;
                }
                anyInProgress = true;
            } else if (questService.availability(character, quest) == QuestService.Availability.AVAILABLE) {
                anyAvailable = true;
            }
        }

        if (anyAvailable) {
            return IndicatorState.AVAILABLE;
        }
        return anyInProgress ? IndicatorState.IN_PROGRESS : IndicatorState.NONE;
    }

    /** 只在標記與 NPC 同區域時取用,避免跨區域存取。 */
    private @Nullable Entity resolve(Entity npcEntity, UUID markerId) {
        for (Entity nearby : npcEntity.getNearbyEntities(4.0D, 4.0D, 4.0D)) {
            if (nearby.getUniqueId().equals(markerId)) {
                return nearby;
            }
        }
        return null;
    }

    /** NPC 消失時一併移除標記,必須在 NPC 區域執行緒上呼叫。 */
    public void removeMarkers(Entity npcEntity) {
        Map<IndicatorState, UUID> npcMarkers = markers.remove(npcEntity.getUniqueId());
        if (npcMarkers == null) {
            return;
        }
        for (UUID markerId : npcMarkers.values()) {
            Entity marker = resolve(npcEntity, markerId);
            if (marker != null) {
                marker.remove();
            }
        }
    }

    public int trackedNpcs() {
        return markers.size();
    }

    /** 指示符狀態與對應符號。符號沿用 Wynncraft 的視覺語彙。 */
    public enum IndicatorState {
        /** 有可接的新任務。 */
        AVAILABLE("<gold><bold>!</bold></gold>"),
        /** 任務進行中,目標尚未完成。 */
        IN_PROGRESS("<gray><bold>?</bold></gray>"),
        /** 目標已完成,可回來回報。 */
        TURN_IN("<gold><bold>?</bold></gold>"),
        /** 什麼都不顯示。 */
        NONE("");

        private final String symbol;

        IndicatorState(String symbol) {
            this.symbol = symbol;
        }
    }
}
