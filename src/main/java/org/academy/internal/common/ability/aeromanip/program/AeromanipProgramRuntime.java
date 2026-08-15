package org.academy.internal.common.ability.aeromanip.program;

import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.internal.common.ability.program.ProgramActionTransaction;

import java.util.Optional;

/** Restricted server-owned environment exposed to Aeromanip programs. */
public interface AeromanipProgramRuntime extends ProgramTargetResolver {
    Object caster();

    Optional<Object> lookTarget();

    ProgramActionTransaction.ProgramAction airflowPush(
            Object entity,
            ProgramDirection direction,
            float power
    );

    ProgramActionTransaction.ProgramAction laminarCut(
            ProgramDirection direction,
            float power
    );
}
