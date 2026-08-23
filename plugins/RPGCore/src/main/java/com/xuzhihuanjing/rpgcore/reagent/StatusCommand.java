package com.xuzhihuanjing.rpgcore.reagent;

import com.xuzhihuanjing.rpgcore.aura.AuraInstance;
import com.xuzhihuanjing.rpgcore.aura.AuraService;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /rpgstatus} —— 讓玩家看見自己的資源與狀態。
 *
 * <p>資源與增益系統就算算得再準，玩家看不到就等於不存在。
 * 在 HUD 完整支援之前，先給一個隨時可查的指令。</p>
 *
 * <p>排版刻意與插件其他輸出一致：置中標題、灰色分隔線、
 * 每列一個項目，數值靠右。管理員與玩家看到的是同一套格式。</p>
 */
public final class StatusCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String DIVIDER = "&8&m                                        ";

    private final ReagentService reagents;
    private final AuraService auras;
    private final Function<Player, String> classResolver;

    /**
     * @param reagents 資源服務
     * @param auras 增益服務
     * @param classResolver 取得玩家目前職業代號
     */
    public StatusCommand(ReagentService reagents, AuraService auras, Function<Player, String> classResolver) {
        this.reagents = reagents;
        this.auras = auras;
        this.classResolver = classResolver;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LEGACY.deserialize("&c這個指令只能由玩家使用。"));
            return true;
        }

        send(player, DIVIDER);
        send(player, "&6&l  ▍角色狀態");
        send(player, "");

        appendReagents(player);
        appendAuras(player);

        send(player, DIVIDER);
        return true;
    }

    /** 輸出資源區塊。 */
    private void appendReagents(Player player) {
        String classId = classResolver.apply(player);
        List<ReagentType> types = reagents.typesFor(classId);

        send(player, "&e  ● 職業資源");
        if (types.isEmpty()) {
            send(player, "&8    （目前沒有任何資源）");
            send(player, "");
            return;
        }
        for (ReagentType type : types) {
            double current = reagents.value(player, type.id());
            double max = reagents.max(player, type.id());
            send(player, "    " + type.format(current, max) + "  " + bar(current, max, type.color()));
        }
        send(player, "");
    }

    /** 輸出增益減益區塊，增益與減益分開列出。 */
    private void appendAuras(Player player) {
        List<AuraInstance> active = auras.auras(player);
        long now = System.currentTimeMillis();

        send(player, "&e  ● 狀態效果");
        if (active.isEmpty()) {
            send(player, "&8    （目前沒有任何狀態）");
            return;
        }

        List<AuraInstance> good = active.stream()
                .filter(instance -> instance.definition().beneficial())
                .filter(instance -> !instance.expired(now))
                .toList();
        List<AuraInstance> bad = active.stream()
                .filter(instance -> !instance.definition().beneficial())
                .filter(instance -> !instance.expired(now))
                .toList();

        if (!good.isEmpty()) {
            send(player, "&a    增益");
            good.forEach(instance -> send(player, "      " + instance.display(now)));
        }
        if (!bad.isEmpty()) {
            send(player, "&c    減益");
            bad.forEach(instance -> send(player, "      " + instance.display(now)));
        }
    }

    /**
     * 畫一條十格的比例條。
     *
     * <p>純數字要玩家自己心算百分比，一條長度條一眼就懂。</p>
     */
    private static String bar(double current, double max, String color) {
        int filled = max <= 0 ? 0 : (int) Math.round(Math.max(0, Math.min(1, current / max)) * 10);
        return color + "▉".repeat(filled) + "&8" + "▉".repeat(10 - filled);
    }

    private static void send(Player player, String legacyText) {
        Component message = legacyText.isEmpty() ? Component.empty() : LEGACY.deserialize(legacyText);
        player.sendMessage(message);
    }
}
