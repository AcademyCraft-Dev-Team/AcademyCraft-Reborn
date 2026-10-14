package org.academy.internal.common.structure.transaction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureCheckpointPolicyTest {
    @Test
    void prunesRemovedAndZeroMassChildrenButNeverTheTarget() {
        var target = UUID.randomUUID();
        var removed = UUID.randomUUID();
        var zeroMass = UUID.randomUUID();
        var healthy = UUID.randomUUID();
        var bodies = List.of(
                new CaptureCheckpointPolicy.Body(target, true, false),
                new CaptureCheckpointPolicy.Body(removed, true, true),
                new CaptureCheckpointPolicy.Body(zeroMass, false, false),
                new CaptureCheckpointPolicy.Body(healthy, false, true));
        assertEquals(List.of(removed, zeroMass),
                CaptureCheckpointPolicy.invalidBodies(target, bodies),
                "The capture target must be protected and healthy children retained");
    }

    @Test
    void keepsDuplicateIdentitiesSingleAndOrderStable() {
        var target = UUID.randomUUID();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var bodies = List.of(
                new CaptureCheckpointPolicy.Body(first, true, true),
                new CaptureCheckpointPolicy.Body(second, false, false),
                new CaptureCheckpointPolicy.Body(first, false, false));
        var pruned = CaptureCheckpointPolicy.invalidBodies(target, bodies);
        assertEquals(List.of(first, second), pruned);
        assertTrue(pruned.stream().noneMatch(target::equals));
    }
}
