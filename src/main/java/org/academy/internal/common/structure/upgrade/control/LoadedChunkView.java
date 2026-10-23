package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Loaded-only block view for shape and neighbour queries. Vanilla {@code Level} reads may
 * synchronously load a chunk; capture planning must never turn a preview into terrain loading,
 * so an unavailable section throws instead of answering "air". Callers map that absence to an
 * unknown observation, never to an empty clearance.
 */
final class LoadedChunkView implements BlockGetter {
    static final int MAX_CELLS = 16_384;
    private final ServerLevel host;
    private final Map<BlockPos, Cell> cells = new HashMap<>();

    LoadedChunkView(ServerLevel host) {
        this.host = host;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return read(position).state();
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos position) {
        return read(position).entity();
    }

    @Override
    public int getHeight() {
        return host.getHeight();
    }

    @Override
    public int getMinY() {
        return host.getMinY();
    }

    private Cell read(BlockPos position) {
        var known = cells.get(position);
        if (known != null) return known;
        if (!host.isInWorldBounds(position) || !host.getWorldBorder().isWithinBounds(position)) {
            throw new Unavailable();
        }
        var chunk = host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) throw new Unavailable();
        if (cells.size() >= MAX_CELLS) throw new Unavailable(true);
        var cell = new Cell(chunk, chunk.getBlockState(position), chunk.getBlockEntities().get(position));
        cells.put(position.immutable(), cell);
        return cell;
    }

    /** Detect a shape callback changing its dependencies before publishing a preview. */
    boolean current() {
        for (var entry : cells.entrySet()) {
            var position = entry.getKey();
            var observed = entry.getValue();
            var chunk = host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk != observed.chunk() || chunk.getBlockState(position) != observed.state()
                    || chunk.getBlockEntities().get(position) != observed.entity()) return false;
        }
        return true;
    }

    private record Cell(LevelChunk chunk, BlockState state, @Nullable BlockEntity entity) {}

    /** Plugin shape callbacks are untrusted observations; unknown coverage is never empty geometry. */
    static <T> T inspectShape(Supplier<T> query) {
        try {
            return query.get();
        } catch (Unavailable unavailable) {
            throw unavailable;
        } catch (RuntimeException | LinkageError failure) {
            throw new ShapeFailure(failure);
        }
    }

    static final class ShapeFailure extends RuntimeException {
        ShapeFailure(Throwable cause) {
            super("Structure preview cannot observe block geometry", cause, false, false);
        }
    }

    /** Control-flow signal for "not loaded", deliberately quieter than a logged exception. */
    static final class Unavailable extends RuntimeException {
        final boolean budget;
        Unavailable() {
            this(false);
        }
        Unavailable(boolean budget) {
            super(null, null, false, false);
            this.budget = budget;
        }
    }
}
