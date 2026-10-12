package org.academy.api.common.structure;

/** Availability of one runtime handle; unloading never removes the saved structure. */
public enum StructureLifecycle {
    LOADING,
    ACTIVE,
    UNLOADED,
    RECOVERY_REQUIRED
}
