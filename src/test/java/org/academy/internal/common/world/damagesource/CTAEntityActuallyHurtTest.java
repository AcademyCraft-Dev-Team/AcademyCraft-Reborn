package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CTAEntityActuallyHurtTest {
    @Test
    void lethalDamageDoesNotInstallAZeroHealthCapBeforeDeathProtection() {
        assertFalse(CTAEntityActuallyHurt.shouldInstallPostDamageHealthCap(0.0f));
        assertFalse(CTAEntityActuallyHurt.shouldInstallPostDamageHealthCap(-1.0f));
        assertFalse(CTAEntityActuallyHurt.shouldInstallPostDamageHealthCap(Float.NaN));
    }

    @Test
    void survivingDamageKeepsTheExistingShortHealthCap() {
        assertTrue(CTAEntityActuallyHurt.shouldInstallPostDamageHealthCap(0.01f));
        assertTrue(CTAEntityActuallyHurt.shouldInstallPostDamageHealthCap(10.0f));
    }
}
