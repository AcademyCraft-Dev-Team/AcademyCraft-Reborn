package org.academy.api.common.ability.program;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * One local action point of a known structure epoch. The world position is resolved from the
 * structure's live pose at execution time; an unloaded or replaced structure invalidates it
 * instead of guessing a replacement location.
 */
public record ProgramStructureAnchor(
        ProgramStructureReference structure,
        BlockPos localPosition
) {
    public ProgramStructureAnchor {
        Objects.requireNonNull(structure, "structure");
        localPosition = Objects.requireNonNull(localPosition, "localPosition").immutable();
    }
}
