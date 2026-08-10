package org.academy.internal.common.entitycontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityMotionGuardTest {
    @Test
    void imprisonmentEffectLastsLongEnoughForClientConfirmation() {
        assertEquals(10, EntityMotionGuard.visibleEffectDuration(2));
    }

    @Test
    void longerShackleDurationIsPreserved() {
        assertEquals(100, EntityMotionGuard.visibleEffectDuration(100));
    }
}
