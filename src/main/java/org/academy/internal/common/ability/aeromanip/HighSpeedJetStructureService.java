package org.academy.internal.common.ability.aeromanip;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.BlockStructureApi;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.BlockStructureKineticHandle;
import org.academy.api.common.structure.BlockStructureKineticOptions;
import org.academy.api.common.structure.BlockStructureKinetics;
import org.academy.api.common.structure.BlockStructurePlacementPolicy;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared server implementation used by High-Speed Jet and Aeromanip programs. */
public final class HighSpeedJetStructureService {
    public static final double DEFAULT_STRUCTURE_RADIUS = 2.0;
    public static final double MAXIMUM_STRUCTURE_RADIUS = 8.0;
    public static final int IMPACT_COOLDOWN_TICKS = 10;
    public static final double MINIMUM_IMPACT_SPEED = 0.35;

    private static final double DIRECTION_DOT_THRESHOLD = Math.cos(Math.toRadians(45.0));
    private static final double BASE_THRUST = 0.48;

    private HighSpeedJetStructureService() {
    }

    public static int countOwnedNozzles(
            ServerPlayer player,
            Vec3 center,
            double radius,
            Vec3 desiredDirection
    ) {
        return candidateNozzles(player, center, radius, desiredDirection).size();
    }

    public static Optional<LaunchHandle> launch(
            ServerPlayer player,
            LaunchRequest request,
            float costMultiplier
    ) {
        if (player == null || request == null
                || !(player.level() instanceof ServerLevel level)
                || !Skills.HIGH_SPEED_JET.get().isEnabled(player)
                || !Float.isFinite(costMultiplier) || costMultiplier <= 0.0f) {
            return Optional.empty();
        }
        var plan = plan(player, request).orElse(null);
        if (plan == null) return Optional.empty();

        var options = new BlockStructureCaptureOptions(
                plan.positions.size(),
                0,
                false,
                request.restoreWhenSettled,
                capturePolicy(player)
        );
        var captured = BlockStructureApi.capture(level, plan.positions, options);
        var structure = captured.structure().orElse(null);
        if (structure == null) return Optional.empty();

        var previousTicks = new HashMap<HighSpeedJetNozzle, Integer>();
        for (var mount : plan.nozzles) {
            previousTicks.put(mount.nozzle, mount.nozzle.activeTicks());
        }
        var blockCount = structure.snapshot().blockCount();
        var powerCost = ProgramPowerScale.costMultiplier(request.power);
        var cpCost = (HighSpeedJet.activationCpCost(1)
                + blockCount * 0.25f) * powerCost * costMultiplier
                * AeromanipConfig.cpMultiplier(player, SkillNames.HIGH_SPEED_JET);
        var airCost = (HighSpeedJet.activationAirCost(1)
                + blockCount * 0.5f) * powerCost;
        var started = new BlockStructureKineticHandle[1];
        boolean activated;
        try {
            activated = HighSpeedJet.Server.executeWithResources(
                    player,
                    cpCost,
                    airCost,
                    () -> {
                        for (var mount : plan.nozzles) {
                            mount.nozzle.attachToStructure(
                                    player.getUUID(),
                                    structure,
                                    mount.supportOffset,
                                    mount.face
                            );
                        }
                        plan.targetNozzle.activate(request.durationTicks);
                        started[0] = BlockStructureKinetics.propel(
                                structure,
                                player,
                                (movingStructure, _) -> force(
                                        movingStructure,
                                        player,
                                        plan.targetNozzle,
                                        plan.launchDirection,
                                        request.power
                                ),
                                new BlockStructureKineticOptions(
                                        request.durationTicks,
                                        maximumSpeed(request.power),
                                        IMPACT_COOLDOWN_TICKS,
                                        true
                                ),
                                impact -> applyImpact(player, request.power, impact)
                        );
                    }
            );
        } catch (RuntimeException exception) {
            if (started[0] != null) started[0].close();
            restoreImmediately(structure);
            previousTicks.forEach(HighSpeedJetNozzle::restoreActiveTicks);
            throw exception;
        }
        if (!activated || started[0] == null) {
            restoreImmediately(structure);
            previousTicks.forEach(HighSpeedJetNozzle::restoreActiveTicks);
            return Optional.empty();
        }
        return Optional.of(new LaunchHandle(structure, started[0], previousTicks));
    }

    static Optional<LaunchPlan> plan(ServerPlayer player, LaunchRequest request) {
        if (player == null || request == null
                || !(player.level() instanceof ServerLevel level)
                || !level.isInWorldBounds(request.seed)
                || !level.isLoaded(request.seed)) {
            return Optional.empty();
        }
        var targetNozzle = targetNozzle(player, request).orElse(null);
        if (targetNozzle == null) return Optional.empty();
        var launchDirection = HighSpeedJet.entityThrustDirection(targetNozzle.direction());
        var movementDirection = HighSpeedJet.nearestFace(launchDirection);
        var selectionCenter = request.seed.relative(movementDirection);
        var selection = BlockStructureApi.selectSphere(
                level,
                selectionCenter,
                request.structureRadius,
                capturePolicy(player)
        );
        if (!selection.succeeded()) return Optional.empty();
        var positions = BlockStructureApi.cropImmediatelyBlocked(
                level, selection.positions(), movementDirection);
        if (!positions.contains(request.seed)) return Optional.empty();
        var origin = minimumCorner(positions);
        var positionSet = Set.copyOf(positions);
        var nozzles = HighSpeedJet.Server.ownedNozzles(level, player).stream()
                .filter(nozzle -> !nozzle.isRemoved() && !nozzle.isEntityMounted())
                .filter(nozzle -> positionSet.contains(nozzle.supportPos()))
                .sorted(Comparator.comparingInt(Entity::getId))
                .map(nozzle -> new NozzleMount(
                        nozzle,
                        nozzle.supportPos().subtract(origin),
                        nozzle.face()
                ))
                .toList();
        if (nozzles.stream().noneMatch(mount -> mount.nozzle == targetNozzle)) {
            return Optional.empty();
        }
        return Optional.of(new LaunchPlan(
                List.copyOf(positions),
                nozzles,
                targetNozzle,
                launchDirection
        ));
    }

    static float impactDamage(int blockCount, double closingSpeed, float power) {
        return BlockStructureKinetics.collisionDamage(blockCount, closingSpeed)
                * ProgramPowerScale.damageMultiplier(power);
    }

    static double knockbackStrength(int blockCount, double closingSpeed, float power) {
        return BlockStructureKinetics.collisionKnockback(blockCount, closingSpeed)
                * ProgramPowerScale.effectMultiplier(power);
    }

    private static void applyImpact(
            ServerPlayer player,
            float power,
            org.academy.api.common.structure.BlockStructureImpact impact
    ) {
        var target = impact.target();
        if (!player.isAlive() || player.hasDisconnected()
                || target instanceof BlockStructure
                || !AeromanipTargeting.canAffectNegatively(player, target)) return;
        var blockCount = impact.structure().snapshot().blockCount();
        var damage = impactDamage(blockCount, impact.closingSpeed(), power)
                * AeromanipConfig.damageMultiplier(player, SkillNames.HIGH_SPEED_JET);
        var system = org.academy.api.server.ability.AbilitySystemServer.getSystem(player);
        damage *= system.getPlayerAbilityPowerMultiplier(player.getUUID())
                * system.getPlayerDamageMultiplier(player.getUUID());
        if (target instanceof LivingEntity living && damage > 0.0f) {
            var damaged = living.hurtServer(
                    player.level(),
                    SkillDamageSource.ofDirect(
                            player,
                            Skills.HIGH_SPEED_JET.get(),
                            impact.structure().asEntity()),
                    damage);
            if (!damaged) return;
            Skills.HIGH_SPEED_JET.get().onHurt(player, living, damage);
        }

        var forceMultiplier = AeromanipTargeting.forceMultiplier(player, target);
        var knockback = knockbackStrength(blockCount, impact.closingSpeed(), power)
                * forceMultiplier;
        if (knockback <= 0.0 || impact.movement().lengthSqr() <= 1.0e-8) return;
        var direction = impact.movement().normalize().scale(knockback).add(0.0, 0.08, 0.0);
        EntityMotionGuard.runWithMotionSource(
                player,
                () -> AeromanipTargeting.addClampedVelocity(target, direction)
        );
    }

    private static Vec3 force(
            BlockStructure structure,
            ServerPlayer player,
            HighSpeedJetNozzle nozzle,
            Vec3 launchDirection,
            float power
    ) {
        if (nozzle.isRemoved() || nozzle.activeTicks() <= 0
                || !nozzle.isOwnedBy(player)
                || !nozzle.isAttachedTo(structure.asEntity())) return Vec3.ZERO;
        var milestoneScale = Skills.HIGH_SPEED_JET.get()
                .getEffectiveProficiencyMilestone(player) >= 3 ? 1.25 : 1.0;
        return launchDirection.scale(
                BASE_THRUST
                        * milestoneScale
                        * ProgramPowerScale.effectMultiplier(power)
        );
    }

    private static Optional<HighSpeedJetNozzle> targetNozzle(
            ServerPlayer player,
            LaunchRequest request
    ) {
        if (!(player.level() instanceof ServerLevel level)) return Optional.empty();
        var desiredDirection = normalizeNullable(request.desiredDirection);
        return HighSpeedJet.Server.ownedNozzles(level, player).stream()
                .filter(nozzle -> !nozzle.isRemoved() && !nozzle.isEntityMounted())
                .filter(nozzle -> nozzle.supportPos().equals(request.seed))
                .filter(nozzle -> desiredDirection == null
                        || HighSpeedJet.entityThrustDirection(nozzle.direction())
                        .dot(desiredDirection) >= DIRECTION_DOT_THRESHOLD)
                .sorted(Comparator
                        .comparing((HighSpeedJetNozzle nozzle) ->
                                request.nozzleFace != null
                                        && nozzle.face() == request.nozzleFace)
                        .reversed()
                        .thenComparingInt(Entity::getId))
                .findFirst();
    }

    private static List<HighSpeedJetNozzle> candidateNozzles(
            ServerPlayer player,
            Vec3 center,
            double radius,
            Vec3 desiredDirection
    ) {
        if (player == null || center == null || !finite(center)
                || !Double.isFinite(radius) || radius < 1.0 || radius > 32.0
                || !(player.level() instanceof ServerLevel level)) {
            return List.of();
        }
        var normalizedDirection = normalizeNullable(desiredDirection);
        var radiusSquared = radius * radius;
        var result = new ArrayList<HighSpeedJetNozzle>();
        for (var nozzle : HighSpeedJet.Server.ownedNozzles(level, player)) {
            if (nozzle.isRemoved() || nozzle.isEntityMounted()
                    || nozzle.position().distanceToSqr(center) > radiusSquared) continue;
            if (normalizedDirection != null
                    && nozzle.direction().dot(normalizedDirection) < DIRECTION_DOT_THRESHOLD) {
                continue;
            }
            result.add(nozzle);
        }
        result.sort(Comparator.comparingInt(Entity::getId));
        return List.copyOf(result);
    }

    private static BlockStructureCaptureOptions.CapturePolicy capturePolicy(ServerPlayer player) {
        return (level, position, state, blockEntity) ->
                level.mayInteract(player, position)
                        && blockEntity == null
                        && BlockStructureCaptureOptions.CapturePolicy.MOVABLE.canCapture(
                        level, position, state, null);
    }

    private static BlockPos minimumCorner(List<BlockPos> positions) {
        var minimumX = Integer.MAX_VALUE;
        var minimumY = Integer.MAX_VALUE;
        var minimumZ = Integer.MAX_VALUE;
        for (var position : positions) {
            minimumX = Math.min(minimumX, position.getX());
            minimumY = Math.min(minimumY, position.getY());
            minimumZ = Math.min(minimumZ, position.getZ());
        }
        return new BlockPos(minimumX, minimumY, minimumZ);
    }

    private static double maximumSpeed(float power) {
        return ProgramPowerScale.interpolate(power, 3.0, 6.0, 8.0);
    }

    private static void restoreImmediately(BlockStructure structure) {
        structure.setVelocity(Vec3.ZERO);
        structure.alignToGrid();
        var restored = structure.restoreToGrid(BlockStructurePlacementPolicy.AIR_ONLY);
        if (!restored.succeeded()) {
            structure.settleToGrid(BlockStructurePlacementPolicy.AIR_ONLY);
        }
    }

    private static Vec3 normalizeNullable(Vec3 direction) {
        if (!finite(direction) || direction.lengthSqr() <= 1.0e-8) return null;
        return direction.normalize();
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    public record LaunchRequest(
            BlockPos seed,
            Direction nozzleFace,
            Vec3 desiredDirection,
            double structureRadius,
            float power,
            int durationTicks,
            boolean restoreWhenSettled
    ) {
        public LaunchRequest {
            if (seed == null) throw new IllegalArgumentException("seed cannot be null");
            seed = seed.immutable();
            if (!Double.isFinite(structureRadius)
                    || structureRadius < DEFAULT_STRUCTURE_RADIUS
                    || structureRadius > MAXIMUM_STRUCTURE_RADIUS) {
                throw new IllegalArgumentException("structureRadius must be between "
                        + DEFAULT_STRUCTURE_RADIUS + " and " + MAXIMUM_STRUCTURE_RADIUS);
            }
            ProgramPowerScale.require(power);
            if (durationTicks < 1 || durationTicks > 60 * 20) {
                throw new IllegalArgumentException("durationTicks must be between 1 and 1200");
            }
            if (desiredDirection != null && normalizeNullable(desiredDirection) == null) {
                throw new IllegalArgumentException("desiredDirection is invalid");
            }
        }
    }

    static record LaunchPlan(
            List<BlockPos> positions,
            List<NozzleMount> nozzles,
            HighSpeedJetNozzle targetNozzle,
            Vec3 launchDirection
    ) {
    }

    private record NozzleMount(
            HighSpeedJetNozzle nozzle,
            BlockPos supportOffset,
            Direction face
    ) {
    }

    public static final class LaunchHandle implements AutoCloseable {
        private final BlockStructure structure;
        private final BlockStructureKineticHandle kinetics;
        private final Map<HighSpeedJetNozzle, Integer> previousNozzleTicks;
        private boolean rolledBack;

        private LaunchHandle(
                BlockStructure structure,
                BlockStructureKineticHandle kinetics,
                Map<HighSpeedJetNozzle, Integer> previousNozzleTicks
        ) {
            this.structure = structure;
            this.kinetics = kinetics;
            this.previousNozzleTicks = Map.copyOf(previousNozzleTicks);
        }

        public BlockStructure structure() {
            return structure;
        }

        @Override
        public void close() {
            if (rolledBack) return;
            rolledBack = true;
            kinetics.close();
            previousNozzleTicks.forEach((nozzle, ticks) -> {
                if (!nozzle.isRemoved()) nozzle.restoreActiveTicks(ticks);
            });
            if (structure.asEntity().isRemoved()) return;
            structure.setVelocity(Vec3.ZERO);
            structure.alignToGrid();
            var restored = structure.restoreToGrid(BlockStructurePlacementPolicy.AIR_ONLY);
            if (!restored.succeeded()) {
                structure.settleToGrid(BlockStructurePlacementPolicy.AIR_ONLY);
            }
        }
    }
}
