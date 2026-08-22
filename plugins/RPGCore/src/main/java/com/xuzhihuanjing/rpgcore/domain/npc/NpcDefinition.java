package com.xuzhihuanjing.rpgcore.domain.npc;

import java.util.List;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * 一個由 RPGCore 管理的 Citizens NPC。
 *
 * @param key          正規化後的 NPC 名稱,對應 Citizens 裡的名字
 * @param displayName  提示訊息用的顯示名稱(MiniMessage)
 * @param role         用途
 * @param quests       這個 NPC 可以給予的任務 id;空表示不給任務
 * @param behavior     行為設定
 */
public record NpcDefinition(String key, String displayName, NpcRole role,
                            List<String> quests, NpcBehavior behavior) {

    public NpcDefinition {
        quests = quests == null ? List.of() : List.copyOf(quests);
        behavior = behavior == null ? NpcBehavior.stationary() : behavior;
    }

    public boolean givesQuests() {
        return !quests.isEmpty();
    }

    public enum NpcRole {
        /** 任務給予者:右鍵可接任務、交付、觸發對話。 */
        QUEST_GIVER,
        /** 場景 NPC:只做行為演出,不參與任務。 */
        AMBIENT,
        /** 同伴:會跟隨玩家,可能協助戰鬥。 */
        COMPANION,
        /** 城鎮居民:在鎮上生活走動,提供氛圍。 */
        TOWN_CITIZEN,
        /** 城堡居民:衛兵、侍從、書記之類。 */
        CASTLE_CITIZEN
    }

    /**
     * NPC 的移動行為。
     *
     * @param type          行為類型
     * @param speed         移動速度倍率
     * @param waypoints     PATROL 用的路徑點,依序循環
     * @param followRange   COMPANION 跟隨的距離,超過就靠近
     * @param targetRange   COMBAT_ALLY 的索敵半徑
     * @param escortTarget  ESCORT 的終點;抵達即完成護送目標
     */
    public record NpcBehavior(BehaviorType type, double speed, List<Waypoint> waypoints,
                              double followRange, double targetRange, @Nullable Waypoint escortTarget,
                              double wanderRadius, int idleTicks) {

        public NpcBehavior {
            speed = speed <= 0 ? 1.0D : Math.min(2.0D, speed);
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
            followRange = followRange <= 0 ? 6.0D : Math.min(48.0D, followRange);
            targetRange = targetRange <= 0 ? 10.0D : Math.min(48.0D, targetRange);
            // 漫步半徑過大會讓公民走出城鎮
            wanderRadius = wanderRadius <= 0 ? 8.0D : Math.min(32.0D, wanderRadius);
            idleTicks = Math.max(0, Math.min(600, idleTicks));
        }

        public static NpcBehavior stationary() {
            return new NpcBehavior(BehaviorType.STATIONARY, 1.0D, List.of(), 6.0D, 10.0D, null, 8.0D, 0);
        }

        /** 需要每 tick 推進的行為;STATIONARY 完全不排程,零成本。 */
        public boolean needsTicking() {
            return type != BehaviorType.STATIONARY;
        }
    }

    public enum BehaviorType {
        /** 原地不動,不排程任何工作。 */
        STATIONARY,
        /** 依路徑點循環巡邏。 */
        PATROL,
        /** 跟隨啟動護送的玩家,抵達終點完成目標。 */
        ESCORT,
        /** 跟隨玩家並攻擊附近敵對生物。 */
        COMBAT_ALLY,
        /** 以生成點為圓心隨機漫步;城鎮公民用這個最自然。 */
        WANDER
    }

    /** 世界座標點;世界未載入時回傳 null,呼叫端據此跳過。 */
    public record Waypoint(String world, double x, double y, double z) {
        public @Nullable Location toLocation() {
            org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
            return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
        }
    }
}
