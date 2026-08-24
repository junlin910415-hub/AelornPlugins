package tw.linsy.aelorn.rpgcore.runtime.module;

import tw.linsy.aelorn.rpgcore.api.module.ModuleContext;
import tw.linsy.aelorn.rpgcore.api.module.ModuleDescriptor;
import tw.linsy.aelorn.rpgcore.api.module.ModuleScheduler;
import tw.linsy.aelorn.rpgcore.runtime.lifecycle.RegistrationScope;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

final class DefaultModuleContext implements ModuleContext {
    private final Plugin owner;
    private final ModuleDescriptor descriptor;
    private final RegistrationScope scope;
    private final ModuleScheduler scheduler;

    DefaultModuleContext(
            Plugin owner,
            ModuleDescriptor descriptor,
            RegistrationScope scope) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.scheduler = new TrackedModuleScheduler(owner, scope);
    }

    @Override
    public ModuleDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public Logger logger() {
        return owner.getLogger();
    }

    @Override
    public ModuleScheduler scheduler() {
        return scheduler;
    }

    @Override
    public <T> Optional<T> service(Class<T> apiType) {
        Objects.requireNonNull(apiType, "apiType");
        if (!apiType.isInterface()) {
            throw new IllegalArgumentException("Modules may resolve public service interfaces only: "
                    + apiType.getName());
        }
        return Optional.ofNullable(owner.getServer().getServicesManager().load(apiType));
    }

    @Override
    public <T extends Listener> T listen(T listener) {
        Objects.requireNonNull(listener, "listener");
        owner.getServer().getPluginManager().registerEvents(listener, owner);
        scope.add("listener:" + listener.getClass().getName(), () -> HandlerList.unregisterAll(listener));
        return listener;
    }

    @Override
    public <T extends AutoCloseable> T manage(T registration) {
        return scope.add(registration);
    }
}
