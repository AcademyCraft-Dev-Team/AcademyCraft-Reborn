package org.academy.internal.common.ability.darkmatter.skills.lv1;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterGenerationTest {
    @Test
    void serverHoldDurationStartsAtOneTenthOfBaseCapacityPerSecond() {
        assertEquals(0.0f, DarkmatterGeneration.unitsForDuration(108.0f, 0, 0, 0, 0));
        assertEquals(0.54f, DarkmatterGeneration.unitsForDuration(108.0f, 1, 0, 0, 0), 0.0001f);
        assertEquals(10.8f, DarkmatterGeneration.unitsForDuration(108.0f, 20, 0, 0, 0), 0.0001f);
    }

    @Test
    void alphaBetaAndGammaPowersContinuouslyScaleHoldSpeed() {
        assertEquals(12.42f, DarkmatterGeneration.unitsForDuration(
                108.0f, 20, 1.0f, 0.0f, 0), 0.0001f);
        assertEquals(15.354225f, DarkmatterGeneration.unitsForDuration(
                108.0f, 20, 0.5f, 1.0f, 3), 0.0001f);
    }

    @Test
    void levelOneThreeAndFivePhaseExtremesProduceDistinctRates() {
        assertEquals(12.42f, DarkmatterGeneration.unitsForDuration(
                108.0f, 20, 1.0f, 0.0f, 0), 0.0001f);
        assertEquals(15.66f, DarkmatterGeneration.unitsForDuration(
                108.0f, 20, 3.0f, 0.0f, 0), 0.0001f);
        assertEquals(18.9f, DarkmatterGeneration.unitsForDuration(
                108.0f, 20, 5.0f, 0.0f, 0), 0.0001f);
    }

    @Test
    void proficiencyAndGammaReduceCreatedMatterCpCost() {
        assertEquals(2.0f, DarkmatterGeneration.cpPerCreatedMatter(0.0f, 0));
        assertEquals(1.5f, DarkmatterGeneration.cpPerCreatedMatter(5.0f, 0));
        assertEquals(1.8f, DarkmatterGeneration.cpPerCreatedMatter(0.0f, 2));
        assertEquals(1.05f, DarkmatterGeneration.cpPerCreatedMatter(5.0f, 3), 0.0001f);
    }

    @Test
    void sixWingsM3OnlyAmplifiesGammaRateAndCostCoefficients() {
        var ordinary = DarkmatterGeneration.unitsForDuration(
                108.0f, 20, 0.0f, 1.0f, 3, 1.0f);
        var masteredSixWings = DarkmatterGeneration.unitsForDuration(
                108.0f, 20, 0.0f, 1.0f, 3, 1.2f);
        Assertions.assertTrue(masteredSixWings > ordinary);
        assertEquals(1.0f,
                DarkmatterGeneration.cpPerCreatedMatter(5.0f, 3, 1.2f), 0.0001f);
    }
}
