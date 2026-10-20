package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/** Planned host-terrain soft-block sweep; this contract neither grants authority nor activates block destruction. */
public interface StructureTerrainBreakPolicy {
    record Limits(int visitedCells, int shapeTests, int destroyedBlocks) {}

    /** A state observation, not permission to break the block. */
    record Candidate(BlockPos position, BlockState observedState) {}

    record Sweep(StructureRef reference, long geometryVersion, long simulationTick,
                 StructurePose previousPose, StructurePose currentPose,
                 List<Candidate> candidates, boolean budgetExhausted) {}

    /** An advisory plan; apply must recheck identity, every state and current permission. */
    record BreakPlan(Sweep sweep, UUID actorId, List<Candidate> permitted, boolean dropItems) {}

    record BreakResult(List<BlockPos> destroyed, List<BlockPos> rejected, boolean budgetExhausted) {}

    /** SOFT-01: Scan bounded swept oriented cells for eligible host soft blocks without loading absent chunks. */
    Sweep scan(ServerLevel host, StructureRef reference, StructurePose previousPose,
               StructurePose currentPose, List<AABB> localCollisionBoxes, Limits limits);

    /** SOFT-02: Check actor destruction settings, host protection/events and soft-block eligibility under the cap. */
    BreakPlan authorize(ServerLevel host, Entity actor, Sweep sweep, boolean dropItems, Limits limits);

    /** SOFT-03: Revalidate and perform actual host destruction once, preserving drop semantics and cache invalidation. */
    BreakResult apply(ServerLevel host, Entity actor, BreakPlan plan, Limits limits);
}
