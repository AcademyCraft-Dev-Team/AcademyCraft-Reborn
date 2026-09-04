package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;

/** Result of a read-only block-structure selection. */
public record BlockStructureSelectionResult(
        Status status,
        List<BlockPos> positions,
        Optional<BlockPos> problemPosition
) {
    public BlockStructureSelectionResult {
        positions = positions == null
                ? List.of()
                : positions.stream().map(BlockPos::immutable).toList();
        problemPosition = problemPosition == null ? Optional.empty() : problemPosition;
    }

    public static BlockStructureSelectionResult success(List<BlockPos> positions) {
        return new BlockStructureSelectionResult(
                Status.SUCCESS,
                positions,
                Optional.empty()
        );
    }

    public static BlockStructureSelectionResult failure(Status status, BlockPos position) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("SUCCESS requires positions");
        return new BlockStructureSelectionResult(
                status,
                List.of(),
                Optional.ofNullable(position)
        );
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        EMPTY,
        TOO_LARGE,
        UNLOADED,
        OUT_OF_WORLD
    }
}
