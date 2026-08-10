package org.academy.internal.client.renderer.vfx;

import org.academy.internal.client.render.vfx.SweepAnimationTimeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SweepAnimationTimelineTest {
    @Test
    void readsDoNotConsumeAnimations() {
        var timeline = new SweepAnimationTimeline<String>();
        timeline.enqueue(7, 100.0f, "sweep");

        assertEquals(timeline.entries(7), timeline.entries(7));
        assertEquals(1, timeline.size(7));
    }

    @Test
    void capsEventsPerEntity() {
        var timeline = new SweepAnimationTimeline<Integer>();
        for (var i = 0; i < 7; i++) timeline.enqueue(3, i, i);

        assertEquals(SweepAnimationTimeline.MAX_EVENTS_PER_ENTITY, timeline.size(3));
        assertEquals(3, timeline.entries(3).getFirst().payload());
        assertEquals(6, timeline.entries(3).getLast().payload());
    }

    @Test
    void prunesExpiredAndMissingEntities() {
        var timeline = new SweepAnimationTimeline<String>();
        timeline.enqueue(1, 0.0f, "expired");
        timeline.enqueue(2, 8.0f, "active");
        timeline.enqueue(3, 8.0f, "missing");

        timeline.prune(10.0f, 10.0f, entityId -> entityId != 3);

        assertEquals(0, timeline.size(1));
        assertEquals(1, timeline.size(2));
        assertEquals(0, timeline.size(3));
    }
}
