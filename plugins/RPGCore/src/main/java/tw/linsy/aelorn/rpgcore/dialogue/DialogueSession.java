package tw.linsy.aelorn.rpgcore.dialogue;

import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueDefinition;
import tw.linsy.aelorn.rpgcore.domain.dialogue.DialogueNode;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * 單一玩家的對話執行狀態。
 *
 * <p>所有欄位只由該玩家的 EntityScheduler 執行緒讀寫,因此不需要同步;
 * 唯一的例外是 {@link #cancelled},收尾時可能由其他路徑(登出、伺服器關閉)設定,
 * 故標記為 volatile。
 */
final class DialogueSession {

    private final DialogueDefinition definition;
    /** 進入對話時的位置,用於鎖定移動時把玩家拉回原地。 */
    private final Location anchor;

    private DialogueNode currentNode;
    private int lineIndex;
    /** 逐字模式下已顯示的字元數。 */
    private int revealedChars;
    /** 目前這句台詞尚需等待的 tick 數。 */
    private int waitTicks;
    private boolean awaitingChoice;
    private @Nullable ScheduledTask task;
    private volatile boolean cancelled;

    DialogueSession(DialogueDefinition definition, DialogueNode startNode, Location anchor) {
        this.definition = definition;
        this.currentNode = startNode;
        this.anchor = anchor;
    }

    DialogueDefinition definition() {
        return definition;
    }

    Location anchor() {
        return anchor;
    }

    DialogueNode currentNode() {
        return currentNode;
    }

    /** 切換節點並重設台詞游標。 */
    void moveTo(DialogueNode node) {
        this.currentNode = node;
        this.lineIndex = 0;
        this.revealedChars = 0;
        this.waitTicks = 0;
        this.awaitingChoice = false;
    }

    int lineIndex() {
        return lineIndex;
    }

    void nextLine() {
        lineIndex++;
        revealedChars = 0;
        waitTicks = 0;
    }

    boolean hasMoreLines() {
        return lineIndex < currentNode.lines().size();
    }

    int revealedChars() {
        return revealedChars;
    }

    void revealChars(int count) {
        this.revealedChars = count;
    }

    int waitTicks() {
        return waitTicks;
    }

    void waitTicks(int ticks) {
        this.waitTicks = ticks;
    }

    boolean awaitingChoice() {
        return awaitingChoice;
    }

    void awaitingChoice(boolean awaiting) {
        this.awaitingChoice = awaiting;
    }

    @Nullable ScheduledTask task() {
        return task;
    }

    void task(@Nullable ScheduledTask task) {
        this.task = task;
    }

    boolean cancelled() {
        return cancelled;
    }

    void cancel() {
        this.cancelled = true;
        ScheduledTask running = task;
        if (running != null && !running.isCancelled()) {
            running.cancel();
        }
        this.task = null;
    }
}
