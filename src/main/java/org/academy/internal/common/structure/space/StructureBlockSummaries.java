package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.structure.StructureBlockSummary;
import org.academy.internal.common.ability.program.ProgramQueryProfiling;

import static org.academy.internal.common.ability.program.ProgramQueryProfiling.Counter.*;
import static org.academy.internal.common.ability.program.ProgramQueryProfiling.Zone.SUMMARY_SCAN;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;

/** Shared bounded scan. Missing cells invalidate the entire result, including earlier matches. */
public final class StructureBlockSummaries {
    private StructureBlockSummaries() {}

    public static Optional<StructureBlockSummary> scan(BlockPos min, BlockPos max, int budget,
                                                      Function<BlockPos, Optional<BlockState>> readLoaded) {
        try (var profile = ProgramQueryProfiling.begin(SUMMARY_SCAN)) {
            StructureBlockSummary.requireBudget(budget);
            long x = (long) max.getX() - min.getX() + 1, y = (long) max.getY() - min.getY() + 1,
                    z = (long) max.getZ() - min.getZ() + 1;
            if (x <= 0 || y <= 0 || z <= 0) {
                profile.count(COMPLETE, 1);
                return Optional.of(new StructureBlockSummary(java.util.Map.of(), 0));
            }
            if (x > budget || y > budget || z > budget || x * y > budget || x * y * z > budget) {
                profile.count(BUDGET_REJECTED, 1);
                return Optional.empty();
            }
            var counts = new HashMap<BlockState, Integer>();
            int scanned = 0;
            for (var position : BlockPos.betweenClosed(min, max)) {
                var state = readLoaded.apply(position);
                if (profile.enabled()) scanned++;
                if (state.isEmpty()) {
                    profile.count(CELLS_READ, scanned);
                    profile.count(MISSING_CELL, 1);
                    return Optional.empty();
                }
                if (!state.get().isAir()) counts.merge(state.get(), 1, Math::addExact);
            }
            profile.count(CELLS_READ, scanned);
            profile.count(COMPLETE, 1);
            return Optional.of(new StructureBlockSummary(counts, (int) (x * y * z)));
        }
    }
}
