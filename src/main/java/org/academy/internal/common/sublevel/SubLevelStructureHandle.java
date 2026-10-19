package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.api.common.structure.StructureWorldAccess;

import java.util.Objects;
import java.util.UUID;

/** StructureHandle adapter for one plot-backed SubLevel activation. */
public final class SubLevelStructureHandle implements StructureHandle {
    private final SubLevel subLevel;
    private final PlotStorage storage;
    private final StructureRef reference;
    private final UUID runtimeId;

    public SubLevelStructureHandle(SubLevel subLevel, PlotStorage storage, Identifier dimension) {
        this.subLevel = Objects.requireNonNull(subLevel, "subLevel");
        this.storage = Objects.requireNonNull(storage, "storage");
        var activation = SubLevelActivationRegistry.activation(subLevel.id());
        this.reference = SubLevelStructureRefs.of(subLevel, Objects.requireNonNull(dimension, "dimension"),
                activation.generation());
        this.runtimeId = activation.runtimeId();
    }

    public SubLevel subLevel() {
        return subLevel;
    }

    @Override
    public StructureRef reference() {
        return reference;
    }

    @Override
    public StructureLifecycle lifecycle() {
        return switch (subLevel.state()) {
            case LOADING, ALLOCATED -> StructureLifecycle.LOADING;
            case ACTIVE, SLEEPING -> StructureLifecycle.ACTIVE;
            case INACTIVE, REMOVED -> StructureLifecycle.UNLOADED;
        };
    }

    @Override
    public UUID runtimeId() {
        return runtimeId;
    }

    @Override
    public long geometryRevision() {
        return subLevel.revision();
    }

    @Override
    public StructurePose pose() {
        requireActive();
        return subLevel.pose();
    }

    @Override
    public void setPose(StructurePose pose) {
        Objects.requireNonNull(pose, "pose");
        if (lifecycle() == StructureLifecycle.ACTIVE) {
            throw new IllegalStateException("Active SubLevel is owned by the physics solver");
        }
        if (lifecycle() != StructureLifecycle.UNLOADED) {
            throw new IllegalStateException("SubLevel is not editable in lifecycle " + lifecycle());
        }
        subLevel.setPose(pose);
    }

    @Override
    public StructureWorldAccess world() {
        return new Access(reference, subLevel, storage);
    }

    private void requireActive() {
        if (lifecycle() != StructureLifecycle.ACTIVE) {
            throw new IllegalStateException("SubLevel handle is not active: " + lifecycle());
        }
    }

    private record Access(StructureRef reference, SubLevel subLevel, PlotStorage storage)
            implements StructureWorldAccess {
        private Access {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(subLevel, "subLevel");
            Objects.requireNonNull(storage, "storage");
        }

        @Override
        public StructureBlockData read(BlockPos localPosition) {
            requireBounds(localPosition);
            requireReady();
            var state = storage.blockState(subLevel.plot(), localPosition);
            return new StructureBlockData(state, saveBlockEntity(localPosition));
        }

        @Override
        public StructureWriteResult compareAndSet(BlockPos localPosition, StructureBlockData expected,
                                                  StructureBlockData replacement) {
            var current = read(localPosition);
            if (!same(current, expected)) return StructureWriteResult.CONFLICT;
            if (!storage.setBlock(subLevel.plot(), localPosition, replacement.state(), 3)) {
                return StructureWriteResult.REJECTED;
            }
            try {
                if (replacement.blockEntityData() != null) {
                    var world = storage.worldPosition(subLevel.plot(), localPosition);
                    var loaded = BlockEntity.loadStatic(world, replacement.state(),
                            replacement.blockEntityData(), storage.level().registryAccess());
                    if (loaded == null) return StructureWriteResult.REJECTED;
                    storage.level().setBlockEntity(loaded);
                    loaded.setChanged();
                }
            } catch (RuntimeException failure) {
                storage.setBlock(subLevel.plot(), localPosition, expected.state(), 3);
                return StructureWriteResult.REJECTED;
            }
            return StructureWriteResult.APPLIED;
        }

        private void requireReady() {
            if (subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING) {
                throw new IllegalStateException("SubLevel world access is not active");
            }
            if (!storage.level().getServer().isSameThread()) {
                throw new IllegalStateException("SubLevel world access is server-thread confined");
            }
        }

        private void requireBounds(BlockPos local) {
            Objects.requireNonNull(local, "local");
            if (!subLevel.localBounds().contains(local.getX() + 0.5, local.getY() + 0.5,
                    local.getZ() + 0.5)) {
                throw new IllegalArgumentException("Local position is outside the SubLevel bounds: " + local);
            }
        }

        private CompoundTag saveBlockEntity(BlockPos local) {
            var entity = storage.blockEntity(subLevel.plot(), local);
            return entity == null ? null : entity.saveWithFullMetadata(storage.level().registryAccess());
        }

        private static boolean same(StructureBlockData left, StructureBlockData right) {
            if (left.state() != right.state()) return false;
            var leftTag = left.blockEntityData();
            var rightTag = right.blockEntityData();
            return Objects.equals(leftTag, rightTag);
        }
    }
}
