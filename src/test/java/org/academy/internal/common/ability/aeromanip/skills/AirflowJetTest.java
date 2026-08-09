package org.academy.internal.common.ability.aeromanip.skills;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirflowJetTest {
    @Test
    void vectorTravelAccumulatesAsMaceFallMomentum() {
        assertEquals(7.0, AirflowJet.Server.accumulateMaceMomentum(
                2.0,
                5.0,
                new Vec3(0.0, 0.0, 2.0)
        ));
        assertEquals(4.0, AirflowJet.Server.accumulateMaceMomentum(
                4.0,
                1.0,
                new Vec3(Double.NaN, 0.0, 0.0)
        ));
    }
}
