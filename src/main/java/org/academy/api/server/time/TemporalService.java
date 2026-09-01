package org.academy.api.server.time;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/** Server-authoritative entry point for time-control state. */
public interface TemporalService {
    /**
     * Adds an owner-bound temporal field. Field mutation and lease closure
     * must happen on the server thread.
     */
    TemporalFieldLease acquireField(TemporalField field);

    /** Effective non-spatial scale for a level-wide channel. */
    double effectiveScale(ServerLevel level, TemporalChannel channel);

    /** Effective scale at an entity, including source-specific immunity. */
    double effectiveScale(Entity entity, TemporalChannel channel);

    /** Effective scale at a block position. */
    double effectiveScale(
            ServerLevel level,
            BlockPos position,
            TemporalChannel channel
    );

    /**
     * Adds a transient immunity contribution for an entity.
     *
     * <p>The returned lease is the only supported way to remove this
     * contribution. The mutation must be performed on the server thread.</p>
     */
    TemporalImmunityLease acquireImmunity(
            Entity entity,
            Set<TemporalPauseSource> sources
    );

    default TemporalImmunityLease acquireImmunity(
            Entity entity,
            TemporalPauseSource source
    ) {
        return acquireImmunity(entity, Set.of(source));
    }

    /**
     * Adds immunity against every hard-pause source known to this version.
     * The returned lease still owns only its own contribution.
     */
    default TemporalImmunityLease acquireTimeStopImmunity(Entity entity) {
        return acquireImmunity(entity, Set.of(TemporalPauseSource.values()));
    }

    boolean isImmune(Entity entity, TemporalPauseSource source);

    /** True when at least one time-stop immunity contribution is active. */
    boolean isTimeStopImmune(Entity entity);
}
