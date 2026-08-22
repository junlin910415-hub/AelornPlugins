package tw.linsy.aelorn.quest;

import java.io.File;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * 把 RPGCore 的任務追蹤資料暴露成 PlaceholderAPI 變數，供 TAB 記分板使用。
 *
 * 為什麼要獨立成一個插件：RPGCore 本身沒有任務類 placeholder，
 * 而重建 RPGCore（反編譯後有 113 處還原瑕疵）風險過高。
 * 這個橋接只做唯讀存取，不改動 RPGCore 的任何行為。
 *
 * 效能模型：TAB 每 tick 會呼叫 placeholder，因此絕不在呼叫路徑上碰磁碟。
 * 快照由非同步排程週期更新，placeholder 只讀記憶體。
 */
public final class AelornQuestBridgePlugin extends JavaPlugin implements Listener {

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

    private final QuestCatalog catalog = new QuestCatalog();
    private final ConcurrentHashMap<UUID, QuestSnapshot> snapshots = new ConcurrentHashMap<>();
    private QuestExpansion expansion;
    private File playerDataFolder;
    private volatile long refreshIntervalTicks = 40L;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        refreshIntervalTicks = Math.max(10L, getConfig().getLong("refresh-interval-ticks", 40L));

        File rpgCoreFolder = new File(getDataFolder().getParentFile(),
                getConfig().getString("rpgcore-folder", "RPGCore"));
        playerDataFolder = new File(rpgCoreFolder, "player-data");
        int loaded = catalog.load(new File(rpgCoreFolder, "quests.yml"));

        expansion = new QuestExpansion(this, "aelornquest");
        if (!expansion.register()) {
            getLogger().severe("PlaceholderAPI 拒絕註冊 aelornquest 擴充，插件停用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        schedulers().asyncRepeating(() -> refreshAll(),
                1L, refreshIntervalTicks * 50L, TimeUnit.MILLISECONDS);

        getLogger().info("AelornQuestBridge 已啟用：載入 " + loaded
                + " 個任務定義，更新間隔 " + refreshIntervalTicks
                + " tick，PlaceholderAPI 識別碼 aelornquest。");
    }

    @Override
    public void onDisable() {
        if (expansion != null && expansion.isRegistered()) {
            expansion.unregister();
        }
        snapshots.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        schedulers().async(() -> refresh(playerId));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        snapshots.remove(event.getPlayer().getUniqueId());
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player.getUniqueId());
        }
    }

    /** 只在非同步排程中呼叫；讀 RPGCore 存檔並更新快取。 */
    private void refresh(UUID playerId) {
        try {
            snapshots.put(playerId, QuestSnapshot.read(playerDataFolder, playerId, catalog));
        } catch (RuntimeException exception) {
            // 存檔正在被 RPGCore 寫入時可能讀到半完成內容；保留上一份快照即可。
            getLogger().fine("讀取任務快照失敗（將沿用上一份）：" + exception.getMessage());
        }
    }

    QuestSnapshot snapshot(UUID playerId) {
        return snapshots.getOrDefault(playerId, QuestSnapshot.EMPTY);
    }

    QuestCatalog catalog() {
        return catalog;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        if (sub.equals("reload")) {
            reloadConfig();
            File rpgCoreFolder = new File(getDataFolder().getParentFile(),
                    getConfig().getString("rpgcore-folder", "RPGCore"));
            int loaded = catalog.load(new File(rpgCoreFolder, "quests.yml"));
            refreshAll();
            sender.sendMessage(Component.text("AelornQuestBridge 已重載：" + loaded + " 個任務定義。",
                    NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("AelornQuestBridge 狀態", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" 任務定義：" + catalog.size()
                + " | 快取玩家：" + snapshots.size()
                + " | 更新間隔：" + refreshIntervalTicks + " tick", NamedTextColor.GRAY));
        if (sender instanceof Player player) {
            QuestSnapshot snapshot = snapshot(player.getUniqueId());
            sender.sendMessage(Component.text(" 你的追蹤任務："
                    + (snapshot.active() ? snapshot.questName() + " " + snapshot.progressText()
                            : "（未追蹤）"), NamedTextColor.GRAY));
        }
        return true;
    }

    /** PlaceholderAPI 擴充：%aelornquest_&lt;key&gt;%。 */
    private static final class QuestExpansion extends PlaceholderExpansion {
        private final AelornQuestBridgePlugin plugin;
        private final String identifier;

        private QuestExpansion(AelornQuestBridgePlugin plugin, String identifier) {
            this.plugin = plugin;
            this.identifier = identifier;
        }

        @Override
        public @NotNull String getIdentifier() {
            return identifier;
        }

        @Override
        public @NotNull String getAuthor() {
            return "LinSy";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            if (player == null) {
                return "";
            }
            QuestSnapshot snapshot = plugin.snapshot(player.getUniqueId());
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "active" -> snapshot.active() ? "true" : "false";
                case "id" -> snapshot.questId();
                case "name" -> snapshot.active() ? snapshot.questName() : "";
                case "name_or_none" -> snapshot.active() ? snapshot.questName() : "未追蹤任務";
                case "description" -> snapshot.questDescription();
                case "category" -> snapshot.category();
                case "level" -> Integer.toString(snapshot.minimumLevel());
                case "progress" -> snapshot.progressText();
                case "objectives_done" -> Integer.toString(snapshot.objectivesDone());
                case "objectives_total" -> Integer.toString(snapshot.objectivesTotal());
                case "objective_1" -> snapshot.objectiveLine(1);
                case "objective_2" -> snapshot.objectiveLine(2);
                case "objective_3" -> snapshot.objectiveLine(3);
                case "accepted" -> Integer.toString(snapshot.acceptedCount());
                case "completed" -> Integer.toString(snapshot.completedCount());
                case "catalog_size" -> Integer.toString(plugin.catalog().size());
                default -> null;
            };
        }
    }
}
