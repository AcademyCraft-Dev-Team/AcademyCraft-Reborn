package org.academy.internal.common.ability.electromaster.skills.lv5;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RailgunTest {
    @Test
    void vanillaFallbackAcceptsOnlyIronIngotAndBlock() {
        assertTrue(Railgun.isVanillaAmmoId(Identifier.withDefaultNamespace("iron_ingot")));
        assertTrue(Railgun.isVanillaAmmoId(Identifier.withDefaultNamespace("iron_block")));
        assertFalse(Railgun.isVanillaAmmoId(Identifier.withDefaultNamespace("stick")));
    }

    @Test
    void damageKeepsTheReferenceBaseAndUsesPlayerScaling() {
        assertEquals(150.0f, Railgun.calculateDamage(1.0f, 1.0f));
        assertEquals(337.5f, Railgun.calculateDamage(1.5f, 1.5f));
        assertEquals(0.0f, Railgun.calculateDamage(-1.0f, 1.0f));
    }
}
