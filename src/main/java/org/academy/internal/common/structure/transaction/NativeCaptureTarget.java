package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWorldAccess;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.StructureCapturePhysics;
import org.academy.internal.common.structure.physics.NativePhysicsWorld;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsGeometry;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Native backend adapter: owns every native runtime, handle and geometry call of a capture. */
public final class NativeCaptureTarget implements CaptureTarget {
    @Override
    public String allocationFailureDetail() {
        return "no_free_native_slot";
    }
    public static final Identifier ID = NativeStructureRuntime.ID;

    private final MinecraftServer server;
    private @Nullable NativeStructureRuntime runtime;

    public NativeCaptureTarget(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Identifier backendId() {
        return ID;
    }

    @Override
    public Optional<StructureRef> allocate(ServerLevel host, BlockPos origin, BlockPos seedOffset, BlockState seedState) {
        // The native slot has no zero-mass body window; the seed travels through the copy stage.
        return runtime().createEmpty(host, StructurePose.identity(Vec3.atLowerCornerOf(origin)))
                .map(StructureHandle::reference);
    }

    @Override
    public int cellBudget() {
        return NativeStructurePhysicsGeometry.MAX_STRUCTURE_CELLS;
    }

    @Override
    public boolean ready(StructureRef target) {
        return runtime().resolve(target)
                .map(handle -> handle.lifecycle() == StructureLifecycle.ACTIVE)
                .orElse(false);
    }

    @Override
    public StructureWriteResult writeCell(StructureRef target, BlockPos localOffset, BlockState state) {
        var handle = runtime().resolve(target).orElse(null);
        if (handle == null) return StructureWriteResult.NOT_READY;
        return handle.world().compareAndSet(localOffset, new StructureBlockData(Blocks.AIR.defaultBlockState()),
                new StructureBlockData(state));
    }

    @Override
    public Optional<BlockState> readCell(StructureRef target, BlockPos localOffset) {
        if (!ready(target)) return Optional.empty();
        return Optional.of(access(target).read(localOffset).state());
    }

    @Override
    public StructureWriteResult clearCell(StructureRef target, BlockPos localOffset, BlockState expected) {
        var access = access(target);
        var current = access.read(localOffset).state();
        if (current.isAir()) return StructureWriteResult.APPLIED;
        if (!current.equals(expected)) return StructureWriteResult.CONFLICT;
        return access.compareAndSet(localOffset, new StructureBlockData(expected),
                new StructureBlockData(Blocks.AIR.defaultBlockState()));
    }

    @Override
    public boolean quarantined(StructureRef target) {
        var handle = runtime().resolve(target).orElse(null);
        return handle == null || handle.lifecycle() == StructureLifecycle.RECOVERY_REQUIRED;
    }

    @Override
    public void deactivate(StructureRef target) {
        runtime().unload(target);
    }

    @Override
    public boolean save(StructureRef target) {
        return runtime().save(target);
    }

    @Override
    public boolean releaseEmpty(StructureRef target) {
        return runtime().discardEmpty(target);
    }

    @Override
    public Optional<StructureHandle> resolve(StructureRef target, boolean recovery) {
        var handle = runtime().resolve(target).orElse(null);
        if (handle != null || !recovery) return Optional.ofNullable(handle);
        return runtime().load(target);
    }

    /**
     * Opens the host region and attaches the copied structure while its source blocks still
     * exist. A later source-removal failure can therefore release a real body instead of
     * leaving an orphan static projection.
     */
    @Override
    public boolean preparePhysics(ServerLevel host, StructureRef target, BlockPos origin, List<BlockPos> positions) {
        var handle = runtime().resolve(target).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) return false;
        var bounds = StructureCapturePhysics.regionBounds(host, positions);
        if (bounds == null) return false;
        StructurePhysicsRegion region;
        try {
            region = StructurePhysics.openRegion(host, bounds);
        } catch (RuntimeException | LinkageError failure) {
            org.academy.AcademyCraft.LOGGER.error("Cannot open the capture physics region for {}", target, failure);
            return false;
        }
        try {
            region.attach(handle);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            org.academy.AcademyCraft.LOGGER.error("Cannot attach the captured structure {}", target, failure);
            try { region.close(); }
            catch (RuntimeException | LinkageError cleanup) { failure.addSuppressed(cleanup); }
            return false;
        }
    }

    @Override
    public void releasePhysics(ServerLevel host, StructureRef target) {
        var region = StructurePhysics.attachedRegion(host, target).orElse(null);
        if (region == null) return;
        try {
            region.detach(target);
        } catch (RuntimeException | LinkageError failure) {
            org.academy.AcademyCraft.LOGGER.error("Cannot detach rolled-back capture {}", target, failure);
        }
        try {
            region.close();
        } catch (RuntimeException | LinkageError failure) {
            org.academy.AcademyCraft.LOGGER.error("Cannot close rolled-back capture region for {}", target, failure);
        }
    }

    @Override
    public boolean verifyCopy(StructureRef target, int expectedCells) {
        var storage = runtime().storageLevel(target);
        if (storage.isEmpty()) return false;
        var geometry = NativeStructurePhysicsGeometry.captureStructure(storage.get());
        return geometry.nonAirCells() == expectedCells
                && geometry.boxes().size() <= NativePhysicsWorld.MAX_BOXES;
    }

    private StructureWorldAccess access(StructureRef target) {
        var handle = runtime().resolve(target).orElseThrow(() ->
                new IllegalStateException("Structure handle is not active: " + StructureLifecycle.UNLOADED));
        return handle.world();
    }

    private NativeStructureRuntime runtime() {
        if (runtime == null) runtime = NativeStructureRuntime.backend(server);
        return Objects.requireNonNull(runtime, "native runtime");
    }
}
