package tw.linsy.aelorn.plugins.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;

/**
 * Which plugins refuse to be touched.
 *
 * Protection comes from three sources and they are combined here rather than at
 * each call site: the explicit {@code protected-plugins} list, the members of
 * every group marked {@code protected}, and this plugin itself when
 * {@code protect-self} is on. An admin who protects a group should not also have
 * to list its members.
 *
 * <p>Matching uses every name a plugin answers to, including its {@code provides}
 * aliases, so protecting {@code Vault} also protects whatever registered itself as
 * Vault.
 */
public final class ProtectionService {

    private final JavaPlugin owner;
    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final AuditLog audit;

    public ProtectionService(JavaPlugin owner, SettingsStore settings,
                             MessageCatalog messages, AuditLog audit) {
        this.owner = owner;
        this.settings = settings;
        this.messages = messages;
        this.audit = audit;
    }

    /** The effective protected set: config list, locked group members, and self. */
    public Set<String> effectiveNames() {
        Set<String> names = new LinkedHashSet<>(settings.manager().guards().protectedPlugins());
        names.addAll(settings.groups().lockedMembers());
        if (settings.manager().guards().protectSelf()) {
            names.add(owner.getName().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    public boolean isProtected(Plugin plugin) {
        Set<String> effective = effectiveNames();
        for (String name : PluginLookup.namesFor(plugin)) {
            if (effective.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public Reply list() {
        List<String> names = new ArrayList<>(effectiveNames());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return Reply.ok(messages.raw("protect.list", "plugins", Texts.join(messages, names)));
    }

    /**
     * Adds a name to {@code protected-plugins} and saves.
     *
     * The name is stored as typed, not as resolved: protecting a plugin that is not
     * loaded yet is a legitimate thing to want, and refusing it would mean an admin
     * cannot protect something before installing it.
     */
    public Reply add(String actor, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            return Reply.fail(messages.raw("common.missing-plugin"));
        }
        List<String> stored = new ArrayList<>(owner.getConfig().getStringList("protected-plugins"));
        if (stored.stream().noneMatch(entry -> entry.equalsIgnoreCase(name))) {
            stored.add(name);
        }
        owner.getConfig().set("protected-plugins", stored);
        owner.saveConfig();
        settings.reload();
        audit.record(actor, "protect-add", name, "SUCCESS",
            messages.plain("audit.protect-add"));
        return Reply.ok(messages.raw("protect.added", "plugin", name));
    }

    public Reply remove(String actor, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            return Reply.fail(messages.raw("common.missing-plugin"));
        }
        if (name.equalsIgnoreCase(owner.getName()) && settings.manager().guards().protectSelf()) {
            return Reply.fail(messages.raw("protect.self-locked"));
        }
        List<String> stored = new ArrayList<>(owner.getConfig().getStringList("protected-plugins"));
        boolean removed = stored.removeIf(entry -> entry.equalsIgnoreCase(name));
        owner.getConfig().set("protected-plugins", stored);
        owner.saveConfig();
        settings.reload();

        // Removing from the list does not necessarily unprotect: a locked group
        // still covers its members, and saying "removed" would be a lie the admin
        // only discovers on the next refused operation.
        if (settings.groups().lockedMembers().contains(name.toLowerCase(Locale.ROOT))) {
            audit.record(actor, "protect-remove", name, "WARN",
                messages.plain("audit.protect-remove-still-locked"));
            return Reply.ok(messages.raw("protect.removed-still-locked", "plugin", name));
        }
        audit.record(actor, "protect-remove", name, removed ? "SUCCESS" : "WARN",
            messages.plain("audit.protect-remove"));
        return Reply.ok(messages.raw("protect.removed", "plugin", name));
    }
}
