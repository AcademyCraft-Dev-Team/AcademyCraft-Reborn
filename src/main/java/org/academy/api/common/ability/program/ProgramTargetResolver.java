package org.academy.api.common.ability.program;

import net.minecraft.world.entity.Entity;

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

    default Optional<ProgramWorldPosition> positionOf(
            Object entityReference,
            ProgramEntityPositionAnchor anchor
    ) {
        var feet = positionOf(entityReference);
        if (feet.isEmpty() || anchor == ProgramEntityPositionAnchor.FEET
                || !(entityReference instanceof Entity entity)) return feet;
        var position = switch (anchor) {
            case FEET -> entity.position();
            case CENTER -> entity.getBoundingBox().getCenter();
            case EYES -> entity.getEyePosition();
        };
        return Optional.of(new ProgramWorldPosition(
                feet.get().dimension(), position.x, position.y, position.z));
    }

    Optional<ProgramDirection> lookDirectionOf(Object entityReference);

    default Optional<ProgramDirection> movementDirectionOf(Object entityReference) {
        if (!(entityReference instanceof Entity entity)) return Optional.empty();
        var movement = entity.getDeltaMovement();
        if (!Double.isFinite(movement.x)
                || !Double.isFinite(movement.y)
                || !Double.isFinite(movement.z)
                || movement.lengthSqr() < 1.0e-12) {
            return Optional.empty();
        }
        return Optional.of(new ProgramDirection(movement.x, movement.y, movement.z));
    }

    List<?> entitiesAround(ProgramWorldPosition center, double radius);

    Optional<ProgramBlockPosition> raycastBlock(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    );

    default Optional<ProgramDirection> blockNormalFromView(
            Object entityReference,
            double maximumDistance
    ) {
        return Optional.empty();
    }

    default Optional<ProgramDirection> raycastBlockNormal(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        return Optional.empty();
    }

    default Optional<Object> raycastEntity(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        return Optional.empty();
    }
}
