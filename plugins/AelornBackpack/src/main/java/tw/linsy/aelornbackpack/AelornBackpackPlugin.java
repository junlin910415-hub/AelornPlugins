package tw.linsy.aelornbackpack;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 艾洛恩自訂背包。
 *
 * 兩個設計限制先寫在這裡，避免日後誤解：
 *
 * 1. 伺服器端無法取代原版按 E 的背包畫面——那是客戶端畫面，
 *    玩家開自己的背包不會觸發 InventoryOpenEvent。
 *    因此本插件的做法是：攔截原版合成區（raw slot 0-4）的所有互動，
 *    讓它無法合成，並把點擊轉成開啟本插件的介面。
 *    視覺上再由資源包把該區域重繪成按鈕即可。
 *
 * 2. 裝備列只做鏡像顯示，不做穿脫。RPGCore 的 EquipmentService 與
 *    MMOItems 的穿戴需求都掛在原版裝備事件上；另開穿脫路徑會繞過那些檢查。
 */
public final class AelornBackpackPlugin extends JavaPlugin implements Listener {

    /**
     * Folia 排程由 AelornLib 提供。延遲解析:即使有呼叫在 onEnable 完成前抵達,
     * 拿到的也是可用的 facade 而不是 null。
     */
    private tw.linsy.aelorn.lib.sched.Schedulers schedulers;

    private tw.linsy.aelorn.lib.sched.Schedulers schedulers() {
        if (schedulers == null) {
            schedulers = tw.linsy.aelorn.lib.AelornLib.require().schedulersFor(this);
        }
        return schedulers;
    }

    private BackpackStorage storage;
    private PouchService pouch;
    private AccessoryStatBridge accessoryStats;
    private volatile boolean pouchEnabled = true;
    private volatile int pouchSlot = 6;
    private volatile org.bukkit.Material pouchMaterial = org.bukkit.Material.BUNDLE;
    private final ConcurrentHashMap<UUID, Integer> openPages = new ConcurrentHashMap<>();
    private volatile boolean interceptCrafting = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        interceptCrafting = getConfig().getBoolean("intercept-vanilla-crafting", true);
        storage = new BackpackStorage(new java.io.File(getDataFolder(), "players"));
        pouch = new PouchService(this);
        accessoryStats = new AccessoryStatBridge(getLogger());
        pouchEnabled = getConfig().getBoolean("pouch.enabled", true);
        // RPGCore 的旅圖冊佔用索引 7（介面顯示為第 8 格），預設避開。
        pouchSlot = Math.max(0, Math.min(8, getConfig().getInt("pouch.slot", 6)));
        String materialName = getConfig().getString("pouch.material", "BUNDLE");
        org.bukkit.Material parsed = org.bukkit.Material.matchMaterial(
                materialName == null ? "BUNDLE" : materialName);
        pouchMaterial = parsed == null ? org.bukkit.Material.BUNDLE : parsed;
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("AelornBackpack 已啟用：每頁 " + BackpackStorage.SLOTS_PER_PAGE
                + " 格，最多 " + BackpackStorage.maxPages() + " 頁"
                + (interceptCrafting ? "，已接管原版合成區。" : "。"));
    }

    @Override
    public void onDisable() {
        // 關服時把所有仍開著的背包寫回磁碟；此處已在主執行緒收尾，直接同步寫。
        for (Player player : Bukkit.getOnlinePlayers()) {
            captureOpenPage(player);
            flushQuietly(player.getUniqueId());
        }
        openPages.clear();
    }

    // ---- 開啟與翻頁 ----

    private void open(Player player, int page) {
        int maxPages = allowedPages(player);
        int safePage = Math.max(0, Math.min(maxPages - 1, page));
        ItemStack[] contents = storage.load(player.getUniqueId(), safePage);
        if (contents == null) {
            player.sendMessage(Component.text(
                    "背包資料讀取失敗，為避免物品遺失已停止開啟。請聯絡管理員。",
                    NamedTextColor.RED));
            getLogger().severe("玩家 " + player.getName() + " 的背包存檔損毀，已拒絕開啟。");
            return;
        }
        ItemStack[] accessories =
                storage.loadAccessories(player.getUniqueId(), BackpackMenu.ACCESSORY_COUNT);
        if (accessories == null) {
            player.sendMessage(Component.text(
                    "飾品欄資料讀取失敗，為避免物品遺失已停止開啟。", NamedTextColor.RED));
            return;
        }
        Inventory inventory = BackpackMenu.create(player, safePage, maxPages, contents, accessories,
                Component.text("背包 " + (safePage + 1) + "/" + maxPages, NamedTextColor.DARK_GRAY));
        openPages.put(player.getUniqueId(), safePage);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.6f, 1.2f);
    }

    private int allowedPages(Player player) {
        int pages = 1;
        for (int candidate = BackpackStorage.maxPages(); candidate >= 2; candidate--) {
            if (player.hasPermission("aelornbackpack.pages." + candidate)) {
                pages = candidate;
                break;
            }
        }
        return pages;
    }

    // ---- 原版合成區接管 ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaCraftPrepare(PrepareItemCraftEvent event) {
        if (interceptCrafting && event.getInventory().getSize() <= 5) {
            // 只擋玩家背包內的 2x2；工作台（size 10）不受影響。
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaInventoryClick(InventoryClickEvent event) {
        if (!interceptCrafting || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof CraftingInventory crafting)
                || crafting.getSize() > 5) {
            return;
        }
        int raw = event.getRawSlot();
        // raw 0 = 產出格，1-4 = 2x2 合成格。
        boolean intoCraftingArea = raw >= 0 && raw <= 4;
        boolean shiftIntoTop = event.isShiftClick() && raw > 4
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
        if (!intoCraftingArea && !shiftIntoTop) {
            return;
        }
        event.setCancelled(true);
        if (intoCraftingArea) {
            // 合成區改當按鈕：點一下開背包。
            // Folia 規定玩家狀態必須在該玩家自己的 EntityScheduler 上操作，
            // 用 GlobalRegionScheduler 開介面會拋 IllegalStateException。
            // 延遲 1 tick 是因為不能在 InventoryClickEvent 處理中直接換介面。
            player.closeInventory();
            schedulers().entityDelayed(player, () -> open(player, 0), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaInventoryDrag(InventoryDragEvent event) {
        if (!interceptCrafting
                || !(event.getView().getTopInventory() instanceof CraftingInventory crafting)
                || crafting.getSize() > 5) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw >= 0 && raw <= 4) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ---- 自訂背包介面 ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBackpackClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < BackpackMenu.MENU_SIZE;

        // 玩家自己的背包區：允許一般操作，但 shift 移入必須落在儲存格。
        if (!inTop) {
            if (event.isShiftClick()
                    && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // 讓原版行為把物品塞進頂部庫存；頂部非儲存格已被下面的規則保護，
                // 但原版 shift 會找第一個空位，可能落在導航列，因此改為自行處理。
                event.setCancelled(true);
                moveIntoStorage(event, holder, player);
            }
            return;
        }

        if (BackpackMenu.isStorageSlot(raw)) {
            return; // 儲存格自由存取
        }

        if (BackpackMenu.isAccessorySlot(raw)) {
            // 放入時檢查類型：只有飾品類物品能進飾品欄。
            ItemStack incoming = event.getCursor();
            if (incoming != null && !incoming.getType().isAir()
                    && !accessoryStats.acceptsAccessory(incoming)) {
                event.setCancelled(true);
                player.sendActionBar(Component.text(
                        "這個欄位只能放飾品（戒指／手鐲／項鍊）。", NamedTextColor.RED));
                return;
            }
            // 佔位玻璃片只是「這格是空的」的視覺提示，不能被拿走。
            if (BackpackMenu.isAccessoryMarker(event.getCurrentItem())
                    && (event.getCursor() == null || event.getCursor().getType().isAir())) {
                event.setCancelled(true);
                return;
            }
            // 放入時把佔位物換掉；取出時下次重繪會補回佔位物。
            if (BackpackMenu.isAccessoryMarker(event.getCurrentItem())) {
                event.setCurrentItem(null);
            }
            return;
        }

        event.setCancelled(true);
        if (raw == BackpackMenu.closeSlot()) {
            player.closeInventory();
            return;
        }
        if (raw == BackpackMenu.previousSlot() && holder.page() > 0) {
            switchPage(player, holder, holder.page() - 1);
            return;
        }
        if (raw == BackpackMenu.nextSlot() && holder.page() + 1 < allowedPages(player)) {
            switchPage(player, holder, holder.page() + 1);
        }
    }

    /** 自行實作 shift 移入，確保只會塞進儲存格，不會污染裝備列或導航列。 */
    private void moveIntoStorage(InventoryClickEvent event, BackpackHolder holder, Player player) {
        ItemStack moving = event.getCurrentItem();
        if (moving == null || moving.getType().isAir()) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        ItemStack remainder = moving.clone();
        for (int slot = BackpackMenu.STORAGE_START;
                slot <= BackpackMenu.STORAGE_END && remainder.getAmount() > 0; slot++) {
            ItemStack existing = top.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                top.setItem(slot, remainder.clone());
                remainder.setAmount(0);
                break;
            }
            if (!existing.isSimilar(remainder)) {
                continue;
            }
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remainder.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            top.setItem(slot, existing);
            remainder.setAmount(remainder.getAmount() - moved);
        }
        event.setCurrentItem(remainder.getAmount() > 0 ? remainder : null);
        player.updateInventory();
    }

    private void switchPage(Player player, BackpackHolder holder, int nextPage) {
        // 先把目前頁存進快取再換頁，否則翻頁會丟掉這一頁的改動。
        storage.store(player.getUniqueId(), holder.page(), snapshot(holder.getInventory()));
        storage.storeAccessories(player.getUniqueId(), snapshotAccessories(holder.getInventory()));
        accessoryStats.apply(player, snapshotAccessories(holder.getInventory()));
        open(player, nextPage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBackpackDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < BackpackMenu.MENU_SIZE && !BackpackMenu.isStorageSlot(raw)
                    && !BackpackMenu.isAccessorySlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onBackpackClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder holder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        storage.store(player.getUniqueId(), holder.page(), snapshot(event.getInventory()));
        storage.storeAccessories(player.getUniqueId(), snapshotAccessories(event.getInventory()));
        accessoryStats.apply(player, snapshotAccessories(event.getInventory()));
        openPages.remove(player.getUniqueId());
        UUID playerId = player.getUniqueId();
        schedulers().async(() -> flushQuietly(playerId));
        refreshPouchLater(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        captureOpenPage(player);
        UUID playerId = player.getUniqueId();
        schedulers().async(() -> {
            flushQuietly(playerId);
            storage.evict(playerId);
        });
    }

    private void captureOpenPage(Player player) {
        Integer page = openPages.remove(player.getUniqueId());
        if (page == null) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof BackpackHolder holder && holder.page() == page) {
            storage.store(player.getUniqueId(), page, snapshot(top));
            storage.storeAccessories(player.getUniqueId(), snapshotAccessories(top));
            accessoryStats.apply(player, snapshotAccessories(top));
        }
    }

    /** 只取儲存區的內容，裝備鏡像與導航列不會被存進去。 */
    private static ItemStack[] snapshot(Inventory inventory) {
        ItemStack[] contents = new ItemStack[BackpackStorage.SLOTS_PER_PAGE];
        for (int index = 0; index < BackpackStorage.SLOTS_PER_PAGE; index++) {
            contents[index] = inventory.getItem(BackpackMenu.STORAGE_START + index);
        }
        return contents;
    }

    /**
     * 取出飾品欄內容。
     * 佔位玻璃片代表「空」，不能存進去，否則玩家下次開啟會看到一堆玻璃片。
     */
    private static ItemStack[] snapshotAccessories(Inventory inventory) {
        ItemStack[] accessories = new ItemStack[BackpackMenu.ACCESSORY_COUNT];
        for (int index = 0; index < BackpackMenu.ACCESSORY_COUNT; index++) {
            ItemStack item = inventory.getItem(BackpackMenu.ACCESSORY_SLOTS[index]);
            accessories[index] = BackpackMenu.isAccessoryMarker(item) ? null : item;
        }
        return accessories;
    }

    private void flushQuietly(UUID playerId) {
        try {
            storage.flush(playerId);
        } catch (IOException exception) {
            getLogger().severe("寫入背包失敗 " + playerId + "：" + exception.getMessage());
        }
    }

    // ---- 儲物袋 ----

    /** 進入伺服器時補發儲物袋，並讓 tooltip 反映目前內容。 */
    @EventHandler
    public void onJoinGivePouch(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!pouchEnabled) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        schedulers().async(() -> {
            ItemStack[] contents = storage.load(playerId, 0);
            // 讀檔損毀時不要發袋子，避免 tooltip 顯示成空的誤導玩家。
            if (contents == null) {
                return;
            }
            schedulers().entity(player,
                    () -> pouch.refresh(player, pouchSlot, pouchMaterial, contents));
        });
    }

    /** 右鍵儲物袋開啟背包。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPouchInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!pouchEnabled || !pouch.isPouch(event.getItem())) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        schedulers().entity(player, () -> open(player, 0));
    }

    /** 儲物袋不可丟棄。 */
    @EventHandler(ignoreCancelled = true)
    public void onPouchDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (pouchEnabled && pouch.isPouch(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** 儲物袋不可移動或放入容器。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPouchMove(InventoryClickEvent event) {
        if (!pouchEnabled) {
            return;
        }
        if (pouch.isPouch(event.getCurrentItem()) || pouch.isPouch(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        // 數字鍵換位也會把袋子換出快捷欄。
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == pouchSlot) {
            event.setCancelled(true);
        }
    }

    /** 關閉背包後刷新袋子 tooltip，讓內容變動立即反映。 */
    private void refreshPouchLater(Player player) {
        if (!pouchEnabled) {
            return;
        }
        UUID playerId = player.getUniqueId();
        schedulers().entityDelayed(player, () -> {
            ItemStack[] contents = storage.load(playerId, 0);
            if (contents != null) {
                pouch.refresh(player, pouchSlot, pouchMaterial, contents);
            }
        }, null, 1L);
    }

    // ---- 指令 ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("reload")) {
            if (!sender.hasPermission("aelornbackpack.admin")) {
                sender.sendMessage(Component.text("你沒有權限。", NamedTextColor.RED));
                return true;
            }
            reloadConfig();
            interceptCrafting = getConfig().getBoolean("intercept-vanilla-crafting", true);
            sender.sendMessage(Component.text("AelornBackpack 已重載。", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("info")) {
            sender.sendMessage(Component.text("AelornBackpack：快取 " + storage.cachedPlayers()
                    + " 名玩家，接管合成區 " + (interceptCrafting ? "開啟" : "關閉"),
                    NamedTextColor.GRAY));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以開啟背包。", NamedTextColor.RED));
            return true;
        }
        open(player, 0);
        return true;
    }
}
