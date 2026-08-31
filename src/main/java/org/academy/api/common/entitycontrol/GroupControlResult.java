package org.academy.api.common.entitycontrol;

import java.util.List;

public record GroupControlResult(
        int applied,
        int unsupported,
        int failed,
        List<GroupControlHandle> handles
) {
    public GroupControlResult {
        applied = Math.max(0, applied);
        unsupported = Math.max(0, unsupported);
        failed = Math.max(0, failed);
        handles = List.copyOf(handles);
    }
}
