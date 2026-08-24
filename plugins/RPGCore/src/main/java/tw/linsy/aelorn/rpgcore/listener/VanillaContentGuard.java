package tw.linsy.aelorn.rpgcore.listener;

import tw.linsy.aelorn.rpgcore.config.CombatCoreSettings;
import tw.linsy.aelorn.rpgcore.item.CustomItemMarker;
import tw.linsy.aelorn.rpgcore.monster.MonsterRuntimeService;
import tw.linsy.aelorn.rpgcore.platform.RpgScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;

public final class VanillaContentGuard implements Listener {
    private final CombatCoreSettings.VanillaContentSettings settings;
    private final CustomItemMarker customItems;
    private final MonsterRuntimeService monsters;
    private final RpgScheduler scheduler;
    private final Map<UUID, ScheduledTask> inventoryTasks = new ConcurrentHashMap<>();

    public VanillaContentGuard(
            CombatCoreSettings.VanillaContentSettings settings,
            CustomItemMarker customItems,
            MonsterRuntimeService monsters,
            RpgScheduler scheduler) {
        this.settings = settings;
        this.customItems = customItems;
        this.monsters = monsters;
        this.scheduler = scheduler;
    }

    public void start() {
        if (!settings.enabled() || !settings.stripPlayerInventories()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            startInventoryGuard(player);
        }
    }

    public void shutdown() {
        inventoryTasks.values().forEach(scheduler::cancel);
        inventoryTasks.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!settings.enabled()
                || !settings.blockNaturalCreatures()
                || !applies(event.getEntity().getWorld().getName())
                || settings.allowedSpawnReasons().contains(event.getSpawnReason())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaDeath(EntityDeathEvent event) {
        if (!settings.enabled()
                || !settings.blockWorldLoot()
                || !applies(event.getEntity().getWorld().getName())
                || monsters.isManaged((Entity) event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (settings.enabled()
                && settings.blockWorldLoot()
                && applies(event.getEntity().getWorld().getName())
                && !customItems.isCustom(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (!settings.enabled()
                || !settings.blockBlockDrops()
                || !applies(event.getBlock().getWorld().getName())
                || bypass(event.getPlayer())) {
            return;
        }
        event.getItems().removeIf(item -> !customItems.isCustom(item.getItemStack()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!settings.enabled() || !settings.blockWorldLoot() || !applies(event.getWorld().getName())) {
            return;
        }
        List<ItemStack> filtered = event.getLoot().stream().filter(customItems::isCustom).toList();
        event.setLoot(filtered);
        if (filtered.isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (blockedFor(event.getPlayer(), event.getItem().getItemStack())) {
            event.setFlyAtPlayer(false);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && blockedFor(player, event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!settings.enabled()
                || !settings.blockFishingAndBartering()
                || !applies(event.getPlayer().getWorld().getName())
                || bypass(event.getPlayer())
                || event.getState() == PlayerFishEvent.State.FISHING) {
            return;
        }
        Entity caught = event.getCaught();
        if (caught instanceof Item item && customItems.isCustom(item.getItemStack())) {
            return;
        }
        event.setExpToDrop(0);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBarter(PiglinBarterEvent event) {
        if (!settings.enabled()
                || !settings.blockFishingAndBartering()
                || !applies(event.getEntity().getWorld().getName())) {
            return;
        }
        event.getOutcome().removeIf(item -> !customItems.isCustom(item));
        if (event.getOutcome().isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (settings.enabled()
                && settings.blockVanillaRecipes()
                && vanillaRecipe(event.getRecipe())
                && event.getView().getPlayer() instanceof Player player
                && applies(player.getWorld().getName())
                && !bypass(player)) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && settings.enabled()
                && settings.blockVanillaRecipes()
                && applies(player.getWorld().getName())
                && !bypass(player)
                && vanillaRecipe(event.getRecipe())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (settings.enabled()
                && settings.blockVanillaRecipes()
                && applies(event.getBlock().getWorld().getName())
                && vanillaRecipe(event.getRecipe())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        stripVanillaResult(event.getView().getPlayer(), event.getResult(), () -> event.setResult(null));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        stripVanillaResult(event.getView().getPlayer(), event.getResult(), () -> event.setResult(null));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        stripVanillaResult(event.getView().getPlayer(), event.getResult(), () -> event.setResult(null));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player player && blockedFor(player, event.getCursor())) {
            event.setCursor(new ItemStack(Material.AIR));
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (blockedFor(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRecipeDiscover(PlayerRecipeDiscoverEvent event) {
        Player player = event.getPlayer();
        if (settings.enabled()
                && settings.blockVanillaRecipes()
                && applies(player.getWorld().getName())
                && !bypass(player)
                && "minecraft".equals(event.getRecipe().getNamespace())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        startInventoryGuard(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scheduler.cancel(inventoryTasks.remove(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleSanitize(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleSanitize(event.getPlayer());
    }

    @EventHandler
    public void onHeldSlot(PlayerItemHeldEvent event) {
        scheduleSanitize(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduleSanitize(player);
        }
    }

    private void startInventoryGuard(Player player) {
        if (!settings.enabled() || !settings.stripPlayerInventories() || inventoryTasks.containsKey(player.getUniqueId())) {
            return;
        }
        ScheduledTask task = scheduler.runEntityAtFixedRate(
                player,
                ignored -> sanitize(player),
                () -> inventoryTasks.remove(player.getUniqueId()),
                1L,
                settings.inventoryScanTicks());
        if (task != null) {
            inventoryTasks.put(player.getUniqueId(), task);
        }
    }

    private void scheduleSanitize(Player player) {
        if (settings.enabled() && settings.stripPlayerInventories()) {
            scheduler.runEntityLater(player, () -> sanitize(player), () -> { }, 1L);
        }
    }

    private void sanitize(Player player) {
        if (!player.isOnline() || !applies(player.getWorld().getName()) || bypass(player)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir() && !customItems.isCustom(item)) {
                inventory.setItem(slot, null);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir() && !customItems.isCustom(cursor)) {
            player.setItemOnCursor(new ItemStack(Material.AIR));
        }
    }

    private void stripVanillaResult(org.bukkit.entity.HumanEntity viewer, ItemStack result, Runnable strip) {
        if (!(viewer instanceof Player player)
                || result == null
                || result.getType().isAir()
                || !settings.enabled()
                || !settings.blockVanillaRecipes()
                || !applies(player.getWorld().getName())
                || bypass(player)
                || customItems.isCustom(result)) {
            return;
        }
        strip.run();
    }

    private boolean blockedFor(Player player, ItemStack item) {
        return settings.enabled()
                && applies(player.getWorld().getName())
                && !bypass(player)
                && item != null
                && !item.getType().isAir()
                && !customItems.isCustom(item);
    }

    private boolean vanillaRecipe(Recipe recipe) {
        return recipe instanceof Keyed keyed && "minecraft".equals(keyed.getKey().getNamespace());
    }

    private boolean bypass(Player player) {
        return !settings.bypassPermission().isBlank() && player.hasPermission(settings.bypassPermission());
    }

    private boolean applies(String worldName) {
        return settings.appliesTo(worldName);
    }
}
