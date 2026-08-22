package tw.linsy.aelornstore.ui;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marks an inventory as belonging to this plugin, and says what each slot does.
 *
 * Click handling reads the token stored against the clicked slot rather than
 * inspecting the item — an item's display name can be spoofed by anything that
 * writes to the inventory, whereas the token map is only ever written when the
 * menu is built.
 */
public final class MenuHolder implements InventoryHolder {

    public enum Type { ROOT, CATEGORY, CONFIRM, TOPUP, PROVIDER }

    private final Type type;
    private final String context;
    private final int page;
    private final Map<Integer, String> tokens = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(Type type, String context, int page) {
        this.type = type;
        this.context = context;
        this.page = page;
    }

    public Type type() {
        return type;
    }

    public String context() {
        return context;
    }

    public int page() {
        return page;
    }

    public void bind(int slot, String token) {
        if (slot >= 0) {
            tokens.put(slot, token);
        }
    }

    public @Nullable String token(int slot) {
        return tokens.get(slot);
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
