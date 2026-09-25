package org.academy.internal.common.ability.darkmatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterStateDataTest {
    @Test
    void newStateStartsBalancedWithLevelScaledRealPoints() {
        var data = new DarkmatterStateData();
        var levelOne = data.phaseSnapshot(1, false);
        assertEquals(50, levelOne.totalPoints());
        assertEquals(25, levelOne.alphaPoints());
        assertEquals(25, levelOne.betaPoints());
        assertEquals(50, levelOne.gammaPoints());
        assertEquals(0.5f, levelOne.alphaPower());
        assertEquals(0.0f, levelOne.activeGammaPower());
    }

    @Test
    void levelChangesPreserveAllocationRatio() {
        var data = new DarkmatterStateData();
        assertTrue(data.setAlphaPoints(1, 40));
        var levelFive = data.phaseSnapshot(5, true);
        assertEquals(250, levelFive.totalPoints());
        assertEquals(200, levelFive.alphaPoints());
        assertEquals(50, levelFive.betaPoints());
        assertEquals(5.0f, levelFive.activeGammaPower());
        assertEquals(250, levelFive.gammaPoints());
    }

    @Test
    void fractionalTuningAccumulatesIntoIntegralPoints() {
        var data = new DarkmatterStateData();
        data.phaseSnapshot(1, false);
        assertFalse(data.tuneAlphaPoints(1, 0.25f));
        assertFalse(data.tuneAlphaPoints(1, 0.25f));
        assertFalse(data.tuneAlphaPoints(1, 0.25f));
        assertTrue(data.tuneAlphaPoints(1, 0.25f));
        assertEquals(26, data.getAlphaPoints(1));
    }
}
