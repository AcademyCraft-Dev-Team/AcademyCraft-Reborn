package org.academy.api.common.entitycontrol;

import java.util.Objects;
import java.util.UUID;

/** Lifecycle notification emitted by an autonomous group-control task. */
public record GroupControlTaskEvent(
        UUID subjectId,
        String subjectName,
        Status status
) {
    public GroupControlTaskEvent {
        Objects.requireNonNull(subjectId, "subjectId");
        subjectName = subjectName == null || subjectName.isBlank() ? subjectId.toString() : subjectName;
        Objects.requireNonNull(status, "status");
    }

    public enum Status {
        ACCEPTED,
        COMPLETED,
        PATH_FAILED,
        CANCELLED
    }
}
