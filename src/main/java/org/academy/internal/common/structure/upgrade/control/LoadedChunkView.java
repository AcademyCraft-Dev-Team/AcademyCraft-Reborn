package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Loaded-only block view for shape and neighbour queries. Vanilla {@code Level} reads may
 * synchronously load a chunk; capture planning must never turn a preview into terrain loading,
 * so an unavailable section throws instead of answering "air". Callers map that absence to an
 * unknown observation, never to an empty clearance.
 */
final class LoadedChunkView implements BlockGetter {
    private final ServerLevel host;

    LoadedChunkView(ServerLevel host) {
        this.host = host;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return chunk(position).getBlockState(position);
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos position) {
        return chunk(position).getBlockEntities().get(position);
    }

    @Override
    public int getHeight() {
        return host.getHeight();
    }

    @Override
    public int getMinY() {
        return host.getMinY();
    }

    LevelChunk chunk(BlockPos position) {
        if (!host.isInWorldBounds(position) || !host.getWorldBorder().isWithinBounds(position)) {
            throw new Unavailable();
        }
        var chunk = host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) throw new Unavailable();
        return chunk;
    }

    /** Control-flow signal for "not loaded", deliberately quieter than a logged exception. */
    static final class Unavailable extends RuntimeException {
        Unavailable() {
            super(null, null, false, false);
        }
    }
}
