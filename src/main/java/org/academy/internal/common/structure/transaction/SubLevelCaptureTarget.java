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
import org.academy.internal.common.sublevel.TargetedChunkDurability;
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
        long started = CaptureTimings.begin();
        var staged = runtime.createStaged(new SubLevelId(transactionId),
                new AABB(0, 0, 0, maxX, maxY, maxZ),
                StructurePose.identity(Vec3.atLowerCornerOf(origin)));
        CaptureTimings.record(transactionId.toString(), "sublevel_create_staged", started);
        return staged.map(value -> handle(runtime, value).reference());
    }

    @Override public String allocationFailureDetail() { return "sublevel_plot_capacity_unavailable"; }

    @Override
    public Optional<List<RestorationCell>> restorationCells(StructureRef target) {
        var runtime = runtime(target);
        var body = runtime == null ? null : runtime.get(new SubLevelId(target.id())).orElse(null);
        if (body == null || !runtime.storage().loadedEntities(body.plot()).map(List::isEmpty).orElse(false)) return Optional.empty();
        var bounds = body.localBounds();
        if (bounds.getXsize() * bounds.getYsize() * bounds.getZsize() > 32768) return Optional.empty();
        var cells = new java.util.ArrayList<RestorationCell>();
        for (var position : BlockPos.betweenClosed((int) Math.floor(bounds.minX), (int) Math.floor(bounds.minY),
                (int) Math.floor(bounds.minZ), (int) Math.ceil(bounds.maxX) - 1,
                (int) Math.ceil(bounds.maxY) - 1, (int) Math.ceil(bounds.maxZ) - 1)) {
            var state = runtime.storage().blockState(body.plot(), position);
            if (state.isAir()) continue;
            if (state.hasBlockEntity() || !state.getFluidState().isEmpty()
                    || runtime.storage().blockEntity(body.plot(), position) != null
                    || cells.size() >= StructureTransactionJournal.MAX_CELLS) return Optional.empty();
            cells.add(new RestorationCell(position, state));
        }
        return cells.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(cells));
    }

    @Override
    public Optional<net.minecraft.nbt.CompoundTag> restorationEvidence(StructureRef target) {
        var runtime = runtime(target);
        var cells = restorationCells(target).orElse(null);
        if (runtime == null || cells == null) return Optional.empty();
        var tag = new net.minecraft.nbt.CompoundTag();
        var images = new net.minecraft.nbt.ListTag();
        for (var cell : cells) {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, runtime.host().registryAccess());
            output.store("state", BlockState.CODEC, cell.state());
            var image = output.buildResult();
            image.putInt("x", cell.localPosition().getX()); image.putInt("y", cell.localPosition().getY()); image.putInt("z", cell.localPosition().getZ());
            images.add(image);
        }
        tag.put("cells", images);
        return Optional.of(tag);
    }

    @Override
    public boolean checkpointRestoration(StructureRef target) {
        var runtime = runtime(target);
        if (runtime == null || !runtime.flushPlotChunks(new SubLevelId(target.id()))) return false;
        runtime.persist();
        return !NativeStructureStorageErrors.failed(runtime.storage().level());
    }

    @Override
    public boolean isolateRestoration(StructureRef target) {
        var runtime = runtime(target);
        if (runtime == null) return false;
        return runtime.isolateRestoration(new SubLevelId(target.id()));
    }

    @Override
    public boolean retireRestored(StructureRef target, net.minecraft.nbt.CompoundTag evidence) {
        // Recovery follows persistent identity even when reactivation changed its generation.
        for (var level : server.getAllLevels()) {
            if (!level.dimension().identifier().equals(target.dimension())) continue;
            var runtime = SubLevelServer.runtime(level).orElse(null);
            if (runtime == null) return false;
            var id = new SubLevelId(target.id());
            var body = runtime.get(id).orElse(null);
            // An unavailable activation may still own a persisted plot. Keep the journal
            // until its manifest ownership is actually retired, rather than losing cleanup.
            if (body == null) return !runtime.hasPersisted(id);
            var images = evidence.getList("cells").orElseThrow();
            var cells = new java.util.LinkedHashMap<BlockPos, BlockState>();
            for (var item : images) {
                if (!(item instanceof net.minecraft.nbt.CompoundTag image)) return false;
                var local = new BlockPos(image.getInt("x").orElseThrow(), image.getInt("y").orElseThrow(), image.getInt("z").orElseThrow());
                var input = net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                        runtime.host().registryAccess(), image);
                cells.put(local, input.read("state", BlockState.CODEC).orElseThrow());
            }
            // The durable commit owns only these source images. A partial prior cleanup is
            // idempotent, but an unexpected edit or block entity retains the reservation.
            for (var cell : cells.entrySet()) {
                var current = runtime.storage().blockState(body.plot(), cell.getKey());
                if (!current.isAir() && !current.equals(cell.getValue())
                        || runtime.storage().blockEntity(body.plot(), cell.getKey()) != null) return false;
            }
            for (var cell : cells.entrySet()) {
                var position = runtime.storage().worldPosition(body.plot(), cell.getKey());
                var area = new net.minecraft.world.level.levelgen.structure.BoundingBox(position);
                runtime.storage().level().getBlockTicks().clearArea(area);
                runtime.storage().level().getFluidTicks().clearArea(area);
                if (!runtime.storage().blockState(body.plot(), cell.getKey()).isAir()
                        && !runtime.storage().setBlock(body.plot(), cell.getKey(), Blocks.AIR.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS)) return false;
            }
            if (!runtime.flushPlotChunks(id)) return false;
            return runtime.remove(id);
        }
        return false;
    }
    @Override public int cellBudget() { return 256; }

    @Override
    public @Nullable String rejectionDetail(ServerLevel host, BlockPos position, BlockState state,
                                             @Nullable BlockEntity blockEntity) {
        return classifyAdmission(blockEntity != null || state.hasBlockEntity(),
                state.hasProperty(BlockStateProperties.WATERLOGGED)
                        && state.getValue(BlockStateProperties.WATERLOGGED),
                !state.getFluidState().isEmpty(),
                host.getBlockTicks().hasScheduledTick(position, state.getBlock()),
                state.getDestroySpeed(host, position));
    }

    /** The shared transaction persists block states; richer payloads remain an explicit rejection. */
    static @Nullable String classifyAdmission(boolean blockEntity, boolean waterlogged, boolean fluid,
                                               boolean scheduledTick, float destroySpeed) {
        if (blockEntity) return "block_entity_not_supported";
        if (waterlogged) return "waterlogged_not_supported";
        if (fluid) return "fluid_not_supported";
        if (scheduledTick) return "scheduled_tick_pending";
        if (destroySpeed < 0.0f) return "unbreakable";
        // Decorative cells move with their supporting structure and retain their render/state
        // data. verifyCopy still rejects a wholly empty collision body before source removal.
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
        return save(target, List.of());
    }

    @Override
    public boolean save(StructureRef target, List<BlockPos> sourcePositions) {
        var runtime = runtime(target);
        if (runtime == null || resolve(target, false).isEmpty() || quarantined(target)
                || NativeStructureStorageErrors.failed(runtime.host())) return false;
        // Both sides must reach disk before the write-ahead record can be retired. Save only the
        // target plot chunks and the host chunks containing the removed source cells; other
        // plots/chunks remain for the normal level save.
        String owner = target.id().toString();
        long plotStarted = CaptureTimings.begin();
        boolean plotSaved = runtime.flushPlotChunks(new SubLevelId(target.id()));
        CaptureTimings.record(owner, "save_plot_level", plotStarted);
        if (!plotSaved || NativeStructureStorageErrors.failed(runtime.storage().level())) return false;
        long manifestStarted = CaptureTimings.begin();
        runtime.persist();
        CaptureTimings.record(owner, "save_manifest", manifestStarted);
        long hostStarted = CaptureTimings.begin();
        boolean hostSaved = sourcePositions.isEmpty()
                ? fullHostSave(runtime)
                : TargetedChunkDurability.saveAround(runtime.host(), sourcePositions);
        CaptureTimings.record(owner, "save_host", hostStarted);
        return hostSaved;
    }

    private static boolean fullHostSave(SubLevelRuntime runtime) {
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
        long started = CaptureTimings.begin();
        var subLevel = runtime.get(new SubLevelId(target.id())).orElseThrow();
        var geometry = PlotGeometry.capture(runtime.storage(), subLevel.plot(), subLevel.localBounds());
        CaptureTimings.record(target.id().toString(), "sublevel_verify_geometry", started);
        return geometry.blocks() == expectedCells && !geometry.boxes().isEmpty();
    }

    @Override
    public boolean finalizeTarget(StructureRef target, BlockPos origin, List<BlockPos> positions) {
        var runtime = runtime(target);
        long started = CaptureTimings.begin();
        boolean finalized = runtime != null && ready(target) && runtime.finalizeStaged(new SubLevelId(target.id()));
        CaptureTimings.record(target.id().toString(), "sublevel_finalize_staged", started);
        return finalized;
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
