package org.academy.internal.common.ability.accelerator.skills.lv1;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorBlastTest {
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
