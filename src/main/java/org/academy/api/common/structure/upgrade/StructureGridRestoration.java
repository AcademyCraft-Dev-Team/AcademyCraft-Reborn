package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Planned transfer from a physical structure to already-loaded, empty host cells. Providers must
 * preserve unique ownership with a durable journal; this API never implies a drop fallback.
 */
public interface StructureGridRestoration {
    int MAX_PREVIEW_CELLS = 4_096;
    int MAX_HOLD_TICKS = 1_200;

    /** Read-only: no tickets, loading, pose changes, content writes, or permission bypass. */
    StructureOperation.Result<Preview> preview(Access access, Options options);

    /**
     * Queues the exact provider-issued preview; rechecks all revisions, pose, coverage and permissions.
     * A caller-created or expired preview id must not authorize a transfer. RESTORED is reported only
     * after durable target ownership and source cleanup; acceptance/READY/RUNNING is not completion.
     */
    StructureOperation.Result<Progress> request(Access access, Request request);

    /**
     * Persistent lookup does not require a previous activation's runtime id. The provider must
     * authorize the actor against persisted operation ownership before exposing any progress;
     * an operation id is not a credential. Offline policy decisions must fail closed if unknown.
     */
    StructureOperation.Result<Progress> status(ServerLevel host, RestorationActor actor, UUID operationId);

    /** Only pre-commit cancellation is allowed; partial writes require verified rollback first. */
    StructureOperation.Result<Progress> cancel(Access access, UUID operationId);

    /** An authorized active controller can defer candidacy for a bounded time, never change ownership. */
    StructureOperation.Result<Hold> hold(StructureOperation.Context context, HoldRequest request);

    /** Renew only the same controller's live hold; recheck authority and the duration cap. */
    StructureOperation.Result<Hold> renewHold(StructureOperation.Context context, UUID holdId,
                                              HoldRequest request);

    /** Release only the caller's hold; a successful release does not itself restore the structure. */
    StructureOperation.Result<Void> releaseHold(StructureOperation.Context context, UUID holdId);

    enum Alignment {
        UPRIGHT_NEAREST_YAW
    }

    enum Cause {
        MARKED_REST,
        EXPLICIT_REQUEST,
        ADMINISTRATIVE_REQUEST,
        CHUNK_UNLOAD
    }

    enum Phase {
        REST_CANDIDATE,
        PLANNING,
        RESTORING,
        DEFERRED,
        RESTORED,
        CANCELLED,
        RECOVERY_REQUIRED
    }

    enum HoldReason {
        EXTRACTION,
        HOVER,
        PRECISION_CONTROL,
        TRACTION,
        JOINT,
        MENU,
        CONTENT_TRANSACTION,
        IMPACT_TRANSACTION
    }

    /**
     * Declared durable attribution for offline restoration, not an authorization token. Providers
     * verify the policy from server configuration and check persisted ownership and protection.
     * Missing owners never imply unrestricted maintenance permission; unsupported policy denies.
     */
    record RestorationActor(Optional<UUID> ownerId, Identifier policyId) {
        public RestorationActor {
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(policyId, "policyId");
        }
    }

    /** Allows offline ownership checks without fabricating a live player/entity or stale runtime id. */
    record Access(ServerLevel host, StructureRef reference, RestorationActor actor) {
        public Access {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(actor, "actor");
        }
    }

    /** All initial policies are AIR_ONLY/LOADED_ONLY; callers cannot opt out of either constraint. */
    record Options(Alignment alignment) {
        public Options {
            Objects.requireNonNull(alignment, "alignment");
        }
    }

    /** Cell mapping only; the provider privately owns the journal and immutable content images. */
    record Placement(BlockPos localPosition, BlockPos targetPosition) {
        public Placement {
            localPosition = Objects.requireNonNull(localPosition, "localPosition").immutable();
            targetPosition = Objects.requireNonNull(targetPosition, "targetPosition").immutable();
        }
    }

    record Preview(UUID previewId, StructureRef reference, UUID runtimeId, long contentRevision,
                   long terrainRevision, StructurePose sourcePose, StructurePose alignedPose,
                   List<Placement> placements) {
        public Preview {
            Objects.requireNonNull(previewId, "previewId");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(runtimeId, "runtimeId");
            Objects.requireNonNull(sourcePose, "sourcePose");
            Objects.requireNonNull(alignedPose, "alignedPose");
            if (contentRevision < 0 || terrainRevision < 0) {
                throw new IllegalArgumentException("preview revisions cannot be negative");
            }
            Objects.requireNonNull(placements, "placements");
            if (placements.isEmpty() || placements.size() > MAX_PREVIEW_CELLS) {
                throw new IllegalArgumentException("preview cell count is outside the bounded range");
            }
            placements = List.copyOf(placements);
            var sources = new HashSet<BlockPos>();
            var targets = new HashSet<BlockPos>();
            for (var placement : placements) {
                if (!sources.add(placement.localPosition()) || !targets.add(placement.targetPosition())) {
                    throw new IllegalArgumentException("preview cell mapping must be one-to-one");
                }
            }
        }
    }

    record Request(UUID previewId, Cause cause) {
        public Request {
            Objects.requireNonNull(previewId, "previewId");
            Objects.requireNonNull(cause, "cause");
        }
    }

    record Progress(UUID operationId, StructureRef reference, Phase phase, int totalCells,
                    int completedCells) {
        public Progress {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(phase, "phase");
            if (totalCells < 1 || totalCells > MAX_PREVIEW_CELLS
                    || completedCells < 0 || completedCells > totalCells) {
                throw new IllegalArgumentException("invalid restoration progress counts");
            }
            if (phase == Phase.RESTORED && completedCells != totalCells) {
                throw new IllegalArgumentException("restored operations must own all completed cells");
            }
        }
    }

    record HoldRequest(UUID controllerId, HoldReason reason, int leaseTicks) {
        public HoldRequest {
            Objects.requireNonNull(controllerId, "controllerId");
            Objects.requireNonNull(reason, "reason");
            if (leaseTicks < 1 || leaseTicks > MAX_HOLD_TICKS) {
                throw new IllegalArgumentException("hold duration must be positive and bounded");
            }
        }
    }

    record Hold(UUID holdId, StructureRef reference, UUID controllerId, HoldReason reason,
                long expiresAtGameTime) {
        public Hold {
            Objects.requireNonNull(holdId, "holdId");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(controllerId, "controllerId");
            Objects.requireNonNull(reason, "reason");
            if (expiresAtGameTime < 0) throw new IllegalArgumentException("hold expiry cannot be negative");
        }
    }
}
