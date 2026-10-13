package org.academy.internal.common.structure.physics;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.structure.NativeStructureRuntime;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

import static org.academy.api.common.structure.StructureTraction.Status.*;

/** Actor-independent traction policy; skills own activation, resource accounting and effects. */
public final class NativeStructureTraction {
    private NativeStructureTraction() {}

    public static StructureTraction.Acquisition acquire(Entity actor, UUID controllerId, StructureHit proposed,
                                                         StructureTraction.Settings settings,
                                                         BiPredicate<Entity, StructureHandle> permission) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(controllerId, "controllerId");
        Objects.requireNonNull(proposed, "hit");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(permission, "permission");
        if (!(actor.level() instanceof ServerLevel host)) return failure(NOT_AVAILABLE);
        var server = host.getServer();
        requireThread(server);
        var runtime = NativeStructureRuntime.existing(server).orElse(null);
        if (runtime == null) return failure(NOT_AVAILABLE);
        NativeStructureControlActor guard;
        try { guard = new NativeStructureControlActor(actor, host, server); }
        catch (NativeStructureControlActor.UnavailableActor unavailable) { return failure(NOT_AVAILABLE); }
        var reference = proposed.block().structure();
        var region = runtime.activePhysicsRegion(host, reference).orElse(null);
        if (!(region instanceof NativeStructurePhysics.Region nativeRegion)) return failure(NOT_AVAILABLE);
        var handle = runtime.resolve(reference).orElse(null);
        if (handle == null || !handle.runtimeId().equals(proposed.runtimeId())) return failure(NOT_AVAILABLE);
        if (!permission.test(actor, handle)) return failure(DENIED);
        if (!available(runtime, host, nativeRegion, handle, guard)) return failure(NOT_AVAILABLE);
        var eye = actor.getEyePosition();
        var look = actor.getLookAngle();
        if (!finite(eye) || !finite(look) || look.lengthSqr() < 1.0E-12) return failure(INVALID_TARGET);
        var validated = StructureQueries.validate(host, eye, eye.add(look.normalize().scale(settings.maxRange())), proposed);
        if (!validated.succeeded()) return failure(NOT_VISIBLE);
        var hit = validated.currentHit().orElseThrow();
        if (!available(runtime, host, nativeRegion, handle, guard)) return failure(NOT_AVAILABLE);
        var motion = region.motion(reference).orElse(null);
        if (motion == null) return failure(NOT_AVAILABLE);
        if (motion.mass() > settings.maxMass()) return failure(TOO_HEAVY);
        var localBounds = nativeRegion.localControlBounds(reference).orElse(null);
        if (localBounds == null) return failure(NOT_AVAILABLE);
        if (!containsPoint(localBounds, hit.localPoint())) return failure(INVALID_TARGET);
        var point = handle.pose().localToWorldPoint(hit.localPoint());
        var body = bodyBounds(nativeRegion, handle).orElse(null);
        if (body == null || !nativeRegion.tractionAvailable(reference)) return failure(NOT_AVAILABLE);
        if (!acceptsTarget(host, nativeRegion, body, point, point, Vec3.ZERO)) return failure(OUT_OF_BOUNDS);
        var control = anchor(hit.localPoint(), point, Vec3.ZERO, settings);
        Optional<StructurePhysicsLease> acquired;
        try { acquired = region.acquireEntityControl(reference, actor, controllerId, control, settings.leaseTicks()); }
        catch (IllegalStateException | IllegalArgumentException rejection) {
            if (!available(runtime, host, nativeRegion, handle, guard) || !nativeRegion.tractionAvailable(reference)) {
                return failure(NOT_AVAILABLE);
            }
            throw rejection;
        }
        if (acquired.isEmpty()) return failure(OCCUPIED);
        var lease = acquired.orElseThrow();
        try {
            var session = new Session(server, host, runtime, nativeRegion, handle, actor, guard,
                    lease, hit.localPoint(), point, settings, permission);
            return new StructureTraction.Acquisition(ACTIVE, Optional.of(session));
        } catch (RuntimeException | Error failure) {
            try { release(nativeRegion, lease); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static final class Session implements StructureTractionSession {
        private final MinecraftServer server;
        private final ServerLevel host;
        private final NativeStructureRuntime runtime;
        private final NativeStructurePhysics.Region region;
        private final StructureHandle handle;
        private final Entity actor;
        private final NativeStructureControlActor guard;
        private final StructurePhysicsLease lease;
        private final StructureRef reference;
        private final UUID runtimeId;
        private final Vec3 localPoint;
        private final StructureTraction.Settings settings;
        private final BiPredicate<Entity, StructureHandle> permission;
        private StructureTraction.Status state = ACTIVE;
        private Vec3 observedPoint, target;
        private boolean updating;

        private Session(MinecraftServer server, ServerLevel host, NativeStructureRuntime runtime,
                        NativeStructurePhysics.Region region, StructureHandle handle, Entity actor,
                        NativeStructureControlActor guard, StructurePhysicsLease lease, Vec3 localPoint,
                        Vec3 point, StructureTraction.Settings settings,
                        BiPredicate<Entity, StructureHandle> permission) {
            this.server = server; this.host = host; this.runtime = runtime; this.region = region;
            this.handle = handle; this.actor = actor; this.guard = guard; this.lease = lease;
            this.localPoint = localPoint; this.settings = settings; this.permission = permission;
            reference = handle.reference(); runtimeId = handle.runtimeId(); observedPoint = point; target = point;
        }

        @Override public StructureRef reference() { requireThread(server); return reference; }
        @Override public UUID runtimeId() { requireThread(server); return runtimeId; }
        @Override public Vec3 localPoint() { requireThread(server); return localPoint; }
        @Override public Vec3 targetPosition() { requireThread(server); return target; }

        @Override public Vec3 worldPoint() {
            requireThread(server);
            try {
                if (state == ACTIVE && handle.lifecycle() == StructureLifecycle.ACTIVE
                        && handle.runtimeId().equals(runtimeId)) return handle.pose().localToWorldPoint(localPoint);
            } catch (IllegalStateException unavailable) { }
            return observedPoint;
        }

        @Override public StructureTraction.Status status() {
            requireThread(server);
            return state == ACTIVE && lease.state() != StructurePhysicsLease.State.ACTIVE ? NOT_AVAILABLE : state;
        }

        @Override public StructureTraction.Status update(Vec3 targetPosition, Vec3 targetVelocity) {
            requireThread(server);
            if (updating) throw new IllegalStateException("Cannot reenter a traction update");
            if (status() != ACTIVE) return finish(status());
            if (!finite(targetPosition) || !finite(targetVelocity)
                    || targetVelocity.length() > StructurePhysicsControl.MAX_TARGET_SPEED) return finish(INVALID_TARGET);
            updating = true;
            try {
                if (!available(runtime, host, region, handle, guard)) return finish(NOT_AVAILABLE);
                if (!permission.test(actor, handle)) return finish(DENIED);
                // A policy callback may remove the actor, unload the handle or stop the backend.
                if (!available(runtime, host, region, handle, guard) || lease.state() != StructurePhysicsLease.State.ACTIVE) {
                    return finish(NOT_AVAILABLE);
                }
                var pose = handle.pose();
                var point = pose.localToWorldPoint(localPoint);
                var eye = actor.getEyePosition();
                if (!finite(eye) || eye.distanceTo(point) > settings.maxRange()) return finish(OUT_OF_RANGE);
                double distance = eye.distanceTo(point);
                if (!Double.isFinite(distance) || distance < 1.0E-6) return finish(NOT_VISIBLE);
                var end = eye.add(point.subtract(eye).scale(Math.min(StructureQueries.MAX_RAY_LENGTH, distance + 1.0E-5) / distance));
                // Reaching any nearer selection face of this exact structure is sufficient.
                // Host blocks and other structures still win the existing loaded-only raycast.
                var visible = StructureQueries.raycast(host, eye, end).orElse(null);
                if (visible == null || !visible.block().structure().equals(reference)
                        || !visible.runtimeId().equals(runtimeId)) return finish(NOT_VISIBLE);
                if (!available(runtime, host, region, handle, guard) || !handle.pose().equals(pose)
                        || lease.state() != StructurePhysicsLease.State.ACTIVE) return finish(NOT_AVAILABLE);
                var motion = region.motion(reference).orElse(null);
                if (motion == null) return finish(NOT_AVAILABLE);
                if (motion.mass() > settings.maxMass()) return finish(TOO_HEAVY);
                var body = bodyBounds(region, handle).orElse(null);
                if (body == null) return finish(NOT_AVAILABLE);
                if (!acceptsTarget(host, region, body, point, targetPosition, targetVelocity)) {
                    // One bounded rebase per update: a pull whose target crosses the current box
                    // follows it instead of forcing every long-range move to end at the edge.
                    var border = host.getWorldBorder();
                    var limits = new AABB(border.getMinX(), host.getMinY(), border.getMinZ(),
                            border.getMaxX(), host.getMaxY(), border.getMaxZ());
                    var candidate = NativeStructureTractionBounds.recentered(region.bounds(), body, point,
                            targetPosition, targetVelocity, host.getMinY(), host.getMaxY(), limits);
                    if (candidate == null || !region.rebind(candidate)
                            || !acceptsTarget(host, region, body, point, targetPosition, targetVelocity)) {
                        return finish(OUT_OF_BOUNDS);
                    }
                }
                var control = anchor(localPoint, targetPosition, targetVelocity, settings);
                try {
                    lease.setControl(control);
                    lease.renew(settings.leaseTicks());
                } catch (IllegalStateException | IllegalArgumentException rejection) {
                    if (lease.state() != StructurePhysicsLease.State.ACTIVE
                            || !available(runtime, host, region, handle, guard)
                            || !region.tractionAvailable(reference)) return finish(NOT_AVAILABLE);
                    throw rejection;
                }
                observedPoint = point; target = targetPosition;
                return ACTIVE;
            } catch (RuntimeException | Error failure) {
                try { finish(NOT_AVAILABLE); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            } finally { updating = false; }
        }

        private StructureTraction.Status finish(StructureTraction.Status reason) {
            if (state == ACTIVE) {
                state = reason;
                release(region, lease);
            }
            return state;
        }

        @Override public void close() {
            requireThread(server);
            finish(CLOSED);
        }
    }

    private static void release(NativeStructurePhysics.Region region, StructurePhysicsLease lease) {
        try { lease.close(); }
        catch (IllegalStateException reentrant) {
            // A lifecycle revocation cannot retain a queued force while geometry code is busy.
            region.invalidateTractionLease(lease);
            if (lease.state() == StructurePhysicsLease.State.ACTIVE) throw reentrant;
        }
    }

    private static boolean available(NativeStructureRuntime runtime, ServerLevel host,
                                     NativeStructurePhysics.Region region, StructureHandle handle,
                                     NativeStructureControlActor guard) {
        return NativeStructureRuntime.existing(host.getServer()).orElse(null) == runtime && guard.available()
                && runtime.resolve(handle.reference()).orElse(null) == handle
                && runtime.activePhysicsRegion(host, handle.reference()).orElse(null) == region;
    }

    private static Optional<AABB> bodyBounds(NativeStructurePhysics.Region region, StructureHandle handle) {
        return region.localControlBounds(handle.reference())
                .map(local -> BlockStructureOrientedBox.transform(local, handle.pose()).bounds());
    }

    private static boolean acceptsTarget(ServerLevel host, NativeStructurePhysics.Region region,
                                         AABB body, Vec3 point, Vec3 target, Vec3 velocity) {
        if (!NativeStructureTractionBounds.accepts(region.bounds(), body, point, target, velocity)) return false;
        var moved = body.move(target.subtract(point));
        var predicted = moved.move(velocity.scale(0.05));
        return insideHost(host, body) && insideHost(host, moved) && insideHost(host, predicted);
    }

    private static boolean insideHost(ServerLevel host, AABB bounds) {
        return bounds.minY >= host.getMinY() && bounds.maxY <= host.getMaxY()
                && host.getWorldBorder().isWithinBounds(bounds);
    }

    private static StructurePhysicsControl.Anchor anchor(Vec3 point, Vec3 target, Vec3 velocity,
                                                         StructureTraction.Settings settings) {
        return new StructurePhysicsControl.Anchor(point, target, velocity,
                settings.stiffness(), settings.damping(), settings.maxForce());
    }

    private static StructureTraction.Acquisition failure(StructureTraction.Status status) {
        return new StructureTraction.Acquisition(status, Optional.empty());
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static boolean containsPoint(AABB bounds, Vec3 point) {
        return point.x >= bounds.minX && point.x <= bounds.maxX
                && point.y >= bounds.minY && point.y <= bounds.maxY
                && point.z >= bounds.minZ && point.z <= bounds.maxZ;
    }

    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Structure traction requires its server thread");
    }
}
