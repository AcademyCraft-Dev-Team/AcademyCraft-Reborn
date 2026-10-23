package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBackendStatus;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructurePipeline;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.BlockStructureTransferState;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Server-thread staged capture coordinator. A capture reserves its target, persists a
 * write-ahead journal record before the first world write of each stage, verifies the copied
 * batch, persists the ownership decision, removes the source, confirms the target save and
 * only then recycles the journal. Restart recovery resolves every retained record before
 * unrelated interaction resumes: pre-decision records roll back, post-decision records
 * complete forward, and unknown outcomes stay isolated with retained evidence.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class StructureCaptureCoordinator {
    private static final int ACTIVATION_TIMEOUT_TICKS = 200;
    private static final int SAVE_TIMEOUT_TICKS = 600;
    private static final int MAX_FINISHED_RESULTS = 256;
    private static final int MAX_EXTRACTION_PREVIEWS = 64;
    private static final int HOST_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | Block.UPDATE_SUPPRESS_DROPS;
    private static final Map<MinecraftServer, StructureCaptureCoordinator> SERVERS = new IdentityHashMap<>();

    private final MinecraftServer server;
    private final StructureCaptureClaims claims = new StructureCaptureClaims();
    private final Map<UUID, PendingCapture> pending = new LinkedHashMap<>();
    private final Map<UUID, StructureCaptureResult> finished = new LinkedHashMap<>();
    private final Map<UUID, StructureCaptureEvidence> finishedEvidence = new LinkedHashMap<>();
    private final Map<StructureCaptureRequest, PreviewInterest> extractionPreviews = new IdentityHashMap<>();
    private @Nullable StructureTransactionJournal journal;
    private boolean journalAttempted;
    private boolean closed;

    private StructureCaptureCoordinator(MinecraftServer server) {
        this.server = server;
    }

    /** Starts a staged capture in a live non-storage host on its owning server thread. */
    public static StructureCaptureResult capture(ServerLevel host, StructureCaptureRequest request) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(request, "request");
        var server = host.getServer();
        if (!server.isSameThread()) {
            throw new IllegalStateException("Structure capture requires the server thread");
        }
        if (server.getLevel(host.dimension()) != host
                || NativeStructureDimensions.isStorageLevel(host.dimension())) {
            throw new IllegalArgumentException("Structure capture requires a live, non-storage host level");
        }
        return coordinator(server).begin(host, request);
    }

    /** Returns the current result of a transaction, or empty for an unknown evicted identity. */
    public static Optional<StructureCaptureResult> poll(MinecraftServer server, UUID transactionId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transactionId, "transactionId");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Structure capture requires the server thread");
        }
        StructureCaptureCoordinator coordinator;
        synchronized (SERVERS) { coordinator = SERVERS.get(server); }
        if (coordinator == null || coordinator.closed) return Optional.empty();
        return coordinator.currentResult(transactionId);
    }

    /** Same bounded lifetime as completed results; absent for pending, failed or recovered captures. */
    public static Optional<StructureCaptureEvidence> completedEvidence(MinecraftServer server, UUID transactionId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transactionId, "transactionId");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Structure capture requires the server thread");
        }
        StructureCaptureCoordinator coordinator;
        synchronized (SERVERS) { coordinator = SERVERS.get(server); }
        return coordinator == null || coordinator.closed ? Optional.empty()
                : Optional.ofNullable(coordinator.finishedEvidence.get(transactionId));
    }

    /**
     * Register a successfully stored extraction preview for optional completion evidence.
     * Only request identity is retained; this neither opens the journal nor loads/observes a body.
     * Ordinary captures without a preview never pay for an orientation geometry observation.
     */
    public static boolean watchExtractionPreview(ServerLevel host, StructureCaptureRequest request, long expiresAt) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(request, "request");
        if (!host.getServer().isSameThread()) {
            throw new IllegalStateException("Extraction preview registration requires the server thread");
        }
        var coordinator = coordinator(host.getServer());
        coordinator.requireRunning();
        if (host.getServer().getLevel(host.dimension()) != host || !request.physical()
                || expiresAt <= host.getGameTime() || expiresAt - host.getGameTime() > 1_200) return false;
        coordinator.expireExtractionPreviews();
        if (!coordinator.extractionPreviews.containsKey(request)
                && coordinator.extractionPreviews.size() >= MAX_EXTRACTION_PREVIEWS) return false;
        coordinator.extractionPreviews.put(request, new PreviewInterest(host.dimension(), expiresAt));
        return true;
    }

    private void expireExtractionPreviews() {
        if (extractionPreviews.isEmpty()) return;
        extractionPreviews.values().removeIf(preview -> {
            var host = server.getLevel(preview.dimension());
            return host == null || host.getGameTime() > preview.expiresAt();
        });
    }

    private static StructureCaptureCoordinator coordinator(MinecraftServer server) {
        synchronized (SERVERS) {
            var existing = SERVERS.get(server);
            if (existing != null) return existing;
            var created = new StructureCaptureCoordinator(server);
            SERVERS.put(server, created);
            return created;
        }
    }

    /**
     * Administration and test hook: resolves every retained journal record now, on the owning
     * server thread. Records whose source cells are already claimed by a live transaction are
     * left untouched.
     */
    public static void recoverNow(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Structure capture requires the server thread");
        }
        coordinator(server).recoverPending();
    }

    /**
     * Removes a structure a capture created but that no controller adopted. Fallback skill
     * rejections use this so a failed adoption never leaves an orphan; the selected capture
     * backend performs the removal and unavailable states return false without side effects.
     */
    public static boolean discard(MinecraftServer server, StructureRef reference) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(reference, "reference");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Structure capture requires the server thread");
        }
        var backend = targetFor(server, reference.backend());
        if (backend == null) return false;
        try {
            return backend.releaseEmpty(reference);
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Cannot discard the uncommitted structure {}", reference, failure);
            return false;
        }
    }

    private StructureCaptureResult begin(ServerLevel host, StructureCaptureRequest request) {
        long totalStarted = CaptureTimings.begin();
        requireRunning();
        var transactionId = UUID.randomUUID();
        var status = StructureBackendRouter.status(server);
        if (status.mode() != StructureBackendStatus.Mode.NATIVE
                && status.mode() != StructureBackendStatus.Mode.SABLE) {
            return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.BACKEND_UNAVAILABLE,
                    switch (status.mode()) {
                        case MIGRATION_REQUIRED -> "native_migration_required";
                        case INCOMPATIBLE -> "sable_incompatible_backend_disabled";
                        case NATIVE, SABLE -> "backend_unavailable";
                    });
        }
        var backend = StructureBackendRouter.captureTarget(host, request.physical()).orElse(null);
        if (backend == null) {
            return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.BACKEND_UNAVAILABLE,
                    status.mode() == StructureBackendStatus.Mode.NATIVE
                            ? "sublevel_runtime_unavailable" : "sable_capture_unavailable");
        }
        var journal = journal();
        if (journal == null || !journal.healthy()) {
            return StructureCaptureResult.failure(transactionId,
                    StructureCaptureResult.Status.JOURNAL_WRITE_FAILED, "journal_unavailable");
        }
        var ordered = new ArrayList<>(new TreeSet<>(request.positions()));
        if (ordered.isEmpty()) {
            return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.EMPTY, "empty_selection");
        }
        if (ordered.size() > request.maximumBlocks()) {
            return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.TOO_LARGE,
                    ordered.get(request.maximumBlocks()), "block_budget_exceeded");
        }
        var cellBudget = backend.cellBudget();
        if (ordered.size() > cellBudget) {
            return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.REJECTED,
                    ordered.get(cellBudget), "native_structure_cell_budget");
        }
        var origin = minimumCorner(ordered);
        for (var position : ordered) {
            if (position.getX() - origin.getX() >= request.maximumSpan()
                    || position.getY() - origin.getY() >= request.maximumSpan()
                    || position.getZ() - origin.getZ() >= request.maximumSpan()) {
                return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.SPAN_TOO_LARGE,
                        position, "selection_span_exceeded");
            }
        }
        var states = new ArrayList<BlockState>(ordered.size());
        long validateStarted = CaptureTimings.begin();
        for (var position : ordered) {
            if (!host.isInWorldBounds(position)) {
                return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.OUT_OF_WORLD,
                        position, "out_of_world");
            }
            if (!host.isLoaded(position)) {
                return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.UNLOADED,
                        position, "unloaded_source");
            }
            if (!request.permission().test(host, position)) {
                return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.PERMISSION_DENIED,
                        position, "permission_denied");
            }
            var state = host.getBlockState(position);
            var rejection = backend.rejectionDetail(host, position, state,
                    host.getBlockEntity(position));
            if (rejection != null) {
                return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.REJECTED,
                        position, rejection);
            }
            states.add(state);
        }
        CaptureTimings.record(transactionId.toString(), "capture_validate_cells", validateStarted);
        long claimStarted = CaptureTimings.begin();
        boolean claimed = claims.tryClaim(host.dimension().identifier(), ordered, transactionId);
        CaptureTimings.record(transactionId.toString(), "capture_claim_cells", claimStarted);
        if (!claimed) {
            var conflict = ordered.stream()
                    .filter(position -> claims.isClaimed(host.dimension().identifier(), position))
                    .findFirst().orElse(ordered.getFirst());
            return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.REJECTED,
                    conflict, "position_claimed");
        }
        try {
            var cells = new ArrayList<StructureTransactionJournal.Cell>(ordered.size());
            long encodeStarted = CaptureTimings.begin();
            for (int index = 0; index < ordered.size(); index++) {
                cells.add(new StructureTransactionJournal.Cell(ordered.get(index),
                        encodeState(host, states.get(index))));
            }
            CaptureTimings.record(transactionId.toString(), "capture_encode_cells", encodeStarted);
            var record = new StructureTransactionJournal.Record(transactionId, backend.backendId(),
                    host.dimension().identifier(), origin, StructureTransactionJournal.Stage.PREPARED, null,
                    cells, null);
            long journalStarted = CaptureTimings.begin();
            boolean journalWritten = persistNew(record);
            CaptureTimings.record(transactionId.toString(), "journal_prepare", journalStarted);
            if (!journalWritten) {
                claims.release(transactionId);
                return StructureCaptureResult.failure(transactionId,
                        StructureCaptureResult.Status.JOURNAL_WRITE_FAILED, "journal_prepare_failed");
            }
            // The first content cell in capture order seeds the allocation in the same call
            // chain so a backend that destroys zero-mass bodies never sees an empty target.
            var seedIndex = firstContentIndex(states);
            var seedOffset = ordered.get(seedIndex).subtract(origin);
            var seedState = states.get(seedIndex);
            long allocateStarted = CaptureTimings.begin();
            var allocated = backend.allocate(host, origin, seedOffset, seedState, ordered, transactionId);
            CaptureTimings.record(transactionId.toString(), "target_allocate", allocateStarted);
            if (allocated.isEmpty()) {
                recycleQuietly(transactionId);
                claims.release(transactionId);
                return StructureCaptureResult.failure(transactionId,
                        StructureCaptureResult.Status.BACKEND_UNAVAILABLE, backend.allocationFailureDetail());
            }
            var target = allocated.get();
            record = record.withTarget(target.id());
            long targetJournalStarted = CaptureTimings.begin();
            boolean targetJournalWritten = persistNew(record);
            CaptureTimings.record(transactionId.toString(), "journal_target", targetJournalStarted);
            if (!targetJournalWritten) {
                unloadQuietly(backend, target);
                recycleQuietly(transactionId);
                discardEmptyQuietly(backend, target);
                claims.release(transactionId);
                return StructureCaptureResult.failure(transactionId,
                        StructureCaptureResult.Status.JOURNAL_WRITE_FAILED, "journal_allocation_failed");
            }
            var seeded = seededAllocation(backend, target, seedOffset, seedState);
            var capture = new PendingCapture(record, host, origin, target, backend, ordered, cells, states,
                    request.permission(), false, seedIndex, seeded, request.physical());
            capture.submittedRequest = request;
            pending.put(transactionId, capture);
            long driveStarted = CaptureTimings.begin();
            try {
                drive(capture);
            } catch (RuntimeException | AssertionError | LinkageError failure) {
                safeFallback(capture, failure);
            }
            CaptureTimings.record(transactionId.toString(), "drive_total", driveStarted);
            CaptureTimings.record(transactionId.toString(), "capture_total", totalStarted);
            return currentResult(transactionId).orElseGet(() -> StructureCaptureResult.failure(transactionId,
                    StructureCaptureResult.Status.RECOVERY_REQUIRED, "transaction_vanished"));
        } catch (RuntimeException | AssertionError | LinkageError failure) {
            if (org.academy.internal.common.sublevel.SubLevelStructureRefs.BACKEND.equals(backend.backendId())) {
                var reservation = new StructureRef(transactionId, host.dimension().identifier(), backend.backendId(), 0);
                try {
                    var handle = backend.resolve(reservation, true).orElse(null);
                    if (handle != null && !backend.releaseEmpty(handle.reference())) {
                        throw new IllegalStateException("Uncertain SubLevel allocation remains reserved");
                    }
                } catch (RuntimeException | LinkageError cleanup) {
                    failure.addSuppressed(cleanup);
                    AcademyCraft.LOGGER.error("SubLevel allocation retained with its prepared journal: {}",
                            transactionId, failure);
                    return StructureCaptureResult.failure(transactionId,
                            StructureCaptureResult.Status.RECOVERY_REQUIRED, "allocation_cleanup_retained");
                }
            }
            recycleQuietly(transactionId);
            claims.release(transactionId);
            AcademyCraft.LOGGER.error("Structure capture could not start: {}", transactionId, failure);
            return StructureCaptureResult.failure(transactionId,
                    StructureCaptureResult.Status.BACKEND_UNAVAILABLE, "capture_start_failed");
        }
    }

    private Optional<StructureCaptureResult> currentResult(UUID transactionId) {
        var capture = pending.get(transactionId);
        if (capture != null) {
            return Optional.of(StructureCaptureResult.pending(transactionId, capture.target, capture.phase.detail()));
        }
        return Optional.ofNullable(finished.get(transactionId));
    }

    private void driveAll() {
        for (var capture : List.copyOf(pending.values())) {
            try {
                drive(capture);
            } catch (RuntimeException | AssertionError | LinkageError failure) {
                safeFallback(capture, failure);
            }
        }
    }

    private void drive(PendingCapture capture) {
        if (capture.recovery) loadRecoverySourceChunks(capture);
        for (int transitions = 0; transitions < 8 && capture.result == null; transitions++) {
            var phase = capture.phase;
            long stepStarted = CaptureTimings.begin();
            switch (phase) {
                case AWAIT_ACTIVATION -> stepAwaitActivation(capture);
                case COPY -> stepCopy(capture);
                case REMOVE_SOURCE -> stepRemoveSource(capture);
                case CLEAR_TARGET -> stepClearTarget(capture);
                case SAVE_TARGET -> stepSaveTarget(capture);
            }
            CaptureTimings.record(capture.transactionId.toString(),
                    "step_" + phase.name().toLowerCase(Locale.ROOT), stepStarted);
            if (capture.result == null && capture.phase == phase) return;
        }
        if (capture.result == null) {
            throw new IllegalStateException("Structure capture did not settle: " + capture.transactionId);
        }
    }

    /** Recovery may start far outside player/spawn tickets; missing residency is not a source edit. */
    private static void loadRecoverySourceChunks(PendingCapture capture) {
        var chunks = new java.util.LinkedHashSet<net.minecraft.world.level.ChunkPos>();
        for (var position : capture.positions) {
            if (!capture.host.isInWorldBounds(position)) {
                throw new IllegalStateException("Recovery source is outside world bounds at " + position);
            }
            chunks.add(new net.minecraft.world.level.ChunkPos(position.getX() >> 4, position.getZ() >> 4));
        }
        // Journal cell count is bounded. Re-read the finite set on each recovery drive instead
        // of keeping permanent tickets or colliding with the storage dimension's ticket owner.
        for (var chunk : chunks) capture.host.getChunk(chunk.x(), chunk.z());
    }

    private void stepAwaitActivation(PendingCapture capture) {
        if (resolveHandle(capture) == null) {
            retain(capture, "target_handle_unavailable", null);
            return;
        }
        if (!capture.backend.ready(capture.target)) {
            if (!sourceMatches(capture)) {
                abort(capture, StructureCaptureResult.Status.SOURCE_CHANGED, "source_changed_before_copy");
                return;
            }
            if (++capture.waited > ACTIVATION_TIMEOUT_TICKS) {
                abort(capture, StructureCaptureResult.Status.TIMEOUT, "target_activation_timeout");
            }
            return;
        }
        capture.phase = capture.recovery ? Phase.CLEAR_TARGET : Phase.COPY;
    }

    private void stepCopy(PendingCapture capture) {
        if (!copyPreconditions(capture)) return;
        for (int index = 0; index < capture.positions.size(); index++) {
            if (capture.seeded && index == capture.seedIndex) continue;
            var offset = capture.positions.get(index).subtract(capture.origin);
            var write = capture.backend.writeCell(capture.target, offset, capture.states.get(index));
            if (write == StructureWriteResult.RECOVERY_REQUIRED) {
                retain(capture, "target_backend_quarantined_during_copy", null);
                return;
            }
            if (write != StructureWriteResult.APPLIED) {
                abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED,
                        "target_write_" + write.name().toLowerCase(Locale.ROOT));
                return;
            }
            capture.copyProgress = Math.max(capture.copyProgress, index + 1);
        }
        finishCopy(capture);
    }

    /** Common source/permission/readiness guards; false means the phase changed or settled. */
    private boolean copyPreconditions(PendingCapture capture) {
        if (!sourceMatches(capture)) {
            abort(capture, StructureCaptureResult.Status.SOURCE_CHANGED, "source_changed_before_copy");
            return false;
        }
        if (!permissionHolds(capture)) {
            abort(capture, StructureCaptureResult.Status.PERMISSION_DENIED, "permission_revoked_before_commit");
            return false;
        }
        if (!capture.backend.ready(capture.target)) {
            capture.phase = Phase.AWAIT_ACTIVATION;
            return false;
        }
        return true;
    }

    /** Verification, finalization and the durable ownership decision after every cell was copied. */
    private void finishCopy(PendingCapture capture) {
        long verifyStarted = CaptureTimings.begin();
        for (int index = 0; index < capture.positions.size(); index++) {
            var offset = capture.positions.get(index).subtract(capture.origin);
            var current = capture.backend.readCell(capture.target, offset);
            if (current.isEmpty() || !current.get().equals(capture.states.get(index))) {
                abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "target_verify_mismatch");
                return;
            }
        }
        CaptureTimings.record(capture.transactionId.toString(), "copy_verify_cells", verifyStarted);
        long budgetStarted = CaptureTimings.begin();
        if (!geometryWithinBudget(capture)) {
            abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "target_geometry_budget");
            return;
        }
        CaptureTimings.record(capture.transactionId.toString(), "copy_geometry_budget", budgetStarted);
        long finalizeStarted = CaptureTimings.begin();
        if (!finalizeTarget(capture)) {
            abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "target_finalize_failed");
            return;
        }
        CaptureTimings.record(capture.transactionId.toString(), "copy_finalize_target", finalizeStarted);
        long physicsStarted = CaptureTimings.begin();
        if (capture.physical && !preparePhysics(capture)) {
            abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "target_physics_prepare_failed");
            return;
        }
        CaptureTimings.record(capture.transactionId.toString(), "copy_prepare_physics", physicsStarted);
        long copiedJournalStarted = CaptureTimings.begin();
        if (!persistStage(capture, StructureTransactionJournal.Stage.COPIED)) {
            abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "journal_copied_persist_failed");
            return;
        }
        CaptureTimings.record(capture.transactionId.toString(), "journal_copied", copiedJournalStarted);
        long decisionJournalStarted = CaptureTimings.begin();
        if (!persistStage(capture, StructureTransactionJournal.Stage.COMMIT_DECIDED)) {
            abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "journal_decision_persist_failed");
            return;
        }
        CaptureTimings.record(capture.transactionId.toString(), "journal_commit_decision", decisionJournalStarted);
        capture.phase = Phase.REMOVE_SOURCE;
    }

    /**
     * Bounded slice for the v2 pipeline. It never runs the full {@link #drive} loop: one call
     * advances at most one phase transition and at most the requested number of copy cells.
     * Journal stages remain the durability barrier; a mid-copy slice can be rolled back/recovered
     * by the existing journal path.
     */
    private StructurePipeline.CaptureSlice slice(PendingCapture capture, StructurePipeline.WorkLimit limit) {
        long started = System.nanoTime();
        long budget = limit.maximumNanos();
        var stageBefore = capture.record.stage();
        int processed = 0;
        if (capture.recovery) loadRecoverySourceChunks(capture);
        try {
            switch (capture.phase) {
                case AWAIT_ACTIVATION -> stepAwaitActivation(capture);
                case COPY -> processed = copySlice(capture, limit.maximumCells(), started, budget);
                case REMOVE_SOURCE -> stepRemoveSource(capture);
                case CLEAR_TARGET -> stepClearTarget(capture);
                case SAVE_TARGET -> stepSaveTarget(capture);
            }
        } catch (RuntimeException | AssertionError | LinkageError failure) {
            safeFallback(capture, failure);
        }
        boolean durable = capture.record.stage() != stageBefore;
        if (capture.result != null) {
            return new StructurePipeline.CaptureSlice(
                    capture.result.succeeded() ? StructurePipeline.CaptureStage.COMPLETE
                            : StructurePipeline.CaptureStage.FAILED,
                    capture.result.succeeded() ? capture.result.affectedBlocks() : processed,
                    durable, capture.result.status().name().toLowerCase(Locale.ROOT)
                    + (capture.result.detail().isEmpty() ? "" : ":" + capture.result.detail()));
        }
        var stage = switch (capture.phase) {
            case AWAIT_ACTIVATION -> StructurePipeline.CaptureStage.PREPARE;
            case COPY -> StructurePipeline.CaptureStage.STAGE;
            case REMOVE_SOURCE -> StructurePipeline.CaptureStage.COMMIT;
            case CLEAR_TARGET -> StructurePipeline.CaptureStage.FAILED;
            case SAVE_TARGET -> StructurePipeline.CaptureStage.DURABLE;
        };
        return new StructurePipeline.CaptureSlice(stage, processed, durable, capture.phase.detail());
    }

    /** Copies at most maximumCells cells from the slice cursor and stops at the time budget. */
    private int copySlice(PendingCapture capture, int maximumCells, long started, long maximumNanos) {
        if (!copyPreconditions(capture)) return 0;
        int copied = 0;
        int size = capture.positions.size();
        while (capture.sliceCursor < size && copied < maximumCells) {
            int index = capture.sliceCursor;
            if (!(capture.seeded && index == capture.seedIndex)) {
                var offset = capture.positions.get(index).subtract(capture.origin);
                var write = capture.backend.writeCell(capture.target, offset, capture.states.get(index));
                if (write == StructureWriteResult.RECOVERY_REQUIRED) {
                    retain(capture, "target_backend_quarantined_during_copy", null);
                    return copied;
                }
                if (write != StructureWriteResult.APPLIED) {
                    abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED,
                            "target_write_" + write.name().toLowerCase(Locale.ROOT));
                    return copied;
                }
                capture.copyProgress = Math.max(capture.copyProgress, index + 1);
            }
            capture.sliceCursor++;
            copied++;
            if ((copied & 15) == 0 && System.nanoTime() - started > maximumNanos) break;
        }
        if (capture.sliceCursor < size) return copied;
        finishCopy(capture);
        return copied;
    }

    /**
     * Bounded capture advance for the v2 pipeline. Server-thread only; no live gameplay path
     * calls it while the pipeline is disabled.
     */
    public static StructurePipeline.CaptureSlice advance(ServerLevel host, UUID transactionId,
                                                         StructurePipeline.WorkLimit limit) {
        if (host == null || transactionId == null || limit == null) {
            return new StructurePipeline.CaptureSlice(StructurePipeline.CaptureStage.FAILED, 0, false,
                    "invalid_request");
        }
        var server = host.getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Structure capture slice requires the server thread");
        }
        if (limit.maximumNanos() <= 0 || limit.maximumCells() <= 0 || limit.maximumPublications() < 0) {
            return new StructurePipeline.CaptureSlice(StructurePipeline.CaptureStage.FAILED, 0, false,
                    "invalid_limit");
        }
        StructureCaptureCoordinator coordinator;
        synchronized (SERVERS) { coordinator = SERVERS.get(server); }
        if (coordinator == null || coordinator.closed) {
            return new StructurePipeline.CaptureSlice(StructurePipeline.CaptureStage.FAILED, 0, false,
                    "coordinator_unavailable");
        }
        var capture = coordinator.pending.get(transactionId);
        if (capture == null) {
            var result = coordinator.finished.get(transactionId);
            if (result == null) {
                return new StructurePipeline.CaptureSlice(StructurePipeline.CaptureStage.FAILED, 0, false,
                        "unknown_transaction");
            }
            return new StructurePipeline.CaptureSlice(result.succeeded()
                    ? StructurePipeline.CaptureStage.COMPLETE : StructurePipeline.CaptureStage.FAILED,
                    result.affectedBlocks(), true, result.status().name().toLowerCase(Locale.ROOT));
        }
        if (capture.host != host) {
            return new StructurePipeline.CaptureSlice(StructurePipeline.CaptureStage.FAILED, 0, false,
                    "host_mismatch");
        }
        return coordinator.slice(capture, limit);
    }

    private void stepRemoveSource(PendingCapture capture) {
        if (capture.recovery) {
            // A durable ownership decision is not proof that the target's persisted copy is
            // still intact. Never remove a surviving source after a failed/corrupt restore.
            if (resolveHandle(capture) == null || !capture.backend.ready(capture.target)) {
                retain(capture, "recovery_target_unavailable_before_source_removal", null);
                return;
            }
            for (int index = 0; index < capture.positions.size(); index++) {
                var offset = capture.positions.get(index).subtract(capture.origin);
                var copied = capture.backend.readCell(capture.target, offset);
                if (copied.isEmpty() || !copied.get().equals(capture.states.get(index))) {
                    retain(capture, "recovery_target_mismatch_before_source_removal", null);
                    return;
                }
            }
            if (org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(capture.target)
                    && !finalizeTarget(capture)) {
                retain(capture, "recovery_target_physics_unavailable", null);
                return;
            }
        }
        var transfer = new BlockStructureTransferState(false);
        try {
            for (int index = 0; index < capture.positions.size(); index++) {
                var position = capture.positions.get(index);
                var state = capture.states.get(index);
                if (!capture.host.isLoaded(position)) {
                    throw new IllegalStateException("Unloaded source cell during removal at " + position);
                }
                var current = capture.host.getBlockState(position);
                if (capture.recovery && current.isAir()) continue;
                if (!current.equals(state) || capture.host.getBlockEntity(position) != null) {
                    if (capture.recovery) {
                        retain(capture, "recovery_found_unknown_source_content", null);
                        return;
                    }
                    throw new IllegalStateException("Source changed during removal at " + position);
                }
                transfer.writeCell(capture.host, position, Blocks.AIR.defaultBlockState(), null,
                        HOST_UPDATE_FLAGS);
            }
            if (!transfer.matchesAfterImages(capture.host)) {
                throw new IllegalStateException("Source batch changed during removal");
            }
            org.academy.internal.common.structure.StructureHostBlockUpdates.publish(
                    capture.host, capture.positions);
        } catch (RuntimeException failure) {
            boolean restored = transfer.rollback(capture.host, HOST_UPDATE_FLAGS);
            // Even an incomplete rollback must publish the actual surviving world state.
            org.academy.internal.common.structure.StructureHostBlockUpdates.publish(
                    capture.host, capture.positions);
            if (restored) {
                abort(capture, StructureCaptureResult.Status.SOURCE_CHANGED, "source_change_rolled_back");
            } else {
                retain(capture, "source_rollback_blocked", failure);
            }
            return;
        }
        capture.phase = Phase.SAVE_TARGET;
    }

    private void stepClearTarget(PendingCapture capture) {
        // A prepared body owns an attachment; detach and close it before cell-level rollback or
        // the empty-slot release would be refused and the failed capture would stay retained.
        releasePhysics(capture);
        if (capture.recovery && !sourceMatches(capture)) {
            retain(capture, "source_changed_before_recovery_rollback", null);
            return;
        }
        // A whole-target release runs before any per-cell clearing: Sable removes the whole ship
        // because its own zero-mass sweep would otherwise invalidate the target mid-clear.
        var releasedWholeTarget = capture.backend.releaseEmpty(capture.target);
        if (!releasedWholeTarget) {
            if (capture.copyProgress > 0) {
                if (resolveHandle(capture) == null || !capture.backend.ready(capture.target)) {
                    if (++capture.waited > ACTIVATION_TIMEOUT_TICKS) {
                        retain(capture, "rollback_target_unavailable", null);
                    }
                    return;
                }
                for (int index = 0; index < capture.copyProgress; index++) {
                    var offset = capture.positions.get(index).subtract(capture.origin);
                    var expected = capture.states.get(index);
                    var current = capture.backend.readCell(capture.target, offset);
                    if (current.isPresent() && current.get().isAir()) continue;
                    if (current.isEmpty() || !current.get().equals(expected)) {
                        retain(capture, "rollback_found_unknown_target_content", null);
                        return;
                    }
                    var cleared = capture.backend.clearCell(capture.target, offset, expected);
                    if (cleared != StructureWriteResult.APPLIED) {
                        retain(capture, "rollback_target_clear_failed:" + cleared.name().toLowerCase(Locale.ROOT), null);
                        return;
                    }
                }
                for (int index = 0; index < capture.copyProgress; index++) {
                    var offset = capture.positions.get(index).subtract(capture.origin);
                    var current = capture.backend.readCell(capture.target, offset);
                    if (current.isEmpty() || !current.get().isAir()) {
                        retain(capture, "rollback_target_clear_verify_failed", null);
                        return;
                    }
                }
            }
            unloadQuietly(capture.backend, capture.target);
        }
        if (!recycleQuietly(capture.transactionId)) {
            retain(capture, "rollback_journal_recycle_failed", null);
            return;
        }
        if (!releasedWholeTarget) discardEmptyQuietly(capture.backend, capture.target);
        if (capture.recovery) {
            finishRecovery(capture);
            return;
        }
        finish(capture, StructureCaptureResult.failure(capture.transactionId,
                capture.failureStatus == null ? StructureCaptureResult.Status.TARGET_WRITE_FAILED
                        : capture.failureStatus,
                capture.failureDetail == null ? "rolled_back" : capture.failureDetail));
    }

    private void stepSaveTarget(PendingCapture capture) {
        if (!capture.backend.save(capture.target, capture.positions)) {
            if (org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(capture.target)) {
                // Plot/host storage errors are sticky for this session. A live copied body must
                // not be edited while its source removal is still uncertain on disk.
                retain(capture, "sublevel_save_failed", null);
                return;
            }
            if (++capture.saveWaited > SAVE_TIMEOUT_TICKS) {
                retain(capture, "target_save_timeout", null);
            }
            return;
        }
        if (!persistStage(capture, StructureTransactionJournal.Stage.PUBLISHED)) {
            retain(capture, "publish_journal_persist_failed", null);
            return;
        }
        notifyBoundaries(capture.host, capture.positions);
        if (!recycleQuietly(capture.transactionId)) {
            retain(capture, "journal_recycle_failed", null);
            return;
        }
        if (capture.recovery) {
            finishRecovery(capture);
            return;
        }
        finish(capture, StructureCaptureResult.success(capture.transactionId, capture.target,
                capture.positions.size()));
    }

    private void abort(PendingCapture capture, StructureCaptureResult.Status status, String detail) {
        capture.failureStatus = status;
        capture.failureDetail = detail;
        capture.phase = Phase.CLEAR_TARGET;
    }

    private void safeFallback(PendingCapture capture, Throwable failure) {
        try {
            if (capture.record.stage().compareTo(StructureTransactionJournal.Stage.COMMIT_DECIDED) >= 0) {
                retain(capture, "unexpected_failure_after_decision", failure);
                return;
            }
            capture.failureStatus = StructureCaptureResult.Status.TARGET_WRITE_FAILED;
            capture.failureDetail = "unexpected_failure_rolled_back";
            capture.phase = Phase.CLEAR_TARGET;
            drive(capture);
            if (capture.result == null) {
                retain(capture, "unexpected_failure_rollback_incomplete", failure);
            }
        } catch (RuntimeException | AssertionError | LinkageError secondary) {
            retain(capture, "unexpected_failure_retained", secondary);
        }
    }

    private void finish(PendingCapture capture, StructureCaptureResult result) {
        capture.result = result;
        pending.remove(capture.transactionId);
        claims.release(capture.transactionId);
        if (capture.reportResults) {
            expireExtractionPreviews();
            var interest = capture.submittedRequest == null ? null : extractionPreviews.get(capture.submittedRequest);
            if (result.succeeded() && capture.physical && capture.submittedRequest != null && interest != null
                    && interest.dimension().equals(capture.host.dimension())
                    && capture.record.stage() == StructureTransactionJournal.Stage.PUBLISHED) {
                extractionPreviews.remove(capture.submittedRequest);
                try {
                    var body = StructureBackendRouter.orientationAccess(capture.host, capture.target)
                            .flatMap(access -> access.observe(capture.host, capture.target)).orElse(null);
                    if (body != null) {
                        var source = new ArrayList<StructureCaptureEvidence.Cell>(capture.positions.size());
                        for (int index = 0; index < capture.cells.size(); index++) {
                            source.add(new StructureCaptureEvidence.Cell(capture.cells.get(index).position(),
                                    capture.states.get(index).toString()));
                        }
                        finishedEvidence.put(capture.transactionId, new StructureCaptureEvidence(
                                capture.record.hostDimension(), capture.target, capture.submittedRequest, source,
                                body.runtimeId(), body.contentRevision()));
                    }
                } catch (RuntimeException | AssertionError | LinkageError unavailable) {
                    // Capture ownership is already committed. Missing optional planning evidence
                    // must refuse later binding, never turn a successful capture into a rollback.
                    AcademyCraft.LOGGER.debug("Capture {} has no extraction binding observation",
                            capture.transactionId, unavailable);
                }
            }
            remember(capture.transactionId, result);
        }
    }

    private void finishRecovery(PendingCapture capture) {
        capture.result = StructureCaptureResult.success(capture.transactionId, capture.target, 0);
        pending.remove(capture.transactionId);
        claims.release(capture.transactionId);
        AcademyCraft.LOGGER.info("Structure capture transaction {} recovered and resolved", capture.transactionId);
    }

    private void retain(PendingCapture capture, String detail, @Nullable Throwable cause) {
        if (org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(capture.target)) {
            releasePhysics(capture);
        }
        var evidence = new CompoundTag();
        evidence.putString("reason", detail);
        evidence.putString("stage", capture.record.stage().name());
        evidence.putString("target_structure", capture.target.id().toString());
        if (cause != null) {
            evidence.putString("exception", cause.getClass().getName());
            evidence.putString("message", String.valueOf(cause.getMessage()));
        }
        try {
            var journal = journal();
            if (journal == null || !journal.healthy()) {
                AcademyCraft.LOGGER.error("Structure transaction journal unavailable while retaining {}",
                        capture.transactionId);
            } else {
                capture.record = capture.record.withEvidence(evidence);
                journal.persist(capture.record);
            }
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Cannot persist structure transaction evidence for {}",
                    capture.transactionId, failure);
        }
        capture.result = StructureCaptureResult.failure(capture.transactionId,
                StructureCaptureResult.Status.RECOVERY_REQUIRED, detail);
        pending.remove(capture.transactionId);
        // Claims stay held: the cells may hold unverified content until recovery resolves them.
        if (capture.reportResults) remember(capture.transactionId, capture.result);
        AcademyCraft.LOGGER.error("Structure capture transaction {} retained for recovery: {}",
                capture.transactionId, detail, cause);
    }

    private void remember(UUID transactionId, StructureCaptureResult result) {
        finished.put(transactionId, result);
        while (finished.size() > MAX_FINISHED_RESULTS) {
            var iterator = finished.keySet().iterator();
            var evicted = iterator.next();
            iterator.remove();
            finishedEvidence.remove(evicted);
        }
    }

    private boolean persistNew(StructureTransactionJournal.Record record) {
        try {
            var journal = journal();
            if (journal == null || !journal.healthy()) return false;
            journal.persist(record);
            return true;
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Structure transaction journal write failed: {}", record.transactionId(), failure);
            return false;
        }
    }

    private boolean persistStage(PendingCapture capture, StructureTransactionJournal.Stage stage) {
        try {
            var journal = journal();
            if (journal == null || !journal.healthy()) return false;
            capture.record = capture.record.withStage(stage);
            journal.persist(capture.record);
            return true;
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Structure transaction journal stage {} failed: {}", stage,
                    capture.transactionId, failure);
            return false;
        }
    }

    private boolean recycleQuietly(UUID transactionId) {
        try {
            var journal = journal();
            if (journal == null || !journal.healthy()) return false;
            journal.delete(transactionId);
            return true;
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Structure transaction journal recycle failed: {}", transactionId, failure);
            return false;
        }
    }

    private void unloadQuietly(CaptureTarget backend, StructureRef reference) {
        try {
            backend.deactivate(reference);
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.error("Structure transaction could not deactivate its empty target: {}",
                    reference, failure);
        }
    }

    /** Capacity hygiene after a confirmed rollback: a failed capture must not burn a permanent slot. */
    private void discardEmptyQuietly(CaptureTarget backend, StructureRef reference) {
        try {
            if (!backend.releaseEmpty(reference)) {
                AcademyCraft.LOGGER.warn("Empty capture target slot was not released; capacity remains consumed: {}",
                        reference);
            }
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.error("Structure transaction could not release its empty target slot: {}",
                    reference, failure);
        }
    }

    private @Nullable StructureHandle resolveHandle(PendingCapture capture) {
        return capture.backend.resolve(capture.target, capture.recovery).orElse(null);
    }

    private boolean geometryWithinBudget(PendingCapture capture) {
        try {
            return capture.backend.verifyCopy(capture.target, capture.positions.size());
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.error("Structure capture target geometry was rejected: {}",
                    capture.transactionId, failure);
            return false;
        }
    }

    /** Backend-owned body finalization runs once the copied cells are verified and before commit. */
    private boolean finalizeTarget(PendingCapture capture) {
        try {
            return capture.backend.finalizeTarget(capture.target, capture.origin, capture.positions);
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Structure capture target finalization failed: {}",
                    capture.transactionId, failure);
            return false;
        }
    }

    /** Body creation still sees the source blocks; a failure therefore rolls back without ghosts. */
    private boolean preparePhysics(PendingCapture capture) {
        try {
            return capture.backend.preparePhysics(capture.host, capture.target, capture.origin, capture.positions);
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Structure capture could not prepare physics: {}",
                    capture.transactionId, failure);
            return false;
        }
    }

    private void releasePhysics(PendingCapture capture) {
        try {
            capture.backend.releasePhysics(capture.host, capture.target);
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Structure capture could not release prepared physics: {}",
                    capture.transactionId, failure);
        }
    }

    private boolean permissionHolds(PendingCapture capture) {
        if (capture.permission == null) return true;
        for (var position : capture.positions) {
            if (!capture.permission.test(capture.host, position)) return false;
        }
        return true;
    }

    private static boolean sourceMatches(PendingCapture capture) {
        for (int index = 0; index < capture.positions.size(); index++) {
            var position = capture.positions.get(index);
            if (!capture.host.isLoaded(position)) return false;
            if (!capture.host.getBlockState(position).equals(capture.states.get(index))) return false;
            if (capture.host.getBlockEntity(position) != null) return false;
        }
        return true;
    }

    private @Nullable StructureTransactionJournal journal() {
        if (journalAttempted) return journal;
        journalAttempted = true;
        try {
            journal = new StructureTransactionJournal(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/academy/" + StructureTransactionJournal.DIRECTORY_NAME));
        } catch (IOException failure) {
            AcademyCraft.LOGGER.error("Structure transaction journal unavailable; captures disabled", failure);
            journal = null;
        }
        return journal;
    }

    private void requireRunning() {
        if (!server.isSameThread()) throw new IllegalStateException("Structure capture requires the server thread");
        if (closed) throw new IllegalStateException("Structure capture belongs to a stopped server");
    }

    private void recoverPending() {
        requireRunning();
        var mode = StructureBackendRouter.status(server).mode();
        if (mode != StructureBackendStatus.Mode.NATIVE && mode != StructureBackendStatus.Mode.SABLE) return;
        var journal = journal();
        if (journal == null || !journal.healthy()) {
            AcademyCraft.LOGGER.error("Structure transaction journal unavailable; pending records cannot be recovered");
            return;
        }
        for (var file : journal.corruptFiles()) {
            AcademyCraft.LOGGER.error("Corrupt structure transaction record retained without guessing its direction: {}",
                    file);
        }
        for (var record : journal.records()) {
            try {
                registerRecovery(record);
            } catch (RuntimeException | AssertionError | LinkageError failure) {
                AcademyCraft.LOGGER.error("Structure transaction recovery registration failed: {}",
                        record.transactionId(), failure);
            }
        }
    }

    private void registerRecovery(StructureTransactionJournal.Record record) {
        var backend = targetFor(server, record.backend());
        if (backend == null) {
            AcademyCraft.LOGGER.error("Structure transaction record belongs to an unsupported backend: {}",
                    record.backend());
            return;
        }
        var host = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
        if (host == null) {
            AcademyCraft.LOGGER.error("Structure transaction {} host level unavailable; record retained",
                    record.transactionId());
            return;
        }
        List<BlockState> states;
        try {
            states = decodeStates(host, record);
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.error("Structure transaction {} payload unreadable; record retained",
                    record.transactionId(), failure);
            return;
        }
        var positions = record.cells().stream().map(StructureTransactionJournal.Cell::position).toList();
        if (!claims.tryClaim(record.hostDimension(), positions, record.transactionId())) {
            AcademyCraft.LOGGER.error("Structure transaction {} overlaps a live claim; record retained",
                    record.transactionId());
            return;
        }
        if (record.stage() == StructureTransactionJournal.Stage.PREPARED) {
            // Plot allocation uses the transaction identity, closing the crash window between
            // persisting an empty plot reservation and recording the allocated target UUID.
            if (org.academy.internal.common.sublevel.SubLevelStructureRefs.BACKEND.equals(record.backend())) {
                var reserved = new StructureRef(record.transactionId(), record.hostDimension(), record.backend(), 0);
                var handle = backend.resolve(reserved, true).orElse(null);
                if (handle != null && !backend.releaseEmpty(handle.reference())) {
                    AcademyCraft.LOGGER.error("Prepared SubLevel reservation could not be rolled back: {}",
                            record.transactionId());
                    return;
                }
            }
            recycleRecovered(record);
            return;
        }
        var target = new StructureRef(record.targetStructure(), record.hostDimension(), record.backend(), 0);
        if (org.academy.internal.common.sublevel.SubLevelStructureRefs.BACKEND.equals(record.backend())) {
            target = backend.resolve(target, true).map(StructureHandle::reference).orElse(target);
        }
        var capture = new PendingCapture(record, host, record.origin(), target, backend, positions,
                record.cells(), states, null, true, 0, false, false);
        capture.copyProgress = record.stage() == StructureTransactionJournal.Stage.TARGET_ALLOCATED
                || record.stage() == StructureTransactionJournal.Stage.COPIED ? positions.size() : 0;
        capture.phase = record.stage() == StructureTransactionJournal.Stage.COMMIT_DECIDED
                || record.stage() == StructureTransactionJournal.Stage.PUBLISHED
                ? Phase.REMOVE_SOURCE : Phase.AWAIT_ACTIVATION;
        pending.put(record.transactionId(), capture);
        AcademyCraft.LOGGER.info("Structure capture transaction {} registered for {} recovery at stage {}",
                record.transactionId(), record.stage() == StructureTransactionJournal.Stage.COMMIT_DECIDED
                        || record.stage() == StructureTransactionJournal.Stage.PUBLISHED ? "forward" : "rollback",
                record.stage());
    }

    private void recycleRecovered(StructureTransactionJournal.Record record) {
        if (!recycleQuietly(record.transactionId())) {
            AcademyCraft.LOGGER.error("Prepared structure transaction {} could not be recycled; record retained",
                    record.transactionId());
            return;
        }
        claims.release(record.transactionId());
        AcademyCraft.LOGGER.info("Prepared structure transaction {} rolled back before any copy", record.transactionId());
    }

    private static @Nullable CaptureTarget targetFor(MinecraftServer server, Identifier backendId) {
        if (NativeCaptureTarget.ID.equals(backendId)) return new NativeCaptureTarget(server);
        if (org.academy.internal.common.sublevel.SubLevelStructureRefs.BACKEND.equals(backendId)) {
            return org.academy.internal.common.sublevel.SubLevelServer.runtime(server).isPresent()
                    ? new SubLevelCaptureTarget(server) : null;
        }
        if (StructureBackendRouter.SABLE_ID.equals(backendId)) {
            return StructureBackendRouter.captureTarget(server).orElse(null);
        }
        return null;
    }

    /** Index of the first non-air cell in capture order; every admitted cell is non-air. */
    private static int firstContentIndex(List<BlockState> states) {
        for (int index = 0; index < states.size(); index++) {
            if (!states.get(index).isAir()) return index;
        }
        return 0;
    }

    /** Whether the backend wrote the seed cell during allocation, checked against the target. */
    private static boolean seededAllocation(CaptureTarget backend, StructureRef target, BlockPos seedOffset,
                                            BlockState seedState) {
        try {
            return backend.readCell(target, seedOffset).filter(seedState::equals).isPresent();
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("Structure capture could not verify the allocation seed for {}",
                    target, failure);
            return false;
        }
    }

    private static List<BlockState> decodeStates(ServerLevel host, StructureTransactionJournal.Record record) {
        var states = new ArrayList<BlockState>(record.cells().size());
        for (var cell : record.cells()) {
            var input = TagValueInput.create(ProblemReporter.DISCARDING, host.registryAccess(), cell.payload());
            states.add(input.read("state", BlockState.CODEC).orElseThrow(() ->
                    new IllegalStateException("Missing structure transaction state payload")));
        }
        return List.copyOf(states);
    }

    private static CompoundTag encodeState(ServerLevel host, BlockState state) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, host.registryAccess());
        output.store("state", BlockState.CODEC, state);
        return output.buildResult();
    }

    private static BlockPos minimumCorner(List<BlockPos> positions) {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (var position : positions) {
            x = Math.min(x, position.getX());
            y = Math.min(y, position.getY());
            z = Math.min(z, position.getZ());
        }
        return new BlockPos(x, y, z);
    }

    private static void notifyBoundaries(ServerLevel host, List<BlockPos> positions) {
        try {
            var updated = new HashSet<BlockPos>();
            for (var position : positions) {
                if (updated.add(position)) {
                    host.updateNeighborsAt(position, host.getBlockState(position).getBlock());
                }
                for (var direction : Direction.values()) {
                    var adjacent = position.relative(direction);
                    if (updated.add(adjacent) && host.isLoaded(adjacent)) {
                        host.updateNeighborsAt(adjacent, host.getBlockState(adjacent).getBlock());
                    }
                }
            }
        } catch (RuntimeException failure) {
            // Ownership has committed; a failing neighbor must not cause the payload to be duplicated.
            AcademyCraft.LOGGER.error("Structure capture boundary notification failed after commit", failure);
        }
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        var server = event.getServer();
        var mode = StructureBackendRouter.status(server).mode();
        if (mode != StructureBackendStatus.Mode.NATIVE && mode != StructureBackendStatus.Mode.SABLE) return;
        coordinator(server).recoverPending();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        StructureCaptureCoordinator coordinator;
        synchronized (SERVERS) { coordinator = SERVERS.get(event.getServer()); }
        if (coordinator == null || coordinator.closed) return;
        coordinator.expireExtractionPreviews();
        coordinator.driveAll();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        StructureCaptureCoordinator coordinator;
        synchronized (SERVERS) { coordinator = SERVERS.remove(event.getServer()); }
        if (coordinator == null) return;
        coordinator.closed = true;
        coordinator.pending.clear();
        coordinator.claims.clear();
        coordinator.finished.clear();
        coordinator.finishedEvidence.clear();
        coordinator.extractionPreviews.clear();
    }

    private enum Phase {
        AWAIT_ACTIVATION("awaiting_target_activation"),
        COPY("copying"),
        REMOVE_SOURCE("removing_source"),
        CLEAR_TARGET("clearing_target"),
        SAVE_TARGET("saving_target");

        private final String detail;

        Phase(String detail) {
            this.detail = detail;
        }

        private String detail() {
            return detail;
        }
    }

    private record PreviewInterest(ResourceKey<net.minecraft.world.level.Level> dimension, long expiresAt) {}

    private static final class PendingCapture {
        private final UUID transactionId;
        private final ServerLevel host;
        private final BlockPos origin;
        private final StructureRef target;
        private final CaptureTarget backend;
        private final List<BlockPos> positions;
        private final List<StructureTransactionJournal.Cell> cells;
        private final List<BlockState> states;
        private final @Nullable BiPredicate<ServerLevel, BlockPos> permission;
        private final boolean recovery;
        private final boolean reportResults;
        private final int seedIndex;
        private final boolean seeded;
        private final boolean physical;
        private @Nullable StructureCaptureRequest submittedRequest;
        private StructureTransactionJournal.Record record;
        private Phase phase = Phase.AWAIT_ACTIVATION;
        private int copyProgress;
        private int sliceCursor;
        private int waited;
        private int saveWaited;
        private StructureCaptureResult.@Nullable Status failureStatus;
        private @Nullable String failureDetail;
        private @Nullable StructureCaptureResult result;

        private PendingCapture(StructureTransactionJournal.Record record, ServerLevel host, BlockPos origin,
                               StructureRef target, CaptureTarget backend, List<BlockPos> positions,
                               List<StructureTransactionJournal.Cell> cells, List<BlockState> states,
                               @Nullable BiPredicate<ServerLevel, BlockPos> permission, boolean recovery,
                               int seedIndex, boolean seeded, boolean physical) {
            this.record = record;
            this.host = host;
            this.origin = origin;
            this.target = target;
            this.backend = backend;
            this.positions = positions;
            this.cells = cells;
            this.states = states;
            this.permission = permission;
            this.recovery = recovery;
            this.reportResults = !recovery;
            this.seedIndex = seedIndex;
            this.seeded = seeded;
            this.physical = physical;
            this.copyProgress = seeded ? seedIndex + 1 : 0;
            this.transactionId = record.transactionId();
        }
    }
}
