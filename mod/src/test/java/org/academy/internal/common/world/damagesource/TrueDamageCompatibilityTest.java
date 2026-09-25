package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrueDamageCompatibilityTest {
    @Test
    void onlyShortCircuitedCustomDeathNeedsVanillaFallback() {
        assertTrue(TrueDamageCompatibility.needsVanillaDeathFallback(false, false));
        assertFalse(TrueDamageCompatibility.needsVanillaDeathFallback(true, false));
        assertFalse(TrueDamageCompatibility.needsVanillaDeathFallback(false, true));
        assertFalse(TrueDamageCompatibility.needsVanillaDeathFallback(true, true));
    }

    @Test
    void findSpecialBypassesAHealthRestoringOverride() throws Throwable {
        var boss = new RestoringBoss();
        boss.die();
        assertFalse(boss.vanillaDeath());

        var lookup = MethodHandles.privateLookupIn(VanillaLiving.class, MethodHandles.lookup());
        var vanillaDie = lookup.findSpecial(
                VanillaLiving.class,
                "die",
                MethodType.methodType(void.class),
                VanillaLiving.class
        );
        vanillaDie.invoke(boss);

        assertTrue(boss.vanillaDeath());
    }

    private static class VanillaLiving {
        private boolean vanillaDeath;

        public void die() {
            vanillaDeath = true;
        }

        boolean vanillaDeath() {
            return vanillaDeath;
        }
    }

    private static final class RestoringBoss extends VanillaLiving {
        @Override
        public void die() {
            // Simulates a custom boss restoring its protected health and returning before super.
        }
    }
}
