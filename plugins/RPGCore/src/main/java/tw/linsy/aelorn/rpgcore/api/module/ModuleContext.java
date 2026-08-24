package tw.linsy.aelorn.rpgcore.api.module;

import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.event.Listener;

/** Restricted host capabilities; deliberately does not expose RPGCore's plugin or concrete services. */
public interface ModuleContext {
    ModuleDescriptor descriptor();

    Logger logger();

    ModuleScheduler scheduler();

    /** Resolves an interface published through ServicesManager; concrete implementation classes are rejected. */
    <T> Optional<T> service(Class<T> apiType);

    <T extends Listener> T listen(T listener);

    <T extends AutoCloseable> T manage(T registration);
}
