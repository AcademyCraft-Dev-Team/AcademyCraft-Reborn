package org.academy.api.common.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A detached local cell value. Block-entity data includes its type and full saved contents;
 * any coordinate metadata uses the local grid, never the backend's storage address.
 * The backend validates whether the data is compatible with the block before writing it.
 */
public record StructureBlockData(BlockState state, @Nullable CompoundTag blockEntityData) {
    public StructureBlockData {
        Objects.requireNonNull(state, "state");
        blockEntityData = blockEntityData == null ? null : blockEntityData.copy();
    }

    public StructureBlockData(BlockState state) {
        this(state, null);
    }

    /** Returns a deep copy, so callers cannot mutate an active block entity through a read. */
    @Override
    public @Nullable CompoundTag blockEntityData() {
        return blockEntityData == null ? null : blockEntityData.copy();
    }
}
