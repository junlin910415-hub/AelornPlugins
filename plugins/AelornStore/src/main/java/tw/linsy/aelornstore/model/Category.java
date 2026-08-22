package tw.linsy.aelornstore.model;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** A tab on the shop's root menu. Products point back at one by id. */
public record Category(
    String id,
    int slot,
    Material icon,
    String name,
    List<String> lore,
    String permission
) {

    public Category {
        lore = List.copyOf(lore);
    }

    public boolean visibleTo(Player player) {
        return permission.isEmpty() || player.hasPermission(permission);
    }
}
