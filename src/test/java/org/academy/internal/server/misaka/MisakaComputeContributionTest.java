package org.academy.internal.server.misaka;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MisakaComputeContributionTest {
    @Test
    void mskCurveAnchors() {
        assertEquals(100f, MisakaComputeContribution.mskPerSecond(100), 0.001f);
        assertEquals(120f, MisakaComputeContribution.mskPerSecond(110), 0.001f);
        assertEquals(400f, MisakaComputeContribution.mskPerSecond(200), 0.001f);
    }

    @Test
    void defaultCpPerMskIsTwo() {
        assertEquals(2.0f, MisakaComputeContribution.CP_PER_MSK, 0.001f);
    }

    @Test
    void personalShareCapIsSeventyFivePercentOfMskAsCpAtDefaultRatio() {
        float msk = MisakaComputeContribution.mskPerSecond(100);
        assertEquals(150f, 0.75f * msk * MisakaComputeContribution.CP_PER_MSK, 0.001f);
    }
}
