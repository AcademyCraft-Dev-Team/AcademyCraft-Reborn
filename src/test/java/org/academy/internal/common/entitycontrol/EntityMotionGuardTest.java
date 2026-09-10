package org.academy.internal.common.entitycontrol;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMotionGuardTest {
    @Test
    void imprisonmentEffectLastsLongEnoughForClientConfirmation() {
        assertEquals(10, EntityMotionGuard.visibleEffectDuration(2));
    }

    @Test
    void longerShackleDurationIsPreserved() {
        assertEquals(100, EntityMotionGuard.visibleEffectDuration(100));
    }

    @Test
    void mergedMixinMotionGuardsAreIgnoredDuringSourceDetection() {
        assertTrue(EntityMotionGuard.isGuardInfrastructure(
                Entity.class,
                "academy$guardSimpleTeleport"
        ));
        assertTrue(EntityMotionGuard.isGuardInfrastructure(
                ServerPlayer.class,
                "academy$guardSimplePlayerTeleport"
        ));
    }

    @Test
    void unrelatedMethodsRemainVisibleDuringSourceDetection() {
        assertFalse(EntityMotionGuard.isGuardInfrastructure(Entity.class, "tick"));
        assertFalse(EntityMotionGuard.isGuardInfrastructure(
                EntityMotionGuardTest.class,
                "academy$guardSimpleTeleport"
        ));
    }
}
