package org.academy.api.client.render.vfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedAnimationTimelineTest {
    @Test void delayedWorldEventsStartAtTheirFirstVisibleFrame() {
        var timeline = new BoundedAnimationTimeline<String>(0);
        assertTrue(timeline.offer(1, "accepted long ago on server", 5000, .8, 0));
        timeline.advance(5000.4);
        assertEquals(0, timeline.started());
        assertEquals(0, timeline.sample(5000.4).progress());
        assertEquals(.5f, timeline.sample(5000.8).progress(), 1e-5f);
        assertNull(timeline.sample(5001.2));
    }
    @Test void jitteredFivePatternSequenceDoesNotRestartOrClipCurrentStroke() {
        var timeline = new BoundedAnimationTimeline<Integer>(0);
        double[] arrival = {0, .72, 1.65, 2.3, 3.22};
        int next = 0;
        var seen = new java.util.ArrayList<Integer>();
        Integer last = null;
        float previousProgress = 0;
        for (int frame = 0; frame < 310; frame++) {
            double now = frame / 60.0;
            while (next < arrival.length && arrival[next] <= now) {
                timeline.offer(next + 1, next, now, .8, 0);
                next++;
            }
            var sample = timeline.sample(now);
            if (sample == null) continue;
            if (!sample.value().equals(last)) { seen.add(sample.value()); previousProgress = 0; }
            assertTrue(sample.progress() >= previousProgress);
            previousProgress = sample.progress(); last = sample.value();
        }
        assertEquals(java.util.List.of(0, 1, 2, 3, 4), seen);
        assertEquals(0, timeline.coalesced());
    }
    @Test void duplicateAndBurstRecoveryAreBounded() {
        var timeline = new BoundedAnimationTimeline<String>(0);
        timeline.offer(1, "running", 0, .8, 0); timeline.sample(0);
        assertFalse(timeline.offer(1, "duplicate", .1, .8, 0));
        timeline.offer(2, "old pending", .1, .8, 0);
        timeline.offer(3, "newest", .2, .8, 0);
        assertEquals("running", timeline.sample(.4).value());
        assertEquals("newest", timeline.sample(.8).value());
        assertEquals(0, timeline.sample(.8).progress());
        assertEquals(1, timeline.coalesced()); assertEquals(1, timeline.duplicates());
        timeline.offer(4, "unseen", .9, .8, 0);
        assertNull(timeline.sample(3)); assertFalse(timeline.hasEvents());
    }
    @Test void sustainedAttacksDoNotAccumulateFrameRoundingDelay() {
        for (int fps : new int[] {30, 59, 144}) {
            var timeline = new BoundedAnimationTimeline<Integer>(0);
            int next = 0;
            for (int frame = 0; frame <= 242 * fps; frame++) {
                double now = frame / (double) fps;
                while (next < 300 && next * .8 <= now + 1e-9) {
                    timeline.offer(next + 1, next, now, .8, 0);
                    next++;
                }
                timeline.sample(now);
            }
            assertEquals(300, timeline.started(), "fps=" + fps);
            assertEquals(0, timeline.coalesced(), "fps=" + fps);
            assertEquals(0, timeline.expired(), "fps=" + fps);
            assertFalse(timeline.hasEvents());
        }
    }
    @Test void longRenderStallDoesNotBackdateANewAttackToAnExpiredStroke() {
        var timeline = new BoundedAnimationTimeline<String>(0);
        timeline.offer(1, "old", 0, .8, 0); timeline.sample(0);
        timeline.offer(2, "latest", .7, .8, 0);
        assertEquals("latest", timeline.sample(1.2).value());
        assertEquals(0, timeline.sample(1.2).progress());
    }
    @Test void trackingSnapshotStartsMidStrokeAndSequenceFloorRejectsHistory() {
        var timeline = new BoundedAnimationTimeline<String>(40);
        assertFalse(timeline.offer(40, "history", 0, .8, 0));
        assertTrue(timeline.offer(41, "snapshot", 2, .8, .5f));
        assertEquals(.5f, timeline.sample(2.1).progress(), 1e-6f);
        assertNull(timeline.sample(2.5));
    }
}
