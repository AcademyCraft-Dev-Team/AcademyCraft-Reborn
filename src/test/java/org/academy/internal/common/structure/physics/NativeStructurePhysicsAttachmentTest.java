package org.academy.internal.common.structure.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructurePhysicsAttachmentTest {
    private static final AABB BOUNDS = new AABB(-12, 64, -12, 12, 88, 12);
    private static final String DIGEST = "0123456789abcdef".repeat(4);

    @Test
    void blockedResumePreservesMotionAndSleepingObservationWithoutLoadingNativeCode() {
        var motion = new NativeStructurePhysicsAttachment.Motion(new Vec3(-10_000, 0.25, 10_000),
                new Vec3(0, 2, 0), new Vec3(0.5, 80.25, -0.5), true, DIGEST);
        var attachment = new NativeStructurePhysicsAttachment(BOUNDS, motion, true);
        var blocked = attachment.withResumeAllowed(false);
        assertEquals(BOUNDS, blocked.bounds());
        assertSame(motion, blocked.motion());
        assertTrue(blocked.motion().sleeping());
        assertFalse(blocked.resumeAllowed());
        assertTrue(attachment.resumeAllowed());
        assertNull(new NativeStructurePhysicsAttachment(BOUNDS, null, true).motion());
    }

    @Test
    void malformedRegionBoundsCannotBecomePersistedIntent() {
        for (var bounds : new AABB[]{new AABB(0, 0, 0, 0, 1, 1), new AABB(0, 0, 0, 24.001, 1, 1),
                new AABB(0, 0, 0, 1, Double.POSITIVE_INFINITY, 1), new AABB(Double.NaN, 0, 0, 1, 1, 1),
                new AABB(29_999_980, 0, 0, 29_999_981, 1, 1)}) {
            assertThrows(IllegalArgumentException.class, () -> new NativeStructurePhysicsAttachment(bounds, null, true));
        }
        assertThrows(NullPointerException.class, () -> new NativeStructurePhysicsAttachment(null, null, true));
        assertDoesNotThrow(() -> new NativeStructurePhysicsAttachment(
                new AABB(29_999_956, 0, -29_999_980, 29_999_980, 24, -29_999_956), null, false));
    }

    @Test
    void motionRejectsNonfiniteValuesLimitsAndNoncanonicalDigests() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 10_000.001, -10_000.001}) {
            assertThrows(IllegalArgumentException.class, () -> motion(new Vec3(invalid, 0, 0), Vec3.ZERO, Vec3.ZERO, DIGEST));
            assertThrows(IllegalArgumentException.class, () -> motion(Vec3.ZERO, new Vec3(0, invalid, 0), Vec3.ZERO, DIGEST));
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 1_000_000.001, -1_000_000.001}) {
            assertThrows(IllegalArgumentException.class, () -> motion(Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, invalid), DIGEST));
        }
        for (var invalid : new String[]{"", "0".repeat(63), "0".repeat(65), "A".repeat(64), "g".repeat(64), " " + DIGEST}) {
            assertThrows(IllegalArgumentException.class, () -> motion(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, invalid));
        }
        assertThrows(NullPointerException.class, () -> motion(null, Vec3.ZERO, Vec3.ZERO, DIGEST));
        assertThrows(NullPointerException.class, () -> motion(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, null));
    }

    private static NativeStructurePhysicsAttachment.Motion motion(Vec3 linear, Vec3 angular, Vec3 center, String digest) {
        return new NativeStructurePhysicsAttachment.Motion(linear, angular, center, false, digest);
    }
}
