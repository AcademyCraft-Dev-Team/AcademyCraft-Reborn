package org.academy.internal.common.entitycontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMotionPolicyTest {
    @Test
    void imprisonmentBlocksEvenSelfSourcedMotion() {
        assertTrue(EntityMotionPolicy.shouldBlock(
                false, true, true, true, true, true
        ));
    }

    @Test
    void internalCorrectionCanRestoreAnImprisonedEntity() {
        assertFalse(EntityMotionPolicy.shouldBlock(
                true, true, true, true, false, false
        ));
    }

    @Test
    void forcedMovementProtectionUsesTheExplicitAbilitySource() {
        assertFalse(EntityMotionPolicy.shouldBlock(
                false, false, true, true, true, false
        ));
        assertTrue(EntityMotionPolicy.shouldBlock(
                false, false, true, true, false, true
        ));
    }

    @Test
    void vanillaSelfMovementFallbackRemainsAllowed() {
        assertFalse(EntityMotionPolicy.shouldBlock(
                false, false, true, false, false, true
        ));
        assertTrue(EntityMotionPolicy.shouldBlock(
                false, false, true, false, false, false
        ));
    }

    @Test
    void unprotectedEntitiesRemainUnaffected() {
        assertFalse(EntityMotionPolicy.shouldBlock(
                false, false, false, true, false, false
        ));
    }

    @Test
    void forcedMovementProtectionBlocksExternalEquipmentManipulation() {
        assertTrue(EntityMotionPolicy.shouldBlockExternalManipulation(true, false));
        assertFalse(EntityMotionPolicy.shouldBlockExternalManipulation(true, true));
        assertFalse(EntityMotionPolicy.shouldBlockExternalManipulation(false, false));
    }
}
