package org.academy.internal.common.ability.darkmatter.skills.lv2;

import org.academy.internal.common.ability.darkmatter.DarkmatterStateData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkmatterPhaseTuningTest {
    @Test
    void eachMilestoneAddsAnotherQuarterToTuningSpeed() {
        assertEquals(0.0100f, DarkmatterPhaseTuning.Server.phaseStep(0), 0.0001f);
        assertEquals(0.0125f, DarkmatterPhaseTuning.Server.phaseStep(1), 0.0001f);
        assertEquals(0.0150f, DarkmatterPhaseTuning.Server.phaseStep(2), 0.0001f);
        assertEquals(0.0175f, DarkmatterPhaseTuning.Server.phaseStep(3), 0.0001f);
    }

    @Test
    void pointRateKeepsTheBaseExtremeToExtremeTimeAtTenSecondsForEveryLevel() {
        assertEquals(0.25f, DarkmatterPhaseTuning.Server.phasePointStep(50, 0), 0.0001f);
        assertEquals(0.75f, DarkmatterPhaseTuning.Server.phasePointStep(150, 0), 0.0001f);
        assertEquals(1.25f, DarkmatterPhaseTuning.Server.phasePointStep(250, 0), 0.0001f);
        assertEquals(250.0f,
                DarkmatterPhaseTuning.Server.phasePointDelta(250, 0, 200), 0.0001f);
    }

    @Test
    void elapsedServerTicksAreAppliedOnceAndDuplicateTicksApplyNothing() {
        assertEquals(0.0f,
                DarkmatterPhaseTuning.Server.phasePointDelta(150, 0, 0), 0.0001f);
        assertEquals(7.5f,
                DarkmatterPhaseTuning.Server.phasePointDelta(150, 0, 10), 0.0001f);
    }

    @Test
    void milestoneTimesMatchEightSixPointSevenAndFivePointSevenSeconds() {
        var total = 250;
        assertEquals(total,
                DarkmatterPhaseTuning.Server.phasePointDelta(total, 1, 160), 0.0001f);
        assertTrue(DarkmatterPhaseTuning.Server.phasePointDelta(total, 2, 133) < total);
        assertTrue(DarkmatterPhaseTuning.Server.phasePointDelta(total, 2, 134) >= total);
        assertTrue(DarkmatterPhaseTuning.Server.phasePointDelta(total, 3, 114) < total);
        assertTrue(DarkmatterPhaseTuning.Server.phasePointDelta(total, 3, 115) >= total);
    }

    @Test
    void fractionalPointAccumulatorReachesTheExactExtremeWithoutLevelDrift() {
        var state = new DarkmatterStateData();
        state.setAlphaPoints(3, 0);
        var step = DarkmatterPhaseTuning.Server.phasePointStep(150, 0);
        for (var tick = 0; tick < 200; tick++) state.tuneAlphaPoints(3, step);
        assertEquals(150, state.getAlphaPoints(3));
    }
}
