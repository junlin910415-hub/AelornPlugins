package tw.linsy.aelorn.plugins.service;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;

/**
 * The checks that run before anything destructive, in one place.
 *
 * Four of them — confirmation, self-protection, the protected list, and enabled
 * dependants — and every state-changing command needs the same four in the same
 * order. The previous version inlined them into each operation, which is how
 * {@code unload} ended up checking protection but not confirmation on one path.
 *
 * <p>Each method returns {@code null} when the operation may proceed and a
 * {@link Reply} when it may not. That reads oddly once and correctly everywhere:
 * the call site is {@code if (blocked != null) return blocked;}, so a forgotten
 * check is visible as a missing statement rather than an inverted boolean.
 */
public final class OperationGuards {

    /** What happened to the dependants of a plugin about to change state. */
    public record Dependants(@Nullable Reply blocked, List<Plugin> disabled, List<String> notes) {

        public Dependants {
            disabled = List.copyOf(disabled);
            notes = List.copyOf(notes);
        }

        static Dependants clear() {
            return new Dependants(null, List.of(), List.of());
        }

        static Dependants stop(Reply reply) {
            return new Dependants(reply, List.of(), List.of());
        }

        public boolean allowed() {
            return blocked == null;
        }
    }

    private final Plugin owner;
    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final ProtectionService protection;
    private final DependencyService dependencies;

    public OperationGuards(Plugin owner, SettingsStore settings, MessageCatalog messages,
                           ProtectionService protection, DependencyService dependencies) {
        this.owner = owner;
        this.settings = settings;
        this.messages = messages;
        this.protection = protection;
        this.dependencies = dependencies;
    }

    /**
     * @param usage the command to re-send, already assembled by the caller, so the
     *              message shows the flags the admin actually typed
     * @return {@code null} when confirmation is not required or was given
     */
    public @Nullable Reply confirmation(String usage, boolean confirmed) {
        if (confirmed || !settings.manager().guards().requireConfirmation()) {
            return null;
        }
        return Reply.fail(messages.raw("common.needs-confirm", "usage", usage));
    }

    /**
     * Refuses to act on this plugin itself, and on anything protected.
     *
     * Self is checked separately from the protected list rather than relying on
     * {@code protect-self}: turning that setting off is a legitimate thing to do to
     * unprotect the config entry, and it must not also make the manager able to
     * disable itself mid-operation.
     */
    public @Nullable Reply protection(Plugin plugin, String action) {
        if (plugin == owner) {
            return Reply.fail(messages.raw("guard.self", "action", action));
        }
        if (protection.isProtected(plugin)) {
            return Reply.fail(messages.raw("guard.protected", "plugin", plugin.getName()));
        }
        return null;
    }

    /**
     * Handles the plugins that would break, disabling them when forced.
     *
     * @param temporary true for a reload, where the disabled dependants are to be
     *                  brought back afterwards; changes only the wording, but that
     *                  wording is the difference between an admin re-enabling them
     *                  by hand and correctly leaving them alone
     */
    public Dependants hardDependants(Plugin target, boolean force, boolean temporary) {
        List<Plugin> dependants = dependencies.dependants(target, true, true);
        if (dependants.isEmpty()) {
            return Dependants.clear();
        }
        if (settings.manager().guards().blockHardDependents() && !force) {
            return Dependants.stop(Reply.fail(messages.raw("guard.dependants-block",
                "plugin", target.getName(),
                "dependants", Texts.join(messages, PluginLookup.namesOf(dependants)))));
        }
        List<Plugin> disabled = new ArrayList<>();
        for (Plugin dependant : dependants) {
            if (dependant.isEnabled()) {
                Bukkit.getPluginManager().disablePlugin(dependant);
                disabled.add(dependant);
            }
        }
        List<String> notes = List.of(messages.raw(
            temporary ? "guard.dependants-suspended" : "guard.dependants-disabled",
            "dependants", Texts.join(messages, PluginLookup.namesOf(disabled))));
        return new Dependants(null, disabled, notes);
    }

    /**
     * A note listing soft dependants, which usually need a reload of their own to
     * notice the change but must not be touched automatically.
     */
    public List<String> softDependantNotes(Plugin target) {
        if (!settings.manager().guards().warnSoftDependents()) {
            return List.of();
        }
        List<String> names = dependencies.dependantNames(target, false, true);
        if (names.isEmpty()) {
            return List.of();
        }
        return List.of(messages.raw("guard.soft-dependants",
            "dependants", Texts.join(messages, names)));
    }
}
