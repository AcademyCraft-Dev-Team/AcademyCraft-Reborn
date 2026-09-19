package org.academy.internal.common.ability.accelerator.skills.lv1;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorBlastTest {
    @Test void blastImpulseFollowsTheRayAndDecaysWithoutLosingItsLift() {
        var near = VectorBlast.blastImpulse(FORWARD, 0, 64);
        var far = VectorBlast.blastImpulse(FORWARD, 64, 64);
        assertTrue(near.z > 3 && far.z > 1 && near.z > far.z);
        assertTrue(near.y > 0 && near.y == far.y);
        assertTrue(VectorBlast.blastImpulse(new Vec3(1, 0, 0), 0, 64).x > 3);
        assertTrue(VectorBlast.blastImpulse(Vec3.ZERO, 0, 64).lengthSqr() == 0);
    }
    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 FORWARD = new Vec3(0.0, 0.0, 1.0);

    @Test
    void acceptsTargetsInsideTheBoundedBeam() {
        assertTrue(VectorBlast.isInsideBeam(
                ORIGIN, FORWARD, new Vec3(0.5, 0.0, 32.0), 64.0));
    }

    @Test
    void usesTheReferenceOneBlockCenterlineRadius() {
        assertTrue(VectorBlast.isInsideBeam(
                ORIGIN, FORWARD, new Vec3(1.0, 0.0, 12.0), 64.0));
        assertFalse(VectorBlast.isInsideBeam(
                ORIGIN, FORWARD, new Vec3(1.01, 0.0, 12.0), 64.0));
    }

    @Test
    void rejectsTargetsBehindOrPastTheServerRange() {
        assertFalse(VectorBlast.isInsideBeam(
                ORIGIN, FORWARD, new Vec3(0.0, 0.0, -0.1), 64.0));
        assertFalse(VectorBlast.isInsideBeam(
                ORIGIN, FORWARD, new Vec3(0.0, 0.0, 64.1), 64.0));
    }
}
