package org.academy.internal.server.time;

import org.academy.api.server.time.TemporalPauseSource;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalImmunityStateTest {
    @Test
    void independentContributionsCannotRemoveEachOther() {
        var state = new TemporalImmunityState();
        var entityId = UUID.randomUUID();
        var sources = Set.of(TemporalPauseSource.VANILLA_FREEZE);

        state.acquire(entityId, sources);
        state.acquire(entityId, sources);
        assertTrue(state.hasAny(entityId));
        assertTrue(state.entityIds().contains(entityId));
        state.release(entityId, sources);
        assertTrue(state.isImmune(entityId, TemporalPauseSource.VANILLA_FREEZE));

        state.release(entityId, sources);
        assertFalse(state.isImmune(entityId, TemporalPauseSource.VANILLA_FREEZE));
        assertFalse(state.hasAny(entityId));
        assertFalse(state.entityIds().contains(entityId));
    }

    @Test
    void pauseSourcesRemainIndependent() {
        var state = new TemporalImmunityState();
        var entityId = UUID.randomUUID();
        state.acquire(entityId, Set.of(TemporalPauseSource.ACADEMY_PAUSE));

        assertTrue(state.isImmune(entityId, TemporalPauseSource.ACADEMY_PAUSE));
        assertFalse(state.isImmune(entityId, TemporalPauseSource.VANILLA_FREEZE));
    }
}
