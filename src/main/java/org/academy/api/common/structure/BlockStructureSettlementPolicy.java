package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Assigns per-cell settlement behavior while a world structure is captured. */
@FunctionalInterface
public interface BlockStructureSettlementPolicy {
    /** Keeps every cell fixed to the nearest grid when the structure stops. */
    BlockStructureSettlementPolicy FIXED = (_, _, _, _) ->
            BlockStructureSettlementMode.FIXED;

    /** Applies falling-block physics only to tagged natural terrain without block entities. */
    BlockStructureSettlementPolicy NATURAL_BLOCKS = (_, _, state, blockEntity) ->
            naturalMode(state, blockEntity != null);

    BlockStructureSettlementMode settlementMode(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            @Nullable BlockEntity blockEntity
    );

    /**
     * Keeps every cell above {@code maximumFallingY} fixed while delegating the lower region.
     * This is useful for moving a building together with a natural foundation.
     */
    static BlockStructureSettlementPolicy fixedAbove(
            int maximumFallingY,
            BlockStructureSettlementPolicy lowerRegionPolicy
    ) {
        if (lowerRegionPolicy == null) {
            throw new IllegalArgumentException("lowerRegionPolicy cannot be null");
        }
        return (level, position, state, blockEntity) -> position.getY() > maximumFallingY
                ? BlockStructureSettlementMode.FIXED
                : lowerRegionPolicy.settlementMode(level, position, state, blockEntity);
    }

    /** Shared classifier used for captured data and backward-compatible saved snapshots. */
    static BlockStructureSettlementMode naturalMode(
            BlockState state,
            boolean hasBlockEntity
    ) {
        return state != null && !hasBlockEntity && state.is(BlockStructureTags.NATURAL_SETTLEMENT)
                ? BlockStructureSettlementMode.FALLING
                : BlockStructureSettlementMode.FIXED;
    }
}
