package org.academy.internal.client.definitions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WingFlightAnimationsTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void convertsGeckoLibRotationAxesToMinecraftCoordinates() {
        var fastRootRotation = WingFlightAnimations.FLYING_FAST
                .boneAnimations().get("root").get(1).keyframes()[0].postTarget();
        assertEquals((float) Math.toRadians(72.5f), fastRootRotation.x(), EPSILON);
        var fastHeadRotation = WingFlightAnimations.FLYING_FAST
                .boneAnimations().get("head").get(1).keyframes()[0].postTarget();
        assertEquals((float) Math.toRadians(-65.0f), fastHeadRotation.x(), EPSILON);

        var slowStartArmRotation = WingFlightAnimations.START_FLYING_SLOW
                .boneAnimations().get("right_arm").get(1).keyframes()[1].postTarget();
        assertEquals((float) Math.toRadians(2.3518f), slowStartArmRotation.y(), EPSILON);
        assertEquals((float) Math.toRadians(24.8632f), slowStartArmRotation.z(), EPSILON);
    }
}
