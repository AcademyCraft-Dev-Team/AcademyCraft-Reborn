package org.academy.internal.server.time;

import com.mojang.serialization.JsonOps;
import org.academy.api.server.time.TemporalPauseSource;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalSavedDataTest {
    @Test
    void persistentImmunityRoundTripsThroughCodec() {
        var entityId = UUID.randomUUID();
        var data = new TemporalSavedData();
        data.setPersistent(
                entityId,
                Set.of(
                        TemporalPauseSource.VANILLA_FREEZE,
                        TemporalPauseSource.ACADEMY_PAUSE
                ),
                true
        );

        var encoded = TemporalSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow();
        var decoded = TemporalSavedData.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertTrue(decoded.isImmune(entityId, TemporalPauseSource.VANILLA_FREEZE));
        assertTrue(decoded.isImmune(entityId, TemporalPauseSource.ACADEMY_PAUSE));
        assertFalse(decoded.isImmune(entityId, TemporalPauseSource.EXTERNAL_COMPATIBILITY));
        assertTrue(decoded.hasAny(entityId));
        assertTrue(decoded.entityIds().contains(entityId));
    }

    @Test
    void removingOneSourcePreservesOtherSources() {
        var entityId = UUID.randomUUID();
        var data = new TemporalSavedData();
        data.setPersistent(
                entityId,
                Set.of(
                        TemporalPauseSource.VANILLA_FREEZE,
                        TemporalPauseSource.ACADEMY_PAUSE
                ),
                true
        );
        data.setPersistent(
                entityId,
                Set.of(TemporalPauseSource.VANILLA_FREEZE),
                false
        );

        assertFalse(data.isImmune(entityId, TemporalPauseSource.VANILLA_FREEZE));
        assertTrue(data.isImmune(entityId, TemporalPauseSource.ACADEMY_PAUSE));
    }

    @Test
    void unchangedPersistenceMutationDoesNotReportDirtyTransition() {
        var entityId = UUID.randomUUID();
        var data = new TemporalSavedData();
        var source = Set.of(TemporalPauseSource.VANILLA_FREEZE);

        assertTrue(data.setPersistent(entityId, source, true));
        assertFalse(data.setPersistent(entityId, source, true));
        assertTrue(data.setPersistent(entityId, source, false));
        assertFalse(data.setPersistent(entityId, source, false));
    }
}
