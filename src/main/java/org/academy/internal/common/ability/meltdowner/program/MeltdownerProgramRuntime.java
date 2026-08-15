package org.academy.internal.common.ability.meltdowner.program;

import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.jspecify.annotations.Nullable;
import org.academy.internal.common.ability.program.ProgramActionTransaction;

import java.util.Optional;

/** Restricted server-owned environment exposed to Meltdowner programs. */
public interface MeltdownerProgramRuntime extends ProgramTargetResolver {
    Object caster();

    Optional<Object> lookTarget();

    ProgramActionTransaction.ProgramAction fireElectronBeam(
            @Nullable ProgramWorldPosition origin,
            @Nullable ProgramDirection direction,
            @Nullable ProgramWorldPosition target,
            float power,
            boolean destroyBlocks
    );

    ProgramActionTransaction.ProgramAction fireMiningBeam(
            @Nullable ProgramWorldPosition origin,
            @Nullable ProgramDirection direction,
            @Nullable ProgramWorldPosition target,
            @Nullable ProgramBlockPosition legacyBlock,
            float power
    );

    ProgramActionTransaction.ProgramAction atomicJet(
            Object entity,
            ProgramDirection direction,
            float power,
            boolean destroyBlocks
    );
}
