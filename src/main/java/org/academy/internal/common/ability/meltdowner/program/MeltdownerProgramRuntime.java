package org.academy.internal.common.ability.meltdowner.program;

import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.internal.common.ability.program.ProgramActionTransaction;

import java.util.Optional;

/** Restricted server-owned environment exposed to Meltdowner programs. */
public interface MeltdownerProgramRuntime extends ProgramTargetResolver {
    Object caster();

    Optional<Object> lookTarget();

    ProgramActionTransaction.ProgramAction fireElectronBeam(
            ProgramDirection direction,
            float power
    );

    ProgramActionTransaction.ProgramAction fireMiningBeam(
            ProgramBlockPosition block,
            float power
    );
}
