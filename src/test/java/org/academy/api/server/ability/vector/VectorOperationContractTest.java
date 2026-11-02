package org.academy.api.server.ability.vector;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.vector.EffectDescriptor;
import org.academy.api.common.ability.vector.EffectFidelity;
import org.academy.api.common.ability.vector.EffectOperation;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VectorOperationContractTest {
    @Test
    void descriptorSnapshotsEvidenceAndCannotPromoteObservationToControl() {
        var id = Identifier.fromNamespaceAndPath("test", "effect");
        var operations = EnumSet.of(EffectOperation.OBSERVE);
        var description = new EffectDescriptor(id, id, operations, EffectFidelity.OBSERVATION_ONLY, "unknown_guidance");
        operations.add(EffectOperation.CONTROL_EXISTING);
        assertFalse(description.supports(EffectOperation.CONTROL_EXISTING));
        assertThrows(IllegalArgumentException.class, () -> new EffectDescriptor(
                id, id, operations, EffectFidelity.OBSERVATION_ONLY, "incorrect_support"));
        assertThrows(UnsupportedOperationException.class,
                () -> description.operations().add(EffectOperation.CONTROL_EXISTING));
    }

    @Test
    void requestsRejectNonFiniteOrUnboundedWorldMutations() {
        assertThrows(IllegalArgumentException.class, () -> VectorOperationRequest.redirect(new Vec3(Double.NaN, 0, 0), 32));
        assertThrows(IllegalArgumentException.class, () -> VectorOperationRequest.redirect(new Vec3(Double.MAX_VALUE, 0, 0), 32));
        assertThrows(IllegalArgumentException.class, () -> VectorOperationRequest.redirect(new Vec3(129, 0, 0), 32));
        assertThrows(IllegalArgumentException.class, () -> VectorOperationRequest.redirect(Vec3.ZERO, 32));
        assertThrows(IllegalArgumentException.class, () -> VectorOperationRequest.redirect(new Vec3(1, 0, 0), Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> VectorOperationRequest.control(new Vec3(1, 0, 0), 32, 1201));
        assertThrows(IllegalArgumentException.class, () -> new VectorOperationRequest(UUID.randomUUID(),
                EffectOperation.REDIRECT_EXISTING, new Vec3(1, 0, 0), 32, 2));
    }

    @Test
    void validRequestsKeepVelocityUnitsAndHaveDistinctInvocationIdentity() {
        var first = VectorOperationRequest.redirect(new Vec3(0, .5, -2), 32);
        var second = VectorOperationRequest.redirect(new Vec3(0, .5, -2), 32);
        assertNotEquals(first.requestId(), second.requestId());
        assertEquals(new Vec3(0, .5, -2), first.velocity());
        assertEquals(1, first.durationTicks());
        assertEquals(1200, VectorOperationRequest.control(first.velocity(), 128, 1200).durationTicks());
        assertEquals(Vec3.ZERO, VectorOperationRequest.control(Vec3.ZERO, 32, 20).velocity());
    }
}
