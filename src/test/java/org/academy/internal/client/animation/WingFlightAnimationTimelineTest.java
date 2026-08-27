package org.academy.internal.client.animation;

import org.academy.internal.common.ability.accelerator.skills.WingFlightPose;
import org.junit.jupiter.api.Test;

import static org.academy.internal.client.animation.WingFlightAnimationTimeline.Phase.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WingFlightAnimationTimelineTest {
    @Test
    void entersSlowFlightThroughTheAuthoredStartClip() {
        var timeline = new WingFlightAnimationTimeline();

        assertEquals(START_FLYING_SLOW, phase(timeline, WingFlightPose.Pose.SLOW, 100.0f));
        assertEquals(START_FLYING_SLOW, phase(timeline, WingFlightPose.Pose.SLOW, 109.9f));
        assertEquals(FLYING_SLOW, phase(timeline, WingFlightPose.Pose.SLOW, 110.0f));
    }

    @Test
    void acceleratesAndReturnsToSlowFlightThroughTransitionClips() {
        var timeline = new WingFlightAnimationTimeline();
        phase(timeline, WingFlightPose.Pose.SLOW, 100.0f);
        phase(timeline, WingFlightPose.Pose.SLOW, 110.0f);

        assertEquals(START_FLYING_FAST, phase(timeline, WingFlightPose.Pose.FAST, 111.0f));
        assertEquals(FLYING_FAST, phase(timeline, WingFlightPose.Pose.FAST, 131.0f));
        assertEquals(QUIT_FLYING_FAST, phase(timeline, WingFlightPose.Pose.SLOW, 132.0f));
        assertEquals(FLYING_SLOW, phase(timeline, WingFlightPose.Pose.SLOW, 142.0f));
    }

    @Test
    void directBoostStillEstablishesTheSlowPoseBeforeFastFlight() {
        var timeline = new WingFlightAnimationTimeline();

        assertEquals(START_FLYING_SLOW, phase(timeline, WingFlightPose.Pose.FAST, 0.0f));
        assertEquals(START_FLYING_FAST, phase(timeline, WingFlightPose.Pose.FAST, 10.0f));
        assertEquals(FLYING_FAST, phase(timeline, WingFlightPose.Pose.FAST, 30.0f));
    }

    @Test
    void stoppingFastFlightPlaysTheBrakeClipBeforeIdle() {
        var timeline = new WingFlightAnimationTimeline();
        phase(timeline, WingFlightPose.Pose.FAST, 0.0f);
        phase(timeline, WingFlightPose.Pose.FAST, 30.0f);

        assertEquals(QUIT_FLYING_FAST, phase(timeline, WingFlightPose.Pose.IDLE, 31.0f));
        assertEquals(STOP_FLYING_SLOW, phase(timeline, WingFlightPose.Pose.IDLE, 41.0f));
        assertEquals(IDLE, phase(timeline, WingFlightPose.Pose.IDLE, 51.0f));
    }

    @Test
    void stoppingSlowFlightReversesTheStartClipBeforeIdle() {
        var timeline = new WingFlightAnimationTimeline();
        phase(timeline, WingFlightPose.Pose.SLOW, 100.0f);
        phase(timeline, WingFlightPose.Pose.SLOW, 110.0f);

        var start = timeline.update(WingFlightPose.Pose.IDLE, 111.0f);
        assertEquals(STOP_FLYING_SLOW, start.phase());
        assertEquals(0.5f, start.clipTimeSeconds(), 0.0001f);

        var middle = timeline.update(WingFlightPose.Pose.IDLE, 116.0f);
        assertEquals(STOP_FLYING_SLOW, middle.phase());
        assertEquals(0.25f, middle.clipTimeSeconds(), 0.0001f);
        assertEquals(IDLE, phase(timeline, WingFlightPose.Pose.IDLE, 121.0f));
    }

    @Test
    void releasingDuringTheStartClipReversesFromTheCurrentFrame() {
        var timeline = new WingFlightAnimationTimeline();
        timeline.update(WingFlightPose.Pose.SLOW, 0.0f);
        timeline.update(WingFlightPose.Pose.SLOW, 3.0f);

        var reversing = timeline.update(WingFlightPose.Pose.IDLE, 3.0f);
        assertEquals(STOP_FLYING_SLOW, reversing.phase());
        assertEquals(0.15f, reversing.clipTimeSeconds(), 0.0001f);
        assertEquals(IDLE, phase(timeline, WingFlightPose.Pose.IDLE, 6.0f));
    }

    private static WingFlightAnimationTimeline.Phase phase(
            WingFlightAnimationTimeline timeline,
            WingFlightPose.Pose pose,
            float tick
    ) {
        return timeline.update(pose, tick).phase();
    }
}
