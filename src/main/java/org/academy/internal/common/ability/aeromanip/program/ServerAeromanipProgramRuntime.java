package org.academy.internal.common.ability.aeromanip.program;

import net.minecraft.core.particles.ParticleTypes;
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
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.skills.lv3.LaminarCutter;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Authoritative Minecraft-server adapter for Aeromanip programs. */
public final class ServerAeromanipProgramRuntime implements AeromanipProgramRuntime {
    public static final double MAX_QUERY_RANGE = 32.0;
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
            ProgramDirection direction,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Vec3 normalizedDirection;

            @Override
            public void validate() {
                requireCasterReady(Skills.LAMINAR_CUTTER.get());
                normalizedDirection = vector(direction);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(Skills.LAMINAR_CUTTER.get());
                normalizedDirection = vector(direction);
                if (!LaminarCutter.Server.tryProgramCast(
                        player,
                        normalizedDirection,
                        laminarRange(power),
                        laminarDamageScale(power),
                        laminarCost(power) * costMultiplier
                )) {
                    throw new IllegalStateException("Laminar Cutter program cast was rejected");
                }
                return ProgramActionTransaction.Undo.NONE;
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
                || !AeromanipTargeting.canAffectNegatively(player, entity)
                || AeromanipTargeting.isBoss(entity)
                || entity.distanceToSqr(player) > range * range) {
            throw new IllegalArgumentException("Entity cannot be pushed by this program");
        }
        if (!EntityMotionGuard.canApplyMotionFrom(player, entity)) {
            throw new IllegalArgumentException("Entity rejected forced airflow movement");
        }
        return entity;
    }

    private double requireForceMultiplier(Entity target) {
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

    private void spawnPushEffect(Entity target) {
        var center = target.getBoundingBox().getCenter();
        targets.level().sendParticles(
                ParticleTypes.GUST,
                center.x,
                center.y,
                center.z,
                2,
                Math.max(0.05, target.getBbWidth() * 0.2),
                Math.max(0.05, target.getBbHeight() * 0.1),
                Math.max(0.05, target.getBbWidth() * 0.2),
                0.02
        );
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
