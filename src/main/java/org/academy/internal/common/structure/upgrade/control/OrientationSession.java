package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureOrientation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One backend-independent angular operation, driven by actual solver substeps, never polling.
 * This is an executable control kernel, not a live backend registration. A server-owned registry
 * must replace/cancel the old session before installing a new one for the same exclusive lease.
 * All methods run on the owning server thread; the backend must not retain or load a world.
 */
public final class OrientationSession {
    /** Exact authority, activation and content; a matching actor alone is insufficient. */
    public record Binding(StructureRef reference, UUID runtimeId, UUID actorId, UUID controlId,
                          long contentRevision) {
        public Binding {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(runtimeId, "runtimeId");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(controlId, "controlId");
            if (contentRevision < 0) throw new IllegalArgumentException("Negative content revision");
        }
    }

    /** stepSequence identifies one real solver substep, independent of the outer server tick. */
    public record Observation(Binding binding, long stepSequence, long poseRevision,
                              long terrainRevision, OrientationController.State state) {
        public Observation {
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(state, "state");
            if (stepSequence < 0 || poseRevision < 0 || terrainRevision < 0) {
                throw new IllegalArgumentException("Negative observation revision");
            }
        }
    }

    public interface Backend {
        /** Empty for unload, missing actor, revoked lease or unavailable solver; never load chunks. */
        Optional<Observation> observe(Binding authority);

        /**
         * Check the entire unwrapped arc using nextAngularVelocity and seconds, including the
         * body's linear sweep. The observation's terrain epoch must stay valid until apply.
         * predictedPose is only a candidate; endpoint checks cannot authorize this operation.
         */
        OrientationSweep.Status sweep(Observation observed, OrientationController.Step command,
                                      double seconds, int maxShapeTests);

        /**
         * Revalidate authority plus pose/content/terrain/step revisions atomically, then correct
         * angular velocity without teleporting or changing aggregate linear COM velocity.
         * Returns false without mutation on any mismatch. For welded bodies preserve each
         * part's COM tangential velocity; copying old linear velocities is insufficient.
         */
        boolean applyAngularVelocity(Observation observed, Vec3 nextAngularVelocity);
    }

    private final UUID operationId;
    private final Binding binding;
    private StructureOrientation.Plan plan;
    private final Optional<StructureOrientation.SpinRequest> spin;
    private final long createdTick;
    private StructureOrientation.Phase phase = StructureOrientation.Phase.PREPARING;
    private StructureOperation.Status status = StructureOperation.Status.RUNNING;
    private String detail = "orientation_preparing";
    private long lastTick;
    private long lastStep = -1;
    private double controlledSeconds;
    private double angleError;
    private double angularSpeed;
    private boolean braking;

    public OrientationSession(Binding binding, StructureOrientation.Plan plan,
                              Optional<StructureOrientation.SpinRequest> spin, long createdTick) {
        this(UUID.randomUUID(), binding, plan, spin, createdTick);
    }

    public OrientationSession(UUID operationId, Binding binding, StructureOrientation.Plan plan,
                              Optional<StructureOrientation.SpinRequest> spin, long createdTick) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.spin = Objects.requireNonNull(spin, "spin");
        if (createdTick < 0) throw new IllegalArgumentException("Negative creation tick");
        if ((plan.kind() == StructureOrientation.Kind.SPIN) != spin.isPresent()) {
            throw new IllegalArgumentException("Spin request does not match plan");
        }
        spin.ifPresent(request -> {
            if (!request.controlId().equals(binding.controlId()) || !request.limits().equals(plan.limits())) {
                throw new IllegalArgumentException("Spin request does not match authority or limits");
            }
        });
        this.createdTick = createdTick;
        lastTick = createdTick;
    }

    public UUID operationId() { return operationId; }

    /** Repeated polls neither advance time nor renew a lease. */
    public StructureOperation.Result<StructureOrientation.Snapshot> poll(Binding caller, UUID requestedOperation) {
        if (!binding.equals(caller) || !operationId.equals(requestedOperation)) return denied();
        return receipt();
    }

    /** No backend call on duplicate substeps, stale authority, terminal commands or expired time. */
    public StructureOperation.Result<StructureOrientation.Snapshot> advance(
            Binding caller, long serverTick, double seconds, Backend backend) {
        Objects.requireNonNull(backend, "backend");
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > OrientationController.MAX_SUBSTEP_SECONDS || serverTick < 0) {
            throw new IllegalArgumentException("Invalid solver substep");
        }
        if (!binding.equals(caller)) return denied();
        if (terminal()) return receipt();
        if (serverTick < lastTick) return finish(StructureOperation.Status.STALE,
                StructureOrientation.Phase.EXPIRED, "server_clock_rewound");
        lastTick = serverTick;
        if (serverTick - createdTick > plan.limits().timeoutTicks()) {
            return finish(StructureOperation.Status.BLOCKED, StructureOrientation.Phase.EXPIRED, "orientation_timeout");
        }
        var observation = backend.observe(binding);
        if (observation.isEmpty() || !binding.equals(observation.get().binding())) {
            return finish(StructureOperation.Status.STALE, StructureOrientation.Phase.EXPIRED, "orientation_authority_lost");
        }
        var observed = observation.get();
        if (lastStep < 0 && (observed.poseRevision() != plan.poseRevision()
                || observed.terrainRevision() != plan.terrainRevision())) {
            return finish(StructureOperation.Status.STALE, StructureOrientation.Phase.EXPIRED, "orientation_plan_stale");
        }
        if (observed.stepSequence() == lastStep) return receipt();
        if (observed.stepSequence() < lastStep) return finish(StructureOperation.Status.STALE,
                StructureOrientation.Phase.EXPIRED, "solver_step_rewound");
        lastStep = observed.stepSequence();
        var state = observed.state();
        angularSpeed = state.angularVelocity().length();
        angleError = plan.targetRotation().map(target -> OrientationTargets.rotationError(state.pose(), target).length())
                .orElse(0.0);
        angleError = Math.min(Math.PI, angleError);
        // A backend can split its next step at remainingSpinSeconds(). Without a split, stop
        // at the preceding boundary rather than extend a finite command past its duration.
        if (!braking && spin.isPresent() && (remainingSpinSeconds() <= 1.0E-9
                || seconds > remainingSpinSeconds() + 1.0E-9)) {
            if (spin.get().finishMode() == StructureOrientation.StopMode.RELEASE_INERTIA) {
                return finish(StructureOperation.Status.COMPLETED, StructureOrientation.Phase.COMPLETED, "spin_released");
            }
            braking = true;
        }
        boolean atRest = angularSpeed <= plan.limits().angularSpeedTolerance();
        if (braking && atRest || !braking && spin.isEmpty()
                && atRest && angleError <= plan.limits().angleTolerance()) {
            return finish(StructureOperation.Status.COMPLETED, StructureOrientation.Phase.COMPLETED, "orientation_completed");
        }
        // The deadline permits an observed completion receipt, but never another mutation.
        if (serverTick - createdTick == plan.limits().timeoutTicks()) {
            return finish(StructureOperation.Status.BLOCKED, StructureOrientation.Phase.EXPIRED, "orientation_timeout");
        }
        var command = braking
                ? OrientationController.stop(state, StructureOrientation.StopMode.BRAKE, plan.limits(), seconds)
                : spin.map(request -> OrientationController.spin(state, request.axis(), request.frame(),
                        request.angularSpeed(), plan.limits(), seconds)).orElseGet(() ->
                        OrientationController.rotate(state, plan.targetRotation().orElseThrow(), plan.limits(), seconds));
        var clearance = backend.sweep(observed, command, seconds, plan.limits().maxShapeTests());
        if (clearance == OrientationSweep.Status.TERRAIN_PENDING) {
            status = StructureOperation.Status.TERRAIN_PENDING;
            detail = "angular_terrain_pending";
            return receipt();
        }
        if (clearance != OrientationSweep.Status.CLEAR) {
            return finish(clearance == OrientationSweep.Status.STALE ? StructureOperation.Status.STALE
                            : StructureOperation.Status.BLOCKED,
                    StructureOrientation.Phase.BLOCKED, "angular_sweep_" + clearance.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (!backend.applyAngularVelocity(observed, command.nextAngularVelocity())) {
            return finish(StructureOperation.Status.STALE, StructureOrientation.Phase.EXPIRED, "angular_commit_rejected");
        }
        controlledSeconds += seconds;
        phase = braking ? StructureOrientation.Phase.BRAKING : StructureOrientation.Phase.CONTROLLING;
        status = StructureOperation.Status.RUNNING;
        detail = braking ? "orientation_braking" : "orientation_controlling";
        return receipt();
    }

    /** Old operations cannot stop their replacements. RELEASE does not clear physical inertia. */
    public StructureOperation.Result<StructureOrientation.Snapshot> stop(
            Binding caller, UUID requestedOperation, StructureOrientation.StopMode mode) {
        return stop(caller, requestedOperation, mode, plan.limits());
    }

    public StructureOperation.Result<StructureOrientation.Snapshot> stop(
            Binding caller, UUID requestedOperation, StructureOrientation.StopMode mode, StructureOrientation.Limits requested) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(requested, "limits");
        if (!binding.equals(caller) || !operationId.equals(requestedOperation)) return denied();
        if (terminal()) return receipt();
        if (mode == StructureOrientation.StopMode.RELEASE_INERTIA) {
            return finish(StructureOperation.Status.COMPLETED, StructureOrientation.Phase.STOPPED, "orientation_stopped");
        }
        var restricted = restrict(plan.limits(), requested);
        plan = new StructureOrientation.Plan(plan.kind(), plan.baselineSource(), plan.targetRotation(), restricted,
                plan.poseRevision(), plan.terrainRevision());
        braking = true;
        phase = StructureOrientation.Phase.BRAKING;
        status = StructureOperation.Status.RUNNING;
        detail = "orientation_braking";
        return receipt();
    }

    /** A stop request can only narrow the original operation's authority and deadline. */
    static StructureOrientation.Limits restrict(StructureOrientation.Limits previous, StructureOrientation.Limits requested) {
        return new StructureOrientation.Limits(Math.min(previous.maxAngularSpeed(), requested.maxAngularSpeed()),
                Math.min(previous.maxAngularAcceleration(), requested.maxAngularAcceleration()),
                Math.min(previous.maxTorque(), requested.maxTorque()), Math.min(previous.timeoutTicks(), requested.timeoutTicks()),
                Math.min(previous.angleTolerance(), requested.angleTolerance()),
                Math.min(previous.angularSpeedTolerance(), requested.angularSpeedTolerance()),
                Math.min(previous.maxRetreatDistance(), requested.maxRetreatDistance()),
                Math.min(previous.maxShapeTests(), requested.maxShapeTests()));
    }

    /** Allows an adapter to choose a real substep ending exactly at the finite spin deadline. */
    public double remainingSpinSeconds() {
        return spin.isPresent() && !braking && !terminal()
                ? Math.max(0.0, spin.get().durationTicks() * 0.05 - controlledSeconds) : 0.0;
    }

    private boolean terminal() {
        return phase == StructureOrientation.Phase.COMPLETED || phase == StructureOrientation.Phase.STOPPED
                || phase == StructureOrientation.Phase.BLOCKED || phase == StructureOrientation.Phase.EXPIRED;
    }

    private StructureOperation.Result<StructureOrientation.Snapshot> finish(
            StructureOperation.Status result, StructureOrientation.Phase nextPhase, String reason) {
        status = result;
        phase = nextPhase;
        detail = reason;
        return receipt();
    }

    private StructureOperation.Result<StructureOrientation.Snapshot> receipt() {
        var snapshot = new StructureOrientation.Snapshot(operationId, binding.controlId(), phase, plan,
                angleError, angularSpeed, lastTick - createdTick);
        return new StructureOperation.Result<>(status, detail, Optional.of(operationId), Optional.of(snapshot));
    }

    private static StructureOperation.Result<StructureOrientation.Snapshot> denied() {
        return new StructureOperation.Result<>(StructureOperation.Status.DENIED, "orientation_receipt_not_owned",
                Optional.empty(), Optional.empty());
    }
}
