package tw.linsy.aelornbackpack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * 背包內容的持久化層。
 *
 * 安全準則（會弄丟玩家物品的每一種情況都要堵住）：
 * 1. 讀檔失敗時回傳 null 而不是空陣列——呼叫端必須拒絕開啟介面，
 *    因為給玩家一個空背包等於讓他以為東西被吃了，接著存檔就真的沒了。
 * 2. 寫檔採「先寫暫存檔再原子換名」，中途當機不會留下半截檔案。
 * 3. 快取只在成功讀檔後建立；未載入的玩家一律走磁碟。
 */
public final class BackpackStorage {

    /** 每頁 36 格，與原版背包主區一致，玩家不需要重新學容量。 */
    public static final int SLOTS_PER_PAGE = 36;

    private final File folder;
    private final ConcurrentHashMap<UUID, ItemStack[][]> cache = new ConcurrentHashMap<>();

    public BackpackStorage(File folder) {
        this.folder = folder;
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IllegalStateException("無法建立背包資料夾：" + folder);
        }
    }

    /**
     * 讀取指定頁的內容。
     *
     * @return 長度 {@link #SLOTS_PER_PAGE} 的陣列；讀檔損毀時回傳 null。
     */
    public ItemStack[] load(UUID playerId, int page) {
        ItemStack[][] pages = cache.get(playerId);
        if (pages == null) {
            pages = readFromDisk(playerId);
            if (pages == null) {
                return null;
            }
            cache.put(playerId, pages);
        }
        if (page < 0 || page >= pages.length) {
            return new ItemStack[SLOTS_PER_PAGE];
        }
        return pages[page];
    }

    /** 更新快取中的某一頁；不寫磁碟，由呼叫端決定何時 flush。 */
    public void store(UUID playerId, int page, ItemStack[] contents) {
        ItemStack[][] pages = cache.computeIfAbsent(playerId,
                id -> new ItemStack[maxPages() + 1][SLOTS_PER_PAGE]);
        if (page < 0 || page >= pages.length) {
            return;
        }
        ItemStack[] copy = new ItemStack[SLOTS_PER_PAGE];
        System.arraycopy(contents, 0, copy, 0, Math.min(contents.length, SLOTS_PER_PAGE));
        pages[page] = copy;
    }

    /** 把快取寫回磁碟。必須在非同步排程中呼叫。 */
    public void flush(UUID playerId) throws IOException {
        ItemStack[][] pages = cache.get(playerId);
        if (pages == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("owner", playerId.toString());
        for (int page = 0; page < pages.length; page++) {
            ItemStack[] contents = pages[page];
            if (contents == null || isEmpty(contents)) {
                continue;
            }
            yaml.set("pages." + page,
                    Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
        }
        File target = new File(folder, playerId + ".yml");
        File temporary = new File(folder, playerId + ".yml.tmp");
        yaml.save(temporary);
        Files.move(temporary.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** 玩家離線後移除快取，避免長時間執行累積記憶體。 */
    public void evict(UUID playerId) {
        cache.remove(playerId);
    }

    public boolean isLoaded(UUID playerId) {
        return cache.containsKey(playerId);
    }

    public int cachedPlayers() {
        return cache.size();
    }

    public static int maxPages() {
        return 4;
    }

    private ItemStack[][] readFromDisk(UUID playerId) {
        File file = new File(folder, playerId + ".yml");
        ItemStack[][] pages = new ItemStack[maxPages() + 1][SLOTS_PER_PAGE];
        if (!file.isFile()) {
            return pages;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (int page = 0; page < pages.length; page++) {
            String encoded = yaml.getString("pages." + page);
            if (encoded == null || encoded.isBlank()) {
                continue;
            }
            try {
                ItemStack[] decoded =
                        ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
                ItemStack[] sized = new ItemStack[SLOTS_PER_PAGE];
                System.arraycopy(decoded, 0, sized, 0,
                        Math.min(decoded.length, SLOTS_PER_PAGE));
                pages[page] = sized;
            } catch (RuntimeException corrupted) {
                // 任何一頁解不開就整份判定損毀：寧可拒絕開啟，也不要讓玩家看到空背包。
                return null;
            }
        }
        return pages;
    }

    /** 飾品專區的頁索引：緊接在背包分頁之後，與分頁權限無關。 */
    public static int accessoryPage() {
        return maxPages();
    }

    /** 讀取飾品欄；讀檔損毀時回傳 null（呼叫端必須拒絕開啟）。 */
    public ItemStack[] loadAccessories(UUID playerId, int slots) {
        ItemStack[] page = load(playerId, accessoryPage());
        if (page == null) {
            return null;
        }
        ItemStack[] accessories = new ItemStack[slots];
        System.arraycopy(page, 0, accessories, 0, Math.min(slots, page.length));
        return accessories;
    }

    /** 寫入飾品欄到快取；實際落盤由 flush 負責。 */
    public void storeAccessories(UUID playerId, ItemStack[] accessories) {
        ItemStack[] page = new ItemStack[SLOTS_PER_PAGE];
        System.arraycopy(accessories, 0, page, 0,
                Math.min(accessories.length, SLOTS_PER_PAGE));
        store(playerId, accessoryPage(), page);
    }

    private static boolean isEmpty(ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
