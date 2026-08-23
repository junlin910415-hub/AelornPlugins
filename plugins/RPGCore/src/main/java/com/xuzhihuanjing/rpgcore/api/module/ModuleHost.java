package com.xuzhihuanjing.rpgcore.api.module;

import java.util.List;
import java.util.Optional;
import org.bukkit.plugin.Plugin;

/** Public extension entrance exposed through Bukkit's ServicesManager. */
public interface ModuleHost {
    /** Registers a module owned by another Bukkit plugin; disabling that owner closes it automatically. */
    ModuleRegistration register(Plugin owner, RpgModule module);

    boolean running();

    List<ModuleDiagnostic> diagnostics();

    Optional<ModuleDiagnostic> diagnostic(String moduleId);
}
