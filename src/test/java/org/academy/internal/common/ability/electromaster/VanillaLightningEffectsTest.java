package org.academy.internal.common.ability.electromaster;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaLightningEffectsTest {
    @Test
    void rejectsNonFiniteStrikePositions() {
        assertTrue(VanillaLightningEffects.isFinite(Vec3.ZERO));
        assertFalse(VanillaLightningEffects.isFinite(new Vec3(Double.NaN, 0.0, 0.0)));
        assertFalse(VanillaLightningEffects.isFinite(new Vec3(0.0, Double.POSITIVE_INFINITY, 0.0)));
    }
}
