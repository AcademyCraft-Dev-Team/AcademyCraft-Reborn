package org.academy.internal.common.ability.electromaster.program;

import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.ProgramActionTransaction;

import java.util.Optional;

/** Restricted server-owned environment exposed to Electromaster programs. */
public interface ElectromasterProgramRuntime extends ProgramTargetResolver {
    Object caster();

    Optional<Object> lookTarget();

    ProgramActionTransaction.ProgramAction arcDischarge(
            Object entity,
            float power
    );

    ProgramActionTransaction.ProgramAction magneticMove(
            Object entity,
            ProgramWorldPosition destination,
            float power
    );
}
