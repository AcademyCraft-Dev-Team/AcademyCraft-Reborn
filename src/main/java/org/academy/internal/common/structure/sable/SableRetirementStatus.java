package org.academy.internal.common.structure.sable;

/** Stable diagnostic results; no result grants permission to repair or delete storage. */
public enum SableRetirementStatus {
    QUEUED,
    RETIRED,
    HOST_UNAVAILABLE,
    CONTAINER_BUSY,
    MISSING_SAVED_POINTER,
    HOLDING_SOURCE_PRESENT,
    EMPTY_SLOT_CACHED_INDEX,
    EMPTY_SLOT_DURABLE_INDEX,
    LIVE_POINTER_CONFLICT,
    HOLDING_POINTER_CONFLICT,
    SOURCE_STILL_LIVE,
    FOREIGN_SLOT,
    PLOT_OWNERSHIP_CONFLICT,
    STORAGE_FAILURE,
    CREATION_CONFLICT,
    UNSAVED_SOURCE_LIVE,
    INTENT_WRITE_FAILED,
    MARK_RETIREMENT_FAILED,
    JOURNAL_COMPLETION_FAILED
}
