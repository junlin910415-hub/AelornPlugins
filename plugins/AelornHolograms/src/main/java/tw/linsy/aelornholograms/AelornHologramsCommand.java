package tw.linsy.aelornholograms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AelornHologramsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_COMMANDS = List.of("help", "create", "delete", "list", "near",
        "movehere", "teleport", "addline", "setline", "insertline", "removeline", "info", "reload", "import");
    private static final List<String> NAME_ARG_COMMANDS = List.of("delete", "movehere", "teleport",
        "addline", "setline", "insertline", "removeline", "info");

    private final AelornHologramsPlugin plugin;

    public AelornHologramsCommand(AelornHologramsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aelornholograms.command") && !sender.hasPermission("dh.command")) {
            message(sender, "<red>你沒有權限使用這個指令。");
            return true;
        }
        String[] normalized = normalizeDecentArgs(args);
        if (normalized.length == 0 || normalized[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        try {
            return execute(sender, normalized);
        } catch (IllegalArgumentException usageError) {
            message(sender, "<red>" + usageError.getMessage());
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aelornholograms.command") && !sender.hasPermission("dh.command")) {
            return List.of();
        }
        String[] normalized = normalizeDecentArgs(args);
        int argCount = Math.max(1, normalized.length);
        if (argCount == 1) {
            return filter(ROOT_COMMANDS, normalized.length == 0 ? "" : normalized[0]);
        }
        String subcommand = normalized[0].toLowerCase(Locale.ROOT);
        if (NAME_ARG_COMMANDS.contains(subcommand) && argCount == 2) {
            return filter(plugin.hologramManager().hologramNames(), normalized[1]);
        }
        if (subcommand.equals("import") && argCount == 2) {
            return filter(List.of("decentholograms"), normalized[1]);
        }
        return List.of();
    }

    private boolean execute(CommandSender sender, String[] args) {
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "delete", "remove" -> delete(sender, args);
            case "list" -> list(sender);
            case "near" -> near(sender, args);
            case "movehere" -> moveHere(sender, args);
            case "teleport", "tp" -> teleport(sender, args);
            case "addline" -> addLine(sender, args);
            case "setline" -> setLine(sender, args);
            case "insertline" -> insertLine(sender, args);
            case "removeline", "deleteline" -> removeLine(sender, args);
            case "info" -> info(sender, args);
            case "reload" -> reload(sender);
            case "import" -> importData(sender, args);
            default -> message(sender, "<red>未知指令。使用 <white>/vh help <red>查看說明。");
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        requireAdmin(sender);
        Player player = requirePlayer(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /vh create <名稱> [文字]");
        }
        String text = args.length >= 3 ? join(args, 2)
            : plugin.getConfig().getString("defaults.text", "&d新浮空文字");
        plugin.hologramManager().create(args[1], player.getLocation(), List.of(text));
        message(sender, "<green>已建立 hologram: <white>" + args[1]);
    }

    private void delete(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /vh delete <名稱>");
        }
        if (!plugin.hologramManager().delete(args[1])) {
            throw new IllegalArgumentException("找不到 hologram: " + args[1]);
        }
        message(sender, "<green>已刪除 hologram: <white>" + args[1]);
    }

    private void list(CommandSender sender) {
        List<String> names = plugin.hologramManager().hologramNames();
        if (names.isEmpty()) {
            message(sender, "<yellow>目前沒有 hologram。");
            return;
        }
        message(sender, "<green>Holograms <gray>(" + names.size() + "): <white>"
            + String.join("<gray>, <white>", names));
    }

    private void near(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        double radius = args.length >= 2 ? parseDouble(args[1], 16.0D) : 16.0D;
        List<Hologram> nearby = plugin.hologramManager().near(player.getLocation(), radius);
        if (nearby.isEmpty()) {
            message(sender, "<yellow>附近沒有 hologram。");
            return;
        }
        for (Hologram hologram : nearby) {
            Location location = hologram.location();
            message(sender, "<white>" + hologram.name() + " <gray>- <aqua>" + formatLocation(location)
                + " <gray>(" + Math.round(location.distance(player.getLocation())) + "m)");
        }
    }

    private void moveHere(CommandSender sender, String[] args) {
        requireAdmin(sender);
        Player player = requirePlayer(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /vh movehere <名稱>");
        }
        if (!plugin.hologramManager().move(args[1], player.getLocation())) {
            throw new IllegalArgumentException("找不到 hologram: " + args[1]);
        }
        message(sender, "<green>已移動 hologram 到目前位置: <white>" + args[1]);
    }

    private void teleport(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /vh teleport <名稱>");
        }
        Hologram hologram = plugin.hologramManager().hologram(args[1])
            .orElseThrow(() -> new IllegalArgumentException("找不到 hologram: " + args[1]));
        player.teleportAsync(hologram.location());
        message(sender, "<green>已傳送到 hologram: <white>" + hologram.name());
    }

    private void addLine(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /vh addline <名稱> <文字>");
        }
        if (!plugin.hologramManager().addLine(args[1], join(args, 2))) {
            throw new IllegalArgumentException("找不到 hologram: " + args[1]);
        }
        message(sender, "<green>已新增一行。");
    }

    private void setLine(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 4) {
            throw new IllegalArgumentException("用法: /vh setline <名稱> <行數> <文字>");
        }
        int line = parseLine(args[2]);
        if (!plugin.hologramManager().setLine(args[1], line, join(args, 3))) {
            throw new IllegalArgumentException("找不到 hologram 或行數不存在。");
        }
        message(sender, "<green>已更新第 <white>" + (line + 1) + " <green>行。");
    }

    private void insertLine(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 4) {
            throw new IllegalArgumentException("用法: /vh insertline <名稱> <行數> <文字>");
        }
        int line = parseLine(args[2]);
        if (!plugin.hologramManager().insertLine(args[1], line, join(args, 3))) {
            throw new IllegalArgumentException("找不到 hologram: " + args[1]);
        }
        message(sender, "<green>已插入第 <white>" + (line + 1) + " <green>行。");
    }

    private void removeLine(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3) {
            throw new IllegalArgumentException("用法: /vh removeline <名稱> <行數>");
        }
        int line = parseLine(args[2]);
        if (!plugin.hologramManager().removeLine(args[1], line)) {
            throw new IllegalArgumentException("找不到 hologram 或行數不存在。");
        }
        message(sender, "<green>已移除第 <white>" + (line + 1) + " <green>行。");
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("用法: /vh info <名稱>");
        }
        Hologram hologram = plugin.hologramManager().hologram(args[1])
            .orElseThrow(() -> new IllegalArgumentException("找不到 hologram: " + args[1]));
        message(sender, "<white>" + hologram.name() + " <gray>- <aqua>" + formatLocation(hologram.location()));
        message(sender, "<gray>行數: <white>" + hologram.lines().size()
            + " <gray>顯示距離: <white>" + hologram.displayRange()
            + " <gray>更新週期: <white>" + hologram.updateInterval() + " ticks");
        List<String> lines = List.copyOf(hologram.lines());
        for (int index = 0; index < lines.size(); index++) {
            message(sender, "<gray>" + (index + 1) + ". <white>" + lines.get(index));
        }
    }

    private void reload(CommandSender sender) {
        requireAdmin(sender);
        plugin.reloadConfig();
        plugin.reloadSettings();
        plugin.placeholderBridge().hook();
        plugin.hologramManager().reload();
        message(sender, "<green>設定與 hologram 已重新載入。");
    }

    private void importData(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2 || !args[1].equalsIgnoreCase("decentholograms")) {
            throw new IllegalArgumentException("用法: /vh import decentholograms");
        }
        int imported = plugin.store().importDecentHolograms(true);
        plugin.hologramManager().reload();
        message(sender, "<green>已匯入 DecentHolograms 資料: <white>" + imported + " <green>個檔案。");
    }

    /** Accepts DecentHolograms-style argument layouts (/dh hologram ..., /dh lines ...). */
    private String[] normalizeDecentArgs(String[] args) {
        if (args.length == 0) {
            return args;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("hologram") || first.equals("holograms") || first.equals("holo")) {
            return Arrays.copyOfRange(args, 1, args.length);
        }
        if (first.equals("lines") && args.length >= 2) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("add") && args.length >= 5) {
                return new String[]{"addline", args[2], join(args, 4)};
            }
            if (action.equals("set") && args.length >= 6) {
                return new String[]{"setline", args[2], args[4], join(args, 5)};
            }
            if ((action.equals("remove") || action.equals("delete")) && args.length >= 5) {
                return new String[]{"removeline", args[2], args[4]};
            }
        }
        return args;
    }

    private void sendHelp(CommandSender sender) {
        message(sender, "<gradient:#d78cff:#7fd7ff>AelornHolograms</gradient> <gray>Folia 浮空文字系統");
        message(sender, "<white>/vh create <名稱> [文字] <gray>- 建立");
        message(sender, "<white>/vh addline <名稱> <文字> <gray>- 新增行");
        message(sender, "<white>/vh setline <名稱> <行數> <文字> <gray>- 修改行");
        message(sender, "<white>/vh movehere <名稱> <gray>- 移到目前位置");
        message(sender, "<white>/vh list / near / info / delete / reload");
        message(sender, "<gray>物品行: <white>#ICON:DIAMOND <gray>方塊行: <white>#BLOCK:STONE");
    }

    private void message(CommandSender sender, String text) {
        sender.sendRichMessage(plugin.settings().messagePrefix() + text);
    }

    private void requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("aelornholograms.admin") && !sender.hasPermission("dh.admin")) {
            throw new IllegalArgumentException("你沒有管理 hologram 的權限。");
        }
    }

    private static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalArgumentException("這個指令必須由玩家在遊戲內執行。");
    }

    private static int parseLine(String raw) {
        try {
            int line = Integer.parseInt(raw);
            if (line <= 0) {
                throw new NumberFormatException(raw);
            }
            return line - 1;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("行數必須是 1 以上的數字。");
        }
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static String join(String[] args, int fromIndex) {
        return fromIndex >= args.length ? ""
            : String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length));
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private static String formatLocation(Location location) {
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return world + " " + Math.round(location.getX()) + ", " + Math.round(location.getY())
            + ", " + Math.round(location.getZ());
    }
}
