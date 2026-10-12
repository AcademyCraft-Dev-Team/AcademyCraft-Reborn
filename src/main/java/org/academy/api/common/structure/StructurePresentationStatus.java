package org.academy.api.common.structure;

/** Presentation is separate from storage ownership and simulation readiness. */
public enum StructurePresentationStatus {
    NOT_ACTIVE,
    EMPTY,
    VISIBLE,
    UNSUPPORTED_CONTENT,
    LIMIT_EXCEEDED,
    HOST_UNAVAILABLE
}
