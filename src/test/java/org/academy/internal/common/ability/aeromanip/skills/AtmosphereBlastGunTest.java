package org.academy.internal.common.ability.aeromanip.skills;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereBlastGunTest {
    private static final Vec3 EYE = Vec3.ZERO;
    private static final Vec3 LOOK = new Vec3(0, 0, 1);

    @Test
    void acceptsTargetsInsideTheFrontalVolume() {
        assertTrue(AtmosphereBlastGun.isInsideBlastVolume(
                EYE,
                LOOK,
                new Vec3(0.5, 0, 7.5),
                0.25
        ));
    }

    @Test
    void rejectsTargetsBehindBeyondOrBesideTheVolume() {
        assertFalse(AtmosphereBlastGun.isInsideBlastVolume(EYE, LOOK, new Vec3(0, 0, -1), 0));
        assertFalse(AtmosphereBlastGun.isInsideBlastVolume(EYE, LOOK, new Vec3(0, 0, 8.1), 0));
        assertFalse(AtmosphereBlastGun.isInsideBlastVolume(EYE, LOOK, new Vec3(1.1, 0, 4), 0));
    }

    @Test
    void rejectsDegenerateLookVectors() {
        assertFalse(AtmosphereBlastGun.isInsideBlastVolume(EYE, Vec3.ZERO, new Vec3(0, 0, 1), 0));
    }
}
