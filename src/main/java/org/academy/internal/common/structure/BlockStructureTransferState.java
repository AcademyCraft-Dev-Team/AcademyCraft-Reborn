package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Retains both images of each attempted write until ownership commits or rollback is verified. */
public final class BlockStructureTransferState {
    private static final int IMAGE_VERSION = 1;

    private final boolean discardAfterRollback;
    private final List<Cell> attempted = new ArrayList<>();
    private int imageVersion = IMAGE_VERSION;
    private boolean rollbackBlocked;

    public BlockStructureTransferState(boolean discardAfterRollback) {
        this.discardAfterRollback = discardAfterRollback;
    }

    public boolean discardAfterRollback() {
        return discardAfterRollback;
    }

    /** Legacy journals without an expected after-image can be retained, but cannot be replayed. */
    public void beforeWrite(ServerLevel level, BlockPos position) {
        attempted.add(new Cell(position.immutable(), readImage(level, position), null));
    }

    /** Prepare and record the intended payload before a world mutation can invoke callbacks. */
    public void writeCell(ServerLevel level, BlockPos position, BlockState state,
                          @Nullable CompoundTag blockEntityData, int flags) {
        if (rollbackBlocked || imageVersion != IMAGE_VERSION
                || attempted.stream().anyMatch(cell -> cell.position.equals(position))) {
            throw new IllegalStateException("Cannot append to this transfer at " + position);
        }
        var prepared = prepare(level, position, state, blockEntityData);
        var cell = new Cell(position.immutable(), readImage(level, position), prepared.image);
        attempted.add(cell);
        writePrepared(level, position, prepared, flags);
        cell.writeCompleted = true;
        if (!matches(level, position, cell.after)) {
            throw new IllegalStateException("Block payload changed during write at " + position);
        }
    }

    /** A later cell's synchronous callbacks must not silently invalidate an earlier write. */
    public boolean matchesAfterImages(ServerLevel level) {
        if (rollbackBlocked || imageVersion != IMAGE_VERSION) return false;
        try {
            for (var cell : attempted) {
                if (!cell.writeCompleted || cell.rolledBack || cell.after == null
                        || !matches(level, cell.position, cell.after)) return false;
            }
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** Validate the entire batch before replacing anything; unknown ownership stays quarantined. */
    public boolean rollback(ServerLevel level, int flags) {
        if (rollbackBlocked || imageVersion != IMAGE_VERSION) {
            rollbackBlocked = true;
            return false;
        }
        try {
            for (var cell : attempted) {
                if (!canRollback(level, cell)) return blockRollback();
            }
            for (var index = attempted.size() - 1; index >= 0; index--) {
                var cell = attempted.get(index);
                // A rollback callback may also have changed another cell since the batch preflight.
                if (!canRollback(level, cell)) return blockRollback();
                if (!matches(level, cell.position, cell.before)) {
                    write(level, cell.position, cell.before.state, cell.before.blockEntityData, flags);
                }
                if (!matches(level, cell.position, cell.before)) return blockRollback();
                cell.rolledBack = true;
            }
            // Keep every before-image until later rollback writes can no longer invalidate it.
            for (var cell : attempted) {
                if (!matches(level, cell.position, cell.before)) return blockRollback();
            }
            attempted.clear();
            return true;
        } catch (RuntimeException failure) {
            return blockRollback();
        }
    }

    private boolean blockRollback() {
        rollbackBlocked = true;
        return false;
    }

    private static boolean canRollback(ServerLevel level, Cell cell) {
        if (cell.after == null) return false;
        if (cell.rolledBack) return matches(level, cell.position, cell.before);
        // A failed callback can export a payload and restore the old block before throwing.
        // Matching the before-image alone therefore cannot prove that no mutation took place.
        // Pure preparation failures never add a cell and do not require this conservative guard.
        return matches(level, cell.position, cell.after);
    }

    /** Pure object preparation, shared by capture preflight and the actual write path. */
    public static void validateWrite(ServerLevel level, BlockPos position, BlockState state,
                                     @Nullable CompoundTag blockEntityData) {
        prepare(level, position, state, blockEntityData);
    }

    /** Replacing a container must not invoke its removal hook with its old inventory attached. */
    public static void write(ServerLevel level, BlockPos position, BlockState state,
                             @Nullable CompoundTag blockEntityData, int flags) {
        writePrepared(level, position, prepare(level, position, state, blockEntityData), flags);
    }

    private static PreparedWrite prepare(ServerLevel level, BlockPos position, BlockState state,
                                          @Nullable CompoundTag blockEntityData) {
        BlockEntity restored = null;
        if (blockEntityData != null) {
            var data = blockEntityData.copy();
            data.putInt("x", position.getX());
            data.putInt("y", position.getY());
            data.putInt("z", position.getZ());
            restored = BlockEntity.loadStatic(position, state, data, level.registryAccess());
            if (!state.hasBlockEntity() || restored == null || !restored.getType().isValid(state)) {
                throw new IllegalStateException("Cannot restore block entity at " + position);
            }
            if (!data.equals(restored.saveWithFullMetadata(level.registryAccess()))) {
                throw new IllegalStateException("Block entity data did not round-trip at " + position);
            }
        } else if (state.hasBlockEntity()) {
            restored = ((EntityBlock) state.getBlock()).newBlockEntity(position, state);
            if (restored == null || !restored.getType().isValid(state)) {
                throw new IllegalStateException("Missing block entity at " + position);
            }
        }
        return new PreparedWrite(new Image(state, restored == null ? null
                : restored.saveWithFullMetadata(level.registryAccess())), restored);
    }

    private static void writePrepared(ServerLevel level, BlockPos position, PreparedWrite prepared,
                                       int flags) {
        org.academy.internal.common.structure.transaction.PayloadIsolation.write(level, position,
                () -> writePermitted(level, position, prepared, flags));
    }

    private static void writePermitted(ServerLevel level, BlockPos position, PreparedWrite prepared, int flags) {
        var old = level.getBlockEntity(position);
        if (old instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container) {
            container.setLootTable(null);
            container.clearContent();
        }
        var state = prepared.image.state;
        var restored = prepared.blockEntity;
        // setBlock returns false for a no-op. The state and BE still require verification/restoration.
        if (level.getBlockState(position).equals(state)) {
            level.getChunkAt(position).removeBlockEntity(position);
        } else {
            if (!level.setBlock(position, state, flags)) {
                throw new IllegalStateException("Block write rejected at " + position);
            }
        }
        if (!level.getBlockState(position).equals(state)) {
            throw new IllegalStateException("Block write changed unexpectedly at " + position);
        }
        if (restored != null) {
            level.setBlockEntity(restored);
            restored.setChanged();
            if (level.getBlockEntity(position) != restored) {
                throw new IllegalStateException("Block entity write rejected at " + position);
            }
        }
    }

    private static Image readImage(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position)) throw new IllegalStateException("Unloaded block at " + position);
        var blockEntity = level.getBlockEntity(position);
        return new Image(level.getBlockState(position), blockEntity == null ? null
                : blockEntity.saveWithFullMetadata(level.registryAccess()));
    }

    private static boolean matches(ServerLevel level, BlockPos position, @Nullable Image expected) {
        if (expected == null || !level.isLoaded(position)) return false;
        var actual = readImage(level, position);
        return actual.state.equals(expected.state)
                && Objects.equals(actual.blockEntityData, expected.blockEntityData);
    }

    public void save(ValueOutput output) {
        output.putBoolean("discard_after_rollback", discardAfterRollback);
        output.putInt("image_version", imageVersion);
        output.putBoolean("rollback_blocked", rollbackBlocked);
        var cells = output.childrenList("attempted");
        for (var cell : attempted) {
            var child = cells.addChild();
            child.putInt("x", cell.position.getX());
            child.putInt("y", cell.position.getY());
            child.putInt("z", cell.position.getZ());
            child.store("state", BlockState.CODEC, cell.before.state);
            if (cell.before.blockEntityData != null) child.store("block_entity", CompoundTag.CODEC,
                    cell.before.blockEntityData);
            if (cell.after != null) {
                child.store("after_state", BlockState.CODEC, cell.after.state);
                if (cell.after.blockEntityData != null) child.store("after_block_entity", CompoundTag.CODEC,
                        cell.after.blockEntityData);
            }
            child.putBoolean("write_completed", cell.writeCompleted);
            child.putBoolean("rolled_back", cell.rolledBack);
        }
    }

    public static BlockStructureTransferState load(ValueInput input) {
        var result = new BlockStructureTransferState(input.getBooleanOr("discard_after_rollback", false));
        result.imageVersion = input.getIntOr("image_version", 0);
        result.rollbackBlocked = input.getBooleanOr("rollback_blocked", false);
        for (var child : input.childrenListOrEmpty("attempted")) {
            var afterState = child.read("after_state", BlockState.CODEC).orElse(null);
            var cell = new Cell(new BlockPos(child.getIntOr("x", 0),
                    child.getIntOr("y", 0), child.getIntOr("z", 0)),
                    new Image(child.read("state", BlockState.CODEC).orElseThrow(),
                            child.read("block_entity", CompoundTag.CODEC).orElse(null)),
                    afterState == null ? null : new Image(afterState,
                            child.read("after_block_entity", CompoundTag.CODEC).orElse(null)));
            cell.writeCompleted = child.getBooleanOr("write_completed", false);
            cell.rolledBack = child.getBooleanOr("rolled_back", false);
            result.attempted.add(cell);
        }
        return result;
    }

    private record Image(BlockState state, @Nullable CompoundTag blockEntityData) {
    }

    private record PreparedWrite(Image image, @Nullable BlockEntity blockEntity) {
    }

    private static final class Cell {
        private final BlockPos position;
        private final Image before;
        private final @Nullable Image after;
        private boolean writeCompleted;
        private boolean rolledBack;

        private Cell(BlockPos position, Image before, @Nullable Image after) {
            this.position = position;
            this.before = before;
            this.after = after;
        }
    }
}
