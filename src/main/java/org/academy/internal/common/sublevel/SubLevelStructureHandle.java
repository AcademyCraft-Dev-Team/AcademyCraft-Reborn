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
import org.academy.AcademyCraft;

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
        if (!SubLevelActivationRegistry.matches(subLevel.id(), runtimeId, reference.generation())) {
            return StructureLifecycle.UNLOADED;
        }
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
        return subLevel.geometryRevision();
    }

    @Override
    public StructurePose pose() {
        requireActive();
        return subLevel.pose();
    }

    @Override
    public void setPose(StructurePose pose) {
        Objects.requireNonNull(pose, "pose");
        if (!SubLevelActivationRegistry.matches(subLevel.id(), runtimeId, reference.generation())) {
            throw new IllegalStateException("SubLevel handle belongs to an expired activation");
        }
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
        return new Access(reference, runtimeId, subLevel, storage);
    }

    private void requireActive() {
        if (lifecycle() != StructureLifecycle.ACTIVE) {
            throw new IllegalStateException("SubLevel handle is not active: " + lifecycle());
        }
    }

    private record Access(StructureRef reference, UUID runtimeId, SubLevel subLevel, PlotStorage storage)
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
            if (org.academy.internal.common.structure.transaction.PayloadIsolation.denied(storage.level(), storage.worldPosition(subLevel.plot(), localPosition))) return StructureWriteResult.NOT_READY;
            var current = read(localPosition);
            if (!same(current, expected)) return StructureWriteResult.CONFLICT;
            if (current.state() != replacement.state()
                    && !storage.setBlock(subLevel.plot(), localPosition, replacement.state(),
                    SubLevelCaptureService.TRANSACTION_FLAGS)) {
                return StructureWriteResult.REJECTED;
            }
            try {
                if (replacement.blockEntityData() != null) {
                    var world = storage.worldPosition(subLevel.plot(), localPosition);
                    var loaded = BlockEntity.loadStatic(world, replacement.state(),
                            replacement.blockEntityData(), storage.level().registryAccess());
                    if (loaded == null) {
                        restoreExpected(localPosition, expected);
                        return StructureWriteResult.REJECTED;
                    }
                    storage.level().setBlockEntity(loaded);
                    loaded.setChanged();
                }
            } catch (RuntimeException failure) {
                restoreExpected(localPosition, expected);
                return StructureWriteResult.REJECTED;
            }
            var runtime = SubLevelServer.runtime(storage.level().getServer(), subLevel.id()).orElse(null);
            if (runtime != null && runtime.get(subLevel.id()).filter(value -> value == subLevel).isPresent()) {
                runtime.refreshGeometry(subLevel.id());
            } else {
                subLevel.geometryChanged();
            }
            return StructureWriteResult.APPLIED;
        }

        private void requireReady() {
            if (subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING) {
                throw new IllegalStateException("SubLevel world access is not active");
            }
            if (!SubLevelActivationRegistry.matches(subLevel.id(), runtimeId, reference.generation())) {
                throw new IllegalStateException("SubLevel world access belongs to an expired activation");
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

        private void restoreExpected(BlockPos localPosition, StructureBlockData expected) {
            storage.setBlock(subLevel.plot(), localPosition, expected.state(), SubLevelCaptureService.TRANSACTION_FLAGS);
            var world = storage.worldPosition(subLevel.plot(), localPosition);
            if (expected.blockEntityData() == null) {
                if (storage.blockEntity(subLevel.plot(), localPosition) != null) {
                    storage.level().getChunkAt(world).removeBlockEntity(world);
                }
                return;
            }
            try {
                var loaded = BlockEntity.loadStatic(world, expected.state(), expected.blockEntityData(),
                        storage.level().registryAccess());
                if (loaded != null) {
                    storage.level().setBlockEntity(loaded);
                    loaded.setChanged();
                }
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.error("SubLevel CAS rollback could not restore BlockEntity at {}", localPosition, failure);
            }
        }

        private static boolean same(StructureBlockData left, StructureBlockData right) {
            if (left.state() != right.state()) return false;
            var leftTag = left.blockEntityData();
            var rightTag = right.blockEntityData();
            return Objects.equals(leftTag, rightTag);
        }
    }
}
