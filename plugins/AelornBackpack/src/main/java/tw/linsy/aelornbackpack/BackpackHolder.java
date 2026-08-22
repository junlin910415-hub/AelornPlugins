package tw.linsy.aelornbackpack;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * 背包介面的持有者標記。
 *
 * 用 InventoryHolder 辨識自家介面，比用標題字串比對可靠得多——
 * 標題會因為資源包字型與語系而變動，型別不會。
 */
public final class BackpackHolder implements InventoryHolder {

    private final UUID ownerId;
    private final int page;
    private Inventory inventory;

    BackpackHolder(UUID ownerId, int page) {
        this.ownerId = ownerId;
        this.page = page;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public int page() {
        return page;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
