package tw.linsy.aelorn.discordbridge;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePreProcessEvent;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AelornDiscordBridgePlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("status", "audit", "reload", "syncpack");

    private final BridgeMetrics metrics = new BridgeMetrics();
    private final DiscordEvents discordEvents = new DiscordEvents();
    private volatile BridgeSettings settings;
    private WindowRateLimiter minecraftLimiter;
    private WindowRateLimiter discordLimiter;
    private WindowRateLimiter shareLimiter;
    private WindowRateLimiter noticeLimiter;
    private volatile ResourcePackSynchronizer.Result lastPackSync;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadBridgeSettings();
        // Must run synchronously: this plugin loads before InteractiveChatDiscordSrvAddon
        // (plugin.yml loadbefore) so the pack copy has to land before the addon reads it.
        synchronizeResourcePack(true);
        PluginCommand command = Objects.requireNonNull(getCommand("aelornbridge"),
            "aelornbridge command is missing from plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        DiscordSRV.api.subscribe(discordEvents);
        getLogger().info("Subscribed to DiscordSRV API events.");
        schedulePostStartupResourceAudit();
        if (DiscordSRV.isReady) {
            scheduleAudit("enable");
        } else {
            getLogger().info("Waiting for DiscordSRV gateway readiness.");
        }
    }

    @Override
    public void onDisable() {
        try {
            DiscordSRV.api.unsubscribe(discordEvents);
        } catch (Throwable cleanupFailure) {
            getLogger().log(Level.FINE, "DiscordSRV listener cleanup skipped.", cleanupFailure);
        }
    }

    private void reloadBridgeSettings() {
        reloadConfig();
        BridgeSettings loaded = BridgeSettings.from(getConfig());
        this.settings = loaded;
        if (minecraftLimiter == null) {
            minecraftLimiter = new WindowRateLimiter(loaded.minecraftMaxMessages(), loaded.minecraftWindowSeconds());
            discordLimiter = new WindowRateLimiter(loaded.discordMaxMessages(), loaded.discordWindowSeconds());
            shareLimiter = new WindowRateLimiter(loaded.shareMaxMessages(), loaded.shareWindowSeconds());
            noticeLimiter = new WindowRateLimiter(1, 3);
        } else {
            minecraftLimiter.reconfigure(loaded.minecraftMaxMessages(), loaded.minecraftWindowSeconds());
            discordLimiter.reconfigure(loaded.discordMaxMessages(), loaded.discordWindowSeconds());
            shareLimiter.reconfigure(loaded.shareMaxMessages(), loaded.shareWindowSeconds());
            noticeLimiter.reconfigure(1, 3);
        }
    }

    /** Audits read config files from disk, so they always run on the async scheduler. */
    private void scheduleAudit(String trigger) {
        getServer().getAsyncScheduler().runNow(this, task -> {
            BridgeAuditor.Report report = BridgeAuditor.audit(this, settings);
            Level level = report.failures() > 0L ? Level.WARNING : Level.INFO;
            getLogger().log(level, "Bridge audit ({0}): {1} pass, {2} warning, {3} failure.",
                new Object[]{trigger, report.passes(), report.warnings(), report.failures()});
            for (BridgeAuditor.Entry entry : report.entries()) {
                if (entry.level() == BridgeAuditor.Level.FAIL) {
                    getLogger().warning("[AUDIT] " + entry.message());
                }
            }
        });
    }

    private void notifyPlayer(Player player, String message) {
        if (noticeLimiter.tryAcquire(player.getUniqueId().toString()).allowed()) {
            String colored = color(settings.messagePrefix() + message);
            player.getScheduler().run(this, task -> player.sendMessage(colored), null);
        }
    }

    private void rejectMinecraft(GameChatMessagePreProcessEvent event, String message) {
        event.setCancelled(true);
        metrics.minecraftBlocked();
        notifyPlayer(event.getPlayer(), message);
    }

    private void rejectDiscord(DiscordGuildMessagePreProcessEvent event, String reason) {
        event.setCancelled(true);
        metrics.discordBlocked();
        getLogger().fine(() -> "Blocked Discord message: " + reason);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> sendStatus(sender);
            case "audit" -> {
                sender.sendMessage(color("&eRunning the bridge audit asynchronously..."));
                getServer().getAsyncScheduler().runNow(this, task -> {
                    BridgeAuditor.Report report = BridgeAuditor.audit(this, settings);
                    sendMessageSafely(sender, "&3Bridge audit: &a" + report.passes() + " pass &8/ &e"
                        + report.warnings() + " warning &8/ &c" + report.failures() + " failure");
                    for (BridgeAuditor.Entry entry : report.entries()) {
                        String prefix = switch (entry.level()) {
                            case PASS -> "&a";
                            case WARN -> "&e";
                            case FAIL -> "&c";
                        };
                        sendMessageSafely(sender, prefix + "[" + entry.level() + "] &7" + entry.message());
                    }
                });
            }
            case "reload" -> {
                reloadBridgeSettings();
                sender.sendMessage(color("&aAelornDiscordBridge configuration reloaded."));
                scheduleAudit("reload");
            }
            case "syncpack" -> {
                sender.sendMessage(color("&eChecking the Nexo/Aeloria resource pack asynchronously..."));
                getServer().getAsyncScheduler().runNow(this, task -> {
                    boolean addonLoaded = getServer().getPluginManager().isPluginEnabled("InteractiveChatDiscordSrvAddon");
                    ResourcePackSynchronizer.Result result = synchronizeResourcePack(!addonLoaded);
                    sendMessageSafely(sender, describeResourcePackResult(result));
                });
            }
            default -> sender.sendMessage(color("&eUsage: /" + label + " <status|audit|reload|syncpack>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private void sendStatus(CommandSender sender) {
        DiscordSRV discordSrv = DiscordSRV.getPlugin();
        BridgeMetrics.Snapshot snapshot = metrics.snapshot();
        String gateway = DiscordSRV.isReady && discordSrv.getJda() != null ? "&aCONNECTED" : "&cDISCONNECTED";
        var mainChannel = discordSrv.getMainTextChannel();
        String channel = mainChannel == null ? "unavailable"
            : "#" + mainChannel.getName() + " (" + mainChannel.getId() + ")";
        var consoleChannel = discordSrv.getConsoleChannel();
        String console = consoleChannel == null ? "unavailable"
            : "#" + consoleChannel.getName() + " (" + consoleChannel.getId() + ")";
        int linkedAccounts = discordSrv.getAccountLinkManager() == null ? 0
            : discordSrv.getAccountLinkManager().getLinkedAccountCount();
        sender.sendMessage(color("&6AelornDiscordBridge &7v" + getPluginMeta().getVersion()));
        sender.sendMessage(color("&7Gateway: " + gateway));
        sender.sendMessage(color("&7Channel: &f" + channel));
        sender.sendMessage(color("&7Console channel: &f" + console));
        sender.sendMessage(color("&7Linked accounts: &f" + linkedAccounts));
        sender.sendMessage(color("&7Minecraft: &a" + snapshot.minecraftForwarded()
            + " forwarded &8/ &c" + snapshot.minecraftBlocked() + " blocked"));
        sender.sendMessage(color("&7Discord: &a" + snapshot.discordForwarded()
            + " forwarded &8/ &c" + snapshot.discordBlocked() + " blocked"));
        sender.sendMessage(color("&7Interactive shares: &f" + snapshot.interactiveShares()));
        ResourcePackSynchronizer.Result packSync = lastPackSync;
        if (packSync != null) {
            sender.sendMessage(color("&7Resource pack: &f" + packSync.status()));
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private ResourcePackSynchronizer.Result synchronizeResourcePack(boolean allowWrite) {
        try {
            ResourcePackSynchronizer.Result result = ResourcePackSynchronizer.synchronize(
                getDataFolder().getParentFile().toPath(), settings.resourcePackSource(),
                settings.resourcePackCopy(), settings.resourcePackAuditEnabled(), allowWrite);
            lastPackSync = result;
            if (result.changed()) {
                getLogger().info("Synchronized the Nexo/Aeloria resource pack for Discord rendering ("
                    + result.bytes() + " bytes).");
            }
            return result;
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Resource pack synchronization failed.", exception);
            ResourcePackSynchronizer.Result failed =
                new ResourcePackSynchronizer.Result(ResourcePackSynchronizer.Status.FAILED, 0L);
            lastPackSync = failed;
            return failed;
        }
    }

    private void schedulePostStartupResourceAudit() {
        getServer().getAsyncScheduler().runDelayed(this, task -> {
            ResourcePackSynchronizer.Result result = synchronizeResourcePack(false);
            switch (result.status()) {
                case SOURCE_MISSING -> getLogger().warning(
                    "Nexo/Aeloria resource pack is unavailable; Discord textures may be incomplete.");
                case PENDING_RESTART -> getLogger().warning(
                    "Nexo/Aeloria resource pack changed after the addon loaded; it will be synchronized before the addon on the next restart.");
                case LOCKED -> getLogger().warning(
                    "Addon resource pack is locked; keeping the current copy until the next restart.");
                case FAILED -> getLogger().warning(
                    "Resource pack audit failed; keeping the current addon copy.");
                default -> {
                }
            }
        }, 20L, TimeUnit.SECONDS);
    }

    private String describeResourcePackResult(ResourcePackSynchronizer.Result result) {
        return switch (result.status()) {
            case SOURCE_MISSING -> "&cNexo/Aeloria resource pack is missing.";
            case PENDING_RESTART -> "&eNexo/Aeloria changed after the addon loaded; restart to synchronize safely.";
            case LOCKED -> "&cAddon resource pack is locked; the current copy was kept unchanged.";
            case FAILED -> "&cResource pack synchronization failed; check the server log.";
            case UPDATED -> "&aNexo/Aeloria resource pack copied and verified.";
            case CURRENT -> "&aAddon resource pack already matches Nexo/Aeloria.";
            case DISABLED -> "&eResource pack synchronization is disabled.";
        };
    }

    private void sendMessageSafely(CommandSender sender, String message) {
        String colored = color(message);
        if (sender instanceof Player player) {
            player.getScheduler().run(this, task -> player.sendMessage(colored), null);
        } else {
            getServer().getGlobalRegionScheduler().execute(this, () -> sender.sendMessage(colored));
        }
    }

    private final class DiscordEvents {

        @Subscribe(priority = ListenerPriority.LOWEST)
        public void onMinecraftChat(GameChatMessagePreProcessEvent event) {
            if (event.isCancelled()) {
                return;
            }
            metrics.minecraftSeen();
            BridgeSettings current = settings;
            String message = event.getMessage();
            BridgeTextPolicy.Inspection inspection = BridgeTextPolicy.inspect(
                message, current.maxMessageCodePoints(), current.blockDirectionalControlCharacters());
            if (!inspection.allowed()) {
                rejectMinecraft(event, inspection.violation() == BridgeTextPolicy.Violation.TOO_LONG
                    ? current.messageTooLong()
                    : current.unsafeCharacters());
                return;
            }

            int shares = BridgeTextPolicy.countInteractiveShares(message);
            if (shares > current.maxSharePlaceholders()) {
                rejectMinecraft(event, current.tooManyShares()
                    .replace("{limit}", String.valueOf(current.maxSharePlaceholders())));
                return;
            }

            if (!event.getPlayer().hasPermission("aelorn.discordbridge.bypass")) {
                String playerKey = event.getPlayer().getUniqueId().toString();
                WindowRateLimiter.Decision decision = minecraftLimiter.tryAcquire(playerKey);
                if (!decision.allowed()) {
                    rejectMinecraft(event, current.rateLimited()
                        .replace("{seconds}", String.valueOf(decision.retryAfterSeconds())));
                    return;
                }
                if (shares > 0) {
                    WindowRateLimiter.Decision shareDecision = shareLimiter.tryAcquire(playerKey);
                    if (!shareDecision.allowed()) {
                        rejectMinecraft(event, current.shareRateLimited()
                            .replace("{seconds}", String.valueOf(shareDecision.retryAfterSeconds())));
                        return;
                    }
                }
            }

            metrics.minecraftForwarded();
            metrics.interactiveShares(shares);
        }

        @Subscribe(priority = ListenerPriority.LOWEST)
        public void onDiscordChat(DiscordGuildMessagePreProcessEvent event) {
            if (event.isCancelled()) {
                return;
            }
            metrics.discordSeen();
            Message message = event.getMessage();
            if (message == null || event.getAuthor() == null) {
                rejectDiscord(event, "missing message or author");
                return;
            }
            if (event.getAuthor().isBot() || message.isWebhookMessage()) {
                rejectDiscord(event, "bot or webhook source");
                return;
            }

            BridgeSettings current = settings;
            if (current.rejectUnexpectedChannel()) {
                if (!current.expectedGuildId().isEmpty()
                    && !current.expectedGuildId().equals(event.getGuild().getId())) {
                    rejectDiscord(event, "unexpected guild");
                    return;
                }
                if (!current.expectedChannelId().isEmpty()
                    && !current.expectedChannelId().equals(event.getChannel().getId())) {
                    rejectDiscord(event, "unexpected channel");
                    return;
                }
            }

            if (current.requireLinkedDiscordAccount()
                && DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getAuthor().getId()) == null) {
                rejectDiscord(event, "unlinked Discord account");
                return;
            }

            String content = message.getContentRaw();
            if (!content.isBlank()) {
                BridgeTextPolicy.Inspection inspection = BridgeTextPolicy.inspect(
                    content, current.maxMessageCodePoints(), current.blockDirectionalControlCharacters());
                if (!inspection.allowed()) {
                    rejectDiscord(event, "message text policy");
                    return;
                }
            } else if (message.getAttachments().isEmpty()) {
                rejectDiscord(event, "empty message");
                return;
            }

            if (current.blockMassMentions() && (message.mentionsEveryone()
                || !message.getMentionedRoles().isEmpty()
                || BridgeTextPolicy.containsMassMentionText(content))) {
                rejectDiscord(event, "mass mention");
                return;
            }

            if (message.getAttachments().size() > current.maxAttachments()) {
                rejectDiscord(event, "too many attachments");
                return;
            }
            boolean oversizedAttachment = message.getAttachments().stream()
                .anyMatch(attachment -> attachment.getSize() > current.maxAttachmentBytes());
            if (oversizedAttachment) {
                rejectDiscord(event, "attachment too large");
                return;
            }

            WindowRateLimiter.Decision decision = discordLimiter.tryAcquire(event.getAuthor().getId());
            if (!decision.allowed()) {
                rejectDiscord(event, "rate limit");
                return;
            }
            metrics.discordForwarded();
        }

        @Subscribe(priority = ListenerPriority.MONITOR)
        public void onDiscordReady(DiscordReadyEvent event) {
            scheduleAudit("discord-ready");
        }
    }
}
