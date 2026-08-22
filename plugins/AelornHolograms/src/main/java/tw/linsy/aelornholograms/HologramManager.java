package tw.linsy.aelornholograms;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Owns hologram lifecycles and the per-hologram Folia region render task.
 *
 * Render-path design: entity properties are applied once at spawn, line content
 * is parsed once at rebuild, and the periodic task only re-applies text whose
 * placeholder output actually changed. Static lines cost one entity-validity
 * check per tick and nothing else.
 */
public final class HologramManager {

    private static final String ENTITY_TAG = "aelornholograms_line";

    private final AelornHologramsPlugin plugin;
    private final HologramStore store;
    private final DisplayEntitySpawner spawner;
    private final NamespacedKey hologramKey;
    private final NamespacedKey lineKey;
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();

    public HologramManager(AelornHologramsPlugin plugin, HologramStore store) {
        this.plugin = plugin;
        this.store = store;
        this.spawner = new DisplayEntitySpawner(plugin);
        this.hologramKey = new NamespacedKey(plugin, "hologram");
        this.lineKey = new NamespacedKey(plugin, "line");
    }

    public void reload() {
        shutdown();
        holograms.clear();
        for (Hologram hologram : store.loadAll()) {
            holograms.put(key(hologram.name()), hologram);
            start(hologram);
        }
    }

    public void shutdown() {
        for (Hologram hologram : holograms.values()) {
            cancelTask(hologram);
            removeEntities(hologram, hologram.location());
        }
    }

    public Collection<Hologram> holograms() {
        return List.copyOf(holograms.values());
    }

    public List<String> hologramNames() {
        return holograms.values().stream().map(Hologram::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public Optional<Hologram> hologram(String name) {
        return Optional.ofNullable(holograms.get(key(name)));
    }

    public Hologram create(String name, Location location, List<String> lines) {
        if (holograms.containsKey(key(name))) {
            throw new IllegalArgumentException("Hologram already exists: " + name);
        }
        DisplaySettings settings = plugin.settings();
        Hologram hologram = new Hologram(name, location.clone(), lines, true, "",
            settings.displayRange(), settings.updateRange(), settings.updateInterval(), settings.lineHeight());
        holograms.put(key(name), hologram);
        store.save(hologram);
        start(hologram);
        return hologram;
    }

    public boolean delete(String name) {
        Hologram hologram = holograms.remove(key(name));
        if (hologram == null) {
            return false;
        }
        cancelTask(hologram);
        removeEntities(hologram, hologram.location());
        store.delete(hologram);
        return true;
    }

    public boolean move(String name, Location destination) {
        Hologram hologram = holograms.get(key(name));
        if (hologram == null) {
            return false;
        }
        Location previous = hologram.location().clone();
        cancelTask(hologram);
        removeEntities(hologram, previous);
        hologram.location(destination.clone());
        store.save(hologram);
        start(hologram);
        return true;
    }

    public boolean setLines(String name, List<String> lines) {
        Hologram hologram = holograms.get(key(name));
        if (hologram == null) {
            return false;
        }
        synchronized (hologram) {
            hologram.lines().clear();
            hologram.lines().addAll(lines);
        }
        store.save(hologram);
        restart(hologram);
        return true;
    }

    public boolean addLine(String name, String line) {
        Hologram hologram = holograms.get(key(name));
        if (hologram == null) {
            return false;
        }
        synchronized (hologram) {
            hologram.lines().add(line);
        }
        store.save(hologram);
        restart(hologram);
        return true;
    }

    public boolean insertLine(String name, int index, String line) {
        Hologram hologram = holograms.get(key(name));
        if (hologram == null) {
            return false;
        }
        synchronized (hologram) {
            int bounded = Math.max(0, Math.min(index, hologram.lines().size()));
            hologram.lines().add(bounded, line);
        }
        store.save(hologram);
        restart(hologram);
        return true;
    }

    public boolean setLine(String name, int index, String line) {
        Hologram hologram = holograms.get(key(name));
        if (hologram == null) {
            return false;
        }
        synchronized (hologram) {
            if (index < 0 || index >= hologram.lines().size()) {
                return false;
            }
            hologram.lines().set(index, line);
        }
        store.save(hologram);
        restart(hologram);
        return true;
    }

    public boolean removeLine(String name, int index) {
        Hologram hologram = holograms.get(key(name));
        if (hologram == null) {
            return false;
        }
        synchronized (hologram) {
            if (index < 0 || index >= hologram.lines().size()) {
                return false;
            }
            hologram.lines().remove(index);
            if (hologram.lines().isEmpty()) {
                hologram.lines().add(plugin.settings().defaultText());
            }
        }
        store.save(hologram);
        restart(hologram);
        return true;
    }

    public List<Hologram> near(Location center, double radius) {
        double radiusSquared = radius * radius;
        return holograms.values().stream()
            .filter(hologram -> hologram.location().getWorld() != null
                && hologram.location().getWorld().equals(center.getWorld())
                && hologram.location().distanceSquared(center) <= radiusSquared)
            .sorted(Comparator.comparingDouble(hologram -> hologram.location().distanceSquared(center)))
            .toList();
    }

    public void refresh(String name) {
        hologram(name).ifPresent(this::restart);
    }

    private void start(Hologram hologram) {
        if (!spawner.isAvailable() || !hologram.enabled() || hologram.location().getWorld() == null) {
            return;
        }
        long interval = Math.max(plugin.settings().minUpdateInterval(), hologram.updateInterval());
        ScheduledTask task = plugin.schedulers()
            .regionRepeating(hologram.location(), () -> render(hologram), 1L, interval);
        hologram.updateTask(task);
    }

    private void restart(Hologram hologram) {
        cancelTask(hologram);
        removeEntities(hologram, hologram.location());
        start(hologram);
    }

    /** Runs on the region thread owning the hologram's location. */
    private void render(Hologram hologram) {
        if (!hologram.enabled()) {
            removeEntitiesNow(hologram);
            return;
        }
        synchronized (hologram) {
            List<String> lines = hologram.lines();
            List<UUID> entityIds = hologram.entityIds();
            List<Hologram.RenderLine> renderLines = hologram.renderLines();
            if (entityIds.size() != lines.size() || renderLines.size() != lines.size()) {
                rebuild(hologram);
                return;
            }
            for (int index = 0; index < lines.size(); index++) {
                Entity entity = Bukkit.getEntity(entityIds.get(index));
                Hologram.RenderLine renderLine = renderLines.get(index);
                if (entity == null || !entity.isValid() || !renderLine.raw.equals(lines.get(index))) {
                    rebuild(hologram);
                    return;
                }
                if (renderLine.dynamic || renderLine.lastAppliedText == null) {
                    applyText(entity, renderLine);
                }
            }
        }
    }

    /** Caller must hold the hologram monitor and the owning region thread. */
    private void rebuild(Hologram hologram) {
        removeEntitiesNow(hologram);
        hologram.renderLines().clear();
        boolean placeholdersActive = plugin.placeholderBridge().active();
        List<String> lines = List.copyOf(hologram.lines());
        for (int index = 0; index < lines.size(); index++) {
            String raw = lines.get(index);
            LineContent content = LineContent.parse(raw);
            boolean dynamic = content.kind() == DisplayEntitySpawner.DisplayKind.TEXT
                && placeholdersActive && content.content().indexOf('%') >= 0;
            Hologram.RenderLine renderLine = new Hologram.RenderLine(raw, content, dynamic);

            Location lineLocation = hologram.location().clone()
                .subtract(0.0D, index * hologram.lineHeight(), 0.0D);
            Optional<Entity> spawned = spawner.spawn(lineLocation, content.kind());
            if (spawned.isEmpty()) {
                continue;
            }
            Entity entity = spawned.get();
            applyStaticProperties(entity, hologram, index);
            applyInitialContent(entity, renderLine);
            hologram.entityIds().add(entity.getUniqueId());
            hologram.renderLines().add(renderLine);
        }
    }

    /** Entity flags, tags and PDC markers: written once per spawn, never per tick. */
    private void applyStaticProperties(Entity entity, Hologram hologram, int lineIndex) {
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setVisibleByDefault(true);
        entity.addScoreboardTag(ENTITY_TAG);
        entity.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, hologram.name());
        entity.getPersistentDataContainer().set(lineKey, PersistentDataType.INTEGER, lineIndex);
        if (entity instanceof Display display) {
            DisplaySettings settings = plugin.settings();
            display.setViewRange(Math.max(1.0F, hologram.displayRange()));
            display.setBillboard(settings.billboard());
            display.setShadowRadius(0.0F);
            display.setShadowStrength(0.0F);
            if (display instanceof TextDisplay textDisplay) {
                textDisplay.setLineWidth(settings.lineWidth());
                textDisplay.setShadowed(settings.shadowed());
                textDisplay.setSeeThrough(settings.seeThrough());
                textDisplay.setTextOpacity(settings.textOpacity());
                textDisplay.setDefaultBackground(false);
                textDisplay.setBackgroundColor(settings.backgroundColor());
                textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
            }
        }
    }

    private void applyInitialContent(Entity entity, Hologram.RenderLine renderLine) {
        switch (renderLine.content.kind()) {
            case TEXT -> applyText(entity, renderLine);
            case ITEM -> {
                if (entity instanceof ItemDisplay itemDisplay) {
                    itemDisplay.setItemStack(new ItemStack(readMaterial(renderLine.content.content(), Material.STONE)));
                    itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                }
            }
            case BLOCK -> {
                if (entity instanceof BlockDisplay blockDisplay) {
                    Material material = readMaterial(renderLine.content.content(), Material.STONE);
                    BlockData blockData = Bukkit.createBlockData(material.isBlock() ? material : Material.STONE);
                    blockDisplay.setBlock(blockData);
                }
            }
        }
    }

    /** Re-formats the line and pushes it to the entity only when the output changed. */
    private void applyText(Entity entity, Hologram.RenderLine renderLine) {
        if (!(entity instanceof TextDisplay textDisplay)) {
            return;
        }
        TextFormatter formatter = plugin.textFormatter();
        String processed = formatter.process(renderLine.content.content(), null);
        if (processed.equals(renderLine.lastAppliedText)) {
            return;
        }
        textDisplay.text(formatter.deserialize(processed));
        renderLine.lastAppliedText = processed;
    }

    private void removeEntities(Hologram hologram, Location location) {
        if (location.getWorld() == null) {
            hologram.entityIds().clear();
            return;
        }
        try {
            if (Bukkit.isOwnedByCurrentRegion(location)) {
                removeEntitiesNow(hologram);
            } else {
                plugin.schedulers().region(location, () -> removeEntitiesNow(hologram));
            }
        } catch (RuntimeException schedulingFailure) {
            plugin.getLogger().warning("Could not schedule hologram cleanup for "
                + hologram.name() + ": " + schedulingFailure.getMessage());
        }
    }

    private void removeEntitiesNow(Hologram hologram) {
        for (UUID entityId : List.copyOf(hologram.entityIds())) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        hologram.entityIds().clear();
    }

    private void cancelTask(Hologram hologram) {
        ScheduledTask task = hologram.updateTask();
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        hologram.updateTask(null);
    }

    private static Material readMaterial(String raw, Material fallback) {
        String name = raw == null ? "" : raw.trim();
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        Material material = Material.matchMaterial(name, true);
        return material == null ? fallback : material;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
