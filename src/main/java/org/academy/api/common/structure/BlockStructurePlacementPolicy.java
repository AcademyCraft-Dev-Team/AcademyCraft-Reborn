package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Controls which existing world blocks may be replaced during materialization. */
@FunctionalInterface
public interface BlockStructurePlacementPolicy {
    BlockStructurePlacementPolicy AIR_ONLY = (level, position, existing) -> existing.isAir();
    BlockStructurePlacementPolicy REPLACEABLE =
            (level, position, existing) -> existing.isAir() || existing.canBeReplaced();

    boolean canReplace(ServerLevel level, BlockPos position, BlockState existing);
}
