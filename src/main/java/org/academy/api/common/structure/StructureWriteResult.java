package org.academy.api.common.structure;

/** Result of a server-thread cell change; only {@link #APPLIED} confirms application. */
public enum StructureWriteResult {
    APPLIED,
    /** The live state or block-entity contents no longer match the expected value. */
    CONFLICT,
    /** Validation or writing failed and the original world state was preserved. */
    REJECTED,
    OUT_OF_BOUNDS,
    /** The bound handle is loading, unloaded, or otherwise unavailable for writing. */
    NOT_READY,
    /** The backend cannot safely establish the outcome; further writes are suspended. */
    RECOVERY_REQUIRED
}
