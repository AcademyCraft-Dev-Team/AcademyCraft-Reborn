package org.academy.internal.common.ability.darkmatter.skills.lv1;

import io.netty.buffer.Unpooled;
import org.academy.api.common.ability.darkmatter.DarkmatterModifiers;
import org.academy.api.common.ability.darkmatter.DarkmatterBlockProfile;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingRegistries;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkmatterShapingTest {
    @Test
    void shapingNeverBypassesIntegrityRepairContract() {
        assertFalse(DarkmatterShaping.Server.repairsOnEnchant(0));
        assertFalse(DarkmatterShaping.Server.repairsOnEnchant(3));
        assertFalse(DarkmatterShaping.Server.unlocksAutoRepair(3));
    }

    @Test
    void omnitoolMatchesAllSixLevelAnchors() {
        assertEquals(2, DarkmatterShaping.Server.toolEfficiency(1));
        assertEquals(1, DarkmatterShaping.Server.toolFortune(1));
        assertEquals(5, DarkmatterShaping.Server.toolEfficiency(3));
        assertEquals(3, DarkmatterShaping.Server.toolFortune(3));
        assertEquals(10, DarkmatterShaping.Server.toolEfficiency(5));
        assertEquals(5, DarkmatterShaping.Server.toolFortune(5));
    }

    @Test
    void efficiencyCurveIsMonotoneAndM1DiscountsAllUses() {
        var previous = -1;
        for (var point = 0; point <= 250; point++) {
            var value = DarkmatterShaping.Server.toolEfficiency(point / 50.0f);
            org.junit.jupiter.api.Assertions.assertTrue(value >= previous);
            previous = value;
        }
        assertEquals(3.6f, DarkmatterShaping.Server.shapingCost(4.0f, 1), 0.0001f);
        assertEquals(1.0f, DarkmatterShaping.Server.gammaShapingMultiplier(2), 0.0001f);
        assertEquals(1.25f, DarkmatterShaping.Server.gammaShapingMultiplier(3), 0.0001f);
    }

    @Test
    void spearAlphaAndBetaAxesProduceIndependentServerValues() {
        assertEquals(7.0f, DarkmatterShaping.Server.spearDamage(1.0f), 0.0001f);
        assertEquals(11.0f, DarkmatterShaping.Server.spearDamage(3.0f), 0.0001f);
        assertEquals(15.0f, DarkmatterShaping.Server.spearDamage(5.0f), 0.0001f);
        assertEquals(10.0f, DarkmatterShaping.Server.spearRange(1.0f), 0.0001f);
        assertEquals(2.5f, DarkmatterShaping.Server.spearSpeed(5.0f), 0.0001f);
        assertEquals(0.50f, DarkmatterShaping.Server.spearPenetration(5.0f), 0.0001f);
    }

    @Test
    void parameterPreviewFunctionsMatchCombatAnchors() {
        assertEquals(8.0f, DarkmatterShaping.Server.directDamage(
                DarkmatterShape.TOOL, 1.0f), 0.0001f);
        assertEquals(13.0f, DarkmatterShaping.Server.directDamage(
                DarkmatterShape.SWORD, 3.0f), 0.0001f);
        assertEquals(10.0f, DarkmatterShaping.Server.phaseDamageBonus(5.0f), 0.0001f);
        assertEquals(0.40f, DarkmatterShaping.Server.penetration(
                DarkmatterShape.BOW, 5.0f), 0.0001f);
        assertEquals(0.50f, DarkmatterShaping.Server.penetration(
                DarkmatterShape.TRIDENT, 5.0f), 0.0001f);
        assertEquals(0.20f, DarkmatterShaping.Server.armorReduction(5.0f), 0.0001f);
        assertEquals(70, DarkmatterShaping.Server.armorWeaknessTicks(5.0f));
    }

    @Test
    void ordinaryEnchantedToolsReceiveRuntimeEfficiencyWithoutStoredVanillaLevels() {
        assertEquals(0.0f, DarkmatterShaping.Server.miningSpeedBonus(0.0f), 0.0001f);
        assertEquals(5.0f, DarkmatterShaping.Server.miningSpeedBonus(1.0f), 0.0001f);
        assertEquals(26.0f, DarkmatterShaping.Server.miningSpeedBonus(3.0f), 0.0001f);
        assertEquals(101.0f, DarkmatterShaping.Server.miningSpeedBonus(5.0f), 0.0001f);
    }

    @Test
    void modifierValidationRejectsUnknownIncompatibleConflictingAndOverBudgetProfiles() {
        var valid = DarkmatterShaping.Server.validateModifiers(
                DarkmatterShape.TOOL,
                Map.of(DarkmatterModifiers.HARVEST, 2, DarkmatterModifiers.MAGNETIC, 1), 3, 0);
        assertTrue(valid.valid());
        assertEquals(3, valid.usedPoints());
        assertEquals(8, valid.budget());

        assertFalse(DarkmatterShaping.Server.validateModifiers(
                DarkmatterShape.ARMOR, Map.of(DarkmatterModifiers.HARVEST, 1), 3, 0).valid());
        assertFalse(DarkmatterShaping.Server.validateModifiers(
                DarkmatterShape.SWORD,
                Map.of(DarkmatterModifiers.PULL, 1, DarkmatterModifiers.KNOCKBACK, 1), 3, 0).valid());
        assertFalse(DarkmatterShaping.Server.validateModifiers(
                DarkmatterShape.SWORD, Map.of("missing_extension", 1), 3, 0).valid());
        assertFalse(DarkmatterShaping.Server.validateModifiers(
                DarkmatterShape.SWORD, Map.of(DarkmatterModifiers.EXPLOSIVE, 3), 1, 0).valid());

        assertTrue(DarkmatterShaping.Server.validateModifiers(
                DarkmatterShape.COATING,
                Map.of(DarkmatterModifiers.HARVEST, 1,
                        DarkmatterModifiers.TELEPORT_PROTECTION, 1), 5, 0).valid());
        assertFalse(DarkmatterShaping.Server.validateModifiers(
                DarkmatterShape.BLOCK,
                Map.of(DarkmatterModifiers.STRUCTURAL_GUARD, 1), 3, 0).valid());
    }

    @Test
    void shapesUseTheConfiguredAbilityLevelGates() {
        assertEquals(1, DarkmatterShape.TOOL.requiredAbilityLevel());
        assertEquals(1, DarkmatterShape.SWORD.requiredAbilityLevel());
        assertEquals(1, DarkmatterShape.BLOCK.requiredAbilityLevel());
        assertEquals(3, DarkmatterShape.SPEAR.requiredAbilityLevel());
        assertEquals(3, DarkmatterShape.TRIDENT.requiredAbilityLevel());
        assertEquals(4, DarkmatterShape.BOW.requiredAbilityLevel());
        assertEquals(4, DarkmatterShape.CROSSBOW.requiredAbilityLevel());
        assertEquals(4, DarkmatterShape.ARROW.requiredAbilityLevel());
        assertEquals(4, DarkmatterShape.ARMOR.requiredAbilityLevel());
        assertEquals(5, DarkmatterShape.COATING.requiredAbilityLevel());
        assertEquals(5, DarkmatterShape.MACE.requiredAbilityLevel());

        for (var shape : DarkmatterShape.values()) {
            var below = Math.max(1, shape.requiredAbilityLevel() - 1);
            if (below < shape.requiredAbilityLevel()) {
                var result = DarkmatterShaping.Server.validateModifiers(
                        shape, Map.of(), below, 0);
                assertFalse(result.valid(), shape.id());
                assertTrue(result.hasLevelGateError(), shape.id());
            }
            assertTrue(DarkmatterShaping.Server.validateModifiers(
                    shape, Map.of(), shape.requiredAbilityLevel(), 0).valid(), shape.id());
        }
    }

    @Test
    void modifiersUseTheConfiguredAbilityLevelGates() {
        DarkmatterModifiers.bootstrap();
        assertModifierLevel(2, DarkmatterModifiers.HOLY, DarkmatterModifiers.DISMEMBER,
                DarkmatterModifiers.SLAUGHTER, DarkmatterModifiers.DRYING,
                DarkmatterModifiers.EXTINGUISH);
        assertModifierLevel(3, DarkmatterModifiers.LUCKY, DarkmatterModifiers.PULL,
                DarkmatterModifiers.KNOCKBACK, DarkmatterModifiers.ANTIGRAVITY);
        assertModifierLevel(4, DarkmatterModifiers.TELEPORT_SUPPRESSION,
                DarkmatterModifiers.TELEPORT_PROTECTION, DarkmatterModifiers.EXPLOSIVE,
                DarkmatterModifiers.FREEZING, DarkmatterModifiers.BURNING,
                DarkmatterModifiers.LIGHTNING, DarkmatterModifiers.REACH);
        assertModifierLevel(5, DarkmatterModifiers.ECHO, DarkmatterModifiers.LAW_EROSION,
                DarkmatterModifiers.FEATHER_PURSUIT, DarkmatterModifiers.STRUCTURAL_GUARD);
        assertModifierLevel(1, DarkmatterModifiers.SHEAR, DarkmatterModifiers.HARVEST,
                DarkmatterModifiers.TILL, DarkmatterModifiers.MAGNETIC,
                DarkmatterModifiers.EDIBLE);

        var locked = DarkmatterShaping.Server.validateModifiers(DarkmatterShape.TOOL,
                Map.of(DarkmatterModifiers.EXPLOSIVE, 1), 3, 3);
        assertFalse(locked.valid());
        assertTrue(locked.hasLevelGateError());
        assertTrue(DarkmatterShaping.Server.validateModifiers(DarkmatterShape.TOOL,
                Map.of(DarkmatterModifiers.EXPLOSIVE, 1), 4, 3).valid());
    }

    private static void assertModifierLevel(int expected, String... ids) {
        for (var id : ids) assertEquals(expected, DarkmatterShapingRegistries.modifier(id)
                .orElseThrow().requiredAbilityLevel(), id);
    }

    @Test
    void materialRequestsOnlyAcceptStorageHotbarAndOffhandIndices() {
        assertTrue(DarkmatterShaping.Server.isMaterialInventorySlot(0));
        assertTrue(DarkmatterShaping.Server.isMaterialInventorySlot(35));
        assertTrue(DarkmatterShaping.Server.isMaterialInventorySlot(40));
        assertFalse(DarkmatterShaping.Server.isMaterialInventorySlot(-1));
        assertFalse(DarkmatterShaping.Server.isMaterialInventorySlot(36));
        assertFalse(DarkmatterShaping.Server.isMaterialInventorySlot(39));
        assertFalse(DarkmatterShaping.Server.isMaterialInventorySlot(41));
    }

    @Test
    void serverResultPacketRoundTripsEveryOutcome() {
        for (var result : DarkmatterShaping.Result.values()) {
            var buffer = Unpooled.buffer();
            DarkmatterShaping.ResultPacket.CODEC.encode(
                    buffer, new DarkmatterShaping.ResultPacket(result));
            assertEquals(result,
                    DarkmatterShaping.ResultPacket.CODEC.decode(buffer).result());
        }
    }

    @Test
    void blockShapingPacketRoundTripsItsPhysicalProfile() {
        var expectedProfile = new DarkmatterBlockProfile(12.5f, 240.0f, true);
        var expected = DarkmatterShaping.CastPacket.shape(
                DarkmatterShape.BLOCK, 35, Map.of(), expectedProfile);
        var buffer = Unpooled.buffer();
        DarkmatterShaping.CastPacket.CODEC.encode(buffer, expected);
        var decoded = DarkmatterShaping.CastPacket.CODEC.decode(buffer);

        assertEquals(DarkmatterShape.BLOCK, decoded.shape());
        assertEquals(35, decoded.alphaPercent());
        assertEquals(expectedProfile, decoded.blockProfile());
    }
}
