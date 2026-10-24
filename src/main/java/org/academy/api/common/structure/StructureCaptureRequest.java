package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.upgrade.StructureProvenance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Backend-independent capture request shared by skills, programs, and non-player callers.
 * Positions are normalized once before any world write; both budgets are enforced during
 * preflight. The permission predicate is evaluated per source cell before the transaction
 * journal is persisted and re-verified immediately before the ownership commit.
 */
public record StructureCaptureRequest(
        List<BlockPos> positions,
        int maximumBlocks,
        int maximumSpan,
        BiPredicate<ServerLevel, BlockPos> permission,
        boolean physical,
        Optional<StructureProvenance.SkillCreation> skillCreation
) {
    public static final int MAXIMUM_BLOCKS = 256;
    public static final int MAXIMUM_SPAN = 24;
    public static final BiPredicate<ServerLevel, BlockPos> ALLOW = (level, position) -> true;

    /** Compatibility constructor for captures that do not create a moving body. */
    public StructureCaptureRequest(List<BlockPos> positions, int maximumBlocks, int maximumSpan,
                                   BiPredicate<ServerLevel, BlockPos> permission) {
        this(positions, maximumBlocks, maximumSpan, permission, false);
    }

    /** Existing callers remain unmarked, including legacy saves and generic/admin captures. */
    public StructureCaptureRequest(List<BlockPos> positions, int maximumBlocks, int maximumSpan,
                                   BiPredicate<ServerLevel, BlockPos> permission, boolean physical) {
        this(positions, maximumBlocks, maximumSpan, permission, physical, Optional.empty());
    }

    /** Must be applied before preview: source intent is committed with this exact capture request. */
    public StructureCaptureRequest withSkillCreation(UUID ownerId, Identifier skillId) {
        return new StructureCaptureRequest(positions, maximumBlocks, maximumSpan, permission, physical,
                Optional.of(StructureProvenance.SkillCreation.resting(ownerId, skillId)));
    }

    public StructureCaptureRequest {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(skillCreation, "skillCreation");
        if (!physical && skillCreation.isPresent()) {
            throw new IllegalArgumentException("Skill restoration metadata requires a physical capture");
        }
        if (maximumBlocks < 1 || maximumBlocks > MAXIMUM_BLOCKS) {
            throw new IllegalArgumentException("maximumBlocks must be between 1 and " + MAXIMUM_BLOCKS);
        }
        if (maximumSpan < 1 || maximumSpan > MAXIMUM_SPAN) {
            throw new IllegalArgumentException("maximumSpan must be between 1 and " + MAXIMUM_SPAN);
        }
        var normalized = new LinkedHashSet<BlockPos>();
        for (var position : positions) {
            Objects.requireNonNull(position, "position");
            normalized.add(position.immutable());
        }
        positions = List.copyOf(normalized);
    }

    public static StructureCaptureRequest of(Iterable<BlockPos> positions) {
        Objects.requireNonNull(positions, "positions");
        var collected = new ArrayList<BlockPos>();
        for (var position : positions) collected.add(position);
        return new StructureCaptureRequest(collected, MAXIMUM_BLOCKS, MAXIMUM_SPAN, ALLOW, false);
    }
}
