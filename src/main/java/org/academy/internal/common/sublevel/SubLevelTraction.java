package org.academy.internal.common.sublevel;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

import static org.academy.api.common.structure.StructureTraction.Status.*;

/** Point traction over the same exclusive SubLevel lease used by direct skills and programs. */
public final class SubLevelTraction {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SubLevelTraction() {}

    public static StructureTraction.Acquisition acquire(Entity actor, UUID controllerId, StructureHit proposed,
                                                         StructureTraction.Settings settings,
                                                         BiPredicate<Entity, StructureHandle> permission) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(controllerId, "controllerId");
        Objects.requireNonNull(proposed, "hit");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(permission, "permission");
        if (!(actor.level() instanceof ServerLevel host)) return failed(NOT_AVAILABLE);
        var server = host.getServer();
        requireThread(server);
        var reference = proposed.block().structure();
        var handle = SubLevelQueries.resolve(server, reference).orElse(null);
        if (handle == null || !handle.runtimeId().equals(proposed.runtimeId()) || !liveActor(host, actor)) {
            return failed(NOT_AVAILABLE);
        }
        if (!permission.test(actor, handle)) return failed(DENIED);
        if (!liveActor(host, actor) || SubLevelQueries.resolve(server, reference)
                .filter(current -> current.runtimeId().equals(proposed.runtimeId())).isEmpty()) return failed(NOT_AVAILABLE);
        var eye = actor.getEyePosition();
        var direction = SubLevelMotionController.normalized(actor.getLookAngle());
        if (!SubLevelMotionController.finite(eye) || direction == null) return failed(INVALID_TARGET);
        var validated = StructureQueries.validate(host, eye, eye.add(direction.scale(settings.maxRange())), proposed);
        if (!validated.succeeded()) return failed(NOT_VISIBLE);
        var hit = validated.currentHit().orElseThrow();
        var motion = SubLevelQueries.motion(host, reference).orElse(null);
        if (motion == null) return failed(NOT_AVAILABLE);
        if (motion.mass() > settings.maxMass()) return failed(TOO_HEAVY);
        var body = SubLevelServer.runtime(host).flatMap(runtime -> runtime.get(new SubLevelId(reference.id()))).orElse(null);
        if (body == null || !body.localBounds().inflate(1.0E-6).contains(hit.localPoint())) return failed(INVALID_TARGET);
        if (!acceptsTarget(host, body, hit.worldPoint(), hit.worldPoint(), Vec3.ZERO)) return failed(OUT_OF_BOUNDS);

        // Each returned session gets a distinct token, even when a caller reuses controllerId.
        // Closing an older session can therefore never release its successor.
        var token = UUID.randomUUID();
        var acquired = StructureDirectControl.acquire(host, reference, actor, token);
        if (acquired != StructureDirectControl.Status.ACTIVE) {
            return failed(acquired == StructureDirectControl.Status.OCCUPIED ? OCCUPIED : NOT_AVAILABLE);
        }
        var session = new Session(host, actor, reference, hit.runtimeId(), token, hit.localPoint(),
                hit.worldPoint(), settings, permission);
        try {
            if (!session.submit()) {
                session.finish(NOT_AVAILABLE);
                return failed(NOT_AVAILABLE);
            }
            SESSIONS.put(token, session);
            return new StructureTraction.Acquisition(ACTIVE, Optional.of(session));
        } catch (RuntimeException | Error failure) {
            session.finish(NOT_AVAILABLE);
            throw failure;
        }
    }

    /** Applies the last accepted request until its short lease expires; never renews the lease. */
    public static void tick(MinecraftServer server) {
        requireThread(server);
        for (var session : List.copyOf(SESSIONS.values())) {
            if (session.host.getServer() != server || session.status() != ACTIVE) continue;
            if (!session.submit()) session.finish(NOT_AVAILABLE);
        }
    }

    public static void clear(MinecraftServer server) {
        requireThread(server);
        for (var session : List.copyOf(SESSIONS.values())) {
            if (session.host.getServer() == server) session.finish(NOT_AVAILABLE);
        }
    }

    private static final class Session implements StructureTractionSession {
        private final ServerLevel host;
        private final Entity actor;
        private final UUID actorId;
        private final StructureRef reference;
        private final UUID runtimeId;
        private final UUID token;
        private final Vec3 localPoint;
        private final StructureTraction.Settings settings;
        private final BiPredicate<Entity, StructureHandle> permission;
        private StructureTraction.Status state = ACTIVE;
        private Vec3 observedPoint;
        private Vec3 target;
        private Vec3 targetVelocity = Vec3.ZERO;
        private long expiresAt;
        private boolean updating;

        private Session(ServerLevel host, Entity actor, StructureRef reference, UUID runtimeId, UUID token,
                        Vec3 localPoint, Vec3 point, StructureTraction.Settings settings,
                        BiPredicate<Entity, StructureHandle> permission) {
            this.host = host;
            this.actor = actor;
            actorId = actor.getUUID();
            this.reference = reference;
            this.runtimeId = runtimeId;
            this.token = token;
            this.localPoint = localPoint;
            this.settings = settings;
            this.permission = permission;
            observedPoint = point;
            target = point;
            expiresAt = host.getGameTime() + settings.leaseTicks();
        }

        @Override public StructureRef reference() { requireThread(host.getServer()); return reference; }
        @Override public UUID runtimeId() { requireThread(host.getServer()); return runtimeId; }
        @Override public Vec3 localPoint() { requireThread(host.getServer()); return localPoint; }
        @Override public Vec3 targetPosition() { requireThread(host.getServer()); return target; }

        @Override public Vec3 worldPoint() {
            requireThread(host.getServer());
            if (state == ACTIVE) {
                SubLevelQueries.resolve(host.getServer(), reference).filter(handle -> handle.runtimeId().equals(runtimeId))
                        .ifPresent(handle -> observedPoint = handle.pose().localToWorldPoint(localPoint));
            }
            return observedPoint;
        }

        @Override public StructureTraction.Status status() {
            requireThread(host.getServer());
            if (state != ACTIVE) return state;
            if (host.getGameTime() >= expiresAt || !actorId.equals(actor.getUUID()) || !liveActor(host, actor)
                    || SubLevelQueries.resolve(host.getServer(), reference)
                    .filter(handle -> handle.runtimeId().equals(runtimeId)).isEmpty()
                    || StructureDirectControl.validate(host, reference, actor, token) != StructureDirectControl.Status.ACTIVE) {
                return finish(NOT_AVAILABLE);
            }
            return ACTIVE;
        }

        @Override public StructureTraction.Status update(Vec3 position, Vec3 velocity) {
            requireThread(host.getServer());
            if (updating) throw new IllegalStateException("Cannot reenter a traction update");
            if (status() != ACTIVE) return state;
            if (!SubLevelMotionController.finite(position) || !SubLevelMotionController.finite(velocity)
                    || velocity.length() > StructurePhysicsControl.MAX_TARGET_SPEED) return finish(INVALID_TARGET);
            updating = true;
            try {
                var handle = SubLevelQueries.resolve(host.getServer(), reference).orElseThrow();
                if (!permission.test(actor, handle)) return finish(DENIED);
                if (status() != ACTIVE) return state;
                var pose = handle.pose();
                var point = pose.localToWorldPoint(localPoint);
                var eye = actor.getEyePosition();
                double distance = eye.distanceTo(point);
                if (!Double.isFinite(distance) || distance > settings.maxRange()) return finish(OUT_OF_RANGE);
                if (distance < 1.0E-6) return finish(NOT_VISIBLE);
                var end = eye.add(point.subtract(eye).scale(
                        Math.min(StructureQueries.MAX_RAY_LENGTH, distance + 1.0E-5) / distance));
                var visible = StructureQueries.raycast(host, eye, end).orElse(null);
                if (visible == null || !visible.block().structure().equals(reference)
                        || !visible.runtimeId().equals(runtimeId)) return finish(NOT_VISIBLE);
                var motion = SubLevelQueries.motion(host, reference).orElse(null);
                if (motion == null) return finish(NOT_AVAILABLE);
                if (motion.mass() > settings.maxMass()) return finish(TOO_HEAVY);
                var body = SubLevelServer.runtime(host)
                        .flatMap(runtime -> runtime.get(new SubLevelId(reference.id()))).orElse(null);
                if (body == null || !acceptsTarget(host, body, point, position, velocity)) return finish(OUT_OF_BOUNDS);
                observedPoint = point;
                target = position;
                targetVelocity = velocity;
                if (!submit()) return finish(NOT_AVAILABLE);
                expiresAt = host.getGameTime() + settings.leaseTicks();
                return ACTIVE;
            } catch (RuntimeException | Error failure) {
                finish(NOT_AVAILABLE);
                throw failure;
            } finally {
                updating = false;
            }
        }

        private boolean submit() {
            var motion = SubLevelQueries.motion(host, reference).orElse(null);
            if (motion == null || motion.mass() > settings.maxMass()) return false;
            return StructureDirectControl.submit(host, reference, actor, token,
                    new StructurePhysicsControl.Anchor(localPoint, target, targetVelocity,
                            settings.stiffness(), settings.damping(), settings.maxForce()));
        }

        private StructureTraction.Status finish(StructureTraction.Status reason) {
            if (state == ACTIVE) {
                state = reason;
                SESSIONS.remove(token, this);
                StructureDirectControl.release(reference, token);
            }
            return state;
        }

        @Override public void close() { requireThread(host.getServer()); finish(CLOSED); }
    }

    private static boolean liveActor(ServerLevel host, Entity actor) {
        if (!actor.isAlive() || actor.isRemoved() || actor.level() != host
                || host.getServer().getLevel(host.dimension()) != host || host.getEntity(actor.getUUID()) != actor) return false;
        return !(actor instanceof ServerPlayer player)
                || host.getServer().getPlayerList().getPlayer(actor.getUUID()) == player
                && player.connection != null && player.connection.isAcceptingMessages() && !player.hasDisconnected();
    }

    private static boolean acceptsTarget(ServerLevel host, SubLevel body, Vec3 point, Vec3 target, Vec3 velocity) {
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (runtime == null) return false;
        var origin = runtime.universe().world().origin();
        var border = host.getWorldBorder();
        var limits = new AABB(Math.max(border.getMinX(), origin.x() - 999_999.75),
                Math.max(host.getMinY(), origin.y() - 999_999.75), Math.max(border.getMinZ(), origin.z() - 999_999.75),
                Math.min(border.getMaxX(), origin.x() + 999_999.75), Math.min(host.getMaxY(), origin.y() + 999_999.75),
                Math.min(border.getMaxZ(), origin.z() + 999_999.75));
        return fitsTarget(limits, body.worldBounds(), point, target, velocity);
    }

    static boolean fitsTarget(AABB limits, AABB body, Vec3 point, Vec3 target, Vec3 velocity) {
        if (!SubLevelMotionController.finite(point) || !SubLevelMotionController.finite(target)
                || !SubLevelMotionController.finite(velocity)) return false;
        var moved = body.move(target.subtract(point));
        return contains(limits, body) && contains(limits, moved) && contains(limits, moved.move(velocity.scale(0.05)));
    }

    private static boolean contains(AABB outer, AABB inner) {
        return inner.minX >= outer.minX && inner.minY >= outer.minY && inner.minZ >= outer.minZ
                && inner.maxX <= outer.maxX && inner.maxY <= outer.maxY && inner.maxZ <= outer.maxZ;
    }

    private static StructureTraction.Acquisition failed(StructureTraction.Status status) {
        return new StructureTraction.Acquisition(status, Optional.empty());
    }

    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("SubLevel traction requires the server thread");
    }
}
