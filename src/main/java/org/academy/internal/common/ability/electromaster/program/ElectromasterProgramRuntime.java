package org.academy.internal.common.ability.electromaster.program;

import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramStructureAnchor;
import org.academy.api.common.ability.program.ProgramStructureControl;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.ability.program.ProgramStructurePose;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Restricted server-owned environment exposed to Electromaster programs.
 */
public interface ElectromasterProgramRuntime extends ProgramTargetResolver,
        org.academy.api.common.ability.program.ProgramStructureOrientationRuntime {
    Object caster();

    Optional<Object> lookTarget();

    ProgramActionTransaction.ProgramAction arcDischarge(
            Object entity,
            float power
    );

    ProgramActionTransaction.ProgramAction magneticMove(
            Object target,
            ProgramWorldPosition destination,
            float power,
            ElectromasterProgramNodeCatalog.EnergyTargetType targetType,
            ElectromasterProgramNodeCatalog.MagneticMode mode,
            boolean forceMagnetize
    );

    List<ProgramBlockPosition> chargeableBlocksAround(
            ProgramWorldPosition center,
            double radius
    );

    List<?> magneticEntitiesAround(ProgramWorldPosition center, double radius);

    OptionalDouble entityEnergyFraction(Object entity);

    OptionalDouble blockEnergyFraction(ProgramBlockPosition block);

    int redstonePower(ProgramBlockPosition block);

    ProgramActionTransaction.ProgramAction currentRecharge(
            Object target,
            ElectromasterProgramNodeCatalog.EnergyTargetType targetType
    );

    /**
     * Starts the shared magnetic capture transaction around the host cell. The structure output
     * exists only after the content transaction committed; a pending capture reports PENDING with
     * no published reference and every rejection keeps the source blocks.
     */
    StructureOperation extractStructure(ProgramBlockPosition host, int budget, boolean forceMagnetize);

    /**
     * Acquires this runtime's exclusive control token for an existing structure epoch. The same
     * structure under another actor or a replaced session is rejected, never silently re-bound.
     */
    StructureOperation controlStructure(ProgramStructureReference structure);

    /**
     * Bounded magnetic pull toward a world target or the anchor's own world point. Force follows
     * the structure's magnetic participation; out-of-range targets and lost regions end the
     * action while keeping the structure and its inertia.
     */
    StructureOperation moveStructure(
            ProgramStructureControl control,
            @Nullable ProgramWorldPosition target,
            @Nullable ProgramStructureAnchor anchor,
            double seconds
    );

    /** Legacy synchronous facade; asynchronous nodes use rotateOrientation to track completion. */
    StructureOperation rotateStructure(
            ProgramStructureControl control,
            @Nullable ProgramStructurePose pose
    );

    /** Retains synchronous third-party runtimes while the server implementation provides receipts. */
    @Override
    default Operation rotateOrientation(ProgramStructureControl control, @Nullable ProgramStructurePose pose) {
        return Operation.fromLegacy(rotateStructure(control, pose).result());
    }

    /** One bounded magnetic launch along an explicit direction; contact damage settles on contact. */
    StructureOperation launchStructure(
            ProgramStructureControl control,
            ProgramDirection direction,
            float power
    );

    /** Idempotent termination of this token's own field and magnetization overlay; successors are never touched. */
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
