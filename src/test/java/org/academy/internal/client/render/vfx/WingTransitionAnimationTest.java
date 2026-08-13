package org.academy.internal.client.render.vfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WingTransitionAnimationTest {
    @Test
    void startsAsBlackWingAndSettlesAsWhiteWing() {
        var start = WingTransitionAnimation.sample(0.0);
        assertEquals(1.0f, start.blackWing().radialScale(), 0.0001f);
        assertFalse(start.whiteWing().visible());

        var end = WingTransitionAnimation.sample(WingTransitionAnimation.DURATION_TICKS);
        assertFalse(end.blackWing().visible());
        assertEquals(1.0f, end.whiteWing().radialScale(), 0.0001f);
        assertEquals(30.0f, end.whiteWing().spreadDegrees(), 0.0001f);
    }

    @Test
    void ascensionFlashBridgesCollapseAndExpansion() {
        var collapse = WingTransitionAnimation.sample(WingTransitionAnimation.DURATION_TICKS * 0.41);
        assertTrue(collapse.blackWing().visible());
        assertTrue(collapse.whiteWing().visible());
        assertTrue(collapse.ascension().visible());

        var burst = WingTransitionAnimation.sample(WingTransitionAnimation.DURATION_TICKS * 0.70);
        assertFalse(burst.blackWing().visible());
        assertTrue(burst.whiteWing().radialScale() > 1.0f);
        assertTrue(burst.ascension().visible());
    }

    @Test
    void lifetimeExcludesCompletedTransition() {
        assertFalse(WingTransitionAnimation.isActive(-0.01));
        assertTrue(WingTransitionAnimation.isActive(WingTransitionAnimation.DURATION_TICKS - 0.01));
        assertFalse(WingTransitionAnimation.isActive(WingTransitionAnimation.DURATION_TICKS));
    }
}
