package org.academy.internal.common.ability.electromaster.skills.lv5;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RailgunTest {
    @Test
    void vanillaFallbackAcceptsIronAmmunitionTiers() {
        assertTrue(Railgun.isVanillaAmmoId(Identifier.withDefaultNamespace("iron_ingot")));
        assertTrue(Railgun.isVanillaAmmoId(Identifier.withDefaultNamespace("iron_block")));
        assertTrue(Railgun.isVanillaAmmoId(Identifier.withDefaultNamespace("anvil")));
        assertTrue(Railgun.isVanillaAmmoId(Identifier.withDefaultNamespace("chipped_anvil")));
        assertTrue(Railgun.isVanillaAmmoId(Identifier.withDefaultNamespace("damaged_anvil")));
        assertFalse(Railgun.isVanillaAmmoId(Identifier.withDefaultNamespace("stick")));
    }

    @Test
    void ammunitionProfilesScaleChargeBeamRangeAndDamage() {
        assertEquals(0, Railgun.AmmoKind.COIN.minimumChargeTicks());
        assertEquals(1.0f, Railgun.AmmoKind.COIN.beamWidthMultiplier());
        assertEquals(50.0f, Railgun.AmmoKind.COIN.beamLength());
        assertEquals(0.8f, Railgun.AmmoKind.COIN.damageMultiplier());

        assertEquals(10, Railgun.AmmoKind.IRON_INGOT.minimumChargeTicks());
        assertEquals(1.5f, Railgun.AmmoKind.IRON_INGOT.beamWidthMultiplier());
        assertEquals(58.0f, Railgun.AmmoKind.IRON_INGOT.beamLength());
        assertEquals(1.0f, Railgun.AmmoKind.IRON_INGOT.damageMultiplier());

        assertEquals(20, Railgun.AmmoKind.IRON_BLOCK.minimumChargeTicks());
        assertEquals(2.0f, Railgun.AmmoKind.IRON_BLOCK.beamWidthMultiplier());
        assertEquals(66.0f, Railgun.AmmoKind.IRON_BLOCK.beamLength());
        assertEquals(1.5f, Railgun.AmmoKind.IRON_BLOCK.damageMultiplier());

        assertEquals(30, Railgun.AmmoKind.ANVIL.minimumChargeTicks());
        assertEquals(2.5f, Railgun.AmmoKind.ANVIL.beamWidthMultiplier());
        assertEquals(74.0f, Railgun.AmmoKind.ANVIL.beamLength());
        assertEquals(2.0f, Railgun.AmmoKind.ANVIL.damageMultiplier());
    }

    @Test
    void damageKeepsTheReferenceBaseAndUsesPlayerScaling() {
        assertEquals(150.0f, Railgun.calculateDamage(1.0f, 1.0f));
        assertEquals(337.5f, Railgun.calculateDamage(1.5f, 1.5f));
        assertEquals(0.0f, Railgun.calculateDamage(-1.0f, 1.0f));
    }
}
