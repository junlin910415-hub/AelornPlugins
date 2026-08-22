package tw.linsy.aelornholograms;

import java.lang.reflect.Method;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/** Reflection-based PlaceholderAPI hook so the plugin has no hard dependency. */
public final class PlaceholderBridge {

    private final AelornHologramsPlugin plugin;
    private volatile Method setPlaceholdersMethod;

    public PlaceholderBridge(AelornHologramsPlugin plugin) {
        this.plugin = plugin;
        hook();
    }

    public void hook() {
        setPlaceholdersMethod = null;
        Plugin placeholderApi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !plugin.getConfig().getBoolean("compatibility.placeholderapi", true)) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholdersMethod = apiClass.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            plugin.getLogger().info("PlaceholderAPI support enabled.");
        } catch (ReflectiveOperationException hookFailure) {
            plugin.getLogger().warning("PlaceholderAPI found but could not be hooked: " + hookFailure.getMessage());
        }
    }

    boolean active() {
        return setPlaceholdersMethod != null;
    }

    public String apply(OfflinePlayer player, String text) {
        Method method = setPlaceholdersMethod;
        if (method == null || text == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            Object result = method.invoke(null, player, text);
            return result == null ? text : String.valueOf(result);
        } catch (ReflectiveOperationException invokeFailure) {
            return text;
        }
    }
}
