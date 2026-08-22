package tw.linsy.aelorn.worlds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tw.linsy.aelorn.lib.text.Messages;
import tw.linsy.aelorn.lib.ui.Menu;
import tw.linsy.aelorn.lib.ui.MenuItem;

/**
 * Paged world-transfer GUI.
 *
 * Size, ordering, every slot, every icon and every lore line come from config.yml
 * and messages.yml — the code only decides which world goes in which free slot.
 *
 * <h2>Why this holds no listener of its own</h2>
 * Built on the core's {@link Menu}, which pairs each slot's icon with its handler and
 * routes clicks through the one inventory listener the core registers for every Aelorn
 * plugin. That listener is where the recurring inventory bugs were being re-fixed per
 * plugin — forgetting to cancel, matching on window title, shift-clicks landing in the
 * top inventory — so this class no longer has an opportunity to get any of them wrong.
 * A click that reaches a handler here has already been cancelled and arrives on the
 * player's own region thread.
 *
 * <p>One menu instance per page, rebuilt on each turn. The core documents menus as
 * cheap and per-viewer for exactly this reason: page state and per-player permission
 * colouring stay local to one window instead of being shared state to keep in sync.
 */
final class WorldTransferMenu {

    private final AelornWorldsPlugin plugin;

    WorldTransferMenu(AelornWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    void open(Player player, int page) {
        GlobalSettings globals = plugin.globals();
        Messages messages = plugin.messages();
        if (!globals.transfer().enabled()) {
            messages.send(player, WorldTransferService.Result.DISABLED.messageKey());
            return;
        }

        GlobalSettings.MenuOptions options = globals.menu();
        List<WorldProfile> visible = visibleWorlds(globals);
        Map<Integer, WorldProfile> pinned = pinnedWorlds(visible, options);
        List<Integer> contentSlots = contentSlots(options, pinned.keySet());

        List<WorldProfile> paged = new ArrayList<>();
        for (WorldProfile profile : visible) {
            if (!pinned.containsValue(profile)) {
                paged.add(profile);
            }
        }

        // Every content slot can be taken by a pinned world or a button; then only
        // the pinned icons are shown rather than paging into slots that do not exist.
        int perPage = contentSlots.size();
        int pages = perPage == 0 ? 1 : Math.max(1, (paged.size() + perPage - 1) / perPage);
        int boundedPage = Math.max(0, Math.min(page, pages - 1));

        Menu menu = Menu.rows(plugin, options.rows(), messages.inlineItem(options.title(),
            "page", boundedPage + 1, "pages", pages, "count", visible.size()));
        // Menu#set overwrites, so the filler has to know what is already spoken for.
        Set<Integer> occupied = new HashSet<>();

        pinned.forEach((slot, profile) -> {
            menu.set(slot, worldItem(player, profile, globals, messages));
            occupied.add(slot);
        });

        int from = boundedPage * perPage;
        int to = perPage == 0 ? from : Math.min(paged.size(), from + perPage);
        for (int index = from; index < to; index++) {
            int slot = contentSlots.get(index - from);
            menu.set(slot, worldItem(player, paged.get(index), globals, messages));
            occupied.add(slot);
        }

        placeButtons(menu, options, messages, boundedPage, pages, occupied);
        fillEmptySlots(menu, options, messages, occupied);
        menu.open(player);
    }

    private List<WorldProfile> visibleWorlds(GlobalSettings globals) {
        WorldRegistry registry = plugin.registry();
        List<WorldProfile> visible = new ArrayList<>();
        for (WorldProfile profile : registry.profiles()) {
            if (!profile.transferable()) {
                continue;
            }
            if (globals.menu().hideUnloaded() && Bukkit.getWorld(profile.name()) == null) {
                continue;
            }
            visible.add(profile);
        }
        return registry.sorted(visible, globals.menu().sort());
    }

    /** Worlds with an explicit {@code menu-slot}; they hold that slot on every page. */
    private static Map<Integer, WorldProfile> pinnedWorlds(List<WorldProfile> visible,
                                                           GlobalSettings.MenuOptions options) {
        Map<Integer, WorldProfile> pinned = new LinkedHashMap<>();
        for (WorldProfile profile : visible) {
            int slot = profile.menuSlot();
            if (slot >= 0 && slot < options.size()) {
                pinned.putIfAbsent(slot, profile);
            }
        }
        return pinned;
    }

    /**
     * Where paged worlds may go. An explicit {@code content-slots} list wins;
     * otherwise every slot above the button row is used, which keeps a smaller
     * {@code rows} value working without the admin recomputing anything.
     */
    private static List<Integer> contentSlots(GlobalSettings.MenuOptions options,
                                              Set<Integer> pinnedSlots) {
        List<Integer> configured = options.contentSlots();
        List<Integer> slots = new ArrayList<>();
        if (!configured.isEmpty()) {
            // Buttons are drawn after the icons, so a shared slot would show the
            // button but still transfer on click. Drop the overlap instead.
            for (int slot : configured) {
                if (!pinnedSlots.contains(slot) && !isButtonSlot(options, slot)) {
                    slots.add(slot);
                }
            }
            return slots;
        }
        boolean anyButton = options.previousPage().enabled() || options.close().enabled()
            || options.nextPage().enabled();
        int limit = anyButton
            ? Math.max(INVENTORY_ROW, options.size() - INVENTORY_ROW) : options.size();
        for (int slot = 0; slot < limit; slot++) {
            if (!pinnedSlots.contains(slot) && !isButtonSlot(options, slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static final int INVENTORY_ROW = 9;

    /** A disabled button has slot -1, which never matches a real inventory slot. */
    private static boolean isButtonSlot(GlobalSettings.MenuOptions options, int slot) {
        return options.previousPage().slot() == slot
            || options.close().slot() == slot
            || options.nextPage().slot() == slot;
    }

    private void placeButtons(Menu menu, GlobalSettings.MenuOptions options, Messages messages,
                              int page, int pages, Set<Integer> occupied) {
        if (options.previousPage().enabled() && page > 0) {
            menu.set(options.previousPage().slot(),
                MenuItem.button(button(options.previousPage().material(), messages,
                        "menu.button.previous-page"),
                    click -> open(click.player(), page - 1)));
            occupied.add(options.previousPage().slot());
        }
        if (options.nextPage().enabled() && page < pages - 1) {
            menu.set(options.nextPage().slot(),
                MenuItem.button(button(options.nextPage().material(), messages,
                        "menu.button.next-page"),
                    click -> open(click.player(), page + 1)));
            occupied.add(options.nextPage().slot());
        }
        if (options.close().enabled()) {
            menu.set(options.close().slot(),
                MenuItem.button(button(options.close().material(), messages, "menu.button.close"),
                    click -> click.close()));
            occupied.add(options.close().slot());
        }
    }

    private void fillEmptySlots(Menu menu, GlobalSettings.MenuOptions options, Messages messages,
                                Set<Integer> occupied) {
        if (!options.fillerEnabled()) {
            return;
        }
        ItemStack filler = button(options.fillerMaterial(), messages, "menu.button.filler");
        for (int slot = 0; slot < menu.size(); slot++) {
            if (occupied.contains(slot)) {
                continue;
            }
            // display() has no handler, so a filler pane is inert rather than a button
            // that happens to do nothing — the click listener will not even look it up.
            menu.set(slot, MenuItem.display(filler.clone()));
        }
    }

    /**
     * A world icon and the transfer it starts.
     *
     * <p>The profile is looked up again when the click arrives rather than captured:
     * a reload between opening the menu and clicking it can replace or drop a world,
     * and acting on the stale copy would transfer against configuration that no longer
     * exists.
     */
    private MenuItem worldItem(Player viewer, WorldProfile profile, GlobalSettings globals,
                               Messages messages) {
        String worldName = profile.name();
        return MenuItem.button(buildIcon(viewer, profile, globals, messages), click -> {
            Player player = click.player();
            click.close();
            WorldProfile current = plugin.registry().byName(worldName).orElse(null);
            if (current == null) {
                plugin.messages().send(player, "command.unknown-world", "world", worldName);
                return;
            }
            WorldTransferService.Result result = plugin.transferService().transfer(player, current);
            if (!result.started()) {
                plugin.messages().send(player, result.messageKey(), "world", current.alias());
            }
        });
    }

    private ItemStack buildIcon(Player player, WorldProfile profile, GlobalSettings globals,
                                Messages messages) {
        World world = Bukkit.getWorld(profile.name());
        boolean live = world != null;
        ItemStack icon = new ItemStack(live ? profile.icon() : globals.menu().unloadedIcon());
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }
        Object[] placeholders = iconPlaceholders(player, profile, world, globals, messages);
        meta.displayName(messages.inlineItem(profile.alias(), placeholders));
        meta.lore(messages.lore(live ? "menu.lore.loaded" : "menu.lore.unloaded", placeholders));
        icon.setItemMeta(meta);
        return icon;
    }

    private Object[] iconPlaceholders(Player player, WorldProfile profile, World world,
                                      GlobalSettings globals, Messages messages) {
        String unset = messages.raw("menu.value.unset");
        boolean allowed = Permissions.has(player, Permissions.TRANSFER)
            && (!globals.transfer().requirePerWorldPermission()
                || Permissions.has(player, Permissions.ENTRY_BYPASS)
                || Permissions.hasOrAdmin(player,
                    WorldTransferService.worldPermission(globals.transfer(), profile)));
        return new Object[]{
            "world", profile.name(),
            "alias", profile.alias(),
            "environment", profile.environment().name(),
            "difficulty", profile.difficulty() == null ? unset : profile.difficulty().name(),
            "pvp", profile.pvp() == null ? unset
                : messages.raw(profile.pvp() ? "menu.value.pvp-on" : "menu.value.pvp-off"),
            "players", world == null ? 0 : world.getPlayers().size(),
            "limit", profile.entry().hasLimit()
                ? String.valueOf(profile.entry().playerLimit()) : messages.raw("menu.value.unlimited"),
            "status", messages.raw(world == null ? "menu.value.unloaded" : "menu.value.loaded"),
            "click", messages.raw(allowed ? "menu.value.click-allowed" : "menu.value.click-denied")
        };
    }

    private static ItemStack button(Material material, Messages messages, String nameKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.item(nameKey));
            item.setItemMeta(meta);
        }
        return item;
    }
}
