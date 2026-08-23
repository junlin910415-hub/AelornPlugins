package com.xuzhihuanjing.rpgcore.integration.citizens;

import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.PluginManager;

/**
 * Citizens 軟整合。
 *
 * <p>與 {@code MythicMobsBridge} 同樣的模式:Citizens 沒裝或版本不合時整組功能靜默停用,
 * 而不是讓 RPGCore 起不來。所有對外方法在不可用時回傳空值。
 *
 * <p>NPC 以**名稱**識別而非數字 id——設定檔裡寫 `軍需官 賽拉` 遠比寫 `17` 好維護,
 * 而且重建 NPC 後 id 會變、名稱不會。比對時會去掉顏色標記並忽略大小寫。
 */
public final class CitizensBridge {

    private final boolean available;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CitizensBridge(PluginManager pluginManager, Logger logger) {
        this.logger = logger;
        boolean present = pluginManager.isPluginEnabled("Citizens");
        boolean usable = false;
        if (present) {
            try {
                usable = CitizensAPI.hasImplementation();
            } catch (Throwable missingApi) {
                logger.warning("Citizens 已安裝但 API 無法使用,NPC 整合停用:" + missingApi.getMessage());
            }
        }
        this.available = usable;
        if (usable) {
            logger.info("Citizens 整合已啟用。");
        }
    }

    public boolean available() {
        return available;
    }

    /** 這個實體是不是 Citizens NPC。這是熱路徑(每次互動/傷害都會問),保持極輕。 */
    public boolean isNpc(Entity entity) {
        if (!available || entity == null) {
            return false;
        }
        try {
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            return registry != null && registry.isNPC(entity);
        } catch (Throwable failure) {
            return false;
        }
    }

    public Optional<NPC> npc(Entity entity) {
        if (!available || entity == null) {
            return Optional.empty();
        }
        try {
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            return registry == null ? Optional.empty() : Optional.ofNullable(registry.getNPC(entity));
        } catch (Throwable failure) {
            return Optional.empty();
        }
    }

    /** NPC 的正規化名稱,用來對應 npcs.yml 的設定。 */
    public Optional<String> npcKey(Entity entity) {
        return npc(entity).map(this::keyOf);
    }

    public String keyOf(NPC npc) {
        return normalize(npc.getName());
    }

    /**
     * 依名稱找出目前已生成的 NPC。
     *
     * <p>會走訪整個 registry,所以只適合設定載入或指令這類低頻路徑,
     * 不要放進每 tick 的行為迴圈。
     */
    public Optional<NPC> findByName(String name) {
        if (!available || name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = normalize(name);
        try {
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            if (registry == null) {
                return Optional.empty();
            }
            for (NPC npc : registry) {
                if (normalize(npc.getName()).equals(wanted)) {
                    return Optional.of(npc);
                }
            }
        } catch (Throwable failure) {
            logger.warning("查找 Citizens NPC 失敗:" + failure.getMessage());
        }
        return Optional.empty();
    }

    /** 去掉 MiniMessage 標記與傳統色碼,再轉小寫,讓設定檔可以直接寫顯示名稱。 */
    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped;
        try {
            stripped = miniMessage.stripTags(raw);
        } catch (RuntimeException notMiniMessage) {
            stripped = raw;
        }
        return stripped.replaceAll("(?i)[§&][0-9a-fk-or]", "").trim().toLowerCase(Locale.ROOT);
    }
}
