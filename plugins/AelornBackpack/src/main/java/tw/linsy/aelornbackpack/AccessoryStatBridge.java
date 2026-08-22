package tw.linsy.aelornbackpack;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * 把飾品欄的屬性總和寫進玩家 PDC，供 MythicCore 讀取合併。
 *
 * 為什麼走 PDC 而不是直接呼叫 MythicCore：
 * 兩個插件之間不建立編譯期相依，任一方缺席另一方照常運作。
 * MythicCore 只讀不寫，本插件只寫不讀，責任邊界乾淨。
 *
 * 格式：`STAT:值;STAT:值`（PDC 字串）。
 * 選字串而非二進位是為了可讀——用 NBT 檢視器就能直接看出玩家身上加了什麼，
 * 排查數值問題時省下大量時間。
 */
public final class AccessoryStatBridge {

    /** 與 MythicCore 端的 ACCESSORY_STATS_KEY 必須完全一致。 */
    public static final NamespacedKey STATS_KEY =
            new NamespacedKey("aelorn", "accessory_stats");

    private static final String API_CLASS = "tw.linsy.aelorn.mythiccore.api.MythicCoreApi";

    private final Logger logger;
    private volatile Method readItemStats;
    private volatile Method readItemType;
    private volatile Object cachedService;
    private boolean warned;

    public AccessoryStatBridge(Logger logger) {
        this.logger = logger;
    }

    /**
     * 依目前飾品重算並寫入 PDC。
     * 沒有任何飾品時移除鍵值，避免留下過期資料。
     */
    public void apply(Player player, ItemStack[] accessories) {
        Map<String, Double> totals = new LinkedHashMap<>();
        if (accessories != null) {
            for (ItemStack accessory : accessories) {
                if (accessory == null || accessory.getType().isAir()) {
                    continue;
                }
                readStats(accessory).forEach((stat, value) ->
                        totals.merge(stat, value, Double::sum));
            }
        }

        if (totals.isEmpty()) {
            player.getPersistentDataContainer().remove(STATS_KEY);
            return;
        }

        StringBuilder encoded = new StringBuilder();
        totals.forEach((stat, value) -> {
            if (Math.abs(value) <= 1.0E-6) {
                return;
            }
            if (encoded.length() > 0) {
                encoded.append(';');
            }
            encoded.append(stat).append(':').append(value);
        });

        if (encoded.length() == 0) {
            player.getPersistentDataContainer().remove(STATS_KEY);
            return;
        }
        player.getPersistentDataContainer()
                .set(STATS_KEY, PersistentDataType.STRING, encoded.toString());
    }

    /**
     * 透過 MythicCore 的服務讀取物品屬性。
     * 用反射是刻意的：MythicCore 是軟相依，缺席時本插件仍要能運作。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Double> readStats(ItemStack item) {
        Object service = service();
        if (service == null) {
            return Map.of();
        }
        try {
            if (readItemStats == null) {
                readItemStats = service.getClass()
                        .getMethod("readItemStats", ItemStack.class);
            }
            Object result = readItemStats.invoke(service, item);
            return result instanceof Map<?, ?> map ? (Map<String, Double>) map : Map.of();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnOnce("讀取飾品屬性失敗：" + failure.getClass().getSimpleName());
            return Map.of();
        }
    }

    /** 飾品欄接受的 MMOItems 類型。與 item-types.yml 的定義一致。 */
    private static final java.util.Set<String> ACCESSORY_TYPES = java.util.Set.of(
            "RING", "AMULET", "BRACELET", "TALISMAN",
            "ACCESSORY", "ORNAMENT", "ARTIFACT");

    /**
     * 判斷物品能否放進飾品欄。
     *
     * 判定策略刻意寬鬆：只擋「明確是別種類型」的物品。
     * MMOItems 不可用、或物品不受 MMOItems 管理時一律放行——
     * 寧可讓玩家多放東西，也不要因為服務暫時不可用就把他鎖在外面。
     */
    public boolean acceptsAccessory(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        String type = readType(item);
        if (type == null || type.isBlank()) {
            return true;
        }
        return ACCESSORY_TYPES.contains(type.toUpperCase(java.util.Locale.ROOT));
    }

    private String readType(ItemStack item) {
        Object service = service();
        if (service == null) {
            return null;
        }
        try {
            if (readItemType == null) {
                readItemType = service.getClass().getMethod("readItemType", ItemStack.class);
            }
            Object result = readItemType.invoke(service, item);
            return result instanceof String text ? text : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }
    private Object service() {
        Object cached = cachedService;
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            RegisteredServiceProvider<?> provider =
                    Bukkit.getServicesManager().getRegistration(apiClass);
            if (provider == null) {
                return null;
            }
            cachedService = provider.getProvider();
            return cachedService;
        } catch (ClassNotFoundException | RuntimeException | LinkageError unavailable) {
            warnOnce("MythicCore 服務不可用，飾品屬性不會生效。");
            return null;
        }
    }

    /** 重載或 MythicCore 重新啟用後呼叫，讓服務參考重新解析。 */
    public void invalidate() {
        cachedService = null;
        readItemStats = null;
        readItemType = null;
        warned = false;
    }

    private void warnOnce(String message) {
        if (!warned) {
            warned = true;
            logger.warning(message);
        }
    }
}
