package com.xuzhihuanjing.rpgcore.api.module;

/** Observable module lifecycle state. */
public enum ModuleState {
    DISCOVERED,
    WAITING_DEPENDENCY,
    STARTING,
    ACTIVE,
    FAILED,
    BLOCKED,
    STOPPING,
    STOPPED
}
