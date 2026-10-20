package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.space.NativeStructureStorageErrors;
import org.academy.internal.common.sublevel.PlotGeometry;
import org.academy.internal.common.sublevel.SubLevel;
import org.academy.internal.common.sublevel.SubLevelCaptureService;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelRuntime;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelState;
import org.academy.internal.common.sublevel.SubLevelStructureHandle;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Plot-backed capture through the same claims, permissions and journal as other backends. */
public final class SubLevelCaptureTarget implements CaptureTarget {
    private final MinecraftServer server;

    public SubLevelCaptureTarget(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<StructureRef> allocate(ServerLevel host, BlockPos origin, BlockPos seedOffset,
                                            BlockState seedState) {
        return allocate(host, origin, seedOffset, seedState, List.of(origin.offset(seedOffset)), UUID.randomUUID());
    }

    @Override
    public Optional<StructureRef> allocate(ServerLevel host, BlockPos origin, BlockPos seedOffset,
                                            BlockState seedState, List<BlockPos> positions, UUID transactionId) {
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (runtime == null || host.getServer() != server) return Optional.empty();
        int maxX = 0, maxY = 0, maxZ = 0;
        for (var position : positions) {
            var local = position.subtract(origin);
            if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0) return Optional.empty();
            maxX = Math.max(maxX, local.getX() + 1);
            maxY = Math.max(maxY, local.getY() + 1);
            maxZ = Math.max(maxZ, local.getZ() + 1);
        }
        if (maxX == 0 || maxY == 0 || maxZ == 0) return Optional.empty();
        return runtime.createStaged(new SubLevelId(transactionId), new AABB(0, 0, 0, maxX, maxY, maxZ),
                        StructurePose.identity(Vec3.atLowerCornerOf(origin)))
                .map(value -> handle(runtime, value).reference());
    }

    @Override public String allocationFailureDetail() { return "sublevel_plot_capacity_unavailable"; }
    @Override public int cellBudget() { return 256; }

    @Override
    public @Nullable String rejectionDetail(ServerLevel host, BlockPos position, BlockState state,
                                             @Nullable BlockEntity blockEntity) {
        return classifyAdmission(blockEntity != null || state.hasBlockEntity(),
                state.hasProperty(BlockStateProperties.WATERLOGGED)
                        && state.getValue(BlockStateProperties.WATERLOGGED),
                !state.getFluidState().isEmpty(),
                host.getBlockTicks().hasScheduledTick(position, state.getBlock()),
                state.getCollisionShape(host, position).isEmpty(), state.getDestroySpeed(host, position));
    }

    /** The shared transaction persists block states; richer payloads remain an explicit rejection. */
    static @Nullable String classifyAdmission(boolean blockEntity, boolean waterlogged, boolean fluid,
                                               boolean scheduledTick, boolean emptyShape, float destroySpeed) {
        if (blockEntity) return "block_entity_not_supported";
        if (waterlogged) return "waterlogged_not_supported";
        if (fluid) return "fluid_not_supported";
        if (scheduledTick) return "scheduled_tick_pending";
        if (destroySpeed < 0.0f) return "unbreakable";
        if (emptyShape) return "no_collision_geometry";
        return null;
    }

    @Override
    public boolean ready(StructureRef target) {
        return resolve(target, false).isPresent() && !quarantined(target);
    }

    @Override
    public StructureWriteResult writeCell(StructureRef target, BlockPos localOffset, BlockState state) {
        return replace(target, localOffset, Blocks.AIR.defaultBlockState(), state);
    }

    @Override
    public Optional<BlockState> readCell(StructureRef target, BlockPos localOffset) {
        var runtime = runtime(target);
        var subLevel = runtime == null ? null : runtime.get(new SubLevelId(target.id())).orElse(null);
        if (subLevel == null || !ready(target) || !inside(subLevel, localOffset)) return Optional.empty();
        return Optional.of(runtime.storage().blockState(subLevel.plot(), localOffset));
    }

    @Override
    public StructureWriteResult clearCell(StructureRef target, BlockPos localOffset, BlockState expected) {
        return replace(target, localOffset, expected, Blocks.AIR.defaultBlockState());
    }

    private StructureWriteResult replace(StructureRef target, BlockPos local, BlockState expected,
                                           BlockState replacement) {
        var runtime = runtime(target);
        var subLevel = runtime == null ? null : runtime.get(new SubLevelId(target.id())).orElse(null);
        if (subLevel == null || !ready(target)) return StructureWriteResult.NOT_READY;
        if (!inside(subLevel, local)) return StructureWriteResult.OUT_OF_BOUNDS;
        var storage = runtime.storage();
        if (!storage.blockState(subLevel.plot(), local).equals(expected)
                || storage.blockEntity(subLevel.plot(), local) != null) return StructureWriteResult.CONFLICT;
        if (replacement.equals(expected)) return StructureWriteResult.APPLIED;
        storage.setBlock(subLevel.plot(), local, replacement, SubLevelCaptureService.TRANSACTION_FLAGS);
        return storage.blockState(subLevel.plot(), local).equals(replacement)
                ? StructureWriteResult.APPLIED : StructureWriteResult.REJECTED;
    }

    @Override
    public boolean quarantined(StructureRef target) {
        var runtime = runtime(target);
        return runtime == null || NativeStructureStorageErrors.failed(runtime.storage().level());
    }

    @Override
    public void deactivate(StructureRef target) {
        var runtime = runtime(target);
        if (runtime != null) runtime.deactivate(new SubLevelId(target.id()));
    }

    @Override
    public boolean save(StructureRef target) {
        var runtime = runtime(target);
        if (runtime == null || resolve(target, false).isEmpty() || quarantined(target)
                || NativeStructureStorageErrors.failed(runtime.host())) return false;
        // Both sides must reach disk before the write-ahead record can be retired.
        runtime.storage().level().save(null, true, false);
        if (NativeStructureStorageErrors.failed(runtime.storage().level())) return false;
        runtime.persist();
        runtime.host().save(null, true, false);
        return !NativeStructureStorageErrors.failed(runtime.host());
    }

    @Override
    public boolean releaseEmpty(StructureRef target) {
        var runtime = runtime(target);
        if (runtime == null || resolve(target, false).isEmpty() || quarantined(target)) return false;
        if (!runtime.remove(new SubLevelId(target.id()))) return false;
        // Plot allocation clears retired cells before reuse, so rollback does not depend on
        // per-cell clearing and cannot accidentally publish a partial body.
        runtime.persist();
        return true;
    }

    @Override
    public Optional<StructureHandle> resolve(StructureRef target, boolean recovery) {
        var runtime = runtime(target);
        var subLevel = runtime == null ? null : runtime.get(new SubLevelId(target.id())).orElse(null);
        if (subLevel == null || subLevel.state() == SubLevelState.REMOVED) return Optional.empty();
        var handle = handle(runtime, subLevel);
        return recovery || handle.reference().equals(target) ? Optional.of(handle) : Optional.empty();
    }

    @Override
    public boolean verifyCopy(StructureRef target, int expectedCells) {
        var runtime = runtime(target);
        if (runtime == null || !ready(target)) return false;
        var subLevel = runtime.get(new SubLevelId(target.id())).orElseThrow();
        var geometry = PlotGeometry.capture(runtime.storage(), subLevel.plot(), subLevel.localBounds());
        return geometry.blocks() == expectedCells && !geometry.boxes().isEmpty();
    }

    @Override
    public boolean finalizeTarget(StructureRef target, BlockPos origin, List<BlockPos> positions) {
        var runtime = runtime(target);
        return runtime != null && ready(target) && runtime.finalizeStaged(new SubLevelId(target.id()));
    }

    @Override
    public void releasePhysics(ServerLevel host, StructureRef target) {
        var runtime = runtime(target);
        if (runtime != null && runtime.host() == host && resolve(target, false).isPresent()) {
            runtime.suspendCapture(new SubLevelId(target.id()));
        }
    }

    @Override public Identifier backendId() { return SubLevelStructureRefs.BACKEND; }

    private SubLevelRuntime runtime(StructureRef target) {
        if (!server.isSameThread()) throw new IllegalStateException("SubLevel capture requires the server thread");
        if (!SubLevelStructureRefs.isSubLevel(target)) return null;
        return SubLevelServer.runtime(server, new SubLevelId(target.id()))
                .filter(value -> value.host().dimension().identifier().equals(target.dimension())).orElse(null);
    }

    private static StructureHandle handle(SubLevelRuntime runtime, SubLevel value) {
        return new SubLevelStructureHandle(value, runtime.storage(), runtime.host().dimension().identifier());
    }

    private static boolean inside(SubLevel value, BlockPos local) {
        return value.localBounds().contains(local.getX() + 0.5, local.getY() + 0.5, local.getZ() + 0.5);
    }
}
