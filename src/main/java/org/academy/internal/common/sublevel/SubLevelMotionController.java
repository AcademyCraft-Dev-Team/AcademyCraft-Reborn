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
    private static final double ARRIVAL = 0.2;
    private static final double MAX_SPEED = 8.0;
    private static final int MAX_TICKS = 1_200;

    private SubLevelMotionController() {
    }

    public static boolean moveTo(MinecraftServer server, SubLevelId id, Vec3 target, double seconds) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) return false;
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null || runtime.get(id).isEmpty()) return false;
        long duration = Math.max(1L, Math.min(MAX_TICKS, (long) Math.ceil(seconds * 20.0)));
        THRUSTS.remove(id);
        SESSIONS.put(id, new Session(target, server.overworld().getGameTime() + duration));
        return true;
    }

    /** Applies a constant world-space velocity for a bounded duration. */
    public static boolean thrust(MinecraftServer server, SubLevelId id, Vec3 direction, double speed,
                                 double seconds) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        if (!Double.isFinite(speed) || speed <= 0.0 || direction.lengthSqr() < 1.0E-12) return false;
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null || runtime.get(id).isEmpty()) return false;
        long duration = Math.max(1L, Math.min(MAX_TICKS, (long) Math.ceil(seconds * 20.0)));
        SESSIONS.remove(id);
        THRUSTS.put(id, new Thrust(direction.normalize(), Math.min(MAX_SPEED, speed),
                server.overworld().getGameTime() + duration));
        return true;
    }

    public static void cancel(SubLevelId id) {
        SESSIONS.remove(id);
        THRUSTS.remove(id);
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty() && THRUSTS.isEmpty()) return;
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) {
            SESSIONS.clear();
            THRUSTS.clear();
            return;
        }
        var thrustIterator = THRUSTS.entrySet().iterator();
        while (thrustIterator.hasNext()) {
            var entry = thrustIterator.next();
            var subLevel = runtime.get(entry.getKey()).orElse(null);
            var thrust = entry.getValue();
            if (subLevel == null || server.overworld().getGameTime() > thrust.expiresAt
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
            var subLevel = runtime.get(entry.getKey()).orElse(null);
            var session = entry.getValue();
            if (subLevel == null || server.overworld().getGameTime() > session.expiresAt) {
                iterator.remove();
                continue;
            }
            if (subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING) {
                iterator.remove();
                continue;
            }
            var center = subLevel.pose().localToWorldPoint(subLevel.localBounds().getCenter());
            var delta = session.target.subtract(center);
            double distance = delta.length();
            if (distance <= ARRIVAL) {
                iterator.remove();
                continue;
            }
            double speed = Math.min(MAX_SPEED, Math.max(0.5, distance * 4.0));
            var velocity = delta.normalize().scale(speed);
            if (!runtime.universe().setVelocity(entry.getKey(),
                    new PhysicsVector(velocity.x, velocity.y, velocity.z), PhysicsVector.ZERO)) {
                iterator.remove();
            }
        }
    }

    private record Session(Vec3 target, long expiresAt) {
    }

    private record Thrust(Vec3 direction, double speed, long expiresAt) {
    }
}
