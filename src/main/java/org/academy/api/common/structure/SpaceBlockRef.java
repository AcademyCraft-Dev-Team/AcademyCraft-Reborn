package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/** An address in a structure's fixed local grid, never a backend storage/plot address. */
public record SpaceBlockRef(StructureRef structure, BlockPos localPosition) {
    public SpaceBlockRef {
        Objects.requireNonNull(structure, "structure");
        localPosition = Objects.requireNonNull(localPosition, "localPosition").immutable();
    }
}
