package org.academy.internal.client.render.vfx;

import org.junit.jupiter.api.Test;
import org.academy.api.common.vfx.RepairVisualParts;

import static org.junit.jupiter.api.Assertions.*;

class RepairVisualTimelineTest {
    @Test void fixedPatchIdentitySurvivesOtherSitesJoiningAndRespectsMainArm() {
        assertEquals(1 << 2, RepairVisualTimeline.patchMask(RepairVisualParts.MAIN_HAND, true));
        assertEquals(1 << 3, RepairVisualTimeline.patchMask(RepairVisualParts.MAIN_HAND, false));
        assertEquals((1 << 1) | (1 << 2), RepairVisualTimeline.patchMask(RepairVisualParts.HEAD | RepairVisualParts.MAIN_HAND, true));
        assertEquals(0, RepairVisualTimeline.patchMask(0, true));
        assertEquals(1 | (1 << 2) | (1 << 3), RepairVisualTimeline.patchMask(RepairVisualParts.BODY, true));
    }

    @Test void repeatedSuccessfulPulsesHoldTheSameBridgeWithoutRestartingGrowth() {
        var timeline = new RepairVisualTimeline();
        timeline.pulse(1 << 2, 100);
        float before = timeline.progress(2, 105);
        timeline.pulse(1 << 2, 105);
        assertEquals(before, timeline.progress(2, 105), 1e-6f);
        timeline.pulse(1 << 2, 110);
        assertEquals(RepairVisualTimeline.BRIDGE_PROGRESS, timeline.progress(2, 114), 1e-6f);
        assertTrue(timeline.active(2, 114));
        assertEquals(1, timeline.progress(2, 117.6), 1e-6f);
        assertFalse(timeline.active(2, 118));
    }

    @Test void newlyRepairedSiteDoesNotRenewAnOldSite() {
        var timeline = new RepairVisualTimeline();
        timeline.pulse(1 << 0, 100);
        timeline.pulse(1 << 1, 105);
        assertFalse(timeline.active(0, 108));
        assertTrue(timeline.active(1, 108));
        assertEquals(1, timeline.progress(0, 108));
        assertEquals(3 / RepairVisualTimeline.DURATION_TICKS, timeline.progress(1, 108), 1e-6f);
    }

    @Test void aLateContinuationSmoothlyRejoinsTheBridgeWithoutReplayingGrowth() {
        var timeline = new RepairVisualTimeline();
        timeline.pulse(1, 100);
        float before = timeline.progress(0, 106);
        timeline.pulse(1, 106);
        assertEquals(before, timeline.progress(0, 106), 1e-6f);
        assertTrue(timeline.progress(0, 106.5) < before);
        assertTrue(timeline.progress(0, 106.5) > RepairVisualTimeline.BRIDGE_PROGRESS);
        assertEquals(RepairVisualTimeline.BRIDGE_PROGRESS, timeline.progress(0, 107), 1e-6f);
        assertEquals(RepairVisualTimeline.BRIDGE_PROGRESS, timeline.progress(0, 110), 1e-6f);
    }

    @Test void alternatingFiveAndSixTickArrivalsDoNotAccumulateIntoAnInvisiblePlateau() {
        var timeline = new RepairVisualTimeline();
        int body = RepairVisualTimeline.patchMask(RepairVisualParts.BODY, true);
        long tick = 100;
        timeline.pulse(body, tick);
        for (int pulse = 0; pulse < 80; pulse++) {
            tick += pulse % 2 == 0 ? 5 : 6;
            float before = timeline.progress(0, tick);
            timeline.pulse(body, tick);
            for (int part : new int[]{0, 2, 3}) {
                assertEquals(before, timeline.progress(part, tick), 1e-6f);
                assertEquals(RepairVisualTimeline.BRIDGE_PROGRESS, timeline.progress(part, tick + 1), 1e-6f);
                assertEquals(RepairVisualTimeline.BRIDGE_PROGRESS, timeline.progress(part, tick + 4), 1e-6f);
                assertTrue(timeline.active(part, tick + 4));
            }
            assertTrue(before <= .80f, "Late packets must not ratchet the steady shape towards disappearance");
            for (int unseen : new int[]{1, 4, 5}) assertFalse(timeline.active(unseen, tick + 1));
        }
        for (int part : new int[]{0, 2, 3}) {
            assertEquals(1, timeline.progress(part, tick + 8), 1e-6f);
            assertFalse(timeline.active(part, tick + 8));
        }
    }

    @Test void aCompletedSiteCanStartANewRepairAndUnseenSitesStayInvisible() {
        var timeline = new RepairVisualTimeline();
        assertFalse(timeline.active(0, 20));
        assertEquals(1, timeline.progress(0, 20));
        timeline.pulse(1, 20);
        assertEquals(1, timeline.progress(0, 28));
        timeline.pulse(1, 30);
        assertEquals(0, timeline.progress(0, 30));
        assertTrue(timeline.active(0, 30));
    }
}
