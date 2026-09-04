package org.academy.internal.common.ability.aeromanip.program;

import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Restricted server-owned environment exposed to Aeromanip programs.
 */
public interface AeromanipProgramRuntime extends ProgramTargetResolver {
    Object caster();

    Optional<Object> lookTarget();

    ProgramActionTransaction.ProgramAction airflowPush(
            Object entity,
            ProgramDirection direction,
            float power
    );

    ProgramActionTransaction.ProgramAction laminarCut(
            @Nullable ProgramWorldPosition origin,
            ProgramDirection direction,
            float power,
            AeromanipChargeTier chargeTier,
            float chargeCostMultiplier,
            @Nullable ProgramDirection planeDirection,
            AeromanipProgramNodeCatalog.BladePlaneMode planeMode
    );

    ProgramActionTransaction.ProgramAction placeTemporaryJetNozzle(
            Object target,
            ProgramDirection direction,
            AeromanipProgramNodeCatalog.NozzleTargetType targetType
    );

    ProgramActionTransaction.ProgramAction fireJets(int durationSeconds);

    int ownedJetNozzleCount(
            ProgramWorldPosition center,
            double radius,
            @Nullable ProgramDirection direction
    );

    ProgramActionTransaction.ProgramAction launchBlockStructure(
            org.academy.api.common.ability.program.ProgramBlockPosition seed,
            ProgramDirection direction,
            AeromanipProgramNodeCatalog.BlockStructureLaunchConfiguration configuration
    );
}
