package tw.linsy.aelorn.plugins.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import tw.linsy.aelorn.lib.ui.Menu;
import tw.linsy.aelorn.lib.ui.MenuItem;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;
import tw.linsy.aelorn.plugins.platform.Sched;
import tw.linsy.aelorn.plugins.service.PluginLifecycleService;
import tw.linsy.aelorn.plugins.service.PluginLookup;
import tw.linsy.aelorn.plugins.service.PluginUnloadService;
import tw.linsy.aelorn.plugins.service.ProtectionService;
import tw.linsy.aelorn.plugins.service.VersionArchiveService;

/**
 * The menu implementation, built on AelornLib's {@code core.ui}.
 *
 * <p><b>Load-order rule:</b> this is the only class in the plugin that names
 * {@code tw.linsy.aelorn.lib.ui}. {@link GuiServices} reaches it through a static
 * factory behind a core-presence check and a {@code catch (Throwable)}, the same shape
 * as {@code CoreSched} and {@code CoreRenderer}. A field of a menu type anywhere else
 * would resolve the class on a server without the core.
 *
 * <h2>Threading is the part that is easy to get wrong</h2>
 * A {@code MenuClick} arrives on the <em>player's</em> region thread. Every lifecycle
 * operation must run on the <em>global</em> region. So each action hops out with
 * {@link Sched#global}, and the menu redraw that follows hops back — which
 * {@link Menu#open} does for itself, so the action code never has to.
 *
 * <p>Getting this wrong does not throw on a quiet server. It corrupts plugin registry
 * state under load, which is why the hop is explicit at every action rather than
 * assumed once at the top.
 *
 * <h2>Confirmation</h2>
 * The services still refuse an unconfirmed destructive call. What the menu supplies is
 * a confirmation screen instead of {@code --confirm} — the guard is unchanged, only
 * the way the operator expresses consent. Reusing the guard rather than bypassing it
 * is why there is no second set of protection rules to keep in sync.
 */
final class CoreGui implements GuiService {

    private static final int ROWS = 6;
    private static final int CONTENT_LAST_SLOT = 44;

    private final Plugin owner;
    private final MessageCatalog messages;
    private final Sched sched;
    private final GuiIcons icons;
    private final PluginLookup lookup;
    private final ProtectionService protection;
    private final PluginLifecycleService lifecycle;
    private final PluginUnloadService unload;
    private final VersionArchiveService archives;

    CoreGui(Plugin owner, MessageCatalog messages, Sched sched, GuiIcons icons,
            PluginLookup lookup, ProtectionService protection,
            PluginLifecycleService lifecycle, PluginUnloadService unload,
            VersionArchiveService archives) {
        this.owner = owner;
        this.messages = messages;
        this.sched = sched;
        this.icons = icons;
        this.lookup = lookup;
        this.protection = protection;
        this.lifecycle = lifecycle;
        this.unload = unload;
        this.archives = archives;
    }

    /**
     * @throws Throwable when AelornLib's menu classes are missing; the caller treats
     *                   any failure as "no GUI"
     */
    static GuiService create(Plugin owner, MessageCatalog messages, Sched sched, GuiIcons icons,
                             PluginLookup lookup, ProtectionService protection,
                             PluginLifecycleService lifecycle, PluginUnloadService unload,
                             VersionArchiveService archives) {
        return new CoreGui(owner, messages, sched, icons, lookup, protection,
            lifecycle, unload, archives);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String unavailableReason() {
        return "";
    }

    // ── 總覽 ──────────────────────────────────────────────────────────────

    @Override
    public void openOverview(Player viewer, String actor) {
        List<Plugin> plugins = lookup.allSorted();
        List<MenuItem> entries = new ArrayList<>(plugins.size());
        for (Plugin plugin : plugins) {
            entries.add(MenuItem.button(overviewIcon(plugin),
                click -> openDetail(click.player(), actor, plugin.getName())));
        }

        Menu menu = Menu.rows(owner, ROWS, render("gui.overview.title", "count", plugins.size()))
            .border(MenuItem.display(GuiIcons.filler(icons.material("filler", Material.GRAY_STAINED_GLASS_PANE))))
            .content(entries, Menu.slotRange(0, CONTENT_LAST_SLOT));
        addPaging(menu, viewer);
        menu.set(49, MenuItem.button(
            GuiIcons.icon(icons.material("close", Material.BARRIER), render("gui.close")),
            click -> click.close()));
        menu.open(viewer);
    }

    /**
     * Paging buttons that redraw rather than reopen.
     *
     * <p>The page number is only put in the title when the server can relabel an open
     * window; otherwise the title stays fixed, because the alternative is a visible
     * close-and-reopen on every turn. See {@code MenuSurface}.
     */
    private void addPaging(Menu menu, Player viewer) {
        menu.set(45, MenuItem.button(
            GuiIcons.icon(icons.material("previous-page", Material.ARROW), render("gui.previous")),
            click -> click.menu().previousPage(click.player())));
        menu.set(53, MenuItem.button(
            GuiIcons.icon(icons.material("next-page", Material.ARROW), render("gui.next")),
            click -> click.menu().nextPage(click.player())));
    }

    private ItemStack overviewIcon(Plugin plugin) {
        boolean enabled = plugin.isEnabled();
        boolean guarded = protection.isProtected(plugin);
        Material material = icons.material(
            guarded ? "state.protected" : enabled ? "state.enabled" : "state.disabled",
            guarded ? Material.SHIELD : enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        return GuiIcons.icon(material,
            render("gui.overview.entry-name", "plugin", plugin.getName()),
            renderLines("gui.overview.entry-lore",
                "version", plugin.getPluginMeta().getVersion(),
                "state", messages.raw(enabled ? "state.enabled" : "state.disabled"),
                "protected", messages.raw(guarded ? "common.yes-label" : "common.no-label")));
    }

    // ── 單一插件 ──────────────────────────────────────────────────────────

    /**
     * Resolved by name rather than by holding the {@link Plugin} object.
     *
     * <p>A menu can outlive the plugin it describes — that is the entire point of an
     * unload button. Holding the instance would keep a dead plugin's class loader
     * alive for as long as somebody left the screen open, which is exactly the leak
     * the unload path works to avoid.
     */
    private void openDetail(Player viewer, String actor, String pluginName) {
        var ref = lookup.resolve(pluginName);
        if (!ref.resolved()) {
            send(viewer, PluginLookup.unresolved(messages, ref, pluginName));
            openOverview(viewer, actor);
            return;
        }
        Plugin plugin = ref.require();
        var meta = plugin.getPluginMeta();

        Menu menu = Menu.rows(owner, ROWS, render("gui.detail.title", "plugin", plugin.getName()))
            .border(MenuItem.display(GuiIcons.filler(icons.material("filler", Material.GRAY_STAINED_GLASS_PANE))));

        menu.set(13, MenuItem.display(GuiIcons.icon(
            icons.material("info", Material.BOOK),
            render("gui.detail.info-name", "plugin", plugin.getName()),
            renderLines("gui.detail.info-lore",
                "version", meta.getVersion(),
                "main", meta.getMainClass(),
                "api", meta.getAPIVersion() == null ? messages.raw("common.unknown") : meta.getAPIVersion(),
                "state", messages.raw(plugin.isEnabled() ? "state.enabled" : "state.disabled"),
                "protected", messages.raw(protection.isProtected(plugin)
                    ? "common.yes-label" : "common.no-label")))));

        // Enable and disable are offered as one slot showing the action that applies,
        // rather than two with one always dead. A greyed-out button an operator cannot
        // use is noise on every single screen.
        if (plugin.isEnabled()) {
            action(menu, 29, viewer, "zpm.manage.state", "disable", Material.GRAY_DYE,
                () -> lifecycle.disable(actor, pluginName, true, false), actor, pluginName);
        } else {
            action(menu, 29, viewer, "zpm.manage.state", "enable", Material.LIME_DYE,
                () -> lifecycle.enable(actor, pluginName, true), actor, pluginName);
        }
        action(menu, 31, viewer, "zpm.manage.state", "reload", Material.CLOCK,
            () -> lifecycle.reload(actor, pluginName, true, false), actor, pluginName);
        action(menu, 33, viewer, "zpm.manage.unload", "unload", Material.TNT,
            () -> unload.unload(actor, pluginName, true, false), actor, pluginName);
        action(menu, 40, viewer, "zpm.version.archive", "archive", Material.CHEST,
            () -> archives.archive(actor, pluginName, ""), actor, pluginName);

        menu.set(45, MenuItem.button(
            GuiIcons.icon(icons.material("back", Material.ARROW), render("gui.back")),
            click -> openOverview(click.player(), actor)));
        menu.set(49, MenuItem.button(
            GuiIcons.icon(icons.material("close", Material.BARRIER), render("gui.close")),
            click -> click.close()));
        menu.open(viewer);
    }

    /**
     * One action button, permission-checked and routed through a confirmation screen.
     *
     * <p>The permission is checked twice on purpose: once here, so an operator without
     * it never sees the button, and again when the click fires, because a permission
     * can be revoked while the screen is open.
     */
    private void action(Menu menu, int slot, Player viewer, String permission, String key,
                        Material fallbackIcon, Supplier<Reply> operation,
                        String actor, String pluginName) {
        if (!viewer.hasPermission(permission)) {
            return;
        }
        ItemStack icon = GuiIcons.icon(
            icons.material("action." + key, fallbackIcon),
            render("gui.action." + key + "-name"),
            renderLines("gui.action." + key + "-lore", "plugin", pluginName));
        menu.set(slot, MenuItem.button(icon, click -> {
            if (!click.player().hasPermission(permission)) {
                sendKey(click.player(), "common.no-permission", "permission", permission);
                return;
            }
            openConfirm(click.player(), actor, pluginName, key, operation);
        }));
    }

    // ── 確認 ──────────────────────────────────────────────────────────────

    private void openConfirm(Player viewer, String actor, String pluginName, String key,
                             Supplier<Reply> operation) {
        Menu menu = Menu.rows(owner, 3,
                render("gui.confirm.title", "action", messages.raw("gui.action." + key + "-name")))
            .border(MenuItem.display(GuiIcons.filler(icons.material("filler", Material.GRAY_STAINED_GLASS_PANE))));

        menu.set(11, MenuItem.button(
            GuiIcons.icon(icons.material("confirm", Material.LIME_CONCRETE),
                render("gui.confirm.accept"),
                renderLines("gui.confirm.accept-lore", "plugin", pluginName)),
            click -> runAction(click.player(), actor, pluginName, operation)));
        menu.set(15, MenuItem.button(
            GuiIcons.icon(icons.material("cancel", Material.RED_CONCRETE), render("gui.confirm.decline")),
            click -> openDetail(click.player(), actor, pluginName)));
        menu.open(viewer);
    }

    /**
     * Runs the operation on the global region, then returns the viewer to the detail
     * screen with the result.
     *
     * <p>The hop is the whole reason this is a method rather than a lambda in the
     * button: a click handler runs on the player's region thread, and enabling a
     * plugin from there mutates registry state nothing on that thread owns.
     */
    private void runAction(Player viewer, String actor, String pluginName, Supplier<Reply> operation) {
        sched.global(() -> {
            Reply reply = operation.get();
            send(viewer, reply);
            // Reopening rather than redrawing: the operation may have changed which
            // buttons apply, and open() rebuilds from current state.
            openDetail(viewer, actor, pluginName);
        });
    }

    // ── 文字 ──────────────────────────────────────────────────────────────

    private Component render(String key, Object... placeholders) {
        return messages.render(messages.raw(key, placeholders));
    }

    private List<Component> renderLines(String key, Object... placeholders) {
        List<Component> lines = new ArrayList<>();
        for (String line : messages.rawList(key, placeholders)) {
            lines.add(messages.render(line));
        }
        return lines;
    }

    private void send(Player viewer, Reply reply) {
        for (String line : reply.lines()) {
            viewer.sendMessage(messages.render(messages.prefix() + line));
        }
    }

    private void sendKey(Player viewer, String key, Object... placeholders) {
        viewer.sendMessage(messages.render(messages.prefix() + messages.raw(key, placeholders)));
    }
}
