package org.academy.internal.common.structure.upgrade.launch;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.api.server.team.TeamRelations;

import java.util.*;

/** Loaded-only, online-authorized kinetic flights; every mutation uses shared protected damage. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class KineticStructureFlight {
    private static final Map<MinecraftServer, Map<StructureRef, Flight>> SERVERS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Budget> BUDGETS = new IdentityHashMap<>();
    static final int MAX_FLIGHTS_PER_ACTOR = 4;
    static final int MAX_ACTIVE_FLIGHTS = 16;
    static final int MAX_ENTITY_CANDIDATES = 32;
    private static final double CONTACT_CLEARANCE = 0.02;
    private static final int SOURCE_CHANGES = Math.clamp(Integer.getInteger("academy.structure.projectileSourceChanges", 128), 1, 128);
    private KineticStructureFlight() {}

    public static boolean active(MinecraftServer server, StructureRef source) {
        if (!server.isSameThread()) throw new IllegalStateException("Kinetic flight requires server thread");
        var flights = SERVERS.get(server);
        var flight = flights == null ? null : flights.get(stable(source));
        return flight != null && !flight.finished && flight.source.dimension().equals(source.dimension())
                && flight.source.backend().equals(source.backend());
    }

    public static boolean observe(ServerPlayer actor, StructureRef reference, int tier) {
        var host = actor.level();
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Kinetic flight requires server thread");
        var access = StructureBackendRouter.orientationAccess(host, reference).orElse(null);
        var body = access == null ? null : access.observe(host, reference).orElse(null);
        if (body == null || !canObserve(actor, reference) || body.localBoxes().size() > 128 || !actor.isAlive() || actor.hasDisconnected()
                || StructureDirectControl.occupied(reference, body.runtimeId())) return false;
        double radius = radius(body.localBoxes(), body.motion().localCenterOfMass());
        if (radius > 31 || !Double.isFinite(radius)) return false;
        var flights = SERVERS.computeIfAbsent(host.getServer(), ignored -> new LinkedHashMap<>());
        var flight = new Flight(host, actor.getUUID(), reference, body, Math.clamp(tier, 0, 2), radius);
        flights.put(stable(reference), flight);
        advance(flight); // Clear this first real step before either backend's solver can stop it.
        // Registration often runs in ServerTick.Post. The next Pre precedes the level's
        // game-time increment, but is a different impending physics step and must predict it.
        // Subsequent Pre callbacks retain ordinary same-game-time deduplication.
        if (!flight.finished) flight.lastTick = Long.MIN_VALUE;
        return true;
    }

    public static boolean canObserve(ServerPlayer actor, StructureRef reference) {
        var host = actor.level();
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Kinetic flight requires server thread");
        if (!actor.isAlive() || actor.hasDisconnected()) return false;
        var body = StructureBackendRouter.orientationAccess(host, reference).flatMap(value -> value.observe(host, reference)).orElse(null);
        if (body == null || body.localBoxes().size() > 128 || radius(body.localBoxes(), body.motion().localCenterOfMass()) > 31) return false;
        var flights = SERVERS.get(host.getServer());
        if (flights == null) return true;
        var existing = flights.get(stable(reference));
        if (existing != null && !existing.finished && !existing.effects.isEmpty()) return false;
        long active = flights.values().stream().filter(value -> !value.finished && value.effects.isEmpty()).count();
        long owned = flights.values().stream().filter(value -> !value.finished && value.actor.equals(actor.getUUID())).count();
        if (existing != null && !existing.finished) {
            active--;
            if (existing.actor.equals(actor.getUUID())) owned--;
        }
        return quotaAllows(flights.size() - (existing == null ? 0 : 1), active, owned);
    }

    static boolean quotaAllows(int retainedRecords, long active, long owned) {
        return retainedRecords >= 0 && retainedRecords < 64 && active >= 0 && active < MAX_ACTIVE_FLIGHTS
                && owned >= 0 && owned < MAX_FLIGHTS_PER_ACTOR;
    }

    @SubscribeEvent public static void beforeTick(ServerTickEvent.Pre event) {
        var flights = SERVERS.get(event.getServer());
        if (flights == null) return;
        for (var flight : List.copyOf(flights.values())) {
            try { advance(flight); }
            catch (RuntimeException failure) {
                flight.finished = true;
                AcademyCraft.LOGGER.error("Kinetic penetration stopped without replay for {}", flight.source, failure);
            }
            if (flight.finished) flights.remove(stable(flight.source), flight);
        }
        if (flights.isEmpty()) SERVERS.remove(event.getServer());
    }

    @SubscribeEvent public static void teleported(StructureTeleportedEvent event) {
        var server = event.host().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Projectile relocation notification requires server thread");
        var flights = SERVERS.get(server);
        if (flights == null) return;
        for (var relocation : event.relocations()) {
            var flight = flights.get(stable(relocation.reference()));
            if (flight == null || !relocationMatches(flight.source, flight.runtime, relocation)) continue;
            var owner = server.getPlayerList().getPlayer(flight.actor);
            if (owner != null && owner.level() == flight.host) {
                var authority = new StructureDamage.ActiveActor(new StructureOperation.ActorContext(flight.host, owner));
                for (var effect : List.copyOf(flight.effects)) StructureDamages.service().cancel(authority, effect);
            }
            flight.finished = true;
            flights.remove(stable(flight.source), flight);
        }
        if (flights.isEmpty()) SERVERS.remove(server);
    }

    static boolean relocationMatches(StructureRef source, UUID runtime, StructureTeleportedEvent.Relocation relocation) {
        return stable(source).equals(stable(relocation.reference())) && runtime.equals(relocation.runtimeId());
    }

    private static void advance(Flight flight) {
        var host = flight.host;
        var actor = host.getServer().getPlayerList().getPlayer(flight.actor);
        if (actor == null || actor.level() != host || !actor.isAlive() || actor.hasDisconnected()) {
            flight.finished = true; return;
        }
        var authority = new StructureDamage.ActiveActor(new StructureOperation.ActorContext(host, actor));
        if (flight.lastTick == host.getGameTime()) return;
        flight.lastTick = host.getGameTime();
        if (!flight.effects.isEmpty()) { advanceEffects(flight, authority); return; }
        var access = StructureBackendRouter.orientationAccess(host, flight.source).orElse(null);
        var body = access == null ? null : access.observe(host, flight.source).orElse(null);
        if (body == null || !body.runtimeId().equals(flight.runtime)
                || StructureDirectControl.occupied(flight.source, flight.runtime)) { flight.finished = true; return; }
        var pose = body.motion().pose();
        var localCenter = body.motion().localCenterOfMass();
        var center = pose.localToWorldPoint(localCenter);
        double segment = center.distanceTo(flight.previousCenter);
        double remainingBeforeStep = Math.max(0, StructureProjectileFlight.MAX_DISTANCE - flight.travelled);
        var testedPose = segment > remainingBeforeStep ? flight.previousPose.lerp(pose, remainingBeforeStep / segment) : pose;
        flight.travelled += segment;
        var budget = BUDGETS.computeIfAbsent(host.getServer(), ignored -> new Budget());
        budget.refresh(host.getServer().getTickCount());
        if (flight.obstacle != null) {
            var area = StructureImpactSweep.sweptBounds(body.localBoxes(), pose, pose, localCenter);
            var obstacle = org.academy.internal.common.structure.upgrade.damage.DamageContactWitness
                    .resolve(host, actor, flight.obstacle, area).orElse(null);
            if (obstacle == null) { flight.finished = true; return; }
            boolean slowed = flight.obstacleVelocity.lengthSqr() > 1E-8
                    && body.linearVelocity().dot(flight.obstacleVelocity.normalize()) < flight.obstacleVelocity.length() * 0.9;
            boolean contact = slowed && body.contentRevision() == flight.obstacleContent && obstacle.stream().anyMatch(box ->
                    flight.obstacleVelocity.dot(box.getCenter().subtract(flight.previousCenter)) > 0
                            && (StructureImpactSweep.intersects(body.localBoxes(), flight.previousPose, pose, box.inflate(0.04))
                            || posteriorContact(flight, body, box, center)));
            var fresh = access.observe(host, flight.source).orElse(null);
            if (contact && !flight.finished && fresh != null && fresh.sameObservation(body)
                    && !StructureDirectControl.occupied(flight.source, flight.runtime)) {
                impact(flight, actor, access, body, center); return;
            }
            if (host.getGameTime() - flight.obstacleAt > 5) flight.obstacle = null;
        }
        var entityBounds = StructureImpactSweep.sweptBounds(body.localBoxes(), flight.previousPose, testedPose, localCenter);
        var candidates = livingCandidates(host, entityBounds);
        if (candidates.size() > MAX_ENTITY_CANDIDATES) { flight.finished = true; return; }
        var entitySweep = new org.academy.internal.common.structure.PreparedStructureImpactSweep(
                body.localBoxes(), flight.previousPose, testedPose);
        for (var entity : candidates) {
            var living = entity;
            if (living == actor || !living.isAlive() || living.isSpectator()
                    || TeamRelations.areAllied(actor, living) || PvpSetting.shouldPrevent(actor, living)) continue;
            var tested = entitySweep.test(living.getBoundingBox(), budget.shapeTests);
            budget.shapeTests -= tested.tests();
            if (!tested.complete()) { flight.finished = true; return; }
            if (!tested.intersects()) continue;
            var skill = Skills.KINETIC_THROW.get();
            if (living.hurtServer(host, SkillDamageSource.of(actor, skill), KineticThrow.structureDamage(flight.cells))) {
                skill.onHurt(actor, living, KineticThrow.structureDamage(flight.cells));
                flight.directHit = living.getUUID();
                impact(flight, actor, access, body, center); return;
            }
        }
        // Natural expiry only withdraws the enhancement; it never explodes or consumes content.
        if (flight.travelled >= StructureProjectileFlight.MAX_DISTANCE || host.getGameTime() > flight.expires) {
            flight.finished = true; return;
        }
        flight.previousCenter = center;
        flight.previousPose = pose;
        var movement = body.freeStepDisplacement(0.05);
        double remaining = StructureProjectileFlight.MAX_DISTANCE - flight.travelled;
        // Do not predict terrain damage beyond the enhancement's remaining actual path.
        if (movement.length() > remaining) movement = movement.normalize().scale(remaining);
        if (movement.lengthSqr() < 1E-10) return;
        var predicted = predictPose(body, movement);
        var sourceHandle = StructureSpaces.resolve(host.getServer(), flight.source).orElse(null);
        if (sourceHandle == null || !sourceHandle.runtimeId().equals(flight.runtime) || !sourceHandle.pose().equals(pose)) {
            flight.finished = true; return;
        }
        var sourceWitness = new StructurePathDamage.SourceWitness(flight.source, flight.runtime, sourceHandle.geometryRevision(), pose);
        // The solver's predictive/CCD contact margin also sees the rim of an exactly
        // one-cell opening. Clear that contact envelope, without shrinking any collider.
        var contactBoxes = body.localBoxes().stream().map(box -> box.inflate(CONTACT_CLEARANCE)).toList();
        double contactRadius = radius(contactBoxes, localCenter);
        float broadRadius = (float) contactRadius;
        if (broadRadius < contactRadius) broadRadius = Math.nextUp(broadRadius);
        var request = new StructurePathDamage.Request(center, center.add(movement), broadRadius,
                Integer.MAX_VALUE, StructureDamage.DropPolicy.EXISTING_SKILL_RULES, false, true, false, false,
                false, null, StructureDamage.Targets.excluding(flight.source),
                Optional.of(new StructurePathDamage.Sweep(contactBoxes, pose, predicted, Optional.of(sourceWitness))));
        var result = StructureDamages.service().path(authority, request);
        if (flight.finished) return; // A synchronous protection/loot callback may have teleported it.
        if (Boolean.getBoolean("academy.structureTeleportSmoke")) AcademyCraft.LOGGER.info(
                "KINETIC_FLIGHT_TRACE tick={} source={} distance={} center={} velocity={} terrain={} path={} details={} receipt={}",
                host.getGameTime(), flight.source.id(), flight.travelled, center, body.linearVelocity(), body.terrainReady(),
                result.distance(), result.detail(), result.receipt());
        if (result.status() != StructureOperation.Status.COMPLETED || result.receipt().rejected() > 0 || result.receipt().deferred() > 0) {
            // Missing coverage, protection and exhausted budgets withdraw the enhancement.
            // The ordinary solver retains momentum and physical collision; an observer
            // failure must not silently undo the player's successful H launch.
            AcademyCraft.LOGGER.info("Kinetic penetration released for {}: {} status={} receipt={}",
                    flight.source, result.detail(), result.status(), result.receipt());
            flight.finished = true; return;
        }
        // An unbreakable obstruction remains a physical collision. Its permissions are checked
        // independently by the subsequent explosion; no block is made passable by this observer.
        if (result.distance() + 1E-6 < movement.length()) {
            // Prediction only records the candidate. A later solver pose must prove actual
            // face contact and lost approaching velocity before any explosion is admitted.
            flight.obstacle = result.obstruction().orElse(null);
            flight.obstacleVelocity = body.linearVelocity();
            flight.obstacleAt = host.getGameTime();
            flight.obstaclePose = pose;
            flight.obstaclePrediction = predicted;
            flight.obstacleMove = movement;
            flight.obstacleContent = body.contentRevision();
        }
    }

    private static void impact(Flight flight, ServerPlayer actor, OrientationBackendAccess access,
                               OrientationBackendAccess.Body body, Vec3 center) {
        stop(access, flight.host, flight.source);
        var authority = new StructureDamage.ActiveActor(new StructureOperation.ActorContext(flight.host, actor));
        var radius = KineticThrow.explosionRadius(flight.tier);
        var entities = livingCandidates(flight.host, new AABB(center, center).inflate(radius));
        if (entities.size() <= MAX_ENTITY_CANDIDATES) for (var living : entities) {
            if (living == actor || living.getUUID().equals(flight.directHit) || !living.isAlive() || living.isSpectator()
                    || TeamRelations.areAllied(actor, living) || PvpSetting.shouldPrevent(actor, living)) continue;
            double distance = living.position().distanceTo(center);
            var sightStart = outsideSource(body, center, living.getEyePosition());
            if (distance >= radius || sightStart.distanceToSqr(living.getEyePosition()) < 1E-8
                    || !StructureQueries.clearSightline(flight.host, sightStart, living.getEyePosition())) continue;
            float damage = (float) (KineticThrow.structureDamage(flight.cells) * (1-distance/radius));
            var skill = Skills.KINETIC_THROW.get();
            if (living.hurtServer(flight.host, SkillDamageSource.of(actor, skill), damage)) skill.onHurt(actor, living, damage);
        }
        submit(flight, authority, new StructureDamage.Request(UUID.randomUUID(), center, radius, 0,
                4096, 128, StructureDamage.DropPolicy.EXISTING_SKILL_RULES, StructureDamage.Targets.excluding(flight.source)));
        // Source-only scanning never visits host terrain. The larger loaded-family discovery
        // envelope keeps falling children in the same bounded operation while slices complete.
        submit(flight, authority, new StructureDamage.Request(UUID.randomUUID(), center, 256, 0,
                4096, SOURCE_CHANGES, StructureDamage.DropPolicy.EXISTING_SKILL_RULES, StructureDamage.Targets.fragments(flight.source)));
        flight.host.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        flight.host.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4, 1);
        if (flight.effects.isEmpty()) flight.finished = true;
    }

    private static void submit(Flight flight, StructureDamage.Authority authority, StructureDamage.Request request) {
        var result = StructureDamages.service().query(authority, request);
        if (Boolean.getBoolean("academy.structureTeleportSmoke")) AcademyCraft.LOGGER.info(
                "KINETIC_FLIGHT_EFFECT query={} status={} detail={} targets={}", request.effectId(), result.status(), result.detail(), request.targets());
        if (result.status() == StructureOperation.Status.READY && result.value().isPresent()) {
            StructureDamages.service().apply(authority, result.value().orElseThrow());
        }
        result.operationId().ifPresent(flight.effects::add);
    }

    private static void advanceEffects(Flight flight, StructureDamage.Authority authority) {
        for (var id : List.copyOf(flight.effects)) {
            var result = StructureDamages.service().status(authority, id);
            if (Boolean.getBoolean("academy.structureTeleportSmoke")) AcademyCraft.LOGGER.info(
                    "KINETIC_FLIGHT_EFFECT status={} phase={} value={}", id, result.status(), result.value());
            var progress = result.value().orElse(null);
            if (progress == null || progress.phase() == StructureDamage.Phase.COMPLETED
                    || progress.phase() == StructureDamage.Phase.CANCELLED || progress.phase() == StructureDamage.Phase.RECOVERY_REQUIRED) {
                flight.effects.remove(id); continue;
            }
            progress.preparedBatch().ifPresent(batch -> StructureDamages.service().apply(authority, batch));
        }
        if (flight.effects.isEmpty()) flight.finished = true;
    }

    private static void stop(OrientationBackendAccess access, ServerLevel host, StructureRef source) {
        access.observeLinear(host, source).ifPresent(body -> access.applyMotion(host, body, Vec3.ZERO, Vec3.ZERO));
    }

    private static StructureRef stable(StructureRef value) {
        return new StructureRef(value.id(), value.dimension(), value.backend(), 0);
    }

    private static List<LivingEntity> livingCandidates(ServerLevel host, AABB bounds) {
        var candidates = new ArrayList<LivingEntity>();
        ((org.academy.mixin.common.ServerLevelEntityManagerAccessor) host).academy$getEntityManager().getEntityGetter()
                .get(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class), bounds, entity -> {
                    candidates.add(entity);
                    return candidates.size() > MAX_ENTITY_CANDIDATES ? net.minecraft.util.AbortableIterationConsumer.Continuation.ABORT
                            : net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE;
                });
        return candidates;
    }

    private static Vec3 outsideSource(OrientationBackendAccess.Body body, Vec3 center, Vec3 target) {
        var pose = body.motion().pose();
        var start = pose.worldToLocalPoint(center);
        var end = pose.worldToLocalPoint(target);
        var delta = end.subtract(start);
        double lengthSquared = delta.lengthSqr();
        if (lengthSquared < 1E-10) return target;
        double exit = 0;
        for (var box : body.localBoxes()) {
            var reverseHit = box.clip(end, start).orElse(null);
            if (reverseHit != null) exit = Math.max(exit, reverseHit.subtract(start).dot(delta) / lengthSquared);
        }
        return center.lerp(target, Math.min(1, exit + 1E-4 / Math.sqrt(lengthSquared)));
    }

    private static boolean posteriorContact(Flight flight, OrientationBackendAccess.Body body, AABB obstacle, Vec3 center) {
        if (flight.obstacle.worldPosition().isEmpty() || flight.obstaclePose == null || flight.obstaclePrediction == null
                || flight.host.getGameTime()-flight.obstacleAt > 1 || body.contentRevision() != flight.obstacleContent
                || flight.obstacleMove.lengthSqr() < 1E-8) return false;
        var oldCenter = flight.obstaclePose.localToWorldPoint(body.motion().localCenterOfMass());
        var direction = flight.obstacleMove.normalize();
        double observed = center.subtract(oldCenter).dot(direction), free = flight.obstacleMove.length();
        if (observed < -0.04 || observed >= free*0.75) return false;
        double first = firstContactFraction(body.localBoxes(), flight.obstaclePose, flight.obstaclePrediction, obstacle.inflate(0.005));
        if (!Double.isFinite(first) || observed+0.08 < free*first) return false;
        // A second dynamic body makes attribution ambiguous; retain physics instead of guessing.
        var area = StructureImpactSweep.sweptBounds(body.localBoxes(), flight.obstaclePose, flight.obstaclePrediction,
                body.motion().localCenterOfMass());
        for (var backend : StructureBackendRouter.orientationBackends(flight.host)) {
            var nearby = backend.nearby(flight.host, area, 33);
            if (!nearby.complete()) return false;
            for (var other : nearby.bodies()) if (!stable(other.reference()).equals(stable(flight.source))) return false;
        }
        return true;
    }

    static double firstContactFraction(List<AABB> boxes, StructurePose from, StructurePose to, AABB obstacle) {
        var target = org.academy.internal.common.structure.BlockStructureOrientedBox.axisAligned(obstacle);
        double previous = 0;
        for (int sample = 0; sample <= 32; sample++) {
            double current = sample/32.0;
            if (intersectsAt(boxes, from.lerp(to,current), target)) {
                for (int bisect=0; bisect<10; bisect++) {
                    double middle = (previous+current)*0.5;
                    if (intersectsAt(boxes, from.lerp(to,middle), target)) current=middle; else previous=middle;
                }
                return current;
            }
            previous=current;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static boolean intersectsAt(List<AABB> boxes, StructurePose pose,
                                        org.academy.internal.common.structure.BlockStructureOrientedBox target) {
        for (var box : boxes) if (org.academy.internal.common.structure.BlockStructureOrientedBox.transform(box,pose).intersects(target)) return true;
        return false;
    }

    static double radius(List<AABB> boxes, Vec3 center) {
        double radius = 0;
        for (var box : boxes) {
            double x = Math.max(Math.abs(box.minX-center.x), Math.abs(box.maxX-center.x));
            double y = Math.max(Math.abs(box.minY-center.y), Math.abs(box.maxY-center.y));
            double z = Math.max(Math.abs(box.minZ-center.z), Math.abs(box.maxZ-center.z));
            radius = Math.max(radius, Math.sqrt(x*x+y*y+z*z));
        }
        return radius;
    }

    static StructurePose predictPose(OrientationBackendAccess.Body body, Vec3 movement) {
        var pose = body.motion().pose();
        var angular = body.motion().angularVelocity();
        var rotation = pose.rotation();
        double speed = angular.length();
        if (speed > 1E-10) rotation = new org.joml.Quaterniond().rotationAxis(speed * 0.05,
                angular.x / speed, angular.y / speed, angular.z / speed).mul(rotation);
        var rotated = new StructurePose(Vec3.ZERO, rotation);
        var center = pose.localToWorldPoint(body.motion().localCenterOfMass()).add(movement);
        return new StructurePose(center.subtract(rotated.localToWorldDirection(body.motion().localCenterOfMass())), rotation);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); BUDGETS.remove(event.getServer()); }

    private static final class Budget {
        int tick = Integer.MIN_VALUE, shapeTests;
        void refresh(int now) {
            if (tick == now) return;
            tick = now; shapeTests = 262_144;
        }
    }

    private static final class Flight {
        final ServerLevel host;
        final UUID actor, runtime;
        final StructureRef source;
        final int tier, cells;
        final double radius;
        final long expires;
        final List<UUID> effects = new ArrayList<>();
        Vec3 previousCenter;
        StructurePose previousPose;
        double travelled;
        StructureDamage.Cell obstacle;
        UUID directHit;
        Vec3 obstacleVelocity = Vec3.ZERO;
        long obstacleAt;
        StructurePose obstaclePose, obstaclePrediction;
        Vec3 obstacleMove = Vec3.ZERO;
        long obstacleContent;
        long lastTick = Long.MIN_VALUE;
        boolean finished;
        Flight(ServerLevel host, UUID actor, StructureRef source, OrientationBackendAccess.Body body, int tier, double radius) {
            this.host = host; this.actor = actor; this.source = source; this.runtime = body.runtimeId(); this.tier = tier; this.radius = radius;
            this.previousPose = body.motion().pose(); this.previousCenter = previousPose.localToWorldPoint(body.motion().localCenterOfMass());
            this.expires = host.getGameTime() + 200;
            this.cells = StructureSpaces.resolve(host.getServer(), source).flatMap(value -> value.world().summarize(4096))
                    .map(value -> value.counts().values().stream().mapToInt(Integer::intValue).sum()).orElse(1);
        }
    }
}
