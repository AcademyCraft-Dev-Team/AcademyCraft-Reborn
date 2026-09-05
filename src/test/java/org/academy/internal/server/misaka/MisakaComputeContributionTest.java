package org.academy.internal.server.misaka;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MisakaComputeContributionTest {
    @Test
    void privilegeShareUses75PercentOfMskAsRecoveryPerSecond() {
        float msk = MisakaComputeContribution.mskPerSecond(100);
        assertEquals(75f, 0.75f * msk * MisakaComputeContribution.CP_PER_MSK, 0.001f);
        msk = MisakaComputeContribution.mskPerSecond(110);
        assertEquals(90f, 0.75f * msk * MisakaComputeContribution.CP_PER_MSK, 0.001f);
        msk = MisakaComputeContribution.mskPerSecond(200);
        assertEquals(300f, 0.75f * msk * MisakaComputeContribution.CP_PER_MSK, 0.001f);
    }
}
