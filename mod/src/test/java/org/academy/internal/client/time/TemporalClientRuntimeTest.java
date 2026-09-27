package org.academy.internal.client.time;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemporalClientRuntimeTest {
    @AfterEach
    void resetRuntime() {
        TemporalClientRuntime.reset();
    }

    @Test
    void rejectsStaleSnapshotsAndRollsBackFromFullState() {
        var session = UUID.randomUUID();
        var player = UUID.randomUUID();
        TemporalClientRuntime.applyState(
                session,
                2L,
                20L,
                Map.of(),
                Map.of(player, 0.5F)
        );
        assertEquals(0.5D, TemporalClientRuntime.effectivePlayerScale(player));

        TemporalClientRuntime.applyState(
                session,
                1L,
                19L,
                Map.of(),
                Map.of(player, 0.0F)
        );
        assertEquals(0.5D, TemporalClientRuntime.effectivePlayerScale(player));

        TemporalClientRuntime.applyState(
                session,
                3L,
                21L,
                Map.of(),
                Map.of()
        );
        assertEquals(1.0D, TemporalClientRuntime.effectivePlayerScale(player));
    }

    @Test
    void acceptsLowerRevisionFromANewServerSession() {
        var player = UUID.randomUUID();
        TemporalClientRuntime.applyState(
                UUID.randomUUID(),
                100L,
                500L,
                Map.of(),
                Map.of(player, 0.0F)
        );
        TemporalClientRuntime.applyState(
                UUID.randomUUID(),
                1L,
                1L,
                Map.of(),
                Map.of(player, 2.0F)
        );
        assertEquals(2.0D, TemporalClientRuntime.effectivePlayerScale(player));
    }
}
