package tw.linsy.aelorn.plugins.service;

import java.util.List;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.model.Reply;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;

/**
 * Re-reading everything, in the order that leaves the plugin usable if a step fails.
 *
 * <p>Its own service because the order is a decision, not an implementation detail:
 *
 * <ol>
 *   <li>Settings, so everything downstream sees the new values.</li>
 *   <li>The message catalog, which may now render with a different serializer.</li>
 *   <li>The descriptor cache, so a jar edited in place is re-read.</li>
 *   <li>The folder watcher last, because it reads the new interval — and because a
 *       failure here leaves the previous watcher running rather than none at all.</li>
 * </ol>
 *
 * <p>Putting this in the command layer would have meant a dispatcher that knows the
 * dependency order between four subsystems, which is exactly the knowledge that gets
 * out of date when a fifth is added.
 */
public final class ConfigReloadService {

    private final SettingsStore settings;
    private final MessageCatalog messages;
    private final JarIndex jars;
    private final JarWatchService watcher;
    private final AuditLog audit;

    public ConfigReloadService(SettingsStore settings, MessageCatalog messages, JarIndex jars,
                               JarWatchService watcher, AuditLog audit) {
        this.settings = settings;
        this.messages = messages;
        this.jars = jars;
        this.watcher = watcher;
        this.audit = audit;
    }

    /** Runs on the global region; the watcher restart schedules its own async work. */
    public Reply reload(String actor) {
        settings.reload();
        messages.reload();
        jars.clearCache();
        watcher.restart();

        audit.record(actor, "config-reload", "PluginsManager", "SUCCESS",
            messages.plain("audit.config-reload"));
        return Reply.ok(List.of(messages.raw("config.reloaded",
            "files", settings.managedFileNames().size(),
            "watcher", messages.raw(settings.manager().scanner().watchEnabled()
                ? "common.yes-label" : "common.no-label"))));
    }
}
