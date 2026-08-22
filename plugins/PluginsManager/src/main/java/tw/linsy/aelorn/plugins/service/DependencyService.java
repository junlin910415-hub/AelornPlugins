package tw.linsy.aelorn.plugins.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import tw.linsy.aelorn.plugins.config.ManagerSettings;
import tw.linsy.aelorn.plugins.nms.InternalsFailure;
import tw.linsy.aelorn.plugins.nms.ServerInternals;

/**
 * Who breaks if this plugin goes away.
 *
 * The question a plugin manager must get right, and the previous version did not:
 * it read each plugin's {@code depend} and {@code softdepend} lists, which finds
 * only <em>direct</em> dependants. Disabling something two steps down the chain
 * therefore looked safe and broke a plugin anyway.
 *
 * <p>So the server is asked instead. Paper maintains the dependency graph it used
 * to order plugin loading, including transitive edges, and answers the exact
 * question. When the internals are unavailable this falls back to the declared
 * lists and says so, because a partial answer with a warning beats refusing to
 * disable anything.
 *
 * <p>Declared lists come from {@code PluginMeta}, not {@code PluginDescriptionFile}:
 * the latter is the legacy view and reports nothing useful for a plugin shipping
 * {@code paper-plugin.yml}, so such a plugin's dependants were invisible.
 */
public final class DependencyService {

    private final ServerInternals internals;
    private final Supplier<ManagerSettings> settings;
    private final Logger logger;

    /** Logged once: a per-call warning would flood a batch group operation. */
    private volatile boolean transitiveFailureReported;

    public DependencyService(ServerInternals internals, Supplier<ManagerSettings> settings, Logger logger) {
        this.internals = internals;
        this.settings = settings;
        this.logger = logger;
    }

    /**
     * Plugins that would break if {@code target} were disabled or unloaded.
     *
     * @param hard        true for required dependencies only, false for soft ones
     * @param enabledOnly restrict to currently enabled plugins, which is what
     *                    matters when deciding whether an operation is safe now
     */
    public List<Plugin> dependants(Plugin target, boolean hard, boolean enabledOnly) {
        boolean transitive = hard && settings.get().guards().useTransitiveDependents();
        List<Plugin> found = new ArrayList<>();
        Set<String> targetNames = lowerCased(PluginLookup.namesFor(target));

        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (candidate == target || (enabledOnly && !candidate.isEnabled())) {
                continue;
            }
            if (transitive ? needsTransitively(candidate, target, targetNames)
                : declares(candidate, targetNames, hard)) {
                found.add(candidate);
            }
        }
        found.sort(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER));
        return found;
    }

    public List<String> dependantNames(Plugin target, boolean hard, boolean enabledOnly) {
        return PluginLookup.namesOf(dependants(target, hard, enabledOnly));
    }

    /** The plugin's own declared required dependencies, for the info report. */
    public static List<String> declaredHard(Plugin plugin) {
        return plugin.getPluginMeta().getPluginDependencies();
    }

    public static List<String> declaredSoft(Plugin plugin) {
        return plugin.getPluginMeta().getPluginSoftDependencies();
    }

    /**
     * Asks the server, falling back to declared dependencies.
     *
     * The fallback is not silent: a manager that quietly answers a weaker question
     * than the one asked is how the previous version's blind spot survived.
     */
    private boolean needsTransitively(Plugin candidate, Plugin target, Set<String> targetNames) {
        try {
            return internals.dependsOn(candidate, target);
        } catch (InternalsFailure unavailable) {
            if (!transitiveFailureReported) {
                transitiveFailureReported = true;
                logger.warning("無法向伺服器查詢遞移相依（" + unavailable.getMessage()
                    + "）；改用各插件自行宣告的相依清單，"
                    + "間接相依的插件不會被偵測到。");
            }
            return declares(candidate, targetNames, true);
        }
    }

    private static boolean declares(Plugin candidate, Set<String> targetNames, boolean hard) {
        List<String> declared = hard ? declaredHard(candidate) : declaredSoft(candidate);
        for (String name : declared) {
            if (name != null && targetNames.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> lowerCased(Set<String> names) {
        Set<String> lowered = new LinkedHashSet<>(names.size());
        for (String name : names) {
            lowered.add(name.toLowerCase(Locale.ROOT));
        }
        return lowered;
    }
}
