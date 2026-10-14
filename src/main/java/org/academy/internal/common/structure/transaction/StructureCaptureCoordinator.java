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
    private static final int HOST_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | Block.UPDATE_SUPPRESS_DROPS;
    private static final Map<MinecraftServer, StructureCaptureCoordinator> SERVERS = new IdentityHashMap<>();

    private final MinecraftServer server;
    private final StructureCaptureClaims claims = new StructureCaptureClaims();
    private final Map<UUID, PendingCapture> pending = new LinkedHashMap<>();
    private final Map<UUID, StructureCaptureResult> finished = new LinkedHashMap<>();
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
        var backend = status.mode() == StructureBackendStatus.Mode.NATIVE
                ? new NativeCaptureTarget(server)
                : StructureBackendRouter.captureTarget(server).orElse(null);
        if (backend == null) {
            return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.BACKEND_UNAVAILABLE,
                    "sable_capture_unavailable");
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
            var rejection = NativeCaptureWhitelist.rejectionDetail(host, position, state,
                    host.getBlockEntity(position));
            if (rejection != null) {
                return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.REJECTED,
                        position, rejection);
            }
            states.add(state);
        }
        if (!claims.tryClaim(host.dimension().identifier(), ordered, transactionId)) {
            var conflict = ordered.stream()
                    .filter(position -> claims.isClaimed(host.dimension().identifier(), position))
                    .findFirst().orElse(ordered.getFirst());
            return StructureCaptureResult.failure(transactionId, StructureCaptureResult.Status.REJECTED,
                    conflict, "position_claimed");
        }
        try {
            var cells = new ArrayList<StructureTransactionJournal.Cell>(ordered.size());
            for (int index = 0; index < ordered.size(); index++) {
                cells.add(new StructureTransactionJournal.Cell(ordered.get(index),
                        encodeState(host, states.get(index))));
            }
            var record = new StructureTransactionJournal.Record(transactionId, backend.backendId(),
                    host.dimension().identifier(), origin, StructureTransactionJournal.Stage.PREPARED, null,
                    cells, null);
            if (!persistNew(record)) {
                claims.release(transactionId);
                return StructureCaptureResult.failure(transactionId,
                        StructureCaptureResult.Status.JOURNAL_WRITE_FAILED, "journal_prepare_failed");
            }
            // The first content cell in capture order seeds the allocation in the same call
            // chain so a backend that destroys zero-mass bodies never sees an empty target.
            var seedIndex = firstContentIndex(states);
            var seedOffset = ordered.get(seedIndex).subtract(origin);
            var seedState = states.get(seedIndex);
            var allocated = backend.allocate(host, origin, seedOffset, seedState);
            if (allocated.isEmpty()) {
                recycleQuietly(transactionId);
                claims.release(transactionId);
                return StructureCaptureResult.failure(transactionId,
                        StructureCaptureResult.Status.BACKEND_UNAVAILABLE, "no_free_native_slot");
            }
            var target = allocated.get();
            record = record.withTarget(target.id());
            if (!persistNew(record)) {
                unloadQuietly(backend, target);
                recycleQuietly(transactionId);
                discardEmptyQuietly(backend, target);
                claims.release(transactionId);
                return StructureCaptureResult.failure(transactionId,
                        StructureCaptureResult.Status.JOURNAL_WRITE_FAILED, "journal_allocation_failed");
            }
            var seeded = seededAllocation(backend, target, seedOffset, seedState);
            var capture = new PendingCapture(record, host, origin, target, backend, ordered, cells, states,
                    request.permission(), false, seedIndex, seeded);
            pending.put(transactionId, capture);
            try {
                drive(capture);
            } catch (RuntimeException | AssertionError | LinkageError failure) {
                safeFallback(capture, failure);
            }
            return currentResult(transactionId).orElseGet(() -> StructureCaptureResult.failure(transactionId,
                    StructureCaptureResult.Status.RECOVERY_REQUIRED, "transaction_vanished"));
        } catch (RuntimeException | AssertionError | LinkageError failure) {
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
        for (int transitions = 0; transitions < 8 && capture.result == null; transitions++) {
            var phase = capture.phase;
            switch (phase) {
                case AWAIT_ACTIVATION -> stepAwaitActivation(capture);
                case COPY -> stepCopy(capture);
                case REMOVE_SOURCE -> stepRemoveSource(capture);
                case CLEAR_TARGET -> stepClearTarget(capture);
                case SAVE_TARGET -> stepSaveTarget(capture);
            }
            if (capture.result == null && capture.phase == phase) return;
        }
        if (capture.result == null) {
            throw new IllegalStateException("Structure capture did not settle: " + capture.transactionId);
        }
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
        if (!sourceMatches(capture)) {
            abort(capture, StructureCaptureResult.Status.SOURCE_CHANGED, "source_changed_before_copy");
            return;
        }
        if (!permissionHolds(capture)) {
            abort(capture, StructureCaptureResult.Status.PERMISSION_DENIED, "permission_revoked_before_commit");
            return;
        }
        if (!capture.backend.ready(capture.target)) {
            capture.phase = Phase.AWAIT_ACTIVATION;
            return;
        }
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
        for (int index = 0; index < capture.positions.size(); index++) {
            var offset = capture.positions.get(index).subtract(capture.origin);
            var current = capture.backend.readCell(capture.target, offset);
            if (current.isEmpty() || !current.get().equals(capture.states.get(index))) {
                abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "target_verify_mismatch");
                return;
            }
        }
        if (!geometryWithinBudget(capture)) {
            abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "target_geometry_budget");
            return;
        }
        if (!persistStage(capture, StructureTransactionJournal.Stage.COPIED)) {
            abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "journal_copied_persist_failed");
            return;
        }
        if (!persistStage(capture, StructureTransactionJournal.Stage.COMMIT_DECIDED)) {
            abort(capture, StructureCaptureResult.Status.TARGET_WRITE_FAILED, "journal_decision_persist_failed");
            return;
        }
        capture.phase = Phase.REMOVE_SOURCE;
    }

    private void stepRemoveSource(PendingCapture capture) {
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
        } catch (RuntimeException failure) {
            if (transfer.rollback(capture.host, HOST_UPDATE_FLAGS)) {
                abort(capture, StructureCaptureResult.Status.SOURCE_CHANGED, "source_change_rolled_back");
            } else {
                retain(capture, "source_rollback_blocked", failure);
            }
            return;
        }
        capture.phase = Phase.SAVE_TARGET;
    }

    private void stepClearTarget(PendingCapture capture) {
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
        if (!capture.backend.save(capture.target)) {
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
        if (capture.reportResults) remember(capture.transactionId, result);
    }

    private void finishRecovery(PendingCapture capture) {
        capture.result = StructureCaptureResult.success(capture.transactionId, capture.target, 0);
        pending.remove(capture.transactionId);
        claims.release(capture.transactionId);
        AcademyCraft.LOGGER.info("Structure capture transaction {} recovered and resolved", capture.transactionId);
    }

    private void retain(PendingCapture capture, String detail, @Nullable Throwable cause) {
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
            iterator.next();
            iterator.remove();
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
            recycleRecovered(record);
            return;
        }
        var target = new StructureRef(record.targetStructure(), record.hostDimension(), record.backend(), 0);
        var capture = new PendingCapture(record, host, record.origin(), target, backend, positions,
                record.cells(), states, null, true, 0, false);
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
        private StructureTransactionJournal.Record record;
        private Phase phase = Phase.AWAIT_ACTIVATION;
        private int copyProgress;
        private int waited;
        private int saveWaited;
        private StructureCaptureResult.@Nullable Status failureStatus;
        private @Nullable String failureDetail;
        private @Nullable StructureCaptureResult result;

        private PendingCapture(StructureTransactionJournal.Record record, ServerLevel host, BlockPos origin,
                               StructureRef target, CaptureTarget backend, List<BlockPos> positions,
                               List<StructureTransactionJournal.Cell> cells, List<BlockState> states,
                               @Nullable BiPredicate<ServerLevel, BlockPos> permission, boolean recovery,
                               int seedIndex, boolean seeded) {
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
            this.copyProgress = seeded ? seedIndex + 1 : 0;
            this.transactionId = record.transactionId();
        }
    }
}
