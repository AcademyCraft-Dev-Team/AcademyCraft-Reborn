package org.academy.api.common.ability.program;

import java.util.List;
import java.util.Optional;

/**
 * Read-only world queries used by common target-selection nodes.
 * Ability runtimes may expose a restricted implementation through their execution environment.
 */
public interface ProgramTargetResolver {
    default Object caster() {
        throw new IllegalStateException("This target resolver does not expose a caster");
    }

    default Optional<Object> lookTarget() {
        return Optional.empty();
    }

    default Optional<ProgramBlockPosition> lookBlockTarget() {
        return Optional.empty();
    }

    Optional<ProgramWorldPosition> positionOf(Object entityReference);

    Optional<ProgramDirection> lookDirectionOf(Object entityReference);

    List<?> entitiesAround(ProgramWorldPosition center, double radius);

    Optional<ProgramBlockPosition> raycastBlock(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    );

    default Optional<Object> raycastEntity(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        return Optional.empty();
    }
}
