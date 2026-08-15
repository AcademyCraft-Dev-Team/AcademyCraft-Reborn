package org.academy.internal.common.ability.electromaster.program;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Authoritative Minecraft-server adapter for Electromaster programs. */
public final class ServerElectromasterProgramRuntime implements ElectromasterProgramRuntime {
    public static final double MAX_QUERY_RANGE = 32.0;
    public static final int MAX_QUERY_RESULTS = 128;

    private final ServerPlayer player;
    private final ServerProgramTargetResolver targets;

    public ServerElectromasterProgramRuntime(ServerPlayer player) {
        this.player = Objects.requireNonNull(player, "player");
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
    public ProgramActionTransaction.ProgramAction arcDischarge(
            Object entityReference,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private LivingEntity target;

            @Override
            public void validate() {
                requireCasterReady(Skills.ARC_GENERATE.get());
                target = requireLivingTarget(entityReference);
                requireEntityInRange(target, arcRange(power));
                requireHostileActionAllowed(target);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                charge(Skills.ARC_GENERATE.get(), arcCost(power));
                var system = AbilitySystemServer.getSystem(player);
                var damage = ArcGenerate.programDamage(
                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                        system.getPlayerDamageMultiplier(player.getUUID()))
                        * arcDamageScale(power);
                if (damage > 0.0f && !target.hurtServer(
                        targets.level(),
                        SkillDamageSource.of(player, Skills.ARC_GENERATE.get()),
                        damage)) {
                    throw new IllegalStateException("Arc discharge target rejected damage");
                }
                ElectromasterArcEffects.spawnChainArc(
                        targets.level(),
                        player.getBoundingBox().getCenter(),
                        target.getBoundingBox().getCenter()
                );
                return ProgramActionTransaction.Undo.NONE;
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction magneticMove(
            Object entityReference,
            ProgramWorldPosition destination,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Entity target;
            private Vec3 targetPosition;

            @Override
            public void validate() {
                requireCasterReady(Skills.MAGNET_MANIPULATION.get());
                target = requireEntityTarget(entityReference);
                if (!MagnetManipulation.isMagnetic(target)) {
                    throw new IllegalArgumentException("Entity target is not magnetic");
                }
                requireEntityInRange(target, MAX_QUERY_RANGE);
                requireMovementAllowed(target);
                targetPosition = targets.requireLocalPosition(destination);
                requirePositionInRange(targetPosition, MAX_QUERY_RANGE);
                if (target.getBoundingBox().getCenter().distanceTo(targetPosition)
                        > magneticMoveRange(power)) {
                    throw new IllegalArgumentException("Magnetic move exceeds its power limit");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                if (!EntityMotionGuard.canApplyMotionFrom(player, target)) {
                    throw new IllegalStateException("Target rejected magnetic movement");
                }
                var previous = target.getDeltaMovement();
                var origin = target.getBoundingBox().getCenter();
                var difference = targetPosition.subtract(origin);
                var velocity = MagnetManipulation.calculateControlledBlockVelocity(
                        previous,
                        origin,
                        targetPosition,
                        difference,
                        magneticMoveSpeed(power),
                        0.65
                );
                if (!finiteNonZero(velocity)) {
                    throw new IllegalStateException("Magnetic movement produced no velocity");
                }
                charge(Skills.MAGNET_MANIPULATION.get(), magneticMoveCost(power));
                setVelocity(target, velocity);
                return () -> {
                    if (targets.sameUsableLevel(target)) setVelocity(target, previous);
                };
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

    private void requireCasterReady(Skill skill) {
        if (!player.isAlive()
                || player.hasDisconnected()
                || player.isSpectator()
                || !skill.isEnabled(player)) {
            throw new IllegalStateException("Required Electromaster skill is unavailable");
        }
    }

    private Entity requireEntityTarget(Object value) {
        if (!(value instanceof Entity entity) || !targets.sameUsableLevel(entity)) {
            throw new IllegalArgumentException("Entity target is invalid");
        }
        return entity;
    }

    private LivingEntity requireLivingTarget(Object value) {
        var entity = requireEntityTarget(value);
        if (!(entity instanceof LivingEntity living) || living == player) {
            throw new IllegalArgumentException("Arc discharge needs another living entity");
        }
        return living;
    }

    private void requireHostileActionAllowed(LivingEntity target) {
        if (CtaFriendlyFireWhitelist.shouldProtect(player, target)) {
            throw new IllegalArgumentException("Friendly-fire policy protects the target");
        }
    }

    private void requireMovementAllowed(Entity target) {
        if (target != player
                && target instanceof LivingEntity living
                && CtaFriendlyFireWhitelist.shouldProtect(player, living)) {
            throw new IllegalArgumentException("Friendly-fire policy protects the target");
        }
    }

    private void requireEntityInRange(Entity entity, double range) {
        if (entity.distanceToSqr(player) > range * range) {
            throw new IllegalArgumentException("Entity target is outside program range");
        }
    }

    private void requirePositionInRange(Vec3 position, double range) {
        if (position.distanceToSqr(player.position()) > range * range) {
            throw new IllegalArgumentException("Position is outside program range");
        }
    }

    private void charge(Skill skill, float cost) {
        if (!AbilitySystemServer.getSystem(player).tryTimedOccupation(player, cost, skill)) {
            throw new IllegalStateException("Insufficient CP for Electromaster program action");
        }
    }

    private void setVelocity(Entity entity, Vec3 velocity) {
        EntityMotionGuard.runWithMotionSource(player, () -> entity.setDeltaMovement(velocity));
        entity.hurtMarked = true;
        entity.resetFallDistance();
        if (entity instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
        }
    }

    private static double arcRange(float power) {
        ProgramPowerScale.require(power);
        return 12.0;
    }

    private static float arcDamageScale(float power) {
        return ProgramPowerScale.damageMultiplier(power);
    }

    private static float arcCost(float power) {
        return ProgramPowerScale.cost(10.0f, power);
    }

    private static double magneticMoveRange(float power) {
        return ProgramPowerScale.interpolate(power, 6.0, 12.0, 20.0);
    }

    private static double magneticMoveSpeed(float power) {
        return ProgramPowerScale.interpolate(power, 0.45, 0.8, 1.15);
    }

    private static float magneticMoveCost(float power) {
        return ProgramPowerScale.cost(16.0f, power);
    }

    private static boolean finiteNonZero(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z)
                && value.lengthSqr() > 1.0E-12;
    }
}
