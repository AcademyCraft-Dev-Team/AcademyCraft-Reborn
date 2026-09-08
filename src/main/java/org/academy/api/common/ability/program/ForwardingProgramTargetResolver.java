package org.academy.api.common.ability.program;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Preserves every world-query capability when an ability runtime wraps a shared resolver. */
public interface ForwardingProgramTargetResolver extends ProgramTargetResolver {
    ProgramTargetResolver targetResolver();

    @Override default Object caster() { return targetResolver().caster(); }
    @Override default Optional<Object> lookTarget() { return targetResolver().lookTarget(); }
    @Override default Optional<ProgramBlockPosition> lookBlockTarget() { return targetResolver().lookBlockTarget(); }
    @Override default Optional<ProgramWorldPosition> positionOf(Object entity) { return targetResolver().positionOf(entity); }
    @Override default Optional<ProgramWorldPosition> positionOf(Object entity, ProgramEntityPositionAnchor anchor) {
        return targetResolver().positionOf(entity, anchor);
    }
    @Override default Optional<ProgramDirection> lookDirectionOf(Object entity) { return targetResolver().lookDirectionOf(entity); }
    @Override default Optional<ProgramVector> motionOf(Object entity) { return targetResolver().motionOf(entity); }
    @Override default Optional<ProgramDirection> movementDirectionOf(Object entity) { return targetResolver().movementDirectionOf(entity); }
    @Override default OptionalDouble heightOf(Object entity) { return targetResolver().heightOf(entity); }
    @Override default List<?> entitiesAround(ProgramWorldPosition center, double radius) {
        return targetResolver().entitiesAround(center, radius);
    }
    @Override default Optional<ProgramBlockPosition> raycastBlock(ProgramWorldPosition origin, ProgramDirection direction, double range) {
        return targetResolver().raycastBlock(origin, direction, range);
    }
    @Override default Optional<Object> raycastEntity(ProgramWorldPosition origin, ProgramDirection direction, double range) {
        return targetResolver().raycastEntity(origin, direction, range);
    }
    @Override default Optional<ProgramDirection> blockNormalFromView(Object entity, double range) {
        return targetResolver().blockNormalFromView(entity, range);
    }
    @Override default Optional<ProgramDirection> raycastBlockNormal(ProgramWorldPosition origin, ProgramDirection direction, double range) {
        return targetResolver().raycastBlockNormal(origin, direction, range);
    }
}
