package com.xuzhihuanjing.rpgcore.api.module;

/** Ownership handle returned to the plugin that contributed a module. */
public interface ModuleRegistration extends AutoCloseable {
    String moduleId();

    @Override
    void close();
}
