package org.academy.api.common.vfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectVisibilityTest {
    @Test void observerNearFarEndpointIsIncluded() {
        assertEquals(4, EffectVisibility.distanceToSegmentSquared(99, 2, 0, 0, 0, 0, 100, 0, 0), 1e-9);
    }
    @Test void degenerateSegmentAndBehindEndpointAreFinite() {
        assertEquals(25, EffectVisibility.distanceToSegmentSquared(3, 4, 0, 0, 0, 0, 0, 0, 0), 1e-9);
        assertEquals(100, EffectVisibility.distanceToSegmentSquared(-10, 0, 0, 0, 0, 0, 100, 0, 0), 1e-9);
    }
}
