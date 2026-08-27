package org.academy.internal.common.ability.darkmatter.program;

import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.ProgramActionTransaction;

import java.util.Optional;

/**
 * Restricted server-owned environment exposed to Darkmatter programs.
 */
public interface DarkmatterProgramRuntime extends ProgramTargetResolver {
    Object caster();

    Optional<Object> lookTarget();

    ProgramActionTransaction.ProgramAction disassembleBlock(
            ProgramBlockPosition block,
            float power
    );

    ProgramActionTransaction.ProgramAction disassembleEntity(
            Object entity,
            float power
    );

    ProgramActionTransaction.ProgramAction darkmatterCut(
            ProgramDirection direction,
            float power
    );

    ProgramActionTransaction.ProgramAction createBeetle(
            ProgramWorldPosition position,
            float power
    );
}
