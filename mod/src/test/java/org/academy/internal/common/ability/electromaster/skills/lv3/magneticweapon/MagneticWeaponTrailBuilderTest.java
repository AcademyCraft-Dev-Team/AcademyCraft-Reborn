package org.academy.internal.common.ability.electromaster.skills.lv3.magneticweapon;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticWeaponTrailBuilderTest {
    @Test
    void sampleHistoryIsBoundedAndClearsDiscontinuities() {
        var history = new ArrayDeque<Vec3>();
        for (var i = 0; i < 12; i++) {
            assertFalse(MagneticWeapon.Client.MagneticWeaponTrailBuilder.appendSample(
                    history, new Vec3(i * 0.1, 0.0, 0.0), 8
            ));
        }
        assertEquals(8, history.size());

        assertTrue(MagneticWeapon.Client.MagneticWeaponTrailBuilder.appendSample(
                history, new Vec3(20.0, 0.0, 0.0), 8
        ));
        assertEquals(1, history.size());
    }
}
