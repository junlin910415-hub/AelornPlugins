package com.xuzhihuanjing.rpgcore.runtime.module;

import com.xuzhihuanjing.rpgcore.api.module.ModuleContext;
import com.xuzhihuanjing.rpgcore.api.module.ModuleDescriptor;
import com.xuzhihuanjing.rpgcore.api.module.ModuleDiagnostic;
import com.xuzhihuanjing.rpgcore.api.module.ModuleState;
import com.xuzhihuanjing.rpgcore.api.module.RpgModule;
import com.xuzhihuanjing.rpgcore.runtime.lifecycle.RegistrationScope;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

/** One module instance plus the resources accumulated during its current activation. */
final class ModuleRuntime {
    private final UUID token;
    private final Plugin owner;
    private final RpgModule module;
    private final ModuleDescriptor descriptor;
    private final RegistrationScope scope;
    private final ModuleContext context;
    private ModuleState state = ModuleState.DISCOVERED;
    private String detail = "registered";
    private long transitionSequence;
    private boolean startInvoked;
    private boolean stopInvoked;

    ModuleRuntime(
            UUID token,
            Plugin owner,
            RpgModule module,
            ModuleDescriptor descriptor,
            DefaultModuleHost.ContextFactory contextFactory,
            long sequence) {
        this.token = Objects.requireNonNull(token, "token");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.module = Objects.requireNonNull(module, "module");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.scope = new RegistrationScope();
        this.context = Objects.requireNonNull(
                contextFactory.create(owner, descriptor, scope), "contextFactory.create()");
        this.transitionSequence = sequence;
    }

    UUID token() {
        return token;
    }

    Plugin owner() {
        return owner;
    }

    RpgModule module() {
        return module;
    }

    ModuleDescriptor descriptor() {
        return descriptor;
    }

    ModuleState state() {
        return state;
    }

    void transition(ModuleState next, String nextDetail, long sequence) {
        state = Objects.requireNonNull(next, "next");
        detail = nextDetail == null ? "" : nextDetail;
        transitionSequence = sequence;
    }

    boolean start(DefaultModuleHost.Sequence sequence) {
        transition(ModuleState.STARTING, "starting", sequence.next());
        startInvoked = true;
        try {
            module.start(context);
            transition(ModuleState.ACTIVE, "active", sequence.next());
            return true;
        } catch (Exception | LinkageError failure) {
            rollback(failure);
            transition(ModuleState.FAILED, describe(failure), sequence.next());
            return false;
        }
    }

    void close(DefaultModuleHost.Sequence sequence) {
        if (state == ModuleState.STOPPED) {
            return;
        }
        transition(ModuleState.STOPPING, "stopping", sequence.next());
        Throwable failure = null;
        if (startInvoked && !stopInvoked) {
            stopInvoked = true;
            try {
                module.stop();
            } catch (Exception | LinkageError stopFailure) {
                failure = stopFailure;
            }
        }
        try {
            scope.close();
        } catch (RuntimeException closeFailure) {
            if (failure == null) {
                failure = closeFailure;
            } else {
                failure.addSuppressed(closeFailure);
            }
        }
        transition(ModuleState.STOPPED, failure == null ? "stopped" : describe(failure), sequence.next());
    }

    ModuleDiagnostic diagnostic() {
        return new ModuleDiagnostic(descriptor, owner.getName(), state, detail, transitionSequence);
    }

    private void rollback(Throwable startupFailure) {
        stopInvoked = true;
        try {
            module.stop();
        } catch (Exception | LinkageError stopFailure) {
            startupFailure.addSuppressed(stopFailure);
        }
        try {
            scope.close();
        } catch (RuntimeException closeFailure) {
            startupFailure.addSuppressed(closeFailure);
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
