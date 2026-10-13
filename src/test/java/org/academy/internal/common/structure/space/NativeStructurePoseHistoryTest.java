package org.academy.internal.common.structure.space;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructurePoseHistoryTest {
    @Test
    void observationIsUsableThroughSixTicksButNotSeven() {
        var history = new NativeStructurePoseHistory();
        var pose = pose(1);
        history.remember(100, 5, 7, pose);
        assertEquals(pose, history.find(106, 5, 7).orElseThrow().pose());
        assertTrue(history.find(107, 5, 7).isEmpty());
        assertTrue(history.find(106, 5, 7).isEmpty(), "An expired observation cannot return after a clock rollback");
    }

    @Test
    void eightFrameBudgetAppliesEvenWithinOneTick() {
        var history = new NativeStructurePoseHistory();
        for (int revision = 1; revision <= 9; revision++) history.remember(100, revision, 7, pose(revision));
        assertTrue(history.find(100, 1, 7).isEmpty());
        for (int revision = 2; revision <= 9; revision++) {
            assertEquals(pose(revision), history.find(100, revision, 7).orElseThrow().pose());
        }
    }

    @Test
    void contentChangeInvalidatesTheWholeOldMotionSequence() {
        var history = new NativeStructurePoseHistory();
        history.remember(100, 20, 7, pose(1));
        history.remember(101, 21, 7, pose(2));
        history.remember(102, 22, 8, pose(3));
        assertTrue(history.find(102, 20, 7).isEmpty());
        assertTrue(history.find(102, 21, 7).isEmpty());
        assertTrue(history.find(102, 22, 7).isEmpty());
        assertEquals(pose(3), history.find(102, 22, 8).orElseThrow().pose());
    }

    @Test
    void backwardPublicationClockBeginsANewContinuityDomain() {
        var history = new NativeStructurePoseHistory();
        history.remember(100, 20, 7, pose(1));
        history.remember(99, 21, 7, pose(2));
        assertTrue(history.find(99, 20, 7).isEmpty());
        assertEquals(pose(2), history.find(99, 21, 7).orElseThrow().pose());
        assertTrue(history.find(98, 21, 7).isEmpty());
        assertTrue(history.find(99, 21, 7).isEmpty(), "A backward lookup invalidates the old domain");
    }

    @Test
    void backwardRevisionCannotRetainHigherVersionObservations() {
        var history = new NativeStructurePoseHistory();
        history.remember(100, 20, 7, pose(1));
        history.remember(101, 19, 7, pose(2));
        assertTrue(history.find(101, 20, 7).isEmpty());
        assertEquals(pose(2), history.find(101, 19, 7).orElseThrow().pose());
    }

    @Test
    void identicalRepublishingRenewsOnlyTheUnchangedFrame() {
        var history = new NativeStructurePoseHistory();
        history.remember(100, 20, 7, pose(1));
        history.remember(106, 20, 7, pose(1));
        var renewed = history.find(112, 20, 7).orElseThrow();
        assertEquals(106, renewed.tick());
        assertEquals(pose(1), renewed.pose());
        assertTrue(history.find(113, 20, 7).isEmpty());
    }

    @Test
    void changingThePoseAtAnExistingRevisionIsRejectedWithoutReplacingIt() {
        var history = new NativeStructurePoseHistory();
        history.remember(100, 20, 7, pose(1));
        assertThrows(IllegalArgumentException.class, () -> history.remember(101, 20, 7, pose(2)));
        var original = history.find(101, 20, 7).orElseThrow();
        assertEquals(100, original.tick());
        assertEquals(pose(1), original.pose());
    }

    @Test
    void explicitInvalidationAndInvalidVersionsCannotResurrectFrames() {
        var history = new NativeStructurePoseHistory();
        history.remember(100, 20, 7, pose(1));
        assertThrows(IllegalArgumentException.class, () -> history.remember(-1, 21, 7, pose(2)));
        assertThrows(IllegalArgumentException.class, () -> history.remember(101, -1, 7, pose(2)));
        assertThrows(IllegalArgumentException.class, () -> history.remember(101, 21, -1, pose(2)));
        assertEquals(pose(1), history.find(101, 20, 7).orElseThrow().pose());
        history.clear();
        assertTrue(history.find(101, 20, 7).isEmpty());
    }

    private static StructurePose pose(double x) {
        return StructurePose.identity(new Vec3(x, 80, 2));
    }
}
