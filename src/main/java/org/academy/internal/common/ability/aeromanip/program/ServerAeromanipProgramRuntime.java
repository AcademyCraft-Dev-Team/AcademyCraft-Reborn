package org.academy.internal.common.ability.aeromanip.program;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.ability.aeromanip.skills.lv3.LaminarCutter;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.AbilityProgramSpatialRanges;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative Minecraft-server adapter for Aeromanip programs.
 */
public final class ServerAeromanipProgramRuntime implements AeromanipProgramRuntime {
    public static final double MAX_QUERY_RANGE = AbilityProgramSpatialRanges.forCategory(
            AeromanipProgramNodeCatalog.AEROMANIP).queryRange();
    public static final double MAX_ACTION_RANGE = AbilityProgramSpatialRanges.forCategory(
            AeromanipProgramNodeCatalog.AEROMANIP).actionRange();
    public static final int MAX_QUERY_RESULTS = 128;

    private final ServerPlayer player;
    private final float costMultiplier;
    private final ServerProgramTargetResolver targets;

    public ServerAeromanipProgramRuntime(ServerPlayer player) {
        this(player, 1.0f);
    }

    public ServerAeromanipProgramRuntime(ServerPlayer player, float costMultiplier) {
        this.player = Objects.requireNonNull(player, "player");
        this.costMultiplier = requireCostMultiplier(costMultiplier);
        targets = new ServerProgramTargetResolver(player, MAX_QUERY_RANGE, MAX_QUERY_RESULTS);
    }

    @Override
    public Object caster() {
        return targets.caster();
    }

    @Override
    public Optional<Object> lookTarget() {
        return targets.lookTarget();
    }

    @Override
    public Optional<ProgramBlockPosition> lookBlockTarget() {
        return targets.lookBlockTarget();
    }

    @Override
    public ProgramActionTransaction.ProgramAction airflowPush(
            Object entityReference,
            ProgramDirection direction,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Entity target;
            private Vec3 normalizedDirection;
            private double forceMultiplier;

            @Override
            public void validate() {
                requireCasterReady(Skills.PNEUMATIC_GRASP.get());
                target = requirePushTarget(entityReference, pushRange(power));
                normalizedDirection = vector(direction);
                forceMultiplier = requireForceMultiplier(target);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                var skill = Skills.PNEUMATIC_GRASP.get();
                requireCasterReady(skill);
                target = requirePushTarget(entityReference, pushRange(power));
                normalizedDirection = vector(direction);
                forceMultiplier = requireForceMultiplier(target);
                var previous = target.getDeltaMovement();
                charge(skill, airflowPushCost(power));
                EntityMotionGuard.runWithMotionSource(player, () ->
                        AeromanipTargeting.steerVelocity(
                                target,
                                normalizedDirection,
                                1.0,
                                airflowPushSpeed(power) * forceMultiplier
                        ));
                target.resetFallDistance();
                spawnPushEffect(target);
                return () -> restoreVelocity(target, previous);
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction laminarCut(
            @Nullable ProgramWorldPosition origin,
            ProgramDirection direction,
            float power,
            AeromanipChargeTier chargeTier,
            float chargeCostMultiplier,
            @Nullable ProgramDirection planeDirection,
            AeromanipProgramNodeCatalog.BladePlaneMode planeMode
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Vec3 castOrigin;
            private Vec3 normalizedDirection;

            @Override
            public void validate() {
                requireCasterReady(Skills.LAMINAR_CUTTER.get());
                castOrigin = requireLaminarOrigin(origin);
                normalizedDirection = vector(direction);
                requireChargeCostMultiplier(chargeCostMultiplier);
                requireBladePlane(normalizedDirection, planeDirection, planeMode, false);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(Skills.LAMINAR_CUTTER.get());
                castOrigin = requireLaminarOrigin(origin);
                normalizedDirection = vector(direction);
                var bladePlane = requireBladePlane(
                        normalizedDirection, planeDirection, planeMode, true);
                if (!LaminarCutter.Server.tryProgramCast(
                        player,
                        castOrigin,
                        normalizedDirection,
                        laminarRange(power),
                        laminarDamageScale(power),
                        laminarCost(power) * costMultiplier
                                * requireChargeCostMultiplier(chargeCostMultiplier),
                        chargeTier,
                        bladePlane
                )) {
                    throw new IllegalStateException("Laminar Cutter program cast was rejected");
                }
                return ProgramActionTransaction.Undo.NONE;
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction placeTemporaryJetNozzle(
            Object targetReference,
            ProgramDirection direction,
            AeromanipProgramNodeCatalog.NozzleTargetType targetType
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Vec3 normalizedDirection;

            @Override
            public void validate() {
                requireCasterReady(Skills.HIGH_SPEED_JET.get());
                normalizedDirection = normalizedVector(direction);
                requireNozzleTarget(targetReference, targetType);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(Skills.HIGH_SPEED_JET.get());
                normalizedDirection = normalizedVector(direction);
                var target = requireNozzleTarget(targetReference, targetType);
                var nozzle = targetType == AeromanipProgramNodeCatalog.NozzleTargetType.ENTITY
                        ? HighSpeedJet.Server.placeTemporaryEntityNozzle(
                        player, (Entity) target, normalizedDirection, costMultiplier)
                        : HighSpeedJet.Server.placeTemporaryBlockNozzle(
                        player, (BlockPos) target, normalizedDirection, costMultiplier);
                return () -> {
                    if (!nozzle.isRemoved()) nozzle.discard();
                };
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction fireJets(int durationSeconds) {
        return new ProgramActionTransaction.ProgramAction() {
            private int durationTicks;

            @Override
            public void validate() {
                requireCasterReady(Skills.HIGH_SPEED_JET.get());
                durationTicks = requireJetDuration(durationSeconds);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(Skills.HIGH_SPEED_JET.get());
                durationTicks = requireJetDuration(durationSeconds);
                var previousTicks = new LinkedHashMap<HighSpeedJetNozzle, Integer>();
                for (var nozzle : HighSpeedJet.Server.ownedNozzles(targets.level(), player)) {
                    previousTicks.put(nozzle, nozzle.activeTicks());
                }
                var activated = HighSpeedJet.Server.activateOwnedNozzles(
                        player, durationTicks, costMultiplier);
                return () -> activated.forEach(nozzle -> {
                    if (!nozzle.isRemoved()) {
                        nozzle.restoreActiveTicks(previousTicks.getOrDefault(nozzle, 0));
                    }
                });
            }
        };
    }

    @Override
    public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
        return targets.positionOf(entityReference);
    }

    @Override
    public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
        return targets.lookDirectionOf(entityReference);
    }

    @Override
    public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
        return targets.entitiesAround(center, radius);
    }

    @Override
    public Optional<ProgramBlockPosition> raycastBlock(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        return targets.raycastBlock(origin, direction, maximumDistance);
    }

    @Override
    public Optional<Object> raycastEntity(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        return targets.raycastEntity(origin, direction, maximumDistance);
    }

    private Entity requirePushTarget(Object value, double range) {
        if (!(value instanceof Entity entity)
                || !targets.sameUsableLevel(entity)
                || (entity != player && (!AeromanipTargeting.canAffectNegatively(player, entity)
                || AeromanipTargeting.isBoss(entity)))
                || entity.distanceToSqr(player) > range * range) {
            throw new IllegalArgumentException("Entity cannot be pushed by this program");
        }
        if (!EntityMotionGuard.canApplyMotionFrom(player, entity)) {
            throw new IllegalArgumentException("Entity rejected forced airflow movement");
        }
        return entity;
    }

    private double requireForceMultiplier(Entity target) {
        if (target == player) return 1.0;
        var multiplier = AeromanipTargeting.forceMultiplier(player, target);
        if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
            throw new IllegalArgumentException("Airflow has no permitted force for this target");
        }
        return multiplier;
    }

    private void requireCasterReady(Skill skill) {
        if (!player.isAlive()
                || player.hasDisconnected()
                || player.isSpectator()
                || !skill.isEnabled(player)) {
            throw new IllegalStateException("Required Aeromanip skill is unavailable");
        }
    }

    private void charge(Skill skill, float cost) {
        var adjusted = cost * costMultiplier * AeromanipConfig.cpMultiplier(
                player, SkillNames.PNEUMATIC_GRASP);
        if (!AbilitySystemServer.getSystem(player)
                .tryTimedOccupation(player, adjusted, skill)) {
            throw new IllegalStateException("Insufficient CP for Aeromanip program action");
        }
    }

    private static float requireCostMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0f) {
            throw new IllegalArgumentException("Program cost multiplier must be positive");
        }
        return multiplier;
    }

    private static float requireChargeCostMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier < 1.0f || multiplier > 2.0f) {
            throw new IllegalArgumentException(
                    "Laminar charge cost multiplier must be between 1 and 2");
        }
        return multiplier;
    }

    private Vec3 requireLaminarOrigin(@Nullable ProgramWorldPosition origin) {
        var value = origin == null ? player.getEyePosition() : targets.requireLocalPosition(origin);
        if (value.distanceToSqr(player.position()) > MAX_ACTION_RANGE * MAX_ACTION_RANGE) {
            throw new IllegalArgumentException("Laminar Cut origin is outside program range");
        }
        return value;
    }

    private @Nullable Vec3 requireBladePlane(
            Vec3 attackDirection,
            @Nullable ProgramDirection requestedDirection,
            AeromanipProgramNodeCatalog.BladePlaneMode mode,
            boolean resolveRandom
    ) {
        Objects.requireNonNull(mode, "mode");
        if (mode == AeromanipProgramNodeCatalog.BladePlaneMode.DISABLED) return null;
        if (mode == AeromanipProgramNodeCatalog.BladePlaneMode.DIRECTION) {
            if (requestedDirection == null) {
                throw new IllegalArgumentException(
                        "Laminar Cut plane direction is required");
            }
            var value = normalizedVector(requestedDirection);
            if (value.subtract(attackDirection.scale(value.dot(attackDirection)))
                    .lengthSqr() <= 1.0e-8) {
                throw new IllegalArgumentException(
                        "Laminar Cut plane direction cannot be parallel to its attack direction");
            }
            return value;
        }
        if (!resolveRandom) return null;
        var right = LaminarCutter.Server.bladeRight(attackDirection);
        var normal = attackDirection.cross(right).normalize();
        var angle = player.getRandom().nextDouble() * Math.PI * 2.0;
        return right.scale(Math.cos(angle)).add(normal.scale(Math.sin(angle)));
    }

    private void spawnPushEffect(Entity target) {
        var center = target.getBoundingBox().getCenter();
        AeromanipVfx.burst(targets.level(), center,
                Math.max(0.35, target.getBbWidth() * 0.55));
    }

    private static void restoreVelocity(Entity entity, Vec3 velocity) {
        if (entity == null || entity.isRemoved()) return;
        EntityMotionGuard.runInternalCorrection(
                entity,
                () -> entity.setDeltaMovement(velocity)
        );
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
        }
    }

    private static Vec3 vector(ProgramDirection direction) {
        Objects.requireNonNull(direction, "direction");
        return new Vec3(direction.x(), direction.y(), direction.z());
    }

    private Object requireNozzleTarget(
            Object targetReference,
            AeromanipProgramNodeCatalog.NozzleTargetType targetType
    ) {
        Objects.requireNonNull(targetType, "targetType");
        if (targetType == AeromanipProgramNodeCatalog.NozzleTargetType.ENTITY) {
            if (!(targetReference instanceof Entity entity)
                    || !targets.sameUsableLevel(entity)
                    || entity instanceof HighSpeedJetNozzle
                    || entity.distanceToSqr(player) > MAX_ACTION_RANGE * MAX_ACTION_RANGE) {
                throw new IllegalArgumentException(
                        "Entity cannot support a temporary jet nozzle");
            }
            return entity;
        }
        if (!(targetReference instanceof ProgramBlockPosition block)) {
            throw new IllegalArgumentException("Temporary nozzle block target is invalid");
        }
        var center = targets.requireLocalPosition(new ProgramWorldPosition(
                block.dimension(), block.x() + 0.5, block.y() + 0.5, block.z() + 0.5));
        if (center.distanceToSqr(player.position()) > MAX_ACTION_RANGE * MAX_ACTION_RANGE) {
            throw new IllegalArgumentException("Temporary nozzle block is outside program range");
        }
        var pos = new BlockPos(block.x(), block.y(), block.z());
        if (!targets.level().hasChunkAt(pos)) {
            throw new IllegalArgumentException("Temporary nozzle block is not loaded");
        }
        return pos;
    }

    private static int requireJetDuration(int durationSeconds) {
        if (durationSeconds < 1 || durationSeconds > 60) {
            throw new IllegalArgumentException("Jet duration must be between 1 and 60 seconds");
        }
        return durationSeconds * 20;
    }

    private static Vec3 normalizedVector(ProgramDirection direction) {
        var value = vector(direction);
        if (!Double.isFinite(value.x)
                || !Double.isFinite(value.y)
                || !Double.isFinite(value.z)
                || value.lengthSqr() <= 1.0e-12) {
            throw new IllegalArgumentException("Direction is invalid");
        }
        return value.normalize();
    }

    private static double pushRange(float power) {
        return ProgramPowerScale.interpolate(power, 8.0, 16.0, 24.0);
    }

    private static double airflowPushSpeed(float power) {
        return ProgramPowerScale.interpolate(power, 0.45, 0.85, 1.35);
    }

    private static float airflowPushCost(float power) {
        return ProgramPowerScale.cost(10.0f, power);
    }

    private static float laminarRange(float power) {
        ProgramPowerScale.require(power);
        return 32.0f;
    }

    private static float laminarDamageScale(float power) {
        return ProgramPowerScale.damageMultiplier(power);
    }

    private static float laminarCost(float power) {
        return ProgramPowerScale.cost(20.0f, power);
    }
}
