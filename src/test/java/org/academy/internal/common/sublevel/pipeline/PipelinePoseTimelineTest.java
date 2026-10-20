package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePipeline;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipelinePoseTimelineTest {
    private final PipelinePoseTimeline timeline = new PipelinePoseTimeline();
    private static final StructureRef REF = new StructureRef(UUID.randomUUID(),
            Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            Identifier.fromNamespaceAndPath("academy", "sublevel"), 1);
    private static final StructurePipeline.Epoch EPOCH = new StructurePipeline.Epoch(REF, UUID.randomUUID(), 1);

    private static StructurePipeline.PoseFrame frame(long sequence, long tick, double x, Vec3 velocity,
                                                     boolean teleport) {
        return new StructurePipeline.PoseFrame(EPOCH, sequence, tick,
                StructurePose.identity(new Vec3(x, 0, 0)), velocity, Vec3.ZERO, teleport);
    }

    @Test
    void samplesExactAndInterpolatedFrames() {
        var history = List.of(frame(1, 10, 0, Vec3.ZERO, false),
                frame(2, 20, 10, Vec3.ZERO, false));
        assertEquals(0.0, timeline.sample(history, 10, 1).pose().worldOrigin().x, 1.0E-9);
        assertEquals(5.0, timeline.sample(history, 15, 1).pose().worldOrigin().x, 1.0E-9);
        assertEquals(10.0, timeline.sample(history, 20, 1).pose().worldOrigin().x, 1.0E-9);
        assertFalse(timeline.sample(history, 15, 1).stale());
        assertFalse(timeline.sample(history, 15, 1).extrapolated());
    }

    @Test
    void extrapolatesWithinTheWindowAndFreezesBeyondIt() {
        var history = List.of(frame(1, 10, 0, new Vec3(2, 0, 0), false),
                frame(2, 20, 10, new Vec3(2, 0, 0), false));
        var extrapolated = timeline.sample(history, 20.5, 1);
        assertTrue(extrapolated.extrapolated());
        assertFalse(extrapolated.stale());
        assertEquals(11.0, extrapolated.pose().worldOrigin().x, 1.0E-9);

        var stale = timeline.sample(history, 22, 1);
        assertTrue(stale.stale());
        assertFalse(stale.extrapolated());
        assertEquals(10.0, stale.pose().worldOrigin().x, 1.0E-9);
    }

    @Test
    void teleportDiscardsOlderHistory() {
        var history = List.of(frame(1, 10, 0, new Vec3(100, 0, 0), false),
                frame(2, 20, 100, new Vec3(1, 0, 0), true));
        var sample = timeline.sample(history, 20.5, 1);
        assertEquals(100.5, sample.pose().worldOrigin().x, 1.0E-9);
        assertTrue(sample.extrapolated());
    }

    @Test
    void newestEpochWinsAndDuplicateSequenceKeepsTheLastFrame() {
        var otherEpoch = new StructurePipeline.Epoch(REF, UUID.randomUUID(), 2);
        var older = new StructurePipeline.PoseFrame(EPOCH, 1, 10,
                StructurePose.identity(new Vec3(0, 0, 0)), Vec3.ZERO, Vec3.ZERO, false);
        var newest = new StructurePipeline.PoseFrame(otherEpoch, 2, 20,
                StructurePose.identity(new Vec3(5, 0, 0)), Vec3.ZERO, Vec3.ZERO, false);
        var duplicate = new StructurePipeline.PoseFrame(otherEpoch, 2, 20,
                StructurePose.identity(new Vec3(7, 0, 0)), Vec3.ZERO, Vec3.ZERO, false);
        var sample = timeline.sample(List.of(older, newest, duplicate), 20, 1);
        assertEquals(otherEpoch, sample.epoch());
        assertEquals(7.0, sample.pose().worldOrigin().x, 1.0E-9);
    }

    @Test
    void aNewEpochWinsEvenWhenItsSequenceRestarted() {
        var newEpoch = new StructurePipeline.Epoch(REF, UUID.randomUUID(), 2);
        var oldFrame = new StructurePipeline.PoseFrame(EPOCH, 100, 10,
                StructurePose.identity(new Vec3(0, 0, 0)), Vec3.ZERO, Vec3.ZERO, false);
        var newFrame = new StructurePipeline.PoseFrame(newEpoch, 1, 20,
                StructurePose.identity(new Vec3(3, 0, 0)), Vec3.ZERO, Vec3.ZERO, false);
        var sample = timeline.sample(List.of(oldFrame, newFrame), 20, 1);
        assertEquals(newEpoch, sample.epoch());
        assertEquals(3.0, sample.pose().worldOrigin().x, 1.0E-9);
    }

    @Test
    void backwardsTicksForLaterSequencesAreRejected() {
        var history = List.of(frame(1, 20, 0, Vec3.ZERO, false),
                frame(2, 10, 5, Vec3.ZERO, false));
        assertThrows(IllegalArgumentException.class, () -> timeline.sample(history, 15, 1));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> timeline.sample(List.of(), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> timeline.sample(
                List.of(frame(1, 1, 0, Vec3.ZERO, false)), -1, 1));
        assertThrows(IllegalArgumentException.class, () -> timeline.sample(
                List.of(frame(1, 1, 0, Vec3.ZERO, false)), 1, -1));
        assertThrows(IllegalArgumentException.class, () -> timeline.sample(
                List.of(frame(1, 1, 0, Vec3.ZERO, false)), 1, 21));
    }
}
