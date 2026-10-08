package org.academy.api.common.vfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectScalarTransitionTest {
    @Test void rapidCloseAndReopenContinueFromTheCurrentEnvelope() {
        var transition = new EffectScalarTransition(0);
        assertEquals(0, transition.update(1, 0, .3f));
        assertEquals(1, transition.update(1, .3f, .3f));
        assertEquals(1, transition.update(0, .4f, .3f));
        float closing = transition.update(0, .52f, .3f);
        assertTrue(closing > 0 && closing < 1);
        assertEquals(closing, transition.update(1, .52f, .3f), .000001f);
        assertTrue(transition.update(1, .60f, .3f) > closing);
        assertEquals(1, transition.update(1, .83f, .3f));
    }

    @Test void reversingDuringTheInitialOpeningDoesNotJumpToFullSpread() {
        var transition = new EffectScalarTransition(0);
        transition.update(1, 0, .3f);
        float opening = transition.update(1, .12f, .3f);
        assertEquals(opening, transition.update(0, .12f, .3f), .000001f);
        assertTrue(transition.update(0, .18f, .3f) < opening);
        assertEquals(0, transition.update(0, .43f, .3f));
    }

    @Test void changingEditorDurationIsContinuousAndPausedFramesStayIdentical() {
        var transition = new EffectScalarTransition(0);
        transition.update(1, 0, .3f);
        float before = transition.update(1, .15f, .3f);
        assertEquals(before, transition.update(1, .15f, .6f), .000001f);
        assertEquals(before, transition.update(1, .15f, .6f), .000001f);
        assertEquals(1, transition.update(1, .76f, .6f));
    }
}
