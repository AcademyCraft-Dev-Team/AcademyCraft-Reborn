package org.academy.api.server.ability.spell;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpellCastGeometryTest {
    @Test void explicitOriginAndEndpointDeriveNormalizedDirection() {
        var origin = new Vec3(3, 2, 1);
        var geometry = SpellCastGeometry.resolve(new SpatialCastRequest(origin, origin.add(0, 0, 8), null, null),
                Vec3.ZERO, new Vec3(1, 0, 0));
        assertEquals(origin, geometry.origin());
        assertEquals(new Vec3(0, 0, 1), geometry.direction());
        assertEquals(SpatialCastRequest.ORIGIN | SpatialCastRequest.ENDPOINT, geometry.request().inputMask());
    }

    @Test void directionIsNotSpeedAndNativeDefaultsRemainDistinct() {
        var nativeGeometry = SpellCastGeometry.resolve(SpatialCastRequest.nativeCast(), new Vec3(1, 2, 3), new Vec3(0, 2, 0));
        assertTrue(nativeGeometry.isNative());
        assertEquals(new Vec3(0, 1, 0), nativeGeometry.direction());
        var specified = SpellCastGeometry.resolve(new SpatialCastRequest(null, null, new Vec3(10, 0, 0), null),
                Vec3.ZERO, new Vec3(0, 0, 1));
        assertEquals(new Vec3(1, 0, 0), specified.direction());
        assertFalse(specified.isNative());
    }

    @Test void conflictingAndDegenerateInputsFailInsteadOfSilentlyOverriding() {
        var conflict = assertThrows(SpellGeometryException.class, () -> SpellCastGeometry.resolve(
                new SpatialCastRequest(null, new Vec3(5, 0, 0), new Vec3(0, 0, 1), null), Vec3.ZERO, new Vec3(1, 0, 0)));
        assertEquals(SpellGeometryException.Reason.CONFLICTING_INPUTS, conflict.reason());
        assertInvalid(new SpatialCastRequest(null, Vec3.ZERO, null, null));
        assertInvalid(new SpatialCastRequest(null, null, Vec3.ZERO, null));
        assertInvalid(new SpatialCastRequest(new Vec3(Double.NaN, 0, 0), null, null, null));
        assertInvalid(new SpatialCastRequest(null, null, new Vec3(Double.POSITIVE_INFINITY, 0, 0), null));
    }

    @Test void distantOriginsAndEndpointsAreRejected() {
        assertInvalid(new SpatialCastRequest(new Vec3(49, 0, 0), null, null, null));
        assertInvalid(new SpatialCastRequest(null, new Vec3(0, 0, 65), null, null));
    }

    private static void assertInvalid(SpatialCastRequest request) {
        var failure = assertThrows(SpellGeometryException.class, () -> SpellCastGeometry.resolve(request, Vec3.ZERO, new Vec3(1, 0, 0)));
        assertEquals(SpellGeometryException.Reason.INVALID_INPUT, failure.reason());
    }
}
