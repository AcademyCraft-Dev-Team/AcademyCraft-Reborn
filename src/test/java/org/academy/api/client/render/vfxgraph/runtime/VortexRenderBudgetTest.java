package org.academy.api.client.render.vfxgraph.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VortexRenderBudgetTest {
    @Test void sixteenSimultaneousFourfoldsKeepHeroAndStayWithinSharedLimits() {
        var budget = new VortexRenderBudget();
        budget.beginFrame(16);
        assertEquals(0, budget.allocate(0, true));
        int admitted = 1;
        for (int i = 1; i < 16; i++) if (budget.allocate(3, true) >= 0) admitted++;
        assertEquals(16, admitted);
        assertTrue(budget.vertices() <= VortexRenderBudget.MAX_VERTICES);
        assertTrue(budget.bytes() <= VortexRenderBudget.MAX_UPLOAD_BYTES);
        for (int i = 0; i < 1000; i++) budget.allocate(1, true);
        assertTrue(budget.vertices() <= VortexRenderBudget.MAX_VERTICES);
        assertTrue(budget.bytes() <= VortexRenderBudget.MAX_UPLOAD_BYTES);
        budget.beginFrame(); assertEquals(0, budget.vertices()); assertEquals(0, budget.bytes());
    }
    @Test void distanceHysteresisAvoidsOscillatingAtBoundary() {
        assertEquals(1, VortexRenderBudget.preferred(17, 1));
        assertEquals(2, VortexRenderBudget.preferred(19, 1));
        assertEquals(2, VortexRenderBudget.preferred(15, 2));
        assertEquals(1, VortexRenderBudget.preferred(13, 2));
    }
}
