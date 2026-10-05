
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import org.academy.internal.common.ability.accelerator.Accelerator;

class WingTransitionAnimationTest {
    @Test
    void startsAsBlackWingAndSettlesAsWhiteWing() {
        var start = Accelerator.Client.WingTransitionAnimation.sample(0.0);
        assertEquals(1.0f, start.blackWing().radialScale(), 0.0001f);
        assertFalse(start.whiteWing().visible());

        var end = Accelerator.Client.WingTransitionAnimation.sample(Accelerator.Client.WingTransitionAnimation.DURATION_TICKS);
        assertFalse(end.blackWing().visible());
        assertEquals(1.0f, end.whiteWing().radialScale(), 0.0001f);
        assertEquals(30.0f, end.whiteWing().spreadDegrees(), 0.0001f);
    }

    @Test
    void ascensionFlashBridgesCollapseAndExpansion() {
        var collapse = Accelerator.Client.WingTransitionAnimation.sample(Accelerator.Client.WingTransitionAnimation.DURATION_TICKS * 0.41);
        assertTrue(collapse.blackWing().visible());
        assertTrue(collapse.whiteWing().visible());
        assertTrue(collapse.ascension().visible());

        var burst = Accelerator.Client.WingTransitionAnimation.sample(Accelerator.Client.WingTransitionAnimation.DURATION_TICKS * 0.70);
        assertFalse(burst.blackWing().visible());
        assertTrue(burst.whiteWing().radialScale() > 1.0f);
        assertTrue(burst.ascension().visible());
    }

    @Test
    void lifetimeExcludesCompletedTransition() {
        assertFalse(Accelerator.Client.WingTransitionAnimation.isActive(-0.01));
        assertTrue(Accelerator.Client.WingTransitionAnimation.isActive(Accelerator.Client.WingTransitionAnimation.DURATION_TICKS - 0.01));
        assertFalse(Accelerator.Client.WingTransitionAnimation.isActive(Accelerator.Client.WingTransitionAnimation.DURATION_TICKS));
    }
}
