package org.academy.internal.common.ability.accelerator.program;

import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.ProgramActionTransaction;

import java.util.List;
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
}
