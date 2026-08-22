package tw.linsy.aelorn.plugins;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import tw.linsy.aelorn.plugins.audit.AuditLog;
import tw.linsy.aelorn.plugins.audit.AuditSink;
import tw.linsy.aelorn.plugins.audit.AuditSinks;
import tw.linsy.aelorn.plugins.command.ManagementCommand;
import tw.linsy.aelorn.plugins.command.ReplySender;
import tw.linsy.aelorn.plugins.config.SettingsStore;
import tw.linsy.aelorn.plugins.gui.GuiService;
import tw.linsy.aelorn.plugins.gui.GuiServices;
import tw.linsy.aelorn.plugins.platform.Platform;
import tw.linsy.aelorn.plugins.service.CommandIndexService;
import tw.linsy.aelorn.plugins.service.ConfigReloadService;
import tw.linsy.aelorn.plugins.service.DependencyService;
import tw.linsy.aelorn.plugins.service.GroupService;
import tw.linsy.aelorn.plugins.service.JarIndex;
import tw.linsy.aelorn.plugins.service.JarWatchService;
import tw.linsy.aelorn.plugins.service.OperationGuards;
import tw.linsy.aelorn.plugins.service.PluginLifecycleService;
import tw.linsy.aelorn.plugins.service.PluginLookup;
import tw.linsy.aelorn.plugins.service.PluginUnloadService;
import tw.linsy.aelorn.plugins.service.ProtectionService;
import tw.linsy.aelorn.plugins.service.ReportService;
import tw.linsy.aelorn.plugins.service.VersionArchiveService;

/**
 * Startup and wiring. No behaviour.
 *
 * <p>Everything below this class is either configuration, a model, a platform
 * abstraction, a service, or the command layer. This file exists to decide the order
 * those are built in, and it should stay boring — if a rule ever needs adding here, it
 * belongs in a service instead.
 *
 * <p>Construction order is a dependency chain, not a preference: the platform is probed
 * first because it decides how everything else schedules and renders, then settings,
 * then the audit trail, then services in dependency order, then the command last.
 */
public final class PluginsManagerPlugin extends JavaPlugin {

    private static final String MESSAGES_FILE = "messages.yml";
    private static final String COMMAND_NAME = "zpm";

    private @Nullable Platform platform;
    private @Nullable AuditLog audit;
    private @Nullable JarWatchService watcher;
    private @Nullable ScheduledTask auditFlushTask;

    @Override
    public void onEnable() {
        Platform detected = Platform.detect(this, MESSAGES_FILE);
        this.platform = detected;

        SettingsStore settings = new SettingsStore(this);

        AuditSink sink = AuditSinks.open(settings.manager().audit(),
            getDataFolder().toPath(), getLogger());
        AuditLog auditLog = new AuditLog(sink, () -> settings.manager().audit().enabled());
        this.audit = auditLog;
        scheduleAuditFlush(detected, settings);

        JarIndex jars = new JarIndex();
        PluginLookup lookup = new PluginLookup();
        ProtectionService protection =
            new ProtectionService(this, settings, detected.messages(), auditLog);
        DependencyService dependencies = new DependencyService(detected.internals(),
            settings::manager, getLogger());
        OperationGuards guards = new OperationGuards(this, settings, detected.messages(),
            protection, dependencies);

        PluginLifecycleService lifecycle = new PluginLifecycleService(settings,
            detected.messages(), auditLog, lookup, guards, jars, detected.internals(), getLogger());
        PluginUnloadService unload = new PluginUnloadService(settings, detected.messages(),
            auditLog, lookup, guards, detected.internals(), detected.profile(), getLogger());
        GroupService groups = new GroupService(settings, detected.messages(), auditLog,
            lifecycle, protection);
        CommandIndexService commandIndex = new CommandIndexService(settings,
            detected.messages(), auditLog, detected.internals());
        VersionArchiveService archives = new VersionArchiveService(getDataFolder().toPath(),
            settings, detected.messages(), auditLog, jars, getLogger());
        ReportService reports = new ReportService(detected, settings, detected.messages(),
            auditLog, lookup, protection, dependencies, jars);

        JarWatchService jarWatcher = new JarWatchService(this, settings, detected.messages(),
            auditLog, jars, detected.profile(), detected.sched(), getLogger());
        this.watcher = jarWatcher;
        ConfigReloadService configReload = new ConfigReloadService(settings,
            detected.messages(), jars, jarWatcher, auditLog);

        GuiService gui = GuiServices.open(this, detected.messages(), detected.sched(),
            lookup, protection, lifecycle, unload, archives);

        ReplySender replies = new ReplySender(detected.messages(), settings, detected.sched());
        ManagementCommand executor = new ManagementCommand(detected.messages(), settings,
            detected.sched(), replies, lookup, reports, lifecycle, unload, protection, groups,
            commandIndex, archives, configReload, auditLog, gui);

        if (!bindCommand(executor)) {
            return;
        }
        jarWatcher.start();

        auditLog.record("system", "enable", getName(), "SUCCESS",
            detected.messages().plain("audit.enable-plugin"));
        getLogger().info("已啟用。");
    }

    @Override
    public void onDisable() {
        // Reverse of enable: stop producing records before flushing them, and cancel our
        // own scheduled work before the schedulers go away with the plugin.
        if (watcher != null) {
            watcher.stop();
        }
        if (auditFlushTask != null) {
            auditFlushTask.cancel();
            auditFlushTask = null;
        }
        if (audit != null && platform != null) {
            audit.record("system", "disable", getName(), "SUCCESS",
                platform.messages().plain("audit.disable-plugin"));
            audit.close();
        }
        if (platform != null) {
            platform.sched().cancelAll();
        }
    }

    /**
     * Periodically writes out a partial audit batch.
     *
     * Skipped entirely for the file sink, which writes each record as it arrives: a
     * repeating task that can only ever call a no-op is the sort of thing that later
     * gets read as evidence the file sink buffers.
     */
    private void scheduleAuditFlush(Platform platform, SettingsStore settings) {
        AuditLog auditLog = this.audit;
        if (auditLog == null || !auditLog.buffers()) {
            return;
        }
        long seconds = settings.manager().audit().sql().flushSeconds();
        this.auditFlushTask = platform.sched().asyncRepeating(auditLog::flush,
            seconds, seconds, TimeUnit.SECONDS);
    }

    /**
     * @return false when {@code plugin.yml} and this class disagree about the command
     *         name, which is a packaging error rather than a runtime condition — so the
     *         plugin disables itself instead of running with no way to reach it
     */
    private boolean bindCommand(ManagementCommand executor) {
        PluginCommand command = getCommand(COMMAND_NAME);
        if (command == null) {
            getLogger().severe("plugin.yml 缺少 " + COMMAND_NAME + " 指令設定，插件無法使用。");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        return true;
    }
}
