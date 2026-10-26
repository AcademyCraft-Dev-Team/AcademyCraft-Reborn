package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.structure.StructureBlockSummary;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;

/** Shared bounded scan. Missing cells invalidate the entire result, including earlier matches. */
public final class StructureBlockSummaries {
    private StructureBlockSummaries() {}

    public static Optional<StructureBlockSummary> scan(BlockPos min, BlockPos max, int budget,
                                                      Function<BlockPos, Optional<BlockState>> readLoaded) {
        StructureBlockSummary.requireBudget(budget);
        long x = (long) max.getX() - min.getX() + 1, y = (long) max.getY() - min.getY() + 1,
                z = (long) max.getZ() - min.getZ() + 1;
        if (x <= 0 || y <= 0 || z <= 0) return Optional.of(new StructureBlockSummary(java.util.Map.of(), 0));
        if (x > budget || y > budget || z > budget || x * y > budget || x * y * z > budget) return Optional.empty();
        var counts = new HashMap<BlockState, Integer>();
        for (var position : BlockPos.betweenClosed(min, max)) {
            var state = readLoaded.apply(position);
            if (state.isEmpty()) return Optional.empty();
            if (!state.get().isAir()) counts.merge(state.get(), 1, Math::addExact);
        }
        return Optional.of(new StructureBlockSummary(counts, (int) (x * y * z)));
    }
}
