package tw.linsy.aelorn.discordbridge;

import github.scarsz.discordsrv.DiscordSRV;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Cross-checks DiscordSRV / InteractiveChat / addon configuration.
 * Reads several YAML files from disk; run on the async scheduler only.
 */
final class BridgeAuditor {

    private BridgeAuditor() {
    }

    static Report audit(AelornDiscordBridgePlugin plugin, BridgeSettings settings) {
        List<Entry> entries = new ArrayList<>();
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        checkPlugin(entries, pluginManager, "DiscordSRV");
        checkPlugin(entries, pluginManager, "InteractiveChat");
        checkPlugin(entries, pluginManager, "InteractiveChatDiscordSrvAddon");

        DiscordSRV discordSrv = DiscordSRV.getPlugin();
        if (DiscordSRV.isReady && discordSrv.getJda() != null) {
            entries.add(Entry.pass("DiscordSRV gateway is connected."));
        } else {
            entries.add(Entry.fail("DiscordSRV gateway is not connected."));
        }

        if (discordSrv.getMainGuild() == null) {
            entries.add(Entry.fail("DiscordSRV main guild is unavailable."));
        } else if (!settings.expectedGuildId().isEmpty()
            && !settings.expectedGuildId().equals(discordSrv.getMainGuild().getId())) {
            entries.add(Entry.fail("Connected Discord guild does not match bridge configuration."));
        } else {
            entries.add(Entry.pass("Discord guild matches bridge configuration."));
        }

        auditRuntimeChannels(entries, discordSrv, settings);

        File pluginsFolder = plugin.getDataFolder().getParentFile();
        auditDiscordSrvConfig(entries, new File(pluginsFolder, "DiscordSRV/config.yml"), settings);
        auditInteractiveChatConfig(entries, new File(pluginsFolder, "InteractiveChat/config.yml"), settings);
        auditAddonConfig(entries, new File(pluginsFolder, "InteractiveChatDiscordSrvAddon/config.yml"));
        auditResourcePack(entries, pluginsFolder, settings);
        return new Report(List.copyOf(entries));
    }

    private static void checkPlugin(List<Entry> entries, PluginManager pluginManager, String name) {
        Plugin plugin = pluginManager.getPlugin(name);
        if (plugin != null && plugin.isEnabled()) {
            entries.add(Entry.pass(name + " is enabled (" + plugin.getPluginMeta().getVersion() + ")."));
        } else {
            entries.add(Entry.fail(name + " is missing or disabled."));
        }
    }

    private static void auditDiscordSrvConfig(List<Entry> entries, File file, BridgeSettings settings) {
        if (!file.isFile()) {
            entries.add(Entry.fail("DiscordSRV config.yml is missing."));
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String botToken = config.getString("BotToken", "");
        if (botToken != null && !botToken.isBlank() && !botToken.equalsIgnoreCase("BOTTOKEN")) {
            entries.add(Entry.warn("DiscordSRV bot token is present; only a connected gateway proves that it is valid."));
        } else {
            entries.add(Entry.fail("DiscordSRV bot token is not configured."));
        }

        String mappedChannel = config.getString("Channels." + settings.gameChannel());
        if (settings.expectedChannelId().equals(mappedChannel)) {
            entries.add(Entry.pass("DiscordSRV game channel mapping is correct."));
        } else {
            entries.add(Entry.fail("DiscordSRV game channel mapping is incorrect."));
        }

        String consoleChannel = config.getString("DiscordConsoleChannelId", "").trim();
        if (!settings.expectedConsoleChannelId().isEmpty()
            && settings.expectedConsoleChannelId().equals(consoleChannel)) {
            entries.add(Entry.pass("DiscordSRV console channel mapping is correct."));
        } else {
            entries.add(Entry.fail("DiscordSRV console channel mapping is incorrect."));
        }

        expectTrue(entries, config, "DiscordChatChannelRequireLinkedAccount");
        expectTrue(entries, config, "DiscordChatChannelBlockBots");
        expectFalse(entries, config, "DiscordChatChannelConsoleCommandEnabled");
        expectFalse(entries, config, "Experiment_WebhookChatMessageDelivery");
    }

    private static void auditRuntimeChannels(
        List<Entry> entries, DiscordSRV discordSrv, BridgeSettings settings) {
        var mappedChannel = discordSrv.getDestinationTextChannelForGameChannelName(settings.gameChannel());
        if (mappedChannel == null) {
            entries.add(Entry.fail("DiscordSRV runtime game channel is unavailable."));
        } else if (!settings.expectedChannelId().isEmpty()
            && !settings.expectedChannelId().equals(mappedChannel.getId())) {
            entries.add(Entry.fail("DiscordSRV runtime game channel does not match bridge configuration."));
        } else {
            entries.add(Entry.pass("DiscordSRV runtime game channel matches bridge configuration."));
        }

        var mainChannel = discordSrv.getMainTextChannel();
        if (mainChannel == null) {
            entries.add(Entry.fail("DiscordSRV main chat channel is unavailable."));
        } else if (!settings.expectedChannelId().isEmpty()
            && !settings.expectedChannelId().equals(mainChannel.getId())) {
            entries.add(Entry.fail("DiscordSRV main chat channel does not match bridge configuration."));
        } else {
            entries.add(Entry.pass("DiscordSRV main chat channel matches bridge configuration."));
        }

        var consoleChannel = discordSrv.getConsoleChannel();
        if (consoleChannel == null) {
            entries.add(Entry.fail("DiscordSRV runtime console channel is unavailable."));
        } else if (!settings.expectedConsoleChannelId().isEmpty()
            && !settings.expectedConsoleChannelId().equals(consoleChannel.getId())) {
            entries.add(Entry.fail("DiscordSRV runtime console channel does not match bridge configuration."));
        } else {
            entries.add(Entry.pass("DiscordSRV runtime console channel matches bridge configuration."));
        }
    }

    private static void auditInteractiveChatConfig(List<Entry> entries, File file, BridgeSettings settings) {
        if (!file.isFile()) {
            entries.add(Entry.fail("InteractiveChat config.yml is missing."));
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int maxPlaceholders = config.getInt("Settings.MaxPlaceholders", -1);
        if (maxPlaceholders >= 1 && maxPlaceholders <= settings.maxSharePlaceholders()) {
            entries.add(Entry.pass("InteractiveChat placeholder limit is enabled."));
        } else {
            entries.add(Entry.warn("InteractiveChat placeholder limit is not optimized."));
        }
        expectAtLeast(entries, config, "ItemDisplay.Item.Cooldown", 2);
        expectAtLeast(entries, config, "ItemDisplay.Inventory.Cooldown", 8);
        expectAtLeast(entries, config, "ItemDisplay.EnderChest.Cooldown", 12);
    }

    private static void auditAddonConfig(List<Entry> entries, File file) {
        if (!file.isFile()) {
            entries.add(Entry.fail("InteractiveChatDiscordSrvAddon config.yml is missing."));
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        expectFalse(entries, config, "DiscordCommands.GlobalSettings.RespondToCommandsInInvalidChannels");
        expectFalse(entries, config, "DiscordCommands.ShareItem.AllowAsOthers");
        expectFalse(entries, config, "DiscordCommands.ShareInventory.AllowAsOthers");
        expectFalse(entries, config, "DiscordCommands.ShareEnderChest.AllowAsOthers");
        expectTrue(entries, config, "DiscordMention.SuppressDiscordPings");
        expectTrue(entries, config, "DiscordAttachments.RestrictImageUrl.Enabled");
        int rendererThreads = config.getInt("Settings.RendererSettings.RendererThreads", -1);
        if (rendererThreads >= 1 && rendererThreads <= 4) {
            entries.add(Entry.pass("Addon renderer thread count is bounded."));
        } else {
            entries.add(Entry.warn("Addon renderer thread count should be between 1 and 4."));
        }
    }

    private static void auditResourcePack(List<Entry> entries, File pluginsFolder, BridgeSettings settings) {
        if (!settings.resourcePackAuditEnabled()) {
            return;
        }
        try {
            ResourcePackSynchronizer.Result result = ResourcePackSynchronizer.synchronize(
                pluginsFolder.toPath(), settings.resourcePackSource(), settings.resourcePackCopy(),
                settings.resourcePackAuditEnabled(), false);
            switch (result.status()) {
                case CURRENT -> entries.add(Entry.pass("Addon resource pack content matches the Nexo/Aeloria source."));
                case SOURCE_MISSING -> entries.add(Entry.warn("Nexo/Aeloria source resource pack is missing."));
                case PENDING_RESTART -> entries.add(Entry.warn("Addon resource pack differs and is pending a safe restart."));
                case LOCKED -> entries.add(Entry.warn("Addon resource pack is locked and could not be audited."));
                case DISABLED -> entries.add(Entry.pass("Resource pack synchronization is disabled."));
                case UPDATED -> entries.add(Entry.pass("Addon resource pack content was synchronized."));
                case FAILED -> entries.add(Entry.warn("Addon resource pack audit failed."));
            }
        } catch (IOException exception) {
            entries.add(Entry.warn("Addon resource pack audit failed: " + exception.getMessage()));
        }
    }

    private static void expectTrue(List<Entry> entries, YamlConfiguration config, String path) {
        if (config.getBoolean(path, false)) {
            entries.add(Entry.pass(path + " is enabled."));
        } else {
            entries.add(Entry.warn(path + " should be enabled."));
        }
    }

    private static void expectFalse(List<Entry> entries, YamlConfiguration config, String path) {
        if (!config.getBoolean(path, true)) {
            entries.add(Entry.pass(path + " is disabled."));
        } else {
            entries.add(Entry.warn(path + " should be disabled."));
        }
    }

    private static void expectAtLeast(List<Entry> entries, YamlConfiguration config, String path, int minimum) {
        if (config.getInt(path, 0) >= minimum) {
            entries.add(Entry.pass(path + " has a safe cooldown."));
        } else {
            entries.add(Entry.warn(path + " cooldown is too short."));
        }
    }

    enum Level {
        PASS,
        WARN,
        FAIL
    }

    record Entry(Level level, String message) {
        static Entry pass(String message) {
            return new Entry(Level.PASS, message);
        }

        static Entry warn(String message) {
            return new Entry(Level.WARN, message);
        }

        static Entry fail(String message) {
            return new Entry(Level.FAIL, message);
        }
    }

    record Report(List<Entry> entries, long passes, long warnings, long failures) {
        Report(List<Entry> entries) {
            this(entries,
                entries.stream().filter(entry -> entry.level() == Level.PASS).count(),
                entries.stream().filter(entry -> entry.level() == Level.WARN).count(),
                entries.stream().filter(entry -> entry.level() == Level.FAIL).count());
        }
    }
}
