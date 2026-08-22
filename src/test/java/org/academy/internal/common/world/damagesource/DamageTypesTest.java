package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageTypesTest {
    @Test
    void vecAndCtaUseVerifiedTrueHealthRoute() {
        assertTrue(DamageTypes.usesVerifiedTrueHealth(DamageTypes.VEC));
        assertTrue(DamageTypes.usesVerifiedTrueHealth(DamageTypes.CTA));
        assertTrue(DamageTypes.usesDirectActuallyHurt(DamageTypes.VEC));
        assertTrue(DamageTypes.usesDirectActuallyHurt(DamageTypes.CTA));
        assertFalse(DamageTypes.usesDirectActuallyHurt(DamageTypes.DM_DAMAGE));
        assertFalse(DamageTypes.usesVerifiedTrueHealth(DamageTypes.MELT_DAMAGE));
    }
}
