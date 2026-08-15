package org.academy.internal.common.ability.teleport.program;

import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.ProgramActionTransaction;

import java.util.Optional;

/** Restricted server-owned environment exposed to Teleport programs. */
public interface TeleportProgramRuntime extends ProgramTargetResolver {
    Object caster();

    Optional<Object> lookTarget();

    ProgramActionTransaction.ProgramAction teleportSelf(
            ProgramWorldPosition destination,
            float power
    );

    ProgramActionTransaction.ProgramAction teleportEntity(
            Object entity,
            ProgramWorldPosition destination,
            float power
    );
}
