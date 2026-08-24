package tw.linsy.aelorn.rpgcore.dialogue;

import tw.linsy.aelorn.rpgcore.config.DialogueRegistry;
import tw.linsy.aelorn.rpgcore.domain.character.CharacterProfile;
import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueChoice;
import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueDefinition;
import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueLine;
import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueNode;
import tw.linsy.aelorn.rpgcore.quest.QuestService;
import tw.linsy.aelorn.rpgcore.service.CharacterService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * 對話演出引擎。
 *
 * <h2>Folia 執行緒模型</h2>
 * 每個對話都跑在**該玩家自己的 EntityScheduler** 上,不是全域排程器。玩家跨區域移動時
 * Folia 會把 entity task 一起搬過去,所以對話不會因為玩家走過邊界而斷掉,也不會有跨區域
 * 存取。session 表用 ConcurrentHashMap 是因為登出/關服可能從別的執行緒來收尾。
 *
 * <h2>呈現方式</h2>
 * 台詞逐句送到聊天欄(不重送、不洗頻);標記 {@code typewriter} 的句子額外在動作列逐字
 * 顯示——動作列是原地更新的,適合做逐字效果,聊天欄不適合。
 */
public final class DialogueService {

    /** 一般台詞之間的間隔。 */
    private static final int DEFAULT_LINE_DELAY_TICKS = 30;
    /** 逐字模式每次顯示的字元數(每 tick)。 */
    private static final int TYPEWRITER_CHARS_PER_TICK = 2;

    private final Plugin plugin;
    private final DialogueRegistry registry;
    private final CharacterService characterService;
    private final QuestService questService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();

    public DialogueService(Plugin plugin, DialogueRegistry registry,
                           CharacterService characterService, QuestService questService) {
        this.plugin = plugin;
        this.registry = registry;
        this.characterService = characterService;
        this.questService = questService;
    }

    /**
     * 嘗試為某個 NPC 開始對話。
     *
     * @return true 表示已接手這次互動;false 表示沒有對應對話,呼叫端應沿用原本的行為
     */
    public boolean startForNpc(Player player, String npcId) {
        DialogueDefinition definition = registry.forNpc(npcId).orElse(null);
        if (definition == null) {
            return false;
        }
        if (isInDialogue(player.getUniqueId())) {
            // 已在對話中就吞掉這次互動,避免連點重啟劇情
            return true;
        }
        CharacterProfile character = characterService.activeCharacter(player.getUniqueId()).orElse(null);
        if (character == null) {
            return false;
        }
        if (definition.requiredQuest() != null && !hasQuest(character, definition.requiredQuest())) {
            return false;
        }
        DialogueNode start = definition.start();
        if (start == null) {
            return false;
        }

        DialogueSession session = new DialogueSession(definition, start, player.getLocation().clone());
        sessions.put(player.getUniqueId(), session);
        // 對話全程跑在玩家自己的區域執行緒上
        session.task(player.getScheduler().runAtFixedRate(plugin,
            task -> tick(player, session), () -> endSession(player.getUniqueId(), false), 1L, 1L));
        return true;
    }

    /** 每 tick 推進一次;必定在玩家的 EntityScheduler 上執行。 */
    private void tick(Player player, DialogueSession session) {
        if (session.cancelled() || !player.isOnline()) {
            endSession(player.getUniqueId(), false);
            return;
        }
        if (session.awaitingChoice()) {
            return;
        }
        if (session.waitTicks() > 0) {
            session.waitTicks(session.waitTicks() - 1);
            return;
        }

        DialogueNode node = session.currentNode();
        if (!session.hasMoreLines()) {
            finishNode(player, session, node);
            return;
        }

        DialogueLine line = node.lines().get(session.lineIndex());
        if (line.typewriter() && revealTypewriter(player, session, line)) {
            return;
        }

        sendLine(player, session.definition(), line);
        session.waitTicks(DEFAULT_LINE_DELAY_TICKS + line.pauseTicks());
        session.nextLine();
    }

    /**
     * 逐字顯示於動作列。
     *
     * @return true 表示還在顯示中,這一 tick 不要推進到下一句
     */
    private boolean revealTypewriter(Player player, DialogueSession session, DialogueLine line) {
        String plain = miniMessage.stripTags(line.text());
        int total = plain.length();
        int revealed = Math.min(total, session.revealedChars() + TYPEWRITER_CHARS_PER_TICK);
        session.revealChars(revealed);
        player.sendActionBar(Component.text(plain.substring(0, revealed)));
        return revealed < total;
    }

    private void finishNode(Player player, DialogueSession session, DialogueNode node) {
        if (node.grantQuestProgress()) {
            questService.recordNpcInteraction(player, session.definition().npcId());
        }
        if (node.waitsForChoice()) {
            presentChoices(player, session, node);
            return;
        }
        String next = node.next();
        if (next == null || next.isBlank()) {
            endSession(player.getUniqueId(), true);
            return;
        }
        DialogueNode target = session.definition().node(next);
        if (target == null) {
            // 設定載入時已驗證過,走到這裡代表狀態異常,安全收場
            plugin.getLogger().warning("Dialogue " + session.definition().id()
                + " lost node " + next + "; ending dialogue.");
            endSession(player.getUniqueId(), true);
            return;
        }
        session.moveTo(target);
    }

    private void presentChoices(Player player, DialogueSession session, DialogueNode node) {
        CharacterProfile character = characterService.activeCharacter(player.getUniqueId()).orElse(null);
        session.awaitingChoice(true);

        List<Component> rendered = new ArrayList<>();
        int shown = 0;
        for (DialogueChoice choice : node.choices()) {
            boolean unlocked = character != null && meetsRequirement(character, choice);
            if (!unlocked && choice.hiddenWhenLocked()) {
                continue;
            }
            shown++;
            rendered.add(renderChoice(player, session, choice, unlocked, shown));
        }

        if (rendered.isEmpty()) {
            // 全部選項都不符條件:不要把玩家卡在等待狀態
            endSession(player.getUniqueId(), true);
            return;
        }
        rendered.forEach(player::sendMessage);
    }

    private Component renderChoice(Player player, DialogueSession session, DialogueChoice choice,
                                   boolean unlocked, int index) {
        Component label = miniMessage.deserialize(
            (unlocked ? "<yellow>[" : "<dark_gray>[") + index + "] " + choice.text()
                + (unlocked ? "</yellow>" : "</dark_gray>"));
        if (!unlocked) {
            return label.append(miniMessage.deserialize(" <red>(條件未達成)</red>"));
        }
        // 用 Adventure 的 callback,不必為了選項多開一個指令
        UUID playerId = player.getUniqueId();
        return label.clickEvent(ClickEvent.callback(audience -> choose(playerId, choice)));
    }

    /** 點擊選項的回呼可能來自別的執行緒,所以要跳回玩家的排程器再改狀態。 */
    private void choose(UUID playerId, DialogueChoice choice) {
        DialogueSession session = sessions.get(playerId);
        if (session == null || !session.awaitingChoice()) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> {
            DialogueSession current = sessions.get(playerId);
            if (current == null || !current.awaitingChoice()) {
                return;
            }
            DialogueNode target = current.definition().node(choice.next());
            if (target == null) {
                endSession(playerId, true);
                return;
            }
            current.moveTo(target);
        }, null);
    }

    private boolean meetsRequirement(CharacterProfile character, DialogueChoice choice) {
        if (!choice.hasRequirement()) {
            return true;
        }
        if (choice.minimumLevel() > 0 && character.level() < choice.minimumLevel()) {
            return false;
        }
        return choice.requiredQuest() == null || hasQuest(character, choice.requiredQuest());
    }

    private boolean hasQuest(CharacterProfile character, String questId) {
        return character.questProgress().containsKey(questId);
    }

    private void sendLine(Player player, DialogueDefinition definition, DialogueLine line) {
        String speaker = line.speakerOr(definition.speaker());
        String text = speaker == null || speaker.isBlank()
            ? line.text()
            : speaker + "<gray>:</gray> " + line.text();
        player.sendMessage(miniMessage.deserialize(text));
    }

    /** 玩家是否正在對話中;移動鎖定與互動攔截都靠這個判斷。 */
    public boolean isInDialogue(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean movementLocked(UUID playerId) {
        DialogueSession session = sessions.get(playerId);
        return session != null && session.definition().lockMovement();
    }

    public boolean skippable(UUID playerId) {
        DialogueSession session = sessions.get(playerId);
        return session != null && session.definition().skippable();
    }

    public @Nullable org.bukkit.Location anchorOf(UUID playerId) {
        DialogueSession session = sessions.get(playerId);
        return session == null ? null : session.anchor();
    }

    /** 玩家主動跳過整段對話。 */
    public void skip(UUID playerId) {
        DialogueSession session = sessions.get(playerId);
        if (session != null && session.definition().skippable()) {
            endSession(playerId, true);
        }
    }

    /**
     * 結束對話並清理。
     *
     * @param completed true 表示正常走完(會清掉動作列),false 表示中斷(登出、關服)
     */
    public void endSession(UUID playerId, boolean completed) {
        DialogueSession session = sessions.remove(playerId);
        if (session == null) {
            return;
        }
        session.cancel();
        if (!completed) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.getScheduler().run(plugin, task -> player.sendActionBar(Component.empty()), null);
        }
    }

    /** 關服時一次收乾淨,避免留下懸空的 ScheduledTask。 */
    public void shutdown() {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            endSession(playerId, false);
        }
    }

    public int activeSessions() {
        return sessions.size();
    }
}
