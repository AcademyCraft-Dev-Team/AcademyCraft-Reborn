package org.academy.internal.common.ability.aeromanip.program;

import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramStructureAnchor;
import org.academy.api.common.ability.program.ProgramStructureControl;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.ability.program.ProgramStructurePose;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.aeromanip.charge.AeromanipChargeTier;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Restricted server-owned environment exposed to Aeromanip programs.
 */
public interface AeromanipProgramRuntime extends ProgramTargetResolver {
    Object caster();

    Optional<Object> lookTarget();

    ProgramActionTransaction.ProgramAction airflowPush(
            Object entity,
            ProgramDirection direction,
            float power
    );

    ProgramActionTransaction.ProgramAction laminarCut(
            @Nullable ProgramWorldPosition origin,
            ProgramDirection direction,
            float power,
            AeromanipChargeTier chargeTier,
            float chargeCostMultiplier,
            @Nullable ProgramDirection planeDirection,
            AeromanipProgramNodeCatalog.BladePlaneMode planeMode
    );

    ProgramActionTransaction.ProgramAction placeTemporaryJetNozzle(
            Object target,
            ProgramDirection direction,
            AeromanipProgramNodeCatalog.NozzleTargetType targetType
    );

    ProgramActionTransaction.ProgramAction fireJets(int durationSeconds);

    int ownedJetNozzleCount(
            ProgramWorldPosition center,
            double radius,
            @Nullable ProgramDirection direction
    );

    ProgramActionTransaction.ProgramAction launchBlockStructure(
            ProgramBlockPosition seed,
            ProgramDirection direction,
            AeromanipProgramNodeCatalog.BlockStructureLaunchConfiguration configuration
    );

    /**
     * Starts the nozzle-hosted capture transaction around the host cell. The structure output
     * exists only after the content transaction committed; a pending capture reports PENDING with
     * no published reference and every rejection keeps the source blocks.
     */
    StructureOperation extractStructure(ProgramBlockPosition host, int radius, int budget);

    /**
     * Acquires this runtime's exclusive control token for an existing structure epoch. A committed
     * Sable body receives the lightweight direct lease; another actor's control, a pending capture
     * and a replaced epoch are rejected explicitly.
     */
    StructureOperation controlStructure(ProgramStructureReference structure);

    /**
     * Bounded nozzle/pressure force at the captured local anchors with directions re-derived from
     * the live pose each step, optionally toward a world target. Out-of-range targets, unresolved
     * supports and lost regions end the action while keeping the structure and its inertia.
     */
    StructureOperation moveStructure(
            ProgramStructureControl control,
            @Nullable ProgramWorldPosition target,
            @Nullable ProgramStructureAnchor anchor,
            double seconds
    );

    /** Finite-torque attitude control is not implemented; the request is reported unavailable. */
    StructureOperation rotateStructure(
            ProgramStructureControl control,
            @Nullable ProgramStructurePose pose
    );

    /**
     * One bounded launch along an explicit direction for the configured window. Damage is settled
     * only at real contacts during flight, never along a preset trajectory.
     */
    StructureOperation launchStructure(
            ProgramStructureControl control,
            ProgramDirection direction,
            float power,
            double seconds
    );

    /** Idempotent termination of this token's own jet force; successors are never touched. */
    StructureOperation releaseStructure(ProgramStructureControl control);

    /** Safe staticization is not implemented; content ownership is preserved. */
    StructureOperation staticizeStructure(
            ProgramStructureReference structure,
            @Nullable ProgramBlockPosition target
    );

    /**
     * Outcome of one structure node. Effects already transferred to a long-lived session are not
     * compensated; rejections stay machine-readable in {@link #result()}.
     */
    record StructureOperation(
            @Nullable ProgramStructureReference structure,
            @Nullable ProgramStructureControl control,
            ProgramStructureOperationResult result
    ) {
        public StructureOperation {
            Objects.requireNonNull(result, "result");
        }

        public static StructureOperation result(ProgramStructureOperationResult result) {
            return new StructureOperation(null, null, result);
        }

        public static StructureOperation structure(
                ProgramStructureReference structure,
                ProgramStructureOperationResult result
        ) {
            return new StructureOperation(Objects.requireNonNull(structure, "structure"), null, result);
        }

        public static StructureOperation control(
                ProgramStructureControl control,
                ProgramStructureOperationResult result
        ) {
            return new StructureOperation(null, Objects.requireNonNull(control, "control"), result);
        }

        public static StructureOperation rejected(String detail) {
            return result(ProgramStructureOperationResult.rejected(detail));
        }

        public static StructureOperation unavailable(String detail) {
            return result(ProgramStructureOperationResult.unavailable(detail));
        }
    }
}
