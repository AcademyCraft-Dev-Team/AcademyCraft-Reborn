package org.academy.internal.common.ability.electromaster.program;

import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.program.ProgramActionTransaction;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Restricted server-owned environment exposed to Electromaster programs.
 */
public interface ElectromasterProgramRuntime extends ProgramTargetResolver {
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
            ElectromasterProgramNodeCatalog.MagneticMode mode
    );

    List<ProgramBlockPosition> chargeableBlocksAround(
            ProgramWorldPosition center,
            double radius
    );

    OptionalDouble entityEnergyFraction(Object entity);

    OptionalDouble blockEnergyFraction(ProgramBlockPosition block);

    int redstonePower(ProgramBlockPosition block);

    ProgramActionTransaction.ProgramAction currentRecharge(
            Object target,
            ElectromasterProgramNodeCatalog.EnergyTargetType targetType
    );
}
