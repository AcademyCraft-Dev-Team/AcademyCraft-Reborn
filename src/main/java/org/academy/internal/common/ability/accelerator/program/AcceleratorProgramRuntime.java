package org.academy.internal.common.ability.accelerator.program;

import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramStructureAnchor;
import org.academy.api.common.ability.program.ProgramStructureControl;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.ability.program.ProgramStructurePose;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramVector;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Restricted server-owned environment exposed to vector-manipulation programs.
 *
 * <p>Implementations must validate entity kind, range, learned skills, friendly-fire policy and
 * available CP inside the returned transaction action. They also derive actual velocity and damage
 * from server state; programs only select a bounded intent tier.</p>
 */
public interface AcceleratorProgramRuntime extends ProgramTargetResolver {
    Object caster();

    Optional<Object> lookTarget();

    List<?> incomingProjectiles();

    ProgramActionTransaction.ProgramAction applyVector(
            Object entity,
            ProgramDirection direction,
            AcceleratorProgramStrength strength
    );

    ProgramActionTransaction.ProgramAction rewriteMotion(
            Object entity,
            ProgramVector motion,
            float power
    );

    ProgramActionTransaction.ProgramAction kineticImpact(
            Object entity,
            ProgramDirection direction,
            AcceleratorProgramStrength strength
    );

    ProgramActionTransaction.ProgramAction kineticShockwave(
            ProgramWorldPosition position,
            ProgramDirection direction,
            float power,
            boolean destroyBlocks,
            int radius
    );

    ProgramActionTransaction.ProgramAction redirectProjectile(
            Object projectile,
            ProgramDirection direction
    );

    ProgramActionTransaction.ProgramAction displaceEntity(
            Object entity,
            ProgramWorldPosition destination,
            AcceleratorProgramStrength strength
    );

    ProgramActionTransaction.ProgramAction displaceBlock(
            ProgramBlockPosition block,
            ProgramBlockPosition destination,
            AcceleratorProgramStrength strength
    );

    /**
     * Starts the shared capture transaction for a host block. The structure output exists only
     * after the content transaction committed; a pending capture reports PENDING with no
     * published reference and rejects without moving the source blocks.
     */
    StructureOperation extractStructure(ProgramBlockPosition host, int tier, int budget);

    /**
     * Acquires this runtime's exclusive control token for an existing structure epoch. The same
     * structure under another actor or a replaced session is rejected, never silently re-bound.
     */
    StructureOperation controlStructure(ProgramStructureReference structure);

    /**
     * Bounded anchor correction toward a world target or the anchor's own world point. Out-of-range
     * targets and lost regions end the action while keeping the structure and its inertia.
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

    /** One bounded launch impulse along an explicit direction; contact damage settles on contact. */
    StructureOperation launchStructure(
            ProgramStructureControl control,
            ProgramDirection direction,
            AcceleratorProgramStrength strength
    );

    /** Idempotent termination of this token's own commands; successors are never touched. */
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
