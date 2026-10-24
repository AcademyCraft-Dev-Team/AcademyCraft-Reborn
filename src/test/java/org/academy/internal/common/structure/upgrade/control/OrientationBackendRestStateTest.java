package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.upgrade.lifecycle.StructureRestTracker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrientationBackendRestStateTest {
    private static final StructureRef REFERENCE = new StructureRef(UUID.randomUUID(),
            Identifier.parse("minecraft:overworld"), Identifier.parse("academy:test"), 0);

    @Test
    void compatibleAdapterPreservesMotionAndTerrainGatesRatherThanInventingRest() {
        var runtime = UUID.randomUUID();
        var tracker = new StructureRestTracker();
        var initial = body(runtime, 7, Vec3.ZERO, Vec3.ZERO, true);
        assertFalse(sample(tracker, initial, 0));
        assertFalse(sample(tracker, body(runtime, 7, new Vec3(1, 0, 0), Vec3.ZERO, true), 100));
        assertFalse(sample(tracker, initial, 200));
        assertFalse(sample(tracker, body(runtime, 7, Vec3.ZERO, new Vec3(0, 1, 0), true), 300));
        assertFalse(sample(tracker, initial, 400));
        assertFalse(sample(tracker, body(runtime, 7, Vec3.ZERO, Vec3.ZERO, false), 500));
        assertFalse(sample(tracker, initial, 600));
        assertTrue(sample(tracker, initial, 700));
    }

    @Test
    void compatibleAdapterKeepsContentAndRuntimeEpochsThatResetTheRestWindow() {
        var runtime = UUID.randomUUID();
        var tracker = new StructureRestTracker();
        assertFalse(sample(tracker, body(runtime, 7, Vec3.ZERO, Vec3.ZERO, true), 0));
        assertFalse(sample(tracker, body(runtime, 8, Vec3.ZERO, Vec3.ZERO, true), 100));
        var reloaded = body(UUID.randomUUID(), 8, Vec3.ZERO, Vec3.ZERO, true);
        assertFalse(sample(tracker, reloaded, 200));
        assertTrue(sample(tracker, reloaded, 300));
    }

    @Test
    void unavailableFullObservationCannotBecomeAnIdleRestCandidate() {
        assertTrue(adapter(Optional.empty()).observeRest(null, REFERENCE).isEmpty());
    }

    @Test
    void malformedRestSamplesCannotReachTheSampler() {
        var runtime = UUID.randomUUID();
        var pose = StructurePose.identity(Vec3.ZERO);
        assertThrows(IllegalArgumentException.class, () -> new OrientationBackendAccess.RestState(
                REFERENCE, runtime, -1, pose, Vec3.ZERO, Vec3.ZERO, true));
        assertThrows(IllegalArgumentException.class, () -> new OrientationBackendAccess.RestState(
                REFERENCE, runtime, 0, pose, new Vec3(Double.NaN, 0, 0), Vec3.ZERO, true));
        assertThrows(IllegalArgumentException.class, () -> new OrientationBackendAccess.RestState(
                REFERENCE, runtime, 0, pose, Vec3.ZERO, new Vec3(0, Double.POSITIVE_INFINITY, 0), true));
    }

    private static boolean sample(StructureRestTracker tracker, OrientationBackendAccess.Body body, long tick) {
        var state = adapter(Optional.of(body)).observeRest(null, REFERENCE).orElseThrow();
        assertEquals(body.reference(), state.reference());
        assertEquals(body.motion().pose(), state.pose());
        return tracker.observe(new StructureRestTracker.Observation(state.runtimeId(), state.contentRevision(),
                state.pose(), state.linearVelocity(), state.angularVelocity(), state.terrainReady(), false),
                tick, 100, 200);
    }

    private static OrientationBackendAccess adapter(Optional<OrientationBackendAccess.Body> body) {
        return new OrientationBackendAccess() {
            @Override public Optional<Body> observe(ServerLevel host, StructureRef reference) { return body; }
            @Override public Nearby nearby(ServerLevel host, AABB bounds, int maximumBodies) {
                throw new AssertionError("Rest sampling must not query other structures");
            }
            @Override public boolean apply(ServerLevel host, Body expected, Vec3 angularVelocity) {
                throw new AssertionError("Rest sampling must not mutate motion");
            }
        };
    }

    private static OrientationBackendAccess.Body body(UUID runtime, long content, Vec3 linear, Vec3 angular, boolean ready) {
        return new OrientationBackendAccess.Body(REFERENCE, runtime, content, 0,
                new OrientationController.State(StructurePose.identity(new Vec3(1, 2, 3)), Vec3.ZERO, angular, 1),
                linear, Vec3.ZERO, List.of(new AABB(0, 0, 0, 1, 1, 1)),
                new OrientationTargets.Baselines(Optional.empty(), Optional.empty()), ready);
    }
}
