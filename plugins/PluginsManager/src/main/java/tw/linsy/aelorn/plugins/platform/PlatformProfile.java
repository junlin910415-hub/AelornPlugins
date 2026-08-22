package tw.linsy.aelorn.plugins.platform;

import org.bukkit.Bukkit;

/**
 * What kind of server this actually is, decided once at enable.
 *
 * Two forks are supported and they differ in exactly one way that matters to a
 * plugin manager: whether the world is split across region threads. Everything
 * else — the scheduler API, Moonrise's tick-thread checks, unversioned
 * CraftBukkit packages — is shared, which is why one NMS adapter serves both.
 *
 * <p>{@link #regionised} is probed by class presence rather than by parsing
 * {@link Bukkit#getName()}, because the fork name is branding and changes with
 * it. {@code RegionizedServer} is Folia's own type: forks that inherit Folia's
 * threading (Luminol, LightingLuminol) have it, and Paper-family forks (Purpur,
 * Pufferfish) do not, whatever they call themselves.
 *
 * @param serverName       {@link Bukkit#getName()}, for display only
 * @param serverVersion    {@link Bukkit#getVersion()}, for display only
 * @param minecraftVersion e.g. {@code 26.2}
 * @param regionised       true when the world is split across region threads
 */
public record PlatformProfile(String serverName,
                              String serverVersion,
                              String minecraftVersion,
                              boolean regionised) {

    /** Folia's own server type; absent on every Paper-family fork. */
    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    public static PlatformProfile detect() {
        return new PlatformProfile(
            Bukkit.getName(),
            Bukkit.getVersion(),
            Bukkit.getMinecraftVersion(),
            classPresent(FOLIA_MARKER));
    }

    /**
     * True when the legacy {@link Bukkit#getScheduler()} can be used at all.
     *
     * Only ever asked on behalf of <em>another</em> plugin: cancelling the
     * scheduled work of a plugin being unloaded has to cover whatever scheduler
     * that plugin used, and on a Paper-family server that includes
     * {@code BukkitRunnable}. On Folia the same call throws, so it is skipped
     * rather than wrapped in a swallow-everything catch.
     */
    public boolean hasLegacyScheduler() {
        return !regionised;
    }

    /** {@code Folia} or {@code Paper}, for messages that must name the threading model. */
    public String threadingModel() {
        return regionised ? "regionised" : "single-threaded";
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, PlatformProfile.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }
}
