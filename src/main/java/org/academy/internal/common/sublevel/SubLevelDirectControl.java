package org.academy.internal.common.sublevel;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One tick of bounded force, sharing the same exclusive lease as program controls. */
public final class SubLevelDirectControl {
    private static final Map<StructureRef, Command> COMMANDS = new HashMap<>();
    /** Orientation hold keyed by the same exclusive control lease as the force command. */
    private static final Map<StructureRef, Hold> HOLDS = new HashMap<>();
    private static final double ORIENTATION_KP = 6.0;
    private static final double ORIENTATION_KD = 4.0;
    private static final double MAX_ORIENTATION_ACCEL = 6.0;
    private static final double MAX_ORIENTATION_SPEED = 1.0;
    private static final double ORIENTATION_DEADBAND_ANGLE = 0.003;
    private static final double ORIENTATION_DEADBAND_SPEED = 0.02;
    /** Freeze the source pose for the first few solver steps before the lift force starts. */
    private static final int STABILIZE_TICKS = 10;

    private SubLevelDirectControl() {}

    public static StructureDirectControl.Probe probe(ServerLevel host, StructureRef reference) {
        var handle = SubLevelQueries.resolve(host.getServer(), reference).orElse(null);
        if (handle == null || !host.dimension().identifier().equals(reference.dimension())
                || SubLevelQueries.motion(host, reference).isEmpty()) {
            return StructureDirectControl.Probe.unavailable("sublevel_unavailable");
        }
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (runtime == null || runtime.universe().isSimulationPaused(new SubLevelId(reference.id()))) {
            return StructureDirectControl.Probe.unavailable("sublevel_terrain_unavailable");
        }
        return StructureDirectControl.Probe.ready(handle.runtimeId());
    }

    public static boolean submit(ServerLevel host, StructureRef reference, Entity actor, UUID controlId,
                                 StructurePhysicsControl control) {
        return submit(host, reference, actor, controlId, control, false);
    }

    public static boolean submit(ServerLevel host, StructureRef reference, Entity actor, UUID controlId,
                                 StructurePhysicsControl control, boolean holdOrientation) {
        if (StructureDirectControl.validate(host, reference, actor, controlId) != StructureDirectControl.Status.ACTIVE) {
            return false;
        }
        if (!validPoint(host, reference, control)) return false;
        SubLevelStructureRefs.id(reference).ifPresent(id -> SubLevelTerrainBreaker.remember(host, id, actor));
        var now = host.getServer().getTickCount();
        if (holdOrientation) {
            var motion = SubLevelQueries.motion(host, reference).orElse(null);
            if (motion != null) {
                var hold = HOLDS.get(reference);
                if (hold == null || !hold.controlId.equals(controlId)) {
                    // Skill physicalization starts from rest: a capture-adjacent disturbance must
                    // not pre-rotate or translate the body before the orientation hold engages.
                    var runtime = SubLevelServer.runtime(host).orElse(null);
                    if (runtime != null) {
                        runtime.universe().setVelocity(new SubLevelId(reference.id()),
                                new PhysicsVector(0, 0, 0), new PhysicsVector(0, 0, 0));
                    }
                    HOLDS.put(reference, new Hold(controlId, motion.pose(), now, now + STABILIZE_TICKS));
                } else {
                    hold.lastSeen = now;
                }
            }
        } else {
            var hold = HOLDS.get(reference);
            if (hold != null && !hold.controlId.equals(controlId)) HOLDS.remove(reference);
        }
        COMMANDS.put(reference, new Command(host, actor, controlId, control, now));
        return true;
    }

    /** Holds an explicit source pose and normalizes the body to it on the first control tick. */
    public static boolean submit(ServerLevel host, StructureRef reference, Entity actor, UUID controlId,
                                 StructurePhysicsControl control, StructurePose holdPose) {
        if (StructureDirectControl.validate(host, reference, actor, controlId) != StructureDirectControl.Status.ACTIVE) {
            return false;
        }
        if (!validPoint(host, reference, control)) return false;
        SubLevelStructureRefs.id(reference).ifPresent(id -> SubLevelTerrainBreaker.remember(host, id, actor));
        var now = host.getServer().getTickCount();
        var hold = HOLDS.get(reference);
        if (hold == null || !hold.controlId.equals(controlId)) {
            var runtime = SubLevelServer.runtime(host).orElse(null);
            if (runtime != null) {
                var id = new SubLevelId(reference.id());
                var pose = new PhysicsPose(
                        new PhysicsVector(holdPose.worldOrigin().x, holdPose.worldOrigin().y,
                                holdPose.worldOrigin().z),
                        new PhysicsQuaternion(holdPose.rotationX(), holdPose.rotationY(),
                                holdPose.rotationZ(), holdPose.rotationW()));
                if (runtime.universe().setPose(id, pose)) {
                    runtime.universe().setVelocity(id,
                            new PhysicsVector(0, 0, 0), new PhysicsVector(0, 0, 0));
                }
            }
            HOLDS.put(reference, new Hold(controlId, holdPose, now, now + STABILIZE_TICKS));
        } else {
            hold.lastSeen = now;
        }
        COMMANDS.put(reference, new Command(host, actor, controlId, control, now));
        return true;
    }

    public static void cancel(StructureRef reference, UUID controlId) {
        var pending = COMMANDS.get(reference);
        if (pending != null && pending.controlId.equals(controlId)) COMMANDS.remove(reference);
        var hold = HOLDS.get(reference);
        if (hold != null && hold.controlId.equals(controlId)) HOLDS.remove(reference);
        // The terrain-break grant deliberately outlives a released control for a bounded window,
        // so a thrown structure keeps its original actor while its inertia carries it.
        SubLevelStructureRefs.id(reference).filter(id -> SubLevelActivationRegistry.find(id)
                .map(activation -> activation.generation() == reference.generation()).orElse(false))
                .ifPresent(SubLevelMotionController::cancel);
    }

    public static void tick(MinecraftServer server) {
        var iterator = COMMANDS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var command = entry.getValue();
            if (command.host.getServer() != server) continue;
            iterator.remove();
            if (!command.actor.isAlive() || command.actor.isRemoved() || command.actor.level() != command.host
                    || Integer.compareUnsigned(server.getTickCount() - command.tick, 1) > 0
                    || StructureDirectControl.validate(command.host, entry.getKey(), command.actor, command.controlId)
                    != StructureDirectControl.Status.ACTIVE) continue;
            if (!validPoint(command.host, entry.getKey(), command.control)) continue;
            var motion = SubLevelQueries.motion(command.host, entry.getKey()).orElse(null);
            var runtime = SubLevelServer.runtime(command.host).orElse(null);
            if (motion == null || runtime == null) continue;
            var state = new StructureMotionState(motion.pose(), motion.linearVelocity(), motion.angularVelocity(),
                    motion.localCenterOfMass(), motion.mass(), false, 0, 0);
            var impulse = StructurePhysicsControls.force(command.control, state).scale(0.05);
            var point = motion.pose().localToWorldPoint(command.control.localPoint());
            var id = new SubLevelId(entry.getKey().id());
            var hold = HOLDS.get(entry.getKey());
            if (hold != null && hold.controlId.equals(command.controlId)
                    && server.getTickCount() <= hold.stabilizeUntil) {
                // The source pose was applied once when the hold was created. Only clear the
                // velocities here; re-teleporting every tick would visibly jitter the client.
                runtime.universe().setVelocity(id,
                        new PhysicsVector(0, 0, 0), new PhysicsVector(0, 0, 0));
                hold.lastSeen = server.getTickCount();
                continue;
            }
            runtime.universe().impulse(id,
                    new PhysicsVector(impulse.x, impulse.y, impulse.z), new PhysicsVector(point.x, point.y, point.z));
            if (hold != null && hold.controlId.equals(command.controlId)) {
                var after = SubLevelQueries.motion(command.host, entry.getKey()).orElse(null);
                if (after != null) {
                    var angular = nextAngularVelocity(hold.target, after.pose(), after.angularVelocity());
                    runtime.universe().setAngularVelocity(id, angular);
                    hold.lastSeen = server.getTickCount();
                }
            }
        }
        HOLDS.entrySet().removeIf(entry -> server.getTickCount() - entry.getValue().lastSeen > 1L);
    }

    public static void clear() {
        COMMANDS.clear();
        HOLDS.clear();
        SubLevelTerrainBreaker.clear();
    }

    /**
     * Critically damped angular-acceleration servo. Unlike replacing angular velocity with a
     * large PD target every tick, this integrates a bounded acceleration and stops inside a
     * small deadband, so contact impulses cannot make the body visibly reverse direction.
     */
    private static PhysicsVector nextAngularVelocity(StructurePose target, StructurePose current,
                                                      Vec3 angularVelocity) {
        var error = new Quaterniond(target.rotation())
                .mul(new Quaterniond(current.rotation()).conjugate());
        if (error.w < 0.0) {
            error.x = -error.x;
            error.y = -error.y;
            error.z = -error.z;
            error.w = -error.w;
        }
        double w = Math.max(-1.0, Math.min(1.0, error.w));
        double sin = Math.sqrt(Math.max(0.0, 1.0 - w * w));
        double angle = 2.0 * Math.acos(w);
        var omega = angularVelocity;
        if (angle < ORIENTATION_DEADBAND_ANGLE
                && Math.hypot(Math.hypot(omega.x, omega.y), omega.z) < ORIENTATION_DEADBAND_SPEED) {
            return new PhysicsVector(0, 0, 0);
        }
        var errorVector = sin < 1.0E-9
                ? Vec3.ZERO
                : new Vec3(error.x / sin, error.y / sin, error.z / sin).scale(angle);
        var acceleration = errorVector.scale(ORIENTATION_KP).subtract(omega.scale(ORIENTATION_KD));
        double accel = acceleration.length();
        if (accel > MAX_ORIENTATION_ACCEL) {
            acceleration = acceleration.scale(MAX_ORIENTATION_ACCEL / accel);
        }
        var next = omega.add(acceleration.scale(0.05));
        double speed = next.length();
        if (speed > MAX_ORIENTATION_SPEED) next = next.scale(MAX_ORIENTATION_SPEED / speed);
        return new PhysicsVector(next.x, next.y, next.z);
    }

    private static PhysicsPose physicsPose(StructurePose pose) {
        return new PhysicsPose(
                new PhysicsVector(pose.worldOrigin().x, pose.worldOrigin().y, pose.worldOrigin().z),
                new PhysicsQuaternion(pose.rotationX(), pose.rotationY(), pose.rotationZ(), pose.rotationW()));
    }

    private static final class Hold {
        private final UUID controlId;
        private final StructurePose target;
        private final long stabilizeUntil;
        private long lastSeen;

        private Hold(UUID controlId, StructurePose target, long lastSeen, long stabilizeUntil) {
            this.controlId = controlId;
            this.target = target;
            this.lastSeen = lastSeen;
            this.stabilizeUntil = stabilizeUntil;
        }
    }

    private static boolean validPoint(ServerLevel host, StructureRef reference, StructurePhysicsControl control) {
        var runtime = SubLevelServer.runtime(host).orElse(null);
        var body = runtime == null ? null : runtime.get(new SubLevelId(reference.id())).orElse(null);
        if (body == null || !body.localBounds().inflate(1.0E-6).contains(control.localPoint())) return false;
        var point = body.pose().localToWorldPoint(control.localPoint());
        return Math.abs(point.x) <= 1_000_000 && Math.abs(point.y) <= 1_000_000 && Math.abs(point.z) <= 1_000_000;
    }

    private record Command(ServerLevel host, Entity actor, UUID controlId, StructurePhysicsControl control, int tick) {}
}
