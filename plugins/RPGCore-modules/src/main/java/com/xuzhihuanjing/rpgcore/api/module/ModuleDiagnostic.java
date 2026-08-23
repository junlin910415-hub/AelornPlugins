package com.xuzhihuanjing.rpgcore.api.module;

import java.util.Objects;

/** Immutable diagnostics snapshot; sequence increases whenever that module changes state. */
public record ModuleDiagnostic(
        ModuleDescriptor descriptor,
        String ownerPlugin,
        ModuleState state,
        String detail,
        long transitionSequence) {

    public ModuleDiagnostic {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        ownerPlugin = Objects.requireNonNull(ownerPlugin, "ownerPlugin").strip();
        if (ownerPlugin.isEmpty()) {
            throw new IllegalArgumentException("ownerPlugin must not be blank");
        }
        state = Objects.requireNonNull(state, "state");
        detail = detail == null ? "" : detail;
        if (transitionSequence < 0L) {
            throw new IllegalArgumentException("transitionSequence must be non-negative");
        }
    }

    public boolean active() {
        return state == ModuleState.ACTIVE;
    }
}
