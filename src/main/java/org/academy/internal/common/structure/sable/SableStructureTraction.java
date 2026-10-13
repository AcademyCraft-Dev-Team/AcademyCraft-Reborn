package org.academy.internal.common.structure.sable;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.joml.Vector3d;

import java.util.*;
import java.util.function.BiPredicate;

import static org.academy.api.common.structure.StructureTraction.Status.*;

/**
 * Optional, explicitly initialized bridge to real Sable substeps. Live slots participate in
 * the verified Simulated physics-staff compatibility gate.
 * Acquiring, renewing and releasing never teleports a body or overwrites its velocities.
 */
public final class SableStructureTraction {
    private static final String RAPIER_PIPELINE = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline";
    private static final Map<MinecraftServer, Map<ServerSubLevel, Session>> SERVERS = new IdentityHashMap<>();
    private static boolean initialized;

    private SableStructureTraction() {}

    public static Optional<StructureDynamics.State> motion(ServerLevel host, StructureRef reference) {
        SableStructureAccess.requireThread(host.getServer());
        var handle = SableStructureAccess.resolveHandle(host.getServer(), reference).orElse(null);
        if (handle == null || handle.host() != host) return Optional.empty();
        var system = SubLevelPhysicsSystem.get(host);
        if (system == null || system.getPaused()
                || !RAPIER_PIPELINE.equals(system.getPipeline().getClass().getName())) return Optional.empty();
        var body = handle.subLevel();
        var mass = body.getMassTracker();
        if (body.getRuntimeId() == PhysicsPipelineBody.NULL_RUNTIME_ID
                || !SableStructureTransforms.unitScale(body.logicalPose()) || mass == null
                || !Double.isFinite(mass.getMass()) || mass.getMass() <= 0 || mass.getCenterOfMass() == null) {
            return Optional.empty();
        }
        var rigidBody = system.getPhysicsHandle(body);
        var center = vector(mass.getCenterOfMass()).subtract(Vec3.atLowerCornerOf(handle.plotOrigin()));
        return Optional.of(new StructureDynamics.State(handle.pose(), vector(rigidBody.getLinearVelocity(new Vector3d())),
                vector(rigidBody.getAngularVelocity(new Vector3d())), center, mass.getMass()));
    }

    /** Called only after the optional bridge's exact version/hash compatibility gate succeeds. */
    public static synchronized void initialize() {
        if (initialized) return;
        NeoForge.EVENT_BUS.addListener(SableStructureTraction::beforePhysics);
        NeoForge.EVENT_BUS.addListener(SableStructureTraction::afterServerTick);
        NeoForge.EVENT_BUS.addListener(SableStructureTraction::entityLeft);
        NeoForge.EVENT_BUS.addListener(SableStructureTraction::playerLoggedOut);
        initialized = true;
    }

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
        SableStructureAccess.requireThread(server);
        if (!initialized) return failure(NOT_AVAILABLE);
        var guard = new SableControlActor(actor, host);
        if (!guard.available()) return failure(NOT_AVAILABLE);
        var handle = SableStructureAccess.resolveHandle(server, proposed.block().structure()).orElse(null);
        if (handle == null || handle.host() != host || !handle.runtimeId().equals(proposed.runtimeId())) {
            return failure(NOT_AVAILABLE);
        }
        var system = SubLevelPhysicsSystem.get(host);
        if (!available(handle, guard, system)) return failure(NOT_AVAILABLE);
        var staff = staffStatus(host, handle.subLevel());
        if (staff != ACTIVE) return failure(staff);
        if (!permission.test(actor, handle)) return failure(DENIED);
        if (!available(handle, guard, system)) return failure(NOT_AVAILABLE);
        var eye = actor.getEyePosition();
        var look = actor.getLookAngle();
        if (!finite(eye) || !finite(look) || look.lengthSqr() < 1.0E-12) return failure(INVALID_TARGET);
        var validated = SableStructureAccess.validate(host, eye,
                eye.add(look.normalize().scale(settings.maxRange())), proposed);
        if (!validated.succeeded()) return failure(NOT_VISIBLE);
        if (!available(handle, guard, system)) return failure(NOT_AVAILABLE);
        var hit = validated.currentHit().orElseThrow();
        var subLevel = handle.subLevel();
        staff = staffStatus(host, subLevel);
        if (staff != ACTIVE) return failure(staff);
        if (subLevel.getMassTracker().getMass() > settings.maxMass()) return failure(TOO_HEAVY);
        var point = handle.pose().localToWorldPoint(hit.localPoint());
        if (!controlPoint(hit.localPoint()) || !targetPoint(point)) return failure(INVALID_TARGET);
        var body = bodyBounds(handle);
        if (body == null || !acceptsTarget(host, body, point, point, Vec3.ZERO)) return failure(OUT_OF_BOUNDS);
        var slots = SERVERS.computeIfAbsent(server, ignored -> new IdentityHashMap<>());
        var existing = slots.get(subLevel);
        if (existing != null && !existing.available()) existing.finish(NOT_AVAILABLE);
        if (slots.containsKey(subLevel)) return failure(OCCUPIED);
        var session = new Session(host, system, slots, handle, actor, guard, hit.localPoint(), point, settings, permission);
        slots.put(subLevel, session);
        return new StructureTraction.Acquisition(ACTIVE, Optional.of(session));
    }

    /** Exact content edits end this activation's control; host terrain edits are checked by LOS. */
    public static void onBlockChanged(ServerLevel host, BlockPos position) {
        SableStructureAccess.requireThread(host.getServer());
        var slots = SERVERS.get(host.getServer());
        if (slots == null || slots.isEmpty()) return;
        var container = SubLevelContainer.getContainer(host);
        if (container == null) return;
        var plot = container.getPlot(new ChunkPos(position.getX() >> 4, position.getZ() >> 4));
        if (plot == null) return;
        var session = slots.get(plot.getSubLevel());
        if (session != null && session.host == host) session.finish(NOT_AVAILABLE);
    }

    public static void shutdown(MinecraftServer server) {
        SableStructureAccess.requireThread(server);
        var slots = SERVERS.remove(server);
        if (slots != null) for (var session : List.copyOf(slots.values())) session.finish(NOT_AVAILABLE);
    }

    /** Non-mutating exact-instance probe used by the isolated physics-staff entry gates. */
    public static boolean exclusiveOwner(ServerLevel host, ServerSubLevel ship) {
        SableStructureAccess.requireThread(host.getServer());
        var slots = SERVERS.get(host.getServer());
        var session = slots == null ? null : slots.get(ship);
        return session != null && session.host == host && session.available();
    }

    private static void beforePhysics(ForgeSablePrePhysicsTickEvent event) {
        var system = event.getPhysicsSystem();
        var slots = SERVERS.get(system.getLevel().getServer());
        if (slots == null || slots.isEmpty()) return;
        SableStructureAccess.requireThread(system.getLevel().getServer());
        for (var session : List.copyOf(slots.values())) {
            if (session.system == system) session.applySubstep(event.getTimeStep());
        }
    }

    private static void afterServerTick(ServerTickEvent.Post event) {
        var slots = SERVERS.get(event.getServer());
        if (slots == null || slots.isEmpty()) return;
        for (var session : List.copyOf(slots.values())) if (!session.available()) session.finish(NOT_AVAILABLE);
    }

    private static void entityLeft(EntityLeaveLevelEvent event) { invalidateActor(event.getEntity()); }
    private static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) { invalidateActor(event.getEntity()); }

    private static void invalidateActor(Entity actor) {
        if (!(actor.level() instanceof ServerLevel host)) return;
        SableStructureAccess.requireThread(host.getServer());
        var slots = SERVERS.get(host.getServer());
        if (slots != null) for (var session : List.copyOf(slots.values())) {
            if (session.guard.is(actor)) session.finish(NOT_AVAILABLE);
        }
    }

    private static final class Session implements StructureTractionSession {
        private final MinecraftServer server;
        private final ServerLevel host;
        private final SubLevelPhysicsSystem system;
        private final Map<ServerSubLevel, Session> slots;
        private final SableStructureHandle handle;
        private final ServerSubLevel subLevel;
        private final int bodyId;
        private final MassData massData;
        private final Entity actor;
        private final SableControlActor guard;
        private final StructureRef reference;
        private final UUID runtimeId;
        private final Vec3 localPoint;
        private final StructureTraction.Settings settings;
        private final BiPredicate<Entity, StructureHandle> permission;
        private StructureTraction.Status state = ACTIVE;
        private Vec3 observedPoint, target, velocity = Vec3.ZERO;
        private int renewedAt;
        private boolean updating;

        private Session(ServerLevel host, SubLevelPhysicsSystem system, Map<ServerSubLevel, Session> slots,
                        SableStructureHandle handle, Entity actor, SableControlActor guard, Vec3 localPoint,
                        Vec3 point, StructureTraction.Settings settings, BiPredicate<Entity, StructureHandle> permission) {
            server = host.getServer(); this.host = host; this.system = system; this.slots = slots;
            this.handle = handle; this.actor = actor; this.guard = guard; this.localPoint = localPoint;
            this.settings = settings; this.permission = permission;
            subLevel = handle.subLevel(); bodyId = subLevel.getRuntimeId(); massData = subLevel.getMassTracker();
            reference = handle.reference(); runtimeId = handle.runtimeId(); observedPoint = point; target = point;
            renewedAt = server.getTickCount();
        }

        @Override public StructureRef reference() { requireThread(); return reference; }
        @Override public UUID runtimeId() { requireThread(); return runtimeId; }
        @Override public Vec3 localPoint() { requireThread(); return localPoint; }
        @Override public Vec3 targetPosition() { requireThread(); return target; }
        @Override public Vec3 worldPoint() {
            requireThread();
            return available() ? handle.pose().localToWorldPoint(localPoint) : observedPoint;
        }
        @Override public StructureTraction.Status status() {
            requireThread();
            return state == ACTIVE && !available() ? NOT_AVAILABLE : state;
        }

        @Override public StructureTraction.Status update(Vec3 targetPosition, Vec3 targetVelocity) {
            requireThread();
            if (updating) throw new IllegalStateException("Cannot reenter a Sable traction update");
            if (state != ACTIVE) return state;
            if (!targetPoint(targetPosition) || !finite(targetVelocity)
                    || targetVelocity.length() > StructurePhysicsControl.MAX_TARGET_SPEED) return finish(INVALID_TARGET);
            updating = true;
            try {
                var result = validate(targetPosition, targetVelocity);
                if (result != ACTIVE) return finish(result);
                observedPoint = handle.pose().localToWorldPoint(localPoint);
                target = targetPosition; velocity = targetVelocity; renewedAt = server.getTickCount();
                return ACTIVE;
            } catch (RuntimeException | Error failure) {
                finish(NOT_AVAILABLE);
                throw failure;
            } finally { updating = false; }
        }

        private void applySubstep(double seconds) {
            requireThread();
            if (state != ACTIVE) return;
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 0.05) {
                finish(NOT_AVAILABLE);
                return;
            }
            if (updating) throw new IllegalStateException("Cannot reenter a Sable traction substep");
            updating = true;
            try {
                var result = validate(target, velocity);
                if (result != ACTIVE) { finish(result); return; }
                var pose = handle.pose();
                var rigidBody = system.getPhysicsHandle(subLevel);
                var center = vector(massData.getCenterOfMass()).subtract(Vec3.atLowerCornerOf(handle.plotOrigin()));
                var motion = new StructureMotionState(pose, vector(rigidBody.getLinearVelocity(new Vector3d())),
                        vector(rigidBody.getAngularVelocity(new Vector3d())), center, massData.getMass(), false, 0, 0);
                var control = new StructurePhysicsControl.Anchor(localPoint, target, velocity,
                        settings.stiffness(), settings.damping(), settings.maxForce());
                var impulse = SableControlForces.localImpulse(control, motion, seconds);
                if (!available()) { finish(NOT_AVAILABLE); return; }
                rigidBody.applyImpulseAtPoint(SableControlForces.plotPoint(handle.plotOrigin(), localPoint), impulse);
                observedPoint = pose.localToWorldPoint(localPoint);
            } catch (RuntimeException | Error failure) {
                finish(NOT_AVAILABLE);
                throw failure;
            } finally { updating = false; }
        }

        private StructureTraction.Status validate(Vec3 proposedTarget, Vec3 proposedVelocity) {
            if (!available()) return NOT_AVAILABLE;
            var staff = staffStatus(host, subLevel);
            if (staff != ACTIVE) return staff;
            if (!permission.test(actor, handle)) return DENIED;
            // Policy and shape callbacks may close this session, acquire a replacement or edit content.
            if (!available()) return NOT_AVAILABLE;
            var pose = handle.pose();
            var point = pose.localToWorldPoint(localPoint);
            var eye = actor.getEyePosition();
            if (!finite(eye) || eye.distanceTo(point) > settings.maxRange()) return OUT_OF_RANGE;
            double distance = eye.distanceTo(point);
            if (!Double.isFinite(distance) || distance < 1.0E-6) return NOT_VISIBLE;
            var end = eye.add(point.subtract(eye).scale(Math.min(StructureQueries.MAX_RAY_LENGTH, distance + 1.0E-5) / distance));
            var visible = SableStructureAccess.raycast(host, eye, end).orElse(null);
            if (visible == null || !visible.block().structure().equals(reference)
                    || !visible.runtimeId().equals(runtimeId)) return NOT_VISIBLE;
            if (!available() || !handle.pose().equals(pose)) return NOT_AVAILABLE;
            staff = staffStatus(host, subLevel);
            if (staff != ACTIVE) return staff;
            if (massData.getMass() > settings.maxMass()) return TOO_HEAVY;
            var body = bodyBounds(handle);
            if (body == null || !acceptsTarget(host, body, point, proposedTarget, proposedVelocity)) return OUT_OF_BOUNDS;
            return ACTIVE;
        }

        private boolean available() {
            return state == ACTIVE && slots.get(subLevel) == this && SERVERS.get(server) == slots
                    && !SableControlForces.expired(server.getTickCount(), renewedAt, settings.leaseTicks())
                    && SableStructureTraction.available(handle, guard, system)
                    && subLevel.getRuntimeId() == bodyId && subLevel.getMassTracker() == massData;
        }

        private StructureTraction.Status finish(StructureTraction.Status reason) {
            if (state == ACTIVE) { state = reason; slots.remove(subLevel, this); }
            return state;
        }

        @Override public void close() { requireThread(); finish(CLOSED); }
        private void requireThread() { SableStructureAccess.requireThread(server); }
    }

    private static boolean available(SableStructureHandle handle, SableControlActor guard, SubLevelPhysicsSystem system) {
        if (!guard.available() || !handle.isCurrent() || system == null || system.getPaused()
                || SubLevelPhysicsSystem.get(handle.host()) != system
                || !RAPIER_PIPELINE.equals(system.getPipeline().getClass().getName())) return false;
        var body = handle.subLevel();
        var mass = body.getMassTracker();
        return body.getRuntimeId() != PhysicsPipelineBody.NULL_RUNTIME_ID
                && SableStructureTransforms.unitScale(body.logicalPose()) && mass != null
                && Double.isFinite(mass.getMass()) && mass.getMass() > 0 && mass.getCenterOfMass() != null;
    }

    private static StructureTraction.Status staffStatus(ServerLevel host, ServerSubLevel ship) {
        return switch (SableStaffCompatibility.status(host, ship)) {
            case FREE -> ACTIVE;
            case OCCUPIED -> OCCUPIED;
            case UNAVAILABLE -> NOT_AVAILABLE;
        };
    }

    private static AABB bodyBounds(SableStructureHandle handle) {
        var bounds = handle.subLevel().getPlot().getBoundingBox();
        if (bounds == null) return null;
        var origin = handle.plotOrigin();
        var local = new AABB(bounds.minX() - (double) origin.getX(), bounds.minY() - (double) origin.getY(),
                bounds.minZ() - (double) origin.getZ(), bounds.maxX() + 1.0 - origin.getX(),
                bounds.maxY() + 1.0 - origin.getY(), bounds.maxZ() + 1.0 - origin.getZ());
        return BlockStructureOrientedBox.transform(local, handle.pose()).bounds();
    }

    private static boolean acceptsTarget(ServerLevel host, AABB body, Vec3 point, Vec3 target, Vec3 velocity) {
        var moved = body.move(target.subtract(point));
        return insideHost(host, body) && insideHost(host, moved) && insideHost(host, moved.move(velocity.scale(0.05)));
    }

    private static boolean insideHost(ServerLevel host, AABB bounds) {
        return bounds.minY >= host.getMinY() && bounds.maxY <= host.getMaxY()
                && host.getWorldBorder().isWithinBounds(bounds);
    }

    private static Vec3 vector(org.joml.Vector3dc value) { return new Vec3(value.x(), value.y(), value.z()); }
    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static boolean controlPoint(Vec3 value) { return components(value, StructurePhysicsControl.MAX_LOCAL_COORDINATE); }
    private static boolean targetPoint(Vec3 value) { return components(value, StructurePhysicsControl.MAX_WORLD_COORDINATE); }
    private static boolean components(Vec3 value, double limit) {
        return finite(value) && Math.abs(value.x) <= limit && Math.abs(value.y) <= limit && Math.abs(value.z) <= limit;
    }
    private static StructureTraction.Acquisition failure(StructureTraction.Status status) {
        return new StructureTraction.Acquisition(status, Optional.empty());
    }
}
