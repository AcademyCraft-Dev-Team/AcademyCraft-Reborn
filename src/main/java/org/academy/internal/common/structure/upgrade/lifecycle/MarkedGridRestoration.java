package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.structure.transaction.CaptureTarget;
import org.academy.internal.common.structure.transaction.StructureCellPayload;
import org.academy.internal.common.structure.transaction.PayloadIsolation;
import org.academy.internal.common.structure.transaction.StructureTransactionJournal;
import org.academy.internal.common.structure.transaction.SubLevelCaptureTarget;
import org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess;
import org.academy.internal.common.structure.upgrade.control.StructureRestSupport;
import org.academy.internal.common.sublevel.SubLevelCaptureService;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.internal.common.sublevel.TargetedChunkDurability;

import java.io.IOException;
import java.util.*;

/** Loaded-only, air-only transfer with a durable target-before-source ownership decision. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MarkedGridRestoration implements StructureGridRestoration {
    public static final MarkedGridRestoration INSTANCE = new MarkedGridRestoration();
    public static final Identifier POLICY_ID = Identifier.fromNamespaceAndPath("academy", "marked_rest");
    private static final Map<MinecraftServer, Registry> SERVERS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, java.util.function.Consumer<Checkpoint>> OBSERVERS = new IdentityHashMap<>();
    private static final int PREVIEW_TICKS = 20;
    private static final int MAX_RECEIPTS = 256;

    private MarkedGridRestoration() {}

    public enum CheckpointPhase { TARGET_SAVED, COMMIT_SAVED }
    public record Checkpoint(ServerLevel host, StructureRef reference, UUID operationId, CheckpointPhase phase) {}

    /** Internal failure-injection observation; production installs no listener. */
    public static void observeCheckpoints(MinecraftServer server, java.util.function.Consumer<Checkpoint> observer) {
        if (!server.isSameThread()) throw new IllegalStateException("Restoration observation requires the server thread");
        OBSERVERS.put(server, Objects.requireNonNull(observer));
    }

    private static void checkpoint(ServerLevel host, StructureRef reference, UUID operationId, CheckpointPhase phase) {
        var observer = OBSERVERS.get(host.getServer());
        if (observer != null) observer.accept(new Checkpoint(host, reference, operationId, phase));
    }

    @Override
    public StructureOperation.Result<Preview> preview(Access access, Options options) {
        return preview(access, false);
    }

    private StructureOperation.Result<Preview> preview(Access access, boolean administrative) {
        requireThread(access.host());
        if (!administrative && authorized(access).isEmpty()) return failure(StructureOperation.Status.DENIED, "restoration_owner_or_policy");
        var registry = registry(access.host().getServer());
        if (registry == null) return failure(StructureOperation.Status.BLOCKED, "restoration_journal_unavailable");
        prune(access.host(), registry);
        try {
            var plan = plan(access, administrative);
            if (plan == null) return failure(StructureOperation.Status.BLOCKED, "restoration_target_or_content_unavailable");
            var id = UUID.randomUUID();
            var body = plan.body();
            var preview = new Preview(id, access.reference(), body.runtimeId(), body.contentRevision(), 0,
                    body.motion().pose(), plan.alignment().pose(), plan.cells().stream()
                    .map(cell -> new Placement(cell.local(), cell.target())).toList());
            registry.previews.put(id, new Prepared(access, preview, plan, access.host().getGameTime(), administrative));
            while (registry.previews.size() > MAX_RECEIPTS) registry.previews.remove(registry.previews.keySet().iterator().next());
            return result(StructureOperation.Status.READY, "restoration_preview", null, preview);
        } catch (RuntimeException failure) {
            return failure(StructureOperation.Status.BLOCKED, "restoration_content_unavailable");
        }
    }

    @Override
    public StructureOperation.Result<Progress> request(Access access, Request request) {
        if (request.cause() == Cause.ADMINISTRATIVE_REQUEST) return failure(StructureOperation.Status.DENIED, "restoration_administrator_required");
        return request(access, request, false);
    }

    /** The only administrative entry point requires the real server command permission. */
    public StructureOperation.Result<Progress> restoreAdministrative(net.minecraft.commands.CommandSourceStack source,
            ServerLevel host, StructureRef reference) {
        requireThread(host);
        if (source.getServer() != host.getServer()
                || !net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(source)
                || !SubLevelStructureRefs.isSubLevel(reference)
                || !reference.dimension().equals(host.dimension().identifier())
                || !LiveStructureProvenance.healthy(host.getServer())) {
            return failure(StructureOperation.Status.DENIED, "restoration_administrator_required");
        }
        var access = new Access(host, reference, new RestorationActor(Optional.ofNullable(source.getPlayer())
                .map(ServerPlayer::getUUID), POLICY_ID));
        var preview = preview(access, true);
        if (preview.value().isEmpty()) return failure(preview.status(), preview.detail());
        return request(access, new Request(preview.value().orElseThrow().previewId(), Cause.ADMINISTRATIVE_REQUEST), true);
    }

    private StructureOperation.Result<Progress> request(Access access, Request request, boolean administrative) {
        requireThread(access.host());
        var metadata = administrative ? LiveStructureProvenance.metadata(access.host().getServer(), access.reference()).orElse(null)
                : authorized(access).orElse(null);
        if (!administrative && metadata == null) return failure(StructureOperation.Status.DENIED, "restoration_owner_or_policy");
        var registry = registry(access.host().getServer());
        if (registry == null) return failure(StructureOperation.Status.BLOCKED, "restoration_journal_unavailable");
        prune(access.host(), registry);
        var prepared = registry.previews.get(request.previewId());
        if (prepared == null || prepared.administrative() != administrative || !prepared.access().equals(access)) return failure(StructureOperation.Status.STALE, "restoration_preview_stale");
        if (busy(access.host(), access.reference()) || activityBusy(access, administrative)) {
            return failure(StructureOperation.Status.CONFLICT, "restoration_in_use");
        }
        var target = target(access.host(), access.reference()).orElse(null);
        var current = plan(access, administrative);
        if (target == null || current == null || !prepared.plan().same(current)) {
            return failure(StructureOperation.Status.STALE, "restoration_preview_changed");
        }
        if (!administrative && !atRest(access.host(), current.body())) return failure(StructureOperation.Status.BLOCKED, "restoration_not_at_rest");
        var owner = player(access);
        boolean offlineOwner = metadata == null || metadata.ownerId()
                .map(id -> access.host().getServer().getPlayerList().getPlayer(id)).isEmpty();
        var event = new StructureRestorationEvent(access.host(), access.reference(), metadata,
                prepared.preview().placements(), offlineOwner, request.cause(), access.actor().ownerId());
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) return failure(StructureOperation.Status.DENIED, "restoration_event_cancelled");
        // Listeners may edit content, acquire a controller or move entities; never trust the earlier snapshot.
        current = plan(access, administrative);
        if (current == null || !prepared.plan().same(current)
                || activityBusy(access, administrative)) {
            return failure(StructureOperation.Status.STALE, "restoration_changed_by_event");
        }
        if (!target.checkpointRestoration(access.reference())) return failure(StructureOperation.Status.BLOCKED, "restoration_source_checkpoint_failed");
        var evidence = target.restorationEvidence(access.reference()).orElse(null);
        if (evidence == null) return failure(StructureOperation.Status.BLOCKED, "restoration_source_evidence_unavailable");
        var checkpointedPlan = plan(access, administrative);
        if (checkpointedPlan == null || !current.same(checkpointedPlan)) return failure(StructureOperation.Status.STALE, "restoration_source_checkpoint_changed");
        var record = record(access, metadata, current, evidence);
        var written = new ArrayList<Cell>();
        boolean deciding = false;
        try {
            registry.journal.persist(record);
            registry.put(record);
            registry.previews.remove(request.previewId());
            if (!target.isolateRestoration(access.reference())) throw new IllegalStateException("Source isolation failed");
            var snapshots = new ArrayList<BlockSnapshot>();
            for (var cell : current.cells()) {
                if (owner != null) snapshots.add(BlockSnapshot.create(access.host().dimension(), access.host(), cell.target()));
                if (!loaded(access.host(), cell.target()) || !access.host().getBlockState(cell.target()).isAir()) {
                    throw new IllegalStateException("Target changed during restoration");
                }
                written.add(cell);
                StructureCellPayload.write(access.host(), cell.target(), cell.placedData(), SubLevelCaptureService.TRANSACTION_FLAGS);
            }
            if (owner != null && NeoForge.EVENT_BUS.post(new BlockEvent.EntityMultiPlaceEvent(snapshots,
                    Blocks.AIR.defaultBlockState(), owner)).isCanceled()) throw new IllegalStateException("Placement event cancelled");
            if (!matchesTargets(access.host(), current.cells()) || !matchesSource(target, access.reference(), current.cells())
                    || !target.restorationEvidence(access.reference()).filter(evidence::equals).isPresent()
                    || !(administrative ? LiveStructureProvenance.healthy(access.host().getServer())
                            && Objects.equals(metadata, LiveStructureProvenance.metadata(access.host().getServer(), access.reference()).orElse(null))
                            : authorized(access).filter(metadata::equals).isPresent())
                    || !targetsSafe(access, current.cells())) {
                throw new IllegalStateException("Content changed during placement callbacks");
            }
            if (!TargetedChunkDurability.saveAround(access.host(), current.cells().stream().map(Cell::target).toList())) {
                throw new IllegalStateException("Target durability failed");
            }
            checkpoint(access.host(), access.reference(), record.transactionId(), CheckpointPhase.TARGET_SAVED);
            deciding = true;
            record = record.withStage(StructureTransactionJournal.Stage.COMMIT_DECIDED);
            registry.journal.persist(record);
            registry.put(record);
            checkpoint(access.host(), access.reference(), record.transactionId(), CheckpointPhase.COMMIT_SAVED);
            return finish(access.host(), registry, record);
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Marked restoration retained/rolled back for {}", access.reference(), failure);
            if (!deciding && matchesSource(target, access.reference(), current.cells()) && rollback(access.host(), written)) {
                try { registry.journal.delete(record.transactionId()); registry.remove(record.transactionId()); }
                catch (IOException cleanup) { AcademyCraft.LOGGER.error("Restoration rollback journal retained", cleanup); }
                return failure(StructureOperation.Status.BLOCKED, "restoration_rolled_back");
            }
            // A failed forced write can have reached disk. Re-read the decision instead of guessing its direction.
            for (var actual : registry.journal.records()) registry.put(actual);
            return progress(record, Phase.RECOVERY_REQUIRED, StructureOperation.Status.RUNNING, "restoration_recovery_required");
        }
    }

    /** Recovery is independent of owner availability; the durable decision already owns the transfer. */
    public void recover(ServerLevel host) {
        requireThread(host);
        var registry = registry(host.getServer());
        if (registry == null) return;
        // Rotate before attempting any I/O, so an unloaded/conflicted transfer cannot starve
        // the next one. Visits are bounded even when other dimensions own every queued entry.
        int visits = Math.min(32, registry.recoveryOrder.size());
        for (int visit = 0; visit < visits; visit++) {
            var operationId = registry.recoveryOrder.removeFirst();
            var record = registry.records.get(operationId);
            if (record == null || record.stage() == StructureTransactionJournal.Stage.PUBLISHED) continue;
            registry.recoveryOrder.addLast(operationId);
            if (!record.hostDimension().equals(host.dimension().identifier())) continue;
            try {
                registry.isolate(record);
                var cells = decodeCells(host, record);
                if (record.stage().compareTo(StructureTransactionJournal.Stage.COMMIT_DECIDED) < 0) {
                    var source = target(host, reference(record)).orElse(null);
                    if (source != null && matchesSource(source, reference(record), cells) && rollback(host, cells)) {
                        registry.journal.delete(record.transactionId());
                        registry.remove(record.transactionId());
                    }
                } else finish(host, registry, record);
            } catch (IOException | RuntimeException failure) {
                AcademyCraft.LOGGER.error("Marked restoration recovery retained {}", record.transactionId(), failure);
            }
            return;
        }
    }

    /** Holds and unfinished ownership transfers suppress the low-cost rest sampler. */
    public boolean busy(ServerLevel host, StructureRef reference) {
        requireThread(host);
        var registry = registry(host.getServer());
        if (registry == null) return true;
        prune(host, registry);
        return registry.holds.values().stream().anyMatch(entry -> entry.context().host() == host
                && sameStructure(entry.hold().reference(), reference))
                || registry.pending.contains(Key.of(reference));
    }

    /** Source isolation reads this durable decision before exposing a restored activation. */
    public static boolean pending(MinecraftServer server, StructureRef reference) {
        var registry = registry(server);
        return registry == null ? SubLevelStructureRefs.isSubLevel(reference) || LiveStructureProvenance.metadata(server, reference).isPresent()
                : registry.pending.contains(Key.of(reference));
    }

    public static boolean pendingCommitted(MinecraftServer server, StructureRef reference) {
        var registry = registry(server);
        return registry != null && registry.committed.contains(Key.of(reference));
    }

    public static boolean hasPending(MinecraftServer server) {
        var registry = registry(server);
        return registry == null || !registry.pending.isEmpty();
    }

    /** Backend allocator reservations for incomplete ownership transfers, with no storage addresses exposed publicly. */
    public static List<CompoundTag> sourceReservations(MinecraftServer server, Identifier backend, Identifier dimension) {
        var registry = registry(server);
        if (registry == null || registry.pending.isEmpty()) return List.of();
        return registry.records.values().stream().filter(record -> record.stage() != StructureTransactionJournal.Stage.PUBLISHED
                && record.backend().equals(backend) && record.hostDimension().equals(dimension))
                .flatMap(record -> record.cells().getFirst().payload().getCompound("source_evidence").stream()).toList();
    }

    @Override
    public StructureOperation.Result<Progress> status(ServerLevel host, RestorationActor actor, UUID operationId) {
        requireThread(host);
        var registry = registry(host.getServer());
        var record = registry == null ? null : registry.records.get(operationId);
        if (record == null || !actor.policyId().equals(POLICY_ID) || actor.ownerId().isEmpty()
                || !record.hostDimension().equals(host.dimension().identifier())
                || !actor.ownerId().get().toString().equals(record.cells().getFirst().payload().getString("owner").orElse(""))) {
            return failure(StructureOperation.Status.DENIED, "restoration_receipt_unavailable");
        }
        return record.stage() == StructureTransactionJournal.Stage.PUBLISHED
                ? progress(record, Phase.RESTORED, StructureOperation.Status.COMPLETED, "restored")
                : progress(record, Phase.RECOVERY_REQUIRED, StructureOperation.Status.RUNNING, "restoration_recovery_required");
    }

    @Override
    public StructureOperation.Result<Progress> cancel(Access access, UUID operationId) {
        var status = status(access.host(), access.actor(), operationId);
        if (status.value().isEmpty() || !sameStructure(status.value().get().reference(), access.reference())) return status;
        var registry = registry(access.host().getServer());
        var record = registry.records.get(operationId);
        if (record.stage().compareTo(StructureTransactionJournal.Stage.COMMIT_DECIDED) >= 0) {
            return failure(StructureOperation.Status.CONFLICT, "restoration_commit_cannot_cancel");
        }
        try {
            var cells = decodeCells(access.host(), record);
            var source = target(access.host(), reference(record)).orElse(null);
            if (source == null || !matchesSource(source, reference(record), cells) || !rollback(access.host(), cells)) return status;
            registry.journal.delete(operationId);
            registry.remove(operationId);
            return progress(record, Phase.CANCELLED, StructureOperation.Status.COMPLETED, "restoration_cancelled");
        } catch (IOException | RuntimeException failure) { return status; }
    }

    @Override
    public StructureOperation.Result<Hold> hold(StructureOperation.Context context, HoldRequest request) {
        if (!controller(context, request.controllerId())) return failure(StructureOperation.Status.DENIED, "restoration_hold_authority");
        var registry = registry(context.host().getServer());
        if (registry == null) return failure(StructureOperation.Status.BLOCKED, "restoration_hold_capacity");
        prune(context.host(), registry);
        if (registry.holds.size() >= MAX_RECEIPTS) return failure(StructureOperation.Status.BLOCKED, "restoration_hold_capacity");
        var hold = new Hold(UUID.randomUUID(), context.reference(), request.controllerId(), request.reason(),
                context.host().getGameTime() + request.leaseTicks());
        registry.holds.put(hold.holdId(), new Held(context, hold));
        return result(StructureOperation.Status.READY, "restoration_held", null, hold);
    }

    @Override
    public StructureOperation.Result<Hold> renewHold(StructureOperation.Context context, UUID holdId, HoldRequest request) {
        var registry = registry(context.host().getServer());
        var held = registry == null ? null : registry.holds.get(holdId);
        if (!owns(held, context) || !held.hold().controllerId().equals(request.controllerId())
                || held.hold().expiresAtGameTime() <= context.host().getGameTime()
                || !controller(context, request.controllerId())) return failure(StructureOperation.Status.DENIED, "restoration_hold_authority");
        var hold = new Hold(holdId, context.reference(), request.controllerId(), request.reason(),
                context.host().getGameTime() + request.leaseTicks());
        registry.holds.put(holdId, new Held(context, hold));
        return result(StructureOperation.Status.READY, "restoration_hold_renewed", null, hold);
    }

    @Override
    public StructureOperation.Result<Void> releaseHold(StructureOperation.Context context, UUID holdId) {
        requireThread(context.host());
        var registry = registry(context.host().getServer());
        if (registry == null || !owns(registry.holds.get(holdId), context)) return failure(StructureOperation.Status.DENIED, "restoration_hold_authority");
        registry.holds.remove(holdId);
        return result(StructureOperation.Status.COMPLETED, "restoration_hold_released", null, null);
    }

    private static boolean controller(StructureOperation.Context context, UUID controlId) {
        requireThread(context.host());
        var body = StructureBackendRouter.orientationAccess(context.host(), context.reference())
                .flatMap(value -> value.observe(context.host(), context.reference())).orElse(null);
        return body != null && body.runtimeId().equals(context.runtimeId()) && body.contentRevision() == context.expectedContentRevision()
                && StructureDirectControl.validate(context.host(), context.reference(), context.actor(), controlId) == StructureDirectControl.Status.ACTIVE;
    }

    private static boolean owns(Held held, StructureOperation.Context context) {
        return held != null && held.context().host() == context.host() && held.context().reference().equals(context.reference())
                && held.context().runtimeId().equals(context.runtimeId()) && held.context().actor() == context.actor();
    }

    private static Optional<StructureProvenance.Metadata> authorized(Access access) {
        if (!access.actor().policyId().equals(POLICY_ID) || !access.reference().dimension().equals(access.host().dimension().identifier())
                || !LiveStructureProvenance.healthy(access.host().getServer())) return Optional.empty();
        var metadata = LiveStructureProvenance.metadata(access.host().getServer(), access.reference())
                .filter(value -> value.ownerId().isPresent() && value.ownerId().equals(access.actor().ownerId())
                        && value.restorePolicy().id().equals(POLICY_ID) && value.restorePolicy().version() == 1
                        && value.restorePolicy().parameters().isEmpty());
        if (player(access) == null && !StructureRestSettings.get(access.host().getServer()).allowOfflineOwners()) return Optional.empty();
        return metadata;
    }

    private static ServerPlayer player(Access access) {
        return access.actor().ownerId().map(id -> access.host().getServer().getPlayerList().getPlayer(id)).orElse(null);
    }

    private static boolean activityBusy(Access access, boolean administrative) {
        if (!administrative) return StructureRestorationActivity.isBusy(access.host(), access.reference());
        var activation = org.academy.internal.common.sublevel.SubLevelActivationRegistry
                .find(new org.academy.internal.common.sublevel.SubLevelId(access.reference().id())).orElse(null);
        return activation == null
                || org.academy.internal.common.structure.transaction.StructureCaptureCoordinator.isPending(access.host().getServer(), access.reference())
                || StructureDirectControl.occupied(access.reference(), activation.runtimeId())
                || org.academy.internal.common.structure.upgrade.control.LiveStructureExtraction.controls(access.host(), access.reference())
                || org.academy.internal.common.structure.upgrade.control.LiveStructureOrientation.controls(access.host(), access.reference())
                || org.academy.internal.common.sublevel.SubLevelEnhancementBootstrap.INTERACTIONS.restorationBusy(access.host(), access.reference());
    }

    private static Plan plan(Access access, boolean administrative) {
        var host = access.host();
        var target = target(host, access.reference()).orElse(null);
        var backend = StructureBackendRouter.orientationAccess(host, access.reference()).orElse(null);
        var body = backend == null ? null : backend.observe(host, access.reference()).orElse(null);
        if (administrative && body == null) body = administrativeBody(access);
        if (target == null || body == null || !administrative && !body.terrainReady()) return null;
        var source = target.restorationCells(access.reference()).orElse(null);
        if (source == null || source.isEmpty()) return null;
        var alignment = RestorationGrid.align(body.motion().pose(), source.stream().map(CaptureTarget.RestorationCell::localPosition).toList());
        for (int lift = 0; lift <= RestorationGrid.MAX_CLEARANCE_LIFT; lift++) {
            var candidate = alignment.lifted(lift);
            var cells = new ArrayList<Cell>();
            var positions = new HashSet<BlockPos>();
            boolean obstructed = false;
            for (var item : source) {
                var destination = candidate.target(item.localPosition());
                if (!positions.add(destination) || !loaded(host, destination) || !host.isInWorldBounds(destination)
                        || !host.getWorldBorder().isWithinBounds(destination)) return null;
                var existing = host.getBlockState(destination);
                if (!existing.isAir()) {
                    // A canopy/step can intersect the snapped upright image even though the
                    // physical image rests on it. Search upward without erasing any world cells.
                    // Non-colliding content (e.g. LIGHT), fluids and block entities remain a refusal.
                    if (existing.hasBlockEntity() || !existing.getFluidState().isEmpty()
                            || !RestorationPlacementView.collidableObstacle(host, destination)) return null;
                    obstructed = true;
                }
                var state = item.state().rotate(candidate.rotation());
                var placed = new StructureBlockData(state, item.data().blockEntityData());
                org.academy.internal.common.structure.BlockStructureTransferState.validateWrite(host, destination, state, placed.blockEntityData());
                cells.add(new Cell(item.localPosition(), destination, item.data(), placed));
            }
            if (!obstructed) return targetsSafe(access, cells) ? new Plan(body, candidate, List.copyOf(cells)) : null;
        }
        return null;
    }

    /** Maintenance can read an inactive/terrain-paused local plot without manufacturing a solver body. */
    private static OrientationBackendAccess.Body administrativeBody(Access access) {
        var runtime = org.academy.internal.common.sublevel.SubLevelServer.runtime(access.host()).orElse(null);
        var id = new org.academy.internal.common.sublevel.SubLevelId(access.reference().id());
        var source = runtime == null ? null : runtime.get(id).orElse(null);
        var activation = org.academy.internal.common.sublevel.SubLevelActivationRegistry.find(id).orElse(null);
        if (source == null || activation == null || activation.generation() != access.reference().generation()
                || source.state() == org.academy.internal.common.sublevel.SubLevelState.ALLOCATED
                || source.state() == org.academy.internal.common.sublevel.SubLevelState.LOADING
                || source.state() == org.academy.internal.common.sublevel.SubLevelState.REMOVED) return null;
        return new OrientationBackendAccess.Body(access.reference(), activation.runtimeId(), source.geometryRevision(), source.revision(),
                new org.academy.internal.common.structure.upgrade.control.OrientationController.State(source.pose(),
                        net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, 1),
                net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, List.of(source.localBounds()),
                new org.academy.internal.common.structure.upgrade.control.OrientationTargets.Baselines(Optional.empty(), Optional.empty()), false);
    }

    /** Rechecked after placement listeners, which can move entities or edit supporting cells. */
    private static boolean targetsSafe(Access access, List<Cell> cells) {
        var host = access.host();
        var owner = player(access);
        var entityManager = ((org.academy.mixin.common.ServerLevelEntityManagerAccessor) host).academy$getEntityManager();
        @SuppressWarnings("unchecked")
        var entityStorage = ((org.academy.mixin.common.PersistentEntitySectionManagerAccessor<net.minecraft.world.entity.Entity>)
                (Object) entityManager).academy$getSectionStorage();
        var inspectedChunks = new HashSet<Long>();
        AABB area = null;
        for (var cell : cells) {
            if (!loaded(host, cell.target()) || !host.getWorldBorder().isWithinBounds(cell.target())
                    || owner != null && !host.mayInteract(owner, cell.target())) return false;
            var box = new AABB(cell.target()).deflate(1e-5);
            long chunk = new net.minecraft.world.level.ChunkPos(cell.target().getX() >> 4, cell.target().getZ() >> 4).pack();
            if (!entityManager.areEntitiesLoaded(chunk)) return false;
            if (inspectedChunks.add(chunk) && entityStorage.getExistingSectionsInChunk(chunk)
                    .flatMap(section -> section.getEntities()).filter(entity -> !entity.isRemoved())
                    .anyMatch(entity -> cells.stream().anyMatch(candidate -> entity.getBoundingBox()
                            .intersects(new AABB(candidate.target()).deflate(1e-5))))) return false;
            if (!host.getEntities((net.minecraft.world.entity.Entity) null, box, entity -> !entity.isRemoved()).isEmpty()) return false;
            area = area == null ? box : area.minmax(box);
        }
        if (!StructureRestSupport.legacyClear(host, area)) return false;
        var localRuntime = org.academy.internal.common.sublevel.SubLevelServer.runtime(host).orElse(null);
        if (localRuntime != null) for (var other : localRuntime.subLevels()) {
            // Inactive plots can retain a collision/view image even without a solver observation.
            if (other.state() != org.academy.internal.common.sublevel.SubLevelState.INACTIVE
                    || SubLevelStructureRefs.isSubLevel(access.reference()) && other.id().value().equals(access.reference().id())) continue;
            if (other.worldBounds().intersects(area)) return false;
        }
        for (var otherBackend : StructureBackendRouter.orientationBackends(host)) {
            var nearby = otherBackend.nearby(host, area, 128);
            if (!nearby.complete()) return false;
            for (var other : nearby.bodies()) {
                if (sameStructure(other.reference(), access.reference())) continue;
                for (var box : other.localBoxes()) {
                    var transformed = BlockStructureOrientedBox.transform(box, other.motion().pose()).bounds();
                    for (var cell : cells) if (transformed.intersects(new AABB(cell.target()).deflate(1e-5))) return false;
                }
            }
        }
        var placementImage = new HashMap<BlockPos, BlockState>();
        for (var cell : cells) placementImage.put(cell.target(), cell.placed());
        return RestorationPlacementView.canSurvive(host, placementImage);
    }

    private static StructureOperation.Result<Progress> finish(ServerLevel host, Registry registry, StructureTransactionJournal.Record record) throws IOException {
        var reference = reference(record);
        var target = target(host, reference).orElse(null);
        if (!matchesTargets(host, decodeCells(host, record))) return progress(record, Phase.RECOVERY_REQUIRED, StructureOperation.Status.RUNNING, "restoration_target_image_changed");
        if (target == null || !target.retireRestored(reference, record.cells().getFirst().payload().getCompound("source_evidence").orElseThrow())) {
            return progress(record, Phase.RECOVERY_REQUIRED, StructureOperation.Status.RUNNING, "restoration_source_cleanup_pending");
        }
        var metadata = LiveStructureProvenance.metadata(host.getServer(), reference);
        var creation = record.cells().getFirst().payload().getString("creation_operation").map(UUID::fromString).orElse(null);
        if (!LiveStructureProvenance.healthy(host.getServer()) || metadata.isPresent()
                && (!metadata.get().creationOperationId().equals(creation)
                || !LiveStructureProvenance.remove(host.getServer(), reference, creation))) {
            return progress(record, Phase.RECOVERY_REQUIRED, StructureOperation.Status.RUNNING, "restoration_metadata_cleanup_pending");
        }
        var published = record.withStage(StructureTransactionJournal.Stage.PUBLISHED);
        registry.journal.persist(published);
        registry.put(published);
        for (var cell : decodeCells(host, record)) {
            if (!loaded(host, cell.target())) continue;
            host.updateNeighborsAt(cell.target(), cell.placed().getBlock());
            if (!cell.placed().getFluidState().isEmpty()) host.scheduleTick(cell.target(), cell.placed().getFluidState().getType(), 1);
            StructureBackendRouter.onBlockChanged(host, cell.target());
        }
        trim(registry);
        return progress(published, Phase.RESTORED, StructureOperation.Status.COMPLETED, "restored");
    }

    private static StructureTransactionJournal.Record record(Access access, StructureProvenance.@org.jspecify.annotations.Nullable Metadata metadata, Plan plan, CompoundTag sourceEvidence) {
        var cells = new ArrayList<StructureTransactionJournal.Cell>();
        for (var cell : plan.cells()) {
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, access.host().registryAccess());
            output.store("source_state", BlockState.CODEC, cell.sourceData().state());
            output.store("state", BlockState.CODEC, cell.placed());
            var payload = output.buildResult();
            if (cell.sourceData().blockEntityData() != null) payload.put("source_block_entity", cell.sourceData().blockEntityData());
            if (cell.placedData().blockEntityData() != null) payload.put("block_entity", cell.placedData().blockEntityData());
            payload.putInt("local_x", cell.local().getX()); payload.putInt("local_y", cell.local().getY()); payload.putInt("local_z", cell.local().getZ());
            access.actor().ownerId().ifPresent(owner -> payload.putString("owner", owner.toString()));
            if (metadata != null) payload.putString("creation_operation", metadata.creationOperationId().toString());
            payload.putLong("generation", access.reference().generation());
            if (cells.isEmpty()) payload.put("source_evidence", sourceEvidence.copy());
            cells.add(new StructureTransactionJournal.Cell(cell.target(), payload));
        }
        return new StructureTransactionJournal.Record(UUID.randomUUID(), access.reference().backend(), access.reference().dimension(),
                cells.getFirst().position(), StructureTransactionJournal.Stage.TARGET_ALLOCATED, access.reference().id(), cells, null);
    }

    private static List<Cell> decodeCells(ServerLevel host, StructureTransactionJournal.Record record) {
        return record.cells().stream().map(entry -> {
            var payload = entry.payload();
            var input = TagValueInput.create(ProblemReporter.DISCARDING, host.registryAccess(), payload);
            return new Cell(new BlockPos(payload.getInt("local_x").orElseThrow(), payload.getInt("local_y").orElseThrow(), payload.getInt("local_z").orElseThrow()),
                    entry.position(), StructureCellPayload.normalize(new StructureBlockData(input.read("source_state", BlockState.CODEC).orElseThrow(),
                            payload.getCompound("source_block_entity").orElse(null))), StructureCellPayload.decode(host, payload));
        }).toList();
    }

    private static boolean rollback(ServerLevel host, List<Cell> cells) {
        // Missing cells may already have produced drops or moved elsewhere after a crash.
        // Only a complete exact image proves rollback ownership; ambiguity keeps the source isolated.
        return RestorationRollback.clear(cells,
                cell -> loaded(host, cell.target()) && StructureCellPayload.read(host, cell.target()).equals(cell.placedData()),
                cell -> { StructureCellPayload.write(host, cell.target(), new StructureBlockData(Blocks.AIR.defaultBlockState()), SubLevelCaptureService.TRANSACTION_FLAGS); return true; },
                () -> TargetedChunkDurability.saveAround(host, cells.stream().map(Cell::target).toList()));
    }

    private static boolean matchesTargets(ServerLevel host, List<Cell> cells) {
        return cells.stream().allMatch(cell -> loaded(host, cell.target()) && StructureCellPayload.read(host, cell.target()).equals(cell.placedData()));
    }

    private static boolean matchesSource(CaptureTarget backend, StructureRef reference, List<Cell> cells) {
        var source = backend.restorationCells(reference).orElse(null);
        if (source == null || source.size() != cells.size()) return false;
        var expected = new HashMap<BlockPos, StructureBlockData>();
        for (var cell : cells) expected.put(cell.local(), cell.sourceData());
        return source.stream().allMatch(cell -> cell.data().equals(expected.get(cell.localPosition())));
    }

    private static boolean loaded(ServerLevel host, BlockPos position) {
        return host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }

    private static boolean atRest(ServerLevel host, OrientationBackendAccess.Body body) {
        return body.linearVelocity().lengthSqr() < StructureRestTracker.MAX_LINEAR_SPEED * StructureRestTracker.MAX_LINEAR_SPEED
                && body.motion().angularVelocity().lengthSqr() < StructureRestTracker.MAX_ANGULAR_SPEED * StructureRestTracker.MAX_ANGULAR_SPEED
                && StructureRestSupport.grounded(host, body);
    }

    private static Optional<CaptureTarget> target(ServerLevel host, StructureRef reference) {
        return SubLevelStructureRefs.isSubLevel(reference) ? Optional.of(new SubLevelCaptureTarget(host.getServer()))
                : reference.backend().equals(StructureBackendRouter.SABLE_ID) ? StructureBackendRouter.captureTarget(host.getServer()) : Optional.empty();
    }

    private static StructureRef reference(StructureTransactionJournal.Record record) {
        return new StructureRef(record.targetStructure(), record.hostDimension(), record.backend(),
                record.cells().getFirst().payload().getLong("generation").orElseThrow());
    }

    private static boolean sameStructure(StructureRef first, StructureRef second) {
        return first.id().equals(second.id()) && first.dimension().equals(second.dimension()) && first.backend().equals(second.backend());
    }

    private static StructureOperation.Result<Progress> progress(StructureTransactionJournal.Record record, Phase phase, StructureOperation.Status status, String detail) {
        return result(status, detail, record.transactionId(), new Progress(record.transactionId(), reference(record), phase,
                record.cells().size(), phase == Phase.RESTORED ? record.cells().size() : 0));
    }

    private static <T> StructureOperation.Result<T> result(StructureOperation.Status status, String detail, UUID id, T value) {
        return new StructureOperation.Result<>(status, detail, Optional.ofNullable(id), Optional.ofNullable(value));
    }
    private static <T> StructureOperation.Result<T> failure(StructureOperation.Status status, String detail) { return result(status, detail, null, null); }

    private static Registry registry(MinecraftServer server) {
        if (SERVERS.containsKey(server)) return SERVERS.get(server);
        try {
            var journal = StructureTransactionJournal.atomicOnly(server.getWorldPath(LevelResource.ROOT).resolve("data/academy/marked_restoration"));
            var records = journal.records();
            if (!journal.healthy() || !journal.corruptFiles().isEmpty()) throw new IOException("Restoration journal unavailable or corrupt");
            var registry = new Registry(server, journal);
            for (var record : records) registry.put(record);
            SERVERS.put(server, registry);
            return registry;
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Marked restoration journal disabled until restart", failure);
            SERVERS.put(server, null);
            return null;
        }
    }

    private static void prune(ServerLevel host, Registry registry) {
        registry.previews.values().removeIf(value -> value.access().host() == host && host.getGameTime() - value.tick() > PREVIEW_TICKS);
        registry.holds.values().removeIf(value -> value.context().host() == host
                && (value.hold().expiresAtGameTime() <= host.getGameTime() || !controller(value.context(), value.hold().controllerId())));
    }

    private static void trim(Registry registry) throws IOException {
        var done = registry.records.values().stream().filter(value -> value.stage() == StructureTransactionJournal.Stage.PUBLISHED).toList();
        for (int index = 0; index < done.size() - MAX_RECEIPTS; index++) {
            registry.journal.delete(done.get(index).transactionId());
            registry.remove(done.get(index).transactionId());
        }
    }

    private static void requireThread(ServerLevel host) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Restoration requires the server thread");
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SERVERS.remove(event.getServer());
        OBSERVERS.remove(event.getServer());
    }

    private record Cell(BlockPos local, BlockPos target, StructureBlockData sourceData, StructureBlockData placedData) {
        BlockState placed() { return placedData.state(); }
    }
    private record Plan(OrientationBackendAccess.Body body, RestorationGrid.Alignment alignment, List<Cell> cells) {
        boolean same(Plan other) { return body.sameObservation(other.body) && alignment.equals(other.alignment) && cells.equals(other.cells); }
    }
    private record Prepared(Access access, Preview preview, Plan plan, long tick, boolean administrative) {}
    private record Held(StructureOperation.Context context, Hold hold) {}
    private record Key(Identifier backend, Identifier dimension, UUID id) {
        static Key of(StructureRef reference) { return new Key(reference.backend(), reference.dimension(), reference.id()); }
        static Key of(StructureTransactionJournal.Record record) { return new Key(record.backend(), record.hostDimension(), record.targetStructure()); }
    }
    private static final class Registry {
        final StructureTransactionJournal journal;
        final Map<UUID, StructureTransactionJournal.Record> records = new LinkedHashMap<>();
        final Map<UUID, Prepared> previews = new LinkedHashMap<>();
        final Map<UUID, Held> holds = new HashMap<>();
        final Set<Key> pending = new HashSet<>();
        final Set<Key> committed = new HashSet<>();
        final Deque<UUID> recoveryOrder = new ArrayDeque<>();
        final MinecraftServer server;
        Registry(MinecraftServer server, StructureTransactionJournal journal) { this.server = server; this.journal = journal; }
        void isolate(StructureTransactionJournal.Record record) {
            if (record.stage() == StructureTransactionJournal.Stage.PUBLISHED) { PayloadIsolation.release(server, record.transactionId()); return; }
            var host = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, record.hostDimension()));
            if (host == null) return;
            PayloadIsolation.lock(host, record.cells().stream().map(StructureTransactionJournal.Cell::position).toList(), record.transactionId());
            var backend = target(host, reference(record)).orElse(null);
            if (backend != null) backend.lockPayload(reference(record), decodeCells(host, record).stream().map(Cell::local).toList(), record.transactionId());
        }
        void put(StructureTransactionJournal.Record record) {
            isolate(record);
            var previous = records.put(record.transactionId(), record);
            var key = Key.of(record);
            if (record.stage() == StructureTransactionJournal.Stage.PUBLISHED) {
                pending.remove(key); committed.remove(key); recoveryOrder.remove(record.transactionId());
            } else {
                if (previous == null || previous.stage() == StructureTransactionJournal.Stage.PUBLISHED) recoveryOrder.addLast(record.transactionId());
                pending.add(key);
                if (record.stage() == StructureTransactionJournal.Stage.COMMIT_DECIDED) committed.add(key);
            }
        }
        void remove(UUID id) {
            PayloadIsolation.release(server, id);
            var record = records.remove(id);
            if (record != null) { pending.remove(Key.of(record)); committed.remove(Key.of(record)); recoveryOrder.remove(id); }
        }
    }
}
