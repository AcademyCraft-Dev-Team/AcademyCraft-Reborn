package org.academy.internal.common.structure.space;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded, immutable model input. It never contains block entity data or owns stored blocks. */
public final class NativeStructureView implements BlockGetter {
    public static final int MAX_CELLS = 4_096;
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    private static final Comparator<Cell> CELL_ORDER = Comparator
            .comparingInt((Cell cell) -> cell.position().getY())
            .thenComparingInt(cell -> cell.position().getX())
            .thenComparingInt(cell -> cell.position().getZ());
    public static final NativeStructureView EMPTY = new NativeStructureView(List.of());
    public static final StreamCodec<RegistryFriendlyByteBuf, NativeStructureView> STREAM_CODEC =
            StreamCodec.of(NativeStructureView::encode, NativeStructureView::decode);

    private final List<Cell> cells;
    private final Map<BlockPos, BlockState> states;
    private final AABB bounds;

    public NativeStructureView(List<Cell> cells) {
        Objects.requireNonNull(cells, "cells");
        if (cells.size() > MAX_CELLS) {
            throw new IllegalArgumentException("A native view can contain at most " + MAX_CELLS + " cells");
        }
        var copy = new ArrayList<Cell>(cells.size());
        var stateCopy = new HashMap<BlockPos, BlockState>(cells.size());
        AABB accumulated = null;
        for (var cell : cells) {
            Objects.requireNonNull(cell, "cell");
            if (stateCopy.putIfAbsent(cell.position(), cell.state()) != null) {
                throw new IllegalArgumentException("Duplicate native view cell " + cell.position());
            }
            copy.add(cell);
            var position = cell.position();
            var cellBounds = new AABB(position.getX(), position.getY(), position.getZ(),
                    position.getX() + 1, position.getY() + 1, position.getZ() + 1);
            accumulated = accumulated == null ? cellBounds : accumulated.minmax(cellBounds);
        }
        copy.sort(CELL_ORDER);
        this.cells = List.copyOf(copy);
        states = Map.copyOf(stateCopy);
        bounds = accumulated == null ? new AABB(0, 0, 0, 0, 0, 0) : accumulated;
    }

    public List<Cell> cells() {
        return cells;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public AABB bounds() {
        return bounds;
    }

    /**
     * MODEL alone is insufficient in 26.2: chests and other block entities also use that
     * shape while requiring separate special/BE renderer submissions. The ordinary single
     * chest has an explicit model path; the other audited vanilla blocks need only block state.
     * Unknown block entity types, including subclasses supplied by mods, fail closed.
     */
    public static boolean isSupportedState(BlockState state) {
        Objects.requireNonNull(state, "state");
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL
                || !state.getFluidState().isEmpty()) return false;
        if (!state.hasBlockEntity()) return true;
        var block = state.getBlock();
        return isSingleChestState(state)
                || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER
                || block == Blocks.HOPPER || block == Blocks.DISPENSER || block == Blocks.DROPPER;
    }

    /** Only the exact, dry vanilla single chest has a dedicated inventory-free model path. */
    public static boolean isSingleChestState(BlockState state) {
        Objects.requireNonNull(state, "state");
        return state.getBlock() == Blocks.CHEST && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                && state.getFluidState().isEmpty();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos position) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return states.getOrDefault(position, Blocks.AIR.defaultBlockState());
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NativeStructureView view) {
        buffer.writeVarInt(view.cells.size());
        for (var cell : view.cells) {
            buffer.writeByte(cell.position().getX());
            buffer.writeShort(cell.position().getY());
            buffer.writeByte(cell.position().getZ());
            buffer.writeVarInt(Block.getId(cell.state()));
        }
    }

    private static NativeStructureView decode(RegistryFriendlyByteBuf buffer) {
        var size = buffer.readVarInt();
        if (size < 0 || size > MAX_CELLS) {
            throw new DecoderException("Native view cell count out of bounds: " + size);
        }
        if (size == 0) return EMPTY;
        var cells = new ArrayList<Cell>(size);
        try {
            for (var index = 0; index < size; index++) {
                var position = new BlockPos(buffer.readByte(), buffer.readShort(), buffer.readByte());
                var stateId = buffer.readVarInt();
                var state = Block.BLOCK_STATE_REGISTRY.byId(stateId);
                if (state == null) throw new DecoderException("Unknown block state " + stateId);
                cells.add(new Cell(position, state));
            }
            return new NativeStructureView(cells);
        } catch (IllegalArgumentException failure) {
            throw new DecoderException("Invalid native structure view", failure);
        }
    }

    public record Cell(BlockPos position, BlockState state) {
        public Cell {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(state, "state");
            if (position.getX() < -32 || position.getX() >= 32
                    || position.getZ() < -32 || position.getZ() >= 32
                    || position.getY() < MIN_Y || position.getY() >= MIN_Y + HEIGHT) {
                throw new IllegalArgumentException("Native view cell is outside its storage domain");
            }
            if (!isSupportedState(state)) {
                throw new IllegalArgumentException("Native views require non-fluid state-only models and an audited block entity type");
            }
        }
    }
}
