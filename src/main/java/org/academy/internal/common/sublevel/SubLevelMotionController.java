package org.academy.internal.common.sublevel;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded continuous move sessions for plot-backed SubLevels. */
public final class SubLevelMotionController {
    private static final Map<SubLevelId, Session> SESSIONS = new HashMap<>();
    private static final Map<SubLevelId, Thrust> THRUSTS = new HashMap<>();
    private static final double MAX_SPEED = 8.0;
    private static final int MAX_TICKS = 1_200;

    private SubLevelMotionController() {
    }

    public static boolean moveTo(MinecraftServer server, SubLevelId id, Vec3 target, double seconds) {
        var subLevel = SubLevelServer.runtime(server, id).flatMap(runtime -> runtime.get(id)).orElse(null);
        return subLevel != null && moveTo(server, id, subLevel.localBounds().getCenter(), target, seconds);
    }

    /** Moves a selected local anchor, retaining that anchor across rotation. */
    public static boolean moveTo(MinecraftServer server, SubLevelId id, Vec3 localAnchor,
                                 Vec3 target, double seconds) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        if (!finite(target) || !finite(localAnchor) || durationTicks(seconds) == 0) return false;
        var runtime = SubLevelServer.runtime(server, id).orElse(null);
        var subLevel = runtime == null ? null : runtime.get(id).orElse(null);
        if (subLevel == null || subLevel.state() != SubLevelState.ACTIVE
                && subLevel.state() != SubLevelState.SLEEPING) return false;
        if (runtime.universe().isSimulationPaused(id)) return false;
        long duration = durationTicks(seconds);
        THRUSTS.remove(id);
        SESSIONS.put(id, new Session(server, localAnchor, target, runtime.host().getGameTime() + duration,
                SubLevelActivationRegistry.activation(id)));
        return true;
    }

    /** Applies a constant world-space velocity for a bounded duration. */
    public static boolean thrust(MinecraftServer server, SubLevelId id, Vec3 direction, double speed,
                                 double seconds) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        var unit = normalized(direction);
        if (!Double.isFinite(speed) || speed <= 0.0 || unit == null || durationTicks(seconds) == 0) return false;
        var runtime = SubLevelServer.runtime(server, id).orElse(null);
        var subLevel = runtime == null ? null : runtime.get(id).orElse(null);
        if (subLevel == null || subLevel.state() != SubLevelState.ACTIVE
                && subLevel.state() != SubLevelState.SLEEPING) return false;
        if (runtime.universe().isSimulationPaused(id)) return false;
        long duration = durationTicks(seconds);
        SESSIONS.remove(id);
        THRUSTS.put(id, new Thrust(server, unit, Math.min(MAX_SPEED, speed),
                runtime.host().getGameTime() + duration, SubLevelActivationRegistry.activation(id)));
        return true;
    }

    public static void cancel(SubLevelId id) {
        SESSIONS.remove(id);
        THRUSTS.remove(id);
    }

    /** Releases all static motion state at a server lifecycle boundary. */
    public static void clear() {
        SESSIONS.clear();
        THRUSTS.clear();
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty() && THRUSTS.isEmpty()) return;
        var thrustIterator = THRUSTS.entrySet().iterator();
        while (thrustIterator.hasNext()) {
            var entry = thrustIterator.next();
            var thrust = entry.getValue();
            if (thrust.server != server) continue;
            var runtime = SubLevelServer.runtime(server, entry.getKey()).orElse(null);
            var subLevel = runtime == null ? null : runtime.get(entry.getKey()).orElse(null);
            if (subLevel == null || runtime.host().getGameTime() >= thrust.expiresAt
                    || !matches(entry.getKey(), thrust.activation)
                    || subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING) {
                thrustIterator.remove();
                continue;
            }
            var velocity = thrust.direction.scale(thrust.speed);
            if (!runtime.universe().setVelocity(entry.getKey(),
                    new PhysicsVector(velocity.x, velocity.y, velocity.z), PhysicsVector.ZERO)) {
                thrustIterator.remove();
            }
        }
        var iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var session = entry.getValue();
            if (session.server != server) continue;
            var runtime = SubLevelServer.runtime(server, entry.getKey()).orElse(null);
            var subLevel = runtime == null ? null : runtime.get(entry.getKey()).orElse(null);
            if (subLevel == null || runtime.host().getGameTime() >= session.expiresAt
                    || !matches(entry.getKey(), session.activation)) {
                iterator.remove();
                continue;
            }
            if (subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING) {
                iterator.remove();
                continue;
            }
            var anchor = subLevel.pose().localToWorldPoint(session.localAnchor);
            var velocity = approachVelocity(session.target.subtract(anchor));
            if (!runtime.universe().setVelocity(entry.getKey(),
                    new PhysicsVector(velocity.x, velocity.y, velocity.z), PhysicsVector.ZERO)) {
                iterator.remove();
            }
        }
    }

    static int durationTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0.0) return 0;
        return (int) Math.max(1.0, Math.min(MAX_TICKS, Math.ceil(seconds * 20.0)));
    }

    static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    static Vec3 normalized(Vec3 direction) {
        if (!finite(direction)) return null;
        double scale = Math.max(Math.abs(direction.x), Math.max(Math.abs(direction.y), Math.abs(direction.z)));
        if (scale < 1.0E-12) return null;
        return new Vec3(direction.x / scale, direction.y / scale, direction.z / scale).normalize();
    }

    /** No minimum speed: the final physics step must brake instead of passing the target. */
    static Vec3 approachVelocity(Vec3 delta) {
        var unit = normalized(delta);
        if (unit == null) return Vec3.ZERO;
        double distance = Math.hypot(Math.hypot(delta.x, delta.y), delta.z);
        return unit.scale(Math.min(MAX_SPEED, distance * 4.0));
    }

    private static boolean matches(SubLevelId id, SubLevelActivationRegistry.Activation activation) {
        return SubLevelActivationRegistry.matches(id, activation.runtimeId(), activation.generation());
    }

    private record Session(MinecraftServer server, Vec3 localAnchor, Vec3 target, long expiresAt,
                           SubLevelActivationRegistry.Activation activation) {
    }

    private record Thrust(MinecraftServer server, Vec3 direction, double speed, long expiresAt,
                          SubLevelActivationRegistry.Activation activation) {
    }
}
