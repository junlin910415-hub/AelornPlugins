package com.xuzhihuanjing.rpgcore.runtime.module;

import com.xuzhihuanjing.rpgcore.api.module.ModuleContext;
import com.xuzhihuanjing.rpgcore.api.module.ModuleDescriptor;
import com.xuzhihuanjing.rpgcore.api.module.ModuleRegistration;
import com.xuzhihuanjing.rpgcore.api.module.ModuleScheduler;
import com.xuzhihuanjing.rpgcore.api.module.ModuleState;
import com.xuzhihuanjing.rpgcore.api.module.RpgModule;
import com.xuzhihuanjing.rpgcore.runtime.lifecycle.RegistrationScope;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

public final class DefaultModuleHostTest {
    private static final Logger LOGGER = Logger.getLogger(DefaultModuleHostTest.class.getName());
    private static final AutoCloseable NOOP = () -> { };

    private DefaultModuleHostTest() {
    }

    public static void main(String[] arguments) {
        namespacedTopologyAndRequiredCycleRejection();
        optionalDependenciesOrderWhenPresentButNeverBlock();
        descriptorValidationAndLegacyCanonicalization();
        failedStartupRollsBackWithoutPoisoningSiblings();
        shutdownIsReverseTopologicalAndRegistrationsAreLifo();
        dynamicRemovalStopsDependentsAndLeavesThemWaiting();
        ownerDisableClosesOwnedModulesAndRearmsForeignDependents();
        queuedCallbackIsSuppressedAfterScopeClose();
        System.out.println("DefaultModuleHostTest PASS "
                + "(namespaces, required/optional topology, cycle rejection, rollback/isolation, "
                + "reverse close, dynamic removal, owner disable, queued callback guard)");
    }

    private static void namespacedTopologyAndRequiredCycleRejection() {
        Plugin owner = owner("TopologyOwner");
        List<String> events = new ArrayList<>();
        DefaultModuleHost host = host();
        host.register(owner, module("aeloria:child", Set.of("aeloria:root"), Set.of(), events, false, 0));
        host.register(owner, module("aeloria:root", Set.of(), Set.of(), events, false, 0));
        host.start();
        require(events.subList(0, 2).equals(List.of("start:aeloria:root", "start:aeloria:child")),
                "dependencies did not start first: " + events);
        require(host.diagnostic("aeloria:root").orElseThrow().state() == ModuleState.ACTIVE,
                "root did not become active");
        host.close();

        DefaultModuleHost cyclic = host();
        cyclic.register(owner, module(
                "aeloria:cycle-a", Set.of("aeloria:cycle-b"), Set.of(), new ArrayList<>(), false, 0));
        try {
            cyclic.register(owner, module(
                    "aeloria:cycle-b", Set.of("aeloria:cycle-a"), Set.of(), new ArrayList<>(), false, 0));
            throw new AssertionError("required dependency cycle was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("cycle"), "unexpected cycle failure: " + expected);
        }
        require(cyclic.diagnostic("aeloria:cycle-b").isEmpty(),
                "rejected cycle module leaked into diagnostics");
        cyclic.close();
    }

    private static void optionalDependenciesOrderWhenPresentButNeverBlock() {
        Plugin owner = owner("OptionalOwner");
        List<String> events = new ArrayList<>();
        DefaultModuleHost host = host();
        host.register(owner, module(
                "aeloria:combat", Set.of(), Set.of("vendor:telemetry"), events, false, 100));
        host.register(owner, module(
                "vendor:telemetry", Set.of(), Set.of(), events, false, -100));
        host.start();
        require(events.subList(0, 2).equals(List.of("start:vendor:telemetry", "start:aeloria:combat")),
                "present optional dependency did not influence start order: " + events);
        host.close();

        DefaultModuleHost missingOptional = host();
        missingOptional.register(owner, module(
                "aeloria:standalone", Set.of(), Set.of("vendor:not-installed"),
                new ArrayList<>(), false, 0));
        missingOptional.start();
        require(missingOptional.diagnostic("aeloria:standalone").orElseThrow().active(),
                "missing optional dependency blocked activation");
        missingOptional.close();

        DefaultModuleHost optionalCycle = host();
        optionalCycle.register(owner, module(
                "vendor:optional-a", Set.of(), Set.of("vendor:optional-b"),
                new ArrayList<>(), false, 0));
        optionalCycle.register(owner, module(
                "vendor:optional-b", Set.of(), Set.of("vendor:optional-a"),
                new ArrayList<>(), false, 0));
        optionalCycle.start();
        require(optionalCycle.diagnostics().stream().allMatch(diagnostic -> diagnostic.active()),
                "optional cycle was treated as a required cycle");
        optionalCycle.close();
    }

    private static void descriptorValidationAndLegacyCanonicalization() {
        ModuleDescriptor legacy = new ModuleDescriptor(
                "legacy", "1", Set.of("base"), Set.of("optional"), 0);
        require(legacy.id().equals("rpgcore:legacy"), "legacy id was not canonicalized: " + legacy.id());
        require(legacy.requiredDependencies().equals(Set.of("rpgcore:base")),
                "required dependency was not canonicalized");
        require(legacy.optionalDependencies().equals(Set.of("rpgcore:optional")),
                "optional dependency was not canonicalized");

        try {
            new ModuleDescriptor("vendor:self", "1", Set.of(), Set.of("vendor:self"), 0);
            throw new AssertionError("optional self dependency was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("itself"), "unexpected self-dependency error");
        }
        try {
            new ModuleDescriptor(
                    "vendor:overlap", "1", Set.of("vendor:shared"), Set.of("vendor:shared"), 0);
            throw new AssertionError("required/optional overlap was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("overlap"), "unexpected overlap error");
        }

        DefaultModuleHost host = host();
        Plugin owner = owner("CollisionOwner");
        host.register(owner, module("collision", Set.of(), Set.of(), new ArrayList<>(), false, 0));
        try {
            host.register(owner, module(
                    "rpgcore:collision", Set.of(), Set.of(), new ArrayList<>(), false, 0));
            throw new AssertionError("canonical id collision was accepted");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("already registered"), "unexpected collision failure");
        }
        require(host.diagnostic("collision").isPresent(), "simple diagnostic lookup did not canonicalize");
        host.close();
    }

    private static void failedStartupRollsBackWithoutPoisoningSiblings() {
        Plugin owner = owner("FailureOwner");
        List<String> events = new ArrayList<>();
        DefaultModuleHost host = host();
        host.register(owner, module("aeloria:good-a", Set.of(), Set.of(), events, false, 0));
        host.register(owner, module("aeloria:broken", Set.of(), Set.of(), events, true, 10));
        host.register(owner, module(
                "aeloria:blocked", Set.of("aeloria:broken"), Set.of(), events, false, 0));
        host.register(owner, module("aeloria:good-b", Set.of(), Set.of(), events, false, 0));
        host.start();

        require(host.diagnostic("aeloria:broken").orElseThrow().state() == ModuleState.FAILED,
                "broken module was not marked failed");
        require(host.diagnostic("aeloria:blocked").orElseThrow().state() == ModuleState.BLOCKED,
                "dependent module was not blocked");
        require(host.diagnostic("aeloria:good-a").orElseThrow().active()
                        && host.diagnostic("aeloria:good-b").orElseThrow().active(),
                "unrelated modules did not survive failure");
        require(events.indexOf("stop:aeloria:broken") >= 0
                        && events.indexOf("close:aeloria:broken:two") >= 0,
                "partial startup was not rolled back: " + events);
        host.close();
    }

    private static void shutdownIsReverseTopologicalAndRegistrationsAreLifo() {
        Plugin owner = owner("ShutdownOwner");
        List<String> events = new ArrayList<>();
        DefaultModuleHost host = host();
        host.register(owner, module("root", Set.of(), Set.of(), events, false, 0));
        host.register(owner, module("middle", Set.of("root"), Set.of(), events, false, 0));
        host.register(owner, module("leaf", Set.of("middle"), Set.of(), events, false, 0));
        host.start();
        events.clear();
        host.close();

        List<String> expected = List.of(
                "stop:leaf", "close:leaf:two", "close:leaf:one",
                "stop:middle", "close:middle:two", "close:middle:one",
                "stop:root", "close:root:two", "close:root:one");
        require(events.equals(expected), "shutdown order drifted: " + events);
    }

    private static void dynamicRemovalStopsDependentsAndLeavesThemWaiting() {
        Plugin owner = owner("RemovalOwner");
        List<String> events = new ArrayList<>();
        DefaultModuleHost host = host();
        ModuleRegistration root = host.register(
                owner, module("root", Set.of(), Set.of(), events, false, 0));
        host.register(owner, module("child", Set.of("root"), Set.of(), events, false, 0));
        host.start();
        events.clear();
        root.close();

        require(events.indexOf("stop:child") < events.indexOf("stop:root"),
                "dependent was not stopped before removed dependency: " + events);
        require(host.diagnostic("root").isEmpty(), "removed module remains registered");
        require(host.diagnostic("child").orElseThrow().state() == ModuleState.WAITING_DEPENDENCY,
                "dependent did not return to waiting state");
        host.close();
    }

    private static void ownerDisableClosesOwnedModulesAndRearmsForeignDependents() {
        Plugin ownerA = owner("OwnerA");
        Plugin ownerB = owner("OwnerB");
        List<String> events = new ArrayList<>();
        DefaultModuleHost host = host();
        ModuleRegistration staleRoot = host.register(
                ownerA, module("owner-a:root", Set.of(), Set.of(), events, false, 0));
        host.register(ownerA, module("owner-a:aux", Set.of(), Set.of(), events, false, 0));
        host.register(ownerB, module(
                "owner-b:child", Set.of("owner-a:root"), Set.of(), events, false, 0));
        host.register(ownerB, module("owner-b:unrelated", Set.of(), Set.of(), events, false, 0));
        host.start();
        events.clear();

        try {
            require(DefaultModuleHost.class
                            .getMethod("onPluginDisable", PluginDisableEvent.class)
                            .isAnnotationPresent(EventHandler.class),
                    "owner-disable safety net is not an event handler");
        } catch (NoSuchMethodException missingHandler) {
            throw new AssertionError("owner-disable safety net is missing", missingHandler);
        }
        host.ownerDisabled(ownerA);
        require(host.diagnostic("owner-a:root").isEmpty()
                        && host.diagnostic("owner-a:aux").isEmpty(),
                "disabled owner's modules remain registered");
        require(events.indexOf("stop:owner-b:child") < events.indexOf("stop:owner-a:root"),
                "foreign dependent did not stop before disabled owner: " + events);
        require(events.contains("close:owner-a:root:one") && events.contains("close:owner-a:root:two"),
                "disabled owner's registration scope was not closed: " + events);
        require(host.diagnostic("owner-b:child").orElseThrow().state() == ModuleState.WAITING_DEPENDENCY,
                "foreign dependent was not rearmed into waiting state");
        require(host.diagnostic("owner-b:unrelated").orElseThrow().active(),
                "unrelated foreign module was disrupted");
        require(host.diagnostic("owner-b:child").orElseThrow().ownerPlugin().equals("OwnerB"),
                "diagnostics lost module owner");

        host.register(ownerB, module("owner-a:root", Set.of(), Set.of(), events, false, 0));
        staleRoot.close();
        require(host.diagnostic("owner-a:root").orElseThrow().active(),
                "stale owner registration removed a replacement with the same id");
        require(host.diagnostic("owner-b:child").orElseThrow().active(),
                "foreign dependent did not restart after its dependency returned");
        host.close();
    }

    private static void queuedCallbackIsSuppressedAfterScopeClose() {
        RegistrationScope scope = new RegistrationScope();
        AtomicInteger calls = new AtomicInteger();
        Runnable queued = scope.guard(calls::incrementAndGet);
        scope.close();
        queued.run();
        require(calls.get() == 0, "queued callback ran after its module scope closed");
    }

    private static DefaultModuleHost host() {
        return new DefaultModuleHost(LOGGER,
                (owner, descriptor, scope) -> new TestContext(descriptor, scope));
    }

    private static Plugin owner(String name) {
        return (Plugin) Proxy.newProxyInstance(
                DefaultModuleHostTest.class.getClassLoader(),
                new Class<?>[] {Plugin.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "isEnabled" -> true;
                    case "getLogger" -> LOGGER;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "TestPlugin[" + name + "]";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }

    private static RpgModule module(
            String id,
            Set<String> requiredDependencies,
            Set<String> optionalDependencies,
            List<String> events,
            boolean fail,
            int priority) {
        return new RpgModule() {
            private final ModuleDescriptor descriptor = new ModuleDescriptor(
                    id, "1.0.0", requiredDependencies, optionalDependencies, priority);

            @Override
            public ModuleDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void start(ModuleContext context) {
                events.add("start:" + id);
                context.manage(() -> events.add("close:" + id + ":one"));
                context.manage(() -> events.add("close:" + id + ":two"));
                if (fail) {
                    throw new IllegalStateException("intentional failure");
                }
            }

            @Override
            public void stop() {
                events.add("stop:" + id);
            }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record TestContext(ModuleDescriptor descriptor, RegistrationScope scope) implements ModuleContext {
        @Override
        public Logger logger() {
            return LOGGER;
        }

        @Override
        public ModuleScheduler scheduler() {
            return NoopScheduler.INSTANCE;
        }

        @Override
        public <T> Optional<T> service(Class<T> apiType) {
            return Optional.empty();
        }

        @Override
        public <T extends Listener> T listen(T listener) {
            throw new UnsupportedOperationException("listeners are not used by this pure lifecycle test");
        }

        @Override
        public <T extends AutoCloseable> T manage(T registration) {
            return scope.add(registration);
        }
    }

    private enum NoopScheduler implements ModuleScheduler {
        INSTANCE;

        @Override
        public boolean executeEntity(Entity entity, Runnable task, Runnable retired) {
            task.run();
            return true;
        }

        @Override
        public AutoCloseable runEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks) {
            return NOOP;
        }

        @Override
        public AutoCloseable runEntityAtFixedRate(
                Entity entity, Runnable task, Runnable retired, long initialDelayTicks, long periodTicks) {
            return NOOP;
        }

        @Override
        public void executeRegion(Location location, Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable runRegionLater(Location location, Runnable task, long delayTicks) {
            return NOOP;
        }

        @Override
        public AutoCloseable runRegionAtFixedRate(
                Location location, Runnable task, long initialDelayTicks, long periodTicks) {
            return NOOP;
        }

        @Override
        public void executeGlobal(Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable runGlobalLater(Runnable task, long delayTicks) {
            return NOOP;
        }

        @Override
        public AutoCloseable runGlobalAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks) {
            return NOOP;
        }
    }
}
