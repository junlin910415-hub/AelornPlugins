package tw.linsy.aelornholograms;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;

public final class Hologram {

    private final String name;
    private Location location;
    private final List<String> lines;
    private boolean enabled;
    private String permission;
    private int displayRange;
    private int updateRange;
    private int updateInterval;
    private double lineHeight;
    private final List<UUID> entityIds = new ArrayList<>();
    private ScheduledTask updateTask;

    // Per-line render cache, parallel to entityIds; owned by HologramManager and
    // guarded by this hologram's monitor. Rebuilt whenever entities respawn.
    private final List<RenderLine> renderLines = new ArrayList<>();

    public Hologram(String name, Location location, List<String> lines, boolean enabled, String permission,
                    int displayRange, int updateRange, int updateInterval, double lineHeight) {
        this.name = name;
        this.location = location;
        this.lines = new ArrayList<>(lines);
        this.enabled = enabled;
        this.permission = permission == null ? "" : permission;
        this.displayRange = displayRange;
        this.updateRange = updateRange;
        this.updateInterval = updateInterval;
        this.lineHeight = lineHeight;
    }

    public String name() {
        return name;
    }

    public Location location() {
        return location;
    }

    public void location(Location location) {
        this.location = location;
    }

    public List<String> lines() {
        return lines;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String permission() {
        return permission;
    }

    public void permission(String permission) {
        this.permission = permission == null ? "" : permission;
    }

    public int displayRange() {
        return displayRange;
    }

    public void displayRange(int displayRange) {
        this.displayRange = displayRange;
    }

    public int updateRange() {
        return updateRange;
    }

    public void updateRange(int updateRange) {
        this.updateRange = updateRange;
    }

    public int updateInterval() {
        return updateInterval;
    }

    public void updateInterval(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    public double lineHeight() {
        return lineHeight;
    }

    public void lineHeight(double lineHeight) {
        this.lineHeight = lineHeight;
    }

    public List<UUID> entityIds() {
        return entityIds;
    }

    public ScheduledTask updateTask() {
        return updateTask;
    }

    public void updateTask(ScheduledTask updateTask) {
        this.updateTask = updateTask;
    }

    List<RenderLine> renderLines() {
        return renderLines;
    }

    /** Mutable per-line render state; see {@link #renderLines()} for locking rules. */
    static final class RenderLine {
        final String raw;
        final LineContent content;
        final boolean dynamic;
        String lastAppliedText;

        RenderLine(String raw, LineContent content, boolean dynamic) {
            this.raw = raw;
            this.content = content;
            this.dynamic = dynamic;
        }
    }
}
