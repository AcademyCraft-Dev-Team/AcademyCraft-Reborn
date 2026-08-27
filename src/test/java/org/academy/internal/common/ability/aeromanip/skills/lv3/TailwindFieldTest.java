package org.academy.internal.common.ability.aeromanip.skills.lv3;

import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TailwindFieldTest {
    @Test
    void chargeTiersSelectTheThreeFieldModes() {
        assertEquals(TailwindField.Mode.PLACED_DIRECTIONAL,
                TailwindField.modeFor(AeromanipChargeTier.INSTANT));
        assertEquals(TailwindField.Mode.FOLLOW_DIRECTIONAL,
                TailwindField.modeFor(AeromanipChargeTier.HALF));
        assertEquals(TailwindField.Mode.FOLLOW_RADIAL,
                TailwindField.modeFor(AeromanipChargeTier.FULL));
    }

    @Test
    void radialModePointsAwayFromTheFieldCenter() {
        assertEquals(new Vec3(1.0, 0.0, 0.0), TailwindField.flowDirection(
                TailwindField.Mode.FOLLOW_RADIAL,
                Vec3.ZERO,
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(4.0, 0.0, 0.0)));
        assertEquals(new Vec3(0.0, 0.0, 1.0), TailwindField.flowDirection(
                TailwindField.Mode.FOLLOW_DIRECTIONAL,
                Vec3.ZERO,
                new Vec3(0.0, 0.0, 2.0),
                new Vec3(4.0, 0.0, 0.0)));
    }
}
