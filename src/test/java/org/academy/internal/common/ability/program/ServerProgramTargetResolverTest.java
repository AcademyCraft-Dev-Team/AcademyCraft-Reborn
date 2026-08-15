package org.academy.internal.common.ability.program;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerProgramTargetResolverTest {
    @Test
    void nearbyEntityQueriesUseSphericalRatherThanBoxDistance() {
        var center = Vec3.ZERO;

        assertTrue(ServerProgramTargetResolver.isWithinRadius(
                center, new Vec3(32.0, 0.0, 0.0), 32.0));
        assertFalse(ServerProgramTargetResolver.isWithinRadius(
                center, new Vec3(32.0, 0.0, 32.0), 32.0));
    }

    @Test
    void rejectsNonFiniteRadiusInputs() {
        assertFalse(ServerProgramTargetResolver.isWithinRadius(
                Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), Double.NaN));
    }
}
