package org.academy.api.common.structure;

import org.academy.api.common.structure.StructureDirectControl.Lease;
import org.academy.api.common.structure.StructureDirectControl.Probe;
import org.academy.api.common.structure.StructureDirectControl.Status;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure registry decisions behind the shared Sable lightweight control lease. The backend probe
 * itself is covered by the isolated Sable server smoke; these cases pin the exclusivity,
 * stale-epoch and idempotency rules the two ability services rely on.
 */
class StructureDirectControlTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONTROL = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID RUNTIME = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void acquireGrantsReadyBodiesAndRefusesForeignTokens() {
        var probe = Probe.ready(RUNTIME);
        assertEquals(Status.ACTIVE, StructureDirectControl.decideAcquire(probe, null, ACTOR, CONTROL));
        assertEquals(Status.ACTIVE, StructureDirectControl.decideAcquire(
                probe, new Lease(ACTOR, CONTROL, RUNTIME), ACTOR, CONTROL));
        assertEquals(Status.OCCUPIED, StructureDirectControl.decideAcquire(
                probe, new Lease(OTHER, CONTROL, RUNTIME), ACTOR, CONTROL));
        assertEquals(Status.ACTIVE, StructureDirectControl.decideAcquire(
                probe, new Lease(ACTOR, UUID.randomUUID(), RUNTIME), ACTOR, CONTROL));
        assertEquals(Status.OCCUPIED, StructureDirectControl.decideAcquire(
                Probe.occupied("traction_active"), null, ACTOR, CONTROL));
        assertEquals(Status.UNAVAILABLE, StructureDirectControl.decideAcquire(
                Probe.unavailable("structure_unavailable"), null, ACTOR, CONTROL));
    }

    @Test
    void staleEpochNeverRegainsAuthority() {
        var newEpoch = Probe.ready(UUID.randomUUID());
        assertEquals(Status.UNAVAILABLE, StructureDirectControl.decideAcquire(
                newEpoch, new Lease(ACTOR, CONTROL, RUNTIME), ACTOR, CONTROL));
        assertEquals(Status.UNAVAILABLE, StructureDirectControl.decideValidate(
                newEpoch, new Lease(ACTOR, CONTROL, RUNTIME), ACTOR, CONTROL));
    }

    @Test
    void validationRequiresTheExactTokenAndStillFreezesOnForeignControl() {
        var probe = Probe.ready(RUNTIME);
        var standing = new Lease(ACTOR, CONTROL, RUNTIME);
        assertEquals(Status.ACTIVE, StructureDirectControl.decideValidate(probe, standing, ACTOR, CONTROL));
        assertEquals(Status.UNAVAILABLE, StructureDirectControl.decideValidate(probe, null, ACTOR, CONTROL));
        assertEquals(Status.UNAVAILABLE, StructureDirectControl.decideValidate(
                probe, new Lease(OTHER, CONTROL, RUNTIME), ACTOR, CONTROL));
        assertEquals(Status.UNAVAILABLE, StructureDirectControl.decideValidate(
                probe, new Lease(ACTOR, UUID.randomUUID(), RUNTIME), ACTOR, CONTROL));
        assertEquals(Status.OCCUPIED, StructureDirectControl.decideValidate(
                Probe.occupied("traction_active"), standing, ACTOR, CONTROL));
        assertEquals(Status.UNAVAILABLE, StructureDirectControl.decideValidate(
                Probe.unavailable("structure_unavailable"), standing, ACTOR, CONTROL));
    }
}
