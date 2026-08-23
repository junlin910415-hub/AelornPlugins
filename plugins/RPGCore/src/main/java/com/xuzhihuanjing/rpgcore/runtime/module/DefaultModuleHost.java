package com.xuzhihuanjing.rpgcore.runtime.module;

import com.xuzhihuanjing.rpgcore.api.module.ModuleContext;
import com.xuzhihuanjing.rpgcore.api.module.ModuleDescriptor;
import com.xuzhihuanjing.rpgcore.api.module.ModuleDiagnostic;
import com.xuzhihuanjing.rpgcore.api.module.ModuleHost;
import com.xuzhihuanjing.rpgcore.api.module.ModuleRegistration;
import com.xuzhihuanjing.rpgcore.api.module.ModuleState;
import com.xuzhihuanjing.rpgcore.api.module.RpgModule;
import com.xuzhihuanjing.rpgcore.runtime.lifecycle.RegistrationScope;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Deterministic, failure-isolated lifecycle host for built-in and external RPGCore modules. */
public final class DefaultModuleHost implements ModuleHost, AutoCloseable, Listener {
    private static final Comparator<ModuleRuntime> START_ORDER = Comparator
            .comparingInt((ModuleRuntime runtime) -> runtime.descriptor().priority()).reversed()
            .thenComparing(runtime -> runtime.descriptor().id());

    private final JavaPlugin hostPlugin;
    private final Logger logger;
    private final ContextFactory contextFactory;
    private final Map<String, ModuleRuntime> modules = new LinkedHashMap<>();
    private final Sequence sequence = new Sequence();
    private boolean running;
    private boolean closed;
    private boolean ownerListenerRegistered;

    public DefaultModuleHost(JavaPlugin plugin) {
        this(
                Objects.requireNonNull(plugin, "plugin"),
                plugin.getLogger(),
                (owner, descriptor, scope) -> new DefaultModuleContext(owner, descriptor, scope));
    }

    DefaultModuleHost(Logger logger, ContextFactory contextFactory) {
        this(null, logger, contextFactory);
    }

    private DefaultModuleHost(JavaPlugin hostPlugin, Logger logger, ContextFactory contextFactory) {
        this.hostPlugin = hostPlugin;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
    }

    @Override
    public synchronized ModuleRegistration register(Plugin owner, RpgModule module) {
        if (closed) {
            throw new IllegalStateException("RPGCore module host is closed");
        }
        Objects.requireNonNull(owner, "owner");
        if (!owner.isEnabled()) {
            throw new IllegalStateException("Module owner is not enabled: " + owner.getName());
        }
        Objects.requireNonNull(module, "module");
        ModuleDescriptor descriptor = Objects.requireNonNull(module.descriptor(), "module.descriptor()");
        if (modules.containsKey(descriptor.id())) {
            throw new IllegalStateException("Module id is already registered: " + descriptor.id());
        }
        UUID token = UUID.randomUUID();
        ModuleRuntime runtime = new ModuleRuntime(
                token, owner, module, descriptor, contextFactory, sequence.next());
        modules.put(descriptor.id(), runtime);
        try {
            topologicalOrder();
        } catch (RuntimeException cycle) {
            modules.remove(descriptor.id(), runtime);
            runtime.close(sequence);
            throw cycle;
        }
        if (running) {
            reconcile();
        }
        return new Registration(this, descriptor.id(), token);
    }

    /** Starts every currently satisfiable module. Unrelated modules survive another module's failure. */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("RPGCore module host is closed");
        }
        if (running) {
            return;
        }
        if (hostPlugin != null && !ownerListenerRegistered) {
            hostPlugin.getServer().getPluginManager().registerEvents(this, hostPlugin);
            ownerListenerRegistered = true;
        }
        running = true;
        reconcile();
    }

    @Override
    public synchronized boolean running() {
        return running && !closed;
    }

    @Override
    public synchronized List<ModuleDiagnostic> diagnostics() {
        return modules.values().stream()
                .sorted(START_ORDER)
                .map(ModuleRuntime::diagnostic)
                .toList();
    }

    @Override
    public synchronized Optional<ModuleDiagnostic> diagnostic(String moduleId) {
        if (moduleId == null) {
            return Optional.empty();
        }
        String canonicalId;
        try {
            canonicalId = ModuleDescriptor.canonicalId(moduleId);
        } catch (IllegalArgumentException | NullPointerException invalidId) {
            return Optional.empty();
        }
        ModuleRuntime runtime = modules.get(canonicalId);
        return runtime == null ? Optional.empty() : Optional.of(runtime.diagnostic());
    }

    /** Safety net for external plugins that forget to close their registration in onDisable. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        ownerDisabled(event.getPlugin());
    }

    synchronized void ownerDisabled(Plugin owner) {
        if (closed || owner == null) {
            return;
        }
        Set<String> owned = new TreeSet<>();
        for (ModuleRuntime runtime : modules.values()) {
            if (runtime.owner() == owner) {
                owned.add(runtime.descriptor().id());
            }
        }
        if (!owned.isEmpty()) {
            removeModules(owned);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        running = false;
        if (ownerListenerRegistered) {
            HandlerList.unregisterAll(this);
            ownerListenerRegistered = false;
        }
        List<ModuleRuntime> order = topologicalOrder();
        for (int index = order.size() - 1; index >= 0; index--) {
            closeRuntime(order.get(index));
        }
        modules.clear();
    }

    private synchronized void unregister(String moduleId, UUID token) {
        if (closed) {
            return;
        }
        ModuleRuntime target = modules.get(moduleId);
        if (target == null || !target.token().equals(token)) {
            return;
        }
        removeModules(Set.of(moduleId));
    }

    private void removeModules(Set<String> removedIds) {
        Set<String> affected = new HashSet<>();
        for (String moduleId : removedIds) {
            affected.addAll(transitiveDependents(moduleId));
        }
        List<ModuleRuntime> order = topologicalOrder();
        for (int index = order.size() - 1; index >= 0; index--) {
            ModuleRuntime runtime = order.get(index);
            if (affected.contains(runtime.descriptor().id())) {
                closeRuntime(runtime);
            }
        }
        for (String removedId : removedIds) {
            modules.remove(removedId);
        }
        for (ModuleRuntime previous : order) {
            String dependentId = previous.descriptor().id();
            if (affected.contains(dependentId) && !removedIds.contains(dependentId)
                    && modules.containsKey(dependentId)) {
                modules.put(dependentId, new ModuleRuntime(
                        previous.token(), previous.owner(), previous.module(), previous.descriptor(),
                        contextFactory, sequence.next()));
            }
        }
        if (running) {
            reconcile();
        }
    }

    private void reconcile() {
        for (ModuleRuntime runtime : topologicalOrder()) {
            if (runtime.state() == ModuleState.ACTIVE || runtime.state() == ModuleState.FAILED
                    || runtime.state() == ModuleState.STOPPED || runtime.state() == ModuleState.STOPPING) {
                continue;
            }
            List<String> missing = new ArrayList<>();
            List<String> blocked = new ArrayList<>();
            boolean waiting = false;
            for (String dependencyId : runtime.descriptor().requiredDependencies()) {
                ModuleRuntime dependency = modules.get(dependencyId);
                if (dependency == null) {
                    missing.add(dependencyId);
                } else if (dependency.state() == ModuleState.FAILED
                        || dependency.state() == ModuleState.BLOCKED
                        || dependency.state() == ModuleState.STOPPED) {
                    blocked.add(dependencyId);
                } else if (dependency.state() != ModuleState.ACTIVE) {
                    waiting = true;
                }
            }
            if (!missing.isEmpty()) {
                runtime.transition(ModuleState.WAITING_DEPENDENCY,
                        "missing: " + String.join(",", missing), sequence.next());
            } else if (!blocked.isEmpty()) {
                runtime.transition(ModuleState.BLOCKED,
                        "blocked by: " + String.join(",", blocked), sequence.next());
            } else if (waiting) {
                runtime.transition(ModuleState.WAITING_DEPENDENCY, "waiting for dependencies", sequence.next());
            } else if (!runtime.start(sequence)) {
                logger.warning("RPGCore module " + runtime.descriptor().id()
                        + " failed in isolation: " + runtime.diagnostic().detail());
            }
        }
    }

    private List<ModuleRuntime> topologicalOrder() {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<ModuleRuntime>> dependents = new HashMap<>();
        for (ModuleRuntime runtime : modules.values()) {
            indegree.put(runtime.descriptor().id(), 0);
        }
        for (ModuleRuntime runtime : modules.values()) {
            for (String dependencyId : runtime.descriptor().requiredDependencies()) {
                if (!modules.containsKey(dependencyId)) {
                    continue;
                }
                indegree.compute(runtime.descriptor().id(), (ignored, value) -> value + 1);
                dependents.computeIfAbsent(dependencyId, ignored -> new ArrayList<>()).add(runtime);
            }
        }
        List<ModuleRuntime> ready = new ArrayList<>();
        for (ModuleRuntime runtime : modules.values()) {
            if (indegree.get(runtime.descriptor().id()) == 0) {
                ready.add(runtime);
            }
        }
        List<ModuleRuntime> ordered = new ArrayList<>(modules.size());
        Set<String> orderedIds = new HashSet<>();
        while (!ready.isEmpty()) {
            Comparator<ModuleRuntime> nextOrder = Comparator
                    .comparingInt((ModuleRuntime runtime) -> unresolvedOptionalDependencies(runtime, orderedIds))
                    .thenComparing(START_ORDER);
            ModuleRuntime runtime = ready.stream().min(nextOrder).orElseThrow();
            ready.remove(runtime);
            ordered.add(runtime);
            orderedIds.add(runtime.descriptor().id());
            for (ModuleRuntime dependent : dependents.getOrDefault(runtime.descriptor().id(), List.of())) {
                int remaining = indegree.compute(dependent.descriptor().id(), (ignored, value) -> value - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (ordered.size() != modules.size()) {
            Set<String> cyclic = new TreeSet<>(modules.keySet());
            ordered.forEach(runtime -> cyclic.remove(runtime.descriptor().id()));
            throw new IllegalArgumentException("Module dependency cycle: " + String.join(" -> ", cyclic));
        }
        return List.copyOf(ordered);
    }

    private int unresolvedOptionalDependencies(ModuleRuntime runtime, Set<String> orderedIds) {
        int unresolved = 0;
        for (String dependencyId : runtime.descriptor().optionalDependencies()) {
            if (modules.containsKey(dependencyId) && !orderedIds.contains(dependencyId)) {
                unresolved++;
            }
        }
        return unresolved;
    }

    private Set<String> transitiveDependents(String moduleId) {
        Set<String> affected = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        affected.add(moduleId);
        queue.add(moduleId);
        while (!queue.isEmpty()) {
            String dependency = queue.removeFirst();
            for (ModuleRuntime runtime : modules.values()) {
                String id = runtime.descriptor().id();
                if (!affected.contains(id) && runtime.descriptor().requiredDependencies().contains(dependency)) {
                    affected.add(id);
                    queue.addLast(id);
                }
            }
        }
        return affected;
    }

    private void closeRuntime(ModuleRuntime runtime) {
        try {
            runtime.close(sequence);
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "Could not close RPGCore module " + runtime.descriptor().id(), failure);
        }
    }

    @FunctionalInterface
    interface ContextFactory {
        ModuleContext create(Plugin owner, ModuleDescriptor descriptor, RegistrationScope scope);
    }

    static final class Sequence {
        private long value;

        long next() {
            return ++value;
        }
    }

    private static final class Registration implements ModuleRegistration {
        private DefaultModuleHost host;
        private final String moduleId;
        private final UUID token;

        private Registration(DefaultModuleHost host, String moduleId, UUID token) {
            this.host = host;
            this.moduleId = moduleId;
            this.token = token;
        }

        @Override
        public String moduleId() {
            return moduleId;
        }

        @Override
        public synchronized void close() {
            DefaultModuleHost current = host;
            host = null;
            if (current != null) {
                current.unregister(moduleId, token);
            }
        }
    }
}
