package tw.linsy.aelornholograms;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

public final class DisplayEntitySpawner {

    private final AelornHologramsPlugin plugin;

    public DisplayEntitySpawner(AelornHologramsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return true;
    }

    /** Must be called on the region thread owning the location. */
    public Optional<Entity> spawn(Location location, DisplayKind kind) {
        World world = location.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        try {
            Entity entity = switch (kind) {
                case TEXT -> world.spawn(location, TextDisplay.class, SpawnReason.CUSTOM);
                case ITEM -> world.spawn(location, ItemDisplay.class, SpawnReason.CUSTOM);
                case BLOCK -> world.spawn(location, BlockDisplay.class, SpawnReason.CUSTOM);
            };
            return Optional.of(entity);
        } catch (IllegalStateException | IllegalArgumentException spawnFailure) {
            plugin.getLogger().warning("Could not spawn " + kind + " display: " + spawnFailure.getMessage());
            return Optional.empty();
        }
    }

    public enum DisplayKind {
        TEXT,
        ITEM,
        BLOCK
    }
}
