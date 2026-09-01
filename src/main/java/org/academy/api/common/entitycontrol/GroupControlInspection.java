package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Diagnostic state for an active high-level group task. */
public record GroupControlInspection(
        UUID subjectId,
        String command,
        Optional<BlockPos> currentBlock,
        int pendingWork,
        Optional<ControlState> movementState,
        int stalledTicks
) {
    public GroupControlInspection {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(command, "command");
        currentBlock = Objects.requireNonNull(currentBlock, "currentBlock");
        movementState = Objects.requireNonNull(movementState, "movementState");
    }
}
