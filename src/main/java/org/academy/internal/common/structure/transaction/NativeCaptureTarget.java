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
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWorldAccess;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.physics.NativePhysicsWorld;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsGeometry;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** Native backend adapter: owns every native runtime, handle and geometry call of a capture. */
public final class NativeCaptureTarget implements CaptureTarget {
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
    public Optional<StructureRef> allocate(ServerLevel host, BlockPos origin) {
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
