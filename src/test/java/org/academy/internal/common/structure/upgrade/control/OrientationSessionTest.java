package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrientationSessionTest {
    private static final StructureOrientation.Limits LIMITS = new StructureOrientation.Limits(2, 6, 100,
            100, .005, .01, 0, 1_000);
    private final OrientationSession.Binding binding = new OrientationSession.Binding(new StructureRef(UUID.randomUUID(),
            Identifier.parse("minecraft:overworld"), Identifier.parse("academy:test"), 0),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3);

    @Test
    void duplicateSubstepAndPollCannotApplyTwice() {
        var session = rotate();
        var backend = new TestBackend();
        assertEquals(StructureOperation.Status.RUNNING, session.advance(binding, 0, .025, backend).status());
        session.advance(binding, 0, .025, backend);
        for (int i = 0; i < 10; i++) session.poll(binding, session.operationId());
        assertEquals(1, backend.applications);
        backend.sequence++;
        session.advance(binding, 0, .025, backend);
        assertEquals(2, backend.applications);
    }

    @Test
    void staleActivationOrTokenCannotReadOrStopAnotherOperation() {
        var session = rotate();
        var other = new OrientationSession.Binding(binding.reference(), UUID.randomUUID(), binding.actorId(),
                binding.controlId(), binding.contentRevision());
        assertTrue(session.poll(other, session.operationId()).value().isEmpty());
        assertEquals(StructureOperation.Status.DENIED, session.stop(binding, UUID.randomUUID(),
                StructureOrientation.StopMode.RELEASE_INERTIA).status());
        assertEquals(StructureOperation.Status.RUNNING, session.poll(binding, session.operationId()).status());
    }

    @Test
    void unloadAndContentChangesEndWithoutMutation() {
        var backend = new TestBackend();
        backend.available = false;
        assertEquals(StructureOperation.Status.STALE, rotate().advance(binding, 0, .025, backend).status());
        backend.available = true;
        backend.observedBinding = new OrientationSession.Binding(binding.reference(), binding.runtimeId(),
                binding.actorId(), binding.controlId(), 4);
        assertEquals(StructureOperation.Status.STALE, rotate().advance(binding, 0, .025, backend).status());
        assertEquals(0, backend.applications);
    }

    @Test
    void blockedAndAtomicCommitFailureCannotReportCompletion() {
        var backend = new TestBackend();
        backend.clearance = OrientationSweep.Status.BLOCKED;
        assertEquals(StructureOperation.Status.BLOCKED, rotate().advance(binding, 0, .025, backend).status());
        backend.clearance = OrientationSweep.Status.CLEAR;
        backend.accept = false;
        assertEquals(StructureOperation.Status.STALE, rotate().advance(binding, 0, .025, backend).status());
        assertEquals(0, backend.applications);
    }

    @Test
    void missingTerrainCanResumeOnlyOnANewSolverSubstep() {
        var backend = new TestBackend();
        var session = rotate();
        backend.clearance = OrientationSweep.Status.TERRAIN_PENDING;
        assertEquals(StructureOperation.Status.TERRAIN_PENDING, session.advance(binding, 0, .025, backend).status());
        backend.clearance = OrientationSweep.Status.CLEAR;
        session.advance(binding, 0, .025, backend);
        assertEquals(0, backend.applications);
        backend.sequence++;
        assertEquals(StructureOperation.Status.RUNNING, session.advance(binding, 1, .025, backend).status());
        assertEquals(1, backend.applications);
    }

    @Test
    void completionRequiresObservedPositionAndObservedSpeed() {
        var backend = new TestBackend();
        var session = rotate();
        backend.pose = target();
        backend.omega = new Vec3(0, 1, 0);
        assertEquals(StructureOperation.Status.RUNNING, session.advance(binding, 0, .025, backend).status());
        backend.sequence++;
        backend.omega = Vec3.ZERO;
        assertEquals(StructureOperation.Status.COMPLETED, session.advance(binding, 1, .025, backend).status());
    }

    @Test
    void spinDurationThenBrakeIsIndependentOfOuterTickAndPolling() {
        var request = new StructureOrientation.SpinRequest(binding.controlId(), new Vec3(0, 1, 0),
                StructureOrientation.AxisFrame.WORLD, 1, 1, StructureOrientation.StopMode.BRAKE, LIMITS);
        var plan = new StructureOrientation.Plan(StructureOrientation.Kind.SPIN,
                StructureOrientation.BaselineSource.NOT_APPLICABLE, Optional.empty(), LIMITS, 4, 7);
        var session = new OrientationSession(binding, plan, Optional.of(request), 0);
        var backend = new TestBackend();
        session.advance(binding, 0, .025, backend);
        backend.sequence++;
        session.advance(binding, 0, .025, backend);
        backend.sequence++;
        var braking = session.advance(binding, 1, .025, backend);
        assertEquals(StructureOrientation.Phase.BRAKING, braking.value().orElseThrow().phase());
        backend.sequence++;
        backend.omega = Vec3.ZERO;
        assertEquals(StructureOperation.Status.COMPLETED, session.advance(binding, 1, .025, backend).status());
    }

    @Test
    void timeoutAndExplicitReleaseMakeNoFurtherBackendCalls() {
        var backend = new TestBackend();
        var session = rotate();
        assertEquals(StructureOrientation.Phase.EXPIRED, session.advance(binding, 101, .025, backend).value().orElseThrow().phase());
        assertEquals(0, backend.observations);
        session = rotate();
        session.stop(binding, session.operationId(), StructureOrientation.StopMode.RELEASE_INERTIA);
        session.advance(binding, 0, .025, backend);
        assertEquals(0, backend.observations);
    }

    @Test
    void staleInitialPlanFailsBeforeSweep() {
        var backend = new TestBackend();
        backend.poseRevision++;
        assertEquals(StructureOperation.Status.STALE, rotate().advance(binding, 0, .025, backend).status());
        assertEquals(0, backend.sweeps);
    }

    @Test
    void stopCanTightenBrakeBudgetButCannotIncreaseExistingAuthority() {
        var session = rotate();
        var backend = new TestBackend();
        session.advance(binding, 0, .025, backend);
        backend.omega = new Vec3(0, 1, 0);
        var tighter = new StructureOrientation.Limits(1, 2, 1, 80, .002, .005, 0, 500);
        var stopped = session.stop(binding, session.operationId(), StructureOrientation.StopMode.BRAKE, tighter);
        assertEquals(tighter, stopped.value().orElseThrow().plan().limits());
        backend.sequence++;
        session.advance(binding, 1, .025, backend);
        assertEquals(.975, backend.omega.y, 1.0E-9); // I = 1, torque = 1, dt = .025.
        var looser = new StructureOrientation.Limits(3, 10, 200, 200, .01, .02, 1, 2_000);
        stopped = session.stop(binding, session.operationId(), StructureOrientation.StopMode.BRAKE, looser);
        assertEquals(tighter, stopped.value().orElseThrow().plan().limits());
        backend.sequence++;
        assertEquals(StructureOrientation.Phase.EXPIRED,
                session.advance(binding, 80, .025, backend).value().orElseThrow().phase());
    }

    @Test
    void deadlineAllowsObservedCompletionButNoAdditionalMutation() {
        var backend = new TestBackend();
        var session = rotate();
        backend.pose = target();
        assertEquals(StructureOperation.Status.COMPLETED, session.advance(binding, 100, .025, backend).status());
        assertEquals(0, backend.applications);
        backend.pose = StructurePose.identity(Vec3.ZERO);
        assertEquals(StructureOrientation.Phase.EXPIRED, rotate().advance(binding, 100, .025, backend).value().orElseThrow().phase());
        assertEquals(0, backend.applications);
    }

    @Test
    void spinAtTimeoutCanReleaseAndUnevenSubstepsNeverExtendCommand() {
        var limits = new StructureOrientation.Limits(2, 6, 100, 1, .005, .01, 0, 1_000);
        var request = new StructureOrientation.SpinRequest(binding.controlId(), new Vec3(0, 1, 0),
                StructureOrientation.AxisFrame.WORLD, 1, 1, StructureOrientation.StopMode.RELEASE_INERTIA, limits);
        var plan = new StructureOrientation.Plan(StructureOrientation.Kind.SPIN,
                StructureOrientation.BaselineSource.NOT_APPLICABLE, Optional.empty(), limits, 4, 7);
        var session = new OrientationSession(binding, plan, Optional.of(request), 0);
        var backend = new TestBackend();
        session.advance(binding, 0, .05, backend);
        backend.sequence++;
        assertEquals(StructureOperation.Status.COMPLETED, session.advance(binding, 1, .025, backend).status());
        assertEquals(1, backend.applications);

        session = new OrientationSession(binding, plan, Optional.of(request), 0);
        backend = new TestBackend();
        session.advance(binding, 0, .03, backend);
        assertEquals(.02, session.remainingSpinSeconds(), 1.0E-9);
        backend.sequence++;
        assertEquals(StructureOperation.Status.COMPLETED, session.advance(binding, 0, .03, backend).status());
        assertEquals(1, backend.applications);
    }

    private OrientationSession rotate() {
        return new OrientationSession(binding, new StructureOrientation.Plan(StructureOrientation.Kind.ROTATE_TO,
                StructureOrientation.BaselineSource.NOT_APPLICABLE, Optional.of(target()), LIMITS, 4, 7), Optional.empty(), 0);
    }

    private static StructurePose target() { return OrientationTargets.axisAngle(new Vec3(0, 1, 0), 1); }

    private final class TestBackend implements OrientationSession.Backend {
        private OrientationSession.Binding observedBinding = binding;
        private StructurePose pose = StructurePose.identity(Vec3.ZERO);
        private Vec3 omega = Vec3.ZERO;
        private long sequence, poseRevision = 4;
        private int applications, observations, sweeps;
        private boolean available = true, accept = true;
        private OrientationSweep.Status clearance = OrientationSweep.Status.CLEAR;

        @Override
        public Optional<OrientationSession.Observation> observe(OrientationSession.Binding authority) {
            observations++;
            return available ? Optional.of(new OrientationSession.Observation(observedBinding, sequence, poseRevision,
                    7, new OrientationController.State(pose, Vec3.ZERO, omega, 1))) : Optional.empty();
        }

        @Override
        public OrientationSweep.Status sweep(OrientationSession.Observation observation,
                                             OrientationController.Step step, double seconds, int budget) {
            sweeps++;
            return clearance;
        }

        @Override
        public boolean applyAngularVelocity(OrientationSession.Observation observed, Vec3 next) {
            if (!accept) return false;
            omega = next;
            applications++;
            return true;
        }
    }
}
