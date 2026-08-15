package org.academy.internal.common.ability.teleport.program;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Authoritative Minecraft-server adapter for Teleport programs. */
public final class ServerTeleportProgramRuntime implements TeleportProgramRuntime {
    public static final double MAX_QUERY_RANGE = 32.0;
    public static final int MAX_QUERY_RESULTS = 128;

    private final ServerPlayer player;
    private final ServerProgramTargetResolver targets;

    public ServerTeleportProgramRuntime(ServerPlayer player) {
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
    public ProgramActionTransaction.ProgramAction teleportSelf(
            ProgramWorldPosition destination,
            float power
    ) {
        return teleportAction(
                player,
                destination,
                power,
                Skills.SELF_TELEPORT.get(),
                selfRange(power),
                selfCost(power),
                false
        );
    }

    @Override
    public ProgramActionTransaction.ProgramAction teleportEntity(
            Object entityReference,
            ProgramWorldPosition destination,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Entity target;
            private ProgramActionTransaction.ProgramAction action;

            @Override
            public void validate() throws Exception {
                target = requireEntityTarget(entityReference);
                if (target == player) {
                    throw new IllegalArgumentException(
                            "Entity Teleport cannot replace the Self Teleport capability");
                }
                requireEntityInRange(target, entityTargetRange(power));
                requireMovementAllowed(target);
                action = teleportAction(
                        target,
                        destination,
                        power,
                        Skills.QUICK_LOCATION_TELEPORT.get(),
                        entityMoveRange(power),
                        entityCost(power),
                        true
                );
                action.validate();
            }

            @Override
            public ProgramActionTransaction.Undo apply() throws Exception {
                if (action == null) {
                    throw new IllegalStateException("Entity Teleport was not validated");
                }
                return action.apply();
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

    private ProgramActionTransaction.ProgramAction teleportAction(
            Entity target,
            ProgramWorldPosition destination,
            float power,
            Skill skill,
            double maximumDisplacement,
            float cost,
            boolean externallyMoved
    ) {
        Objects.requireNonNull(destination, "destination");
        ProgramPowerScale.require(power);
        return new ProgramActionTransaction.ProgramAction() {
            private Vec3 requested;
            private Vec3 safeDestination;

            @Override
            public void validate() {
                requireCasterReady(skill);
                requireTeleportable(target, externallyMoved);
                requested = targets.requireLocalPosition(destination);
                requirePositionInRange(requested, MAX_QUERY_RANGE);
                safeDestination = requireSafeDestination(
                        target, requested, maximumDisplacement);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(skill);
                requireTeleportable(target, externallyMoved);
                safeDestination = requireSafeDestination(
                        target, requested, maximumDisplacement);
                var origin = target.position();
                var previousMotion = target.getDeltaMovement();
                charge(skill, cost);
                if (!teleport(target, safeDestination)) {
                    throw new IllegalStateException("Target rejected program teleport");
                }
                setMotion(target, Vec3.ZERO);
                target.resetFallDistance();
                return () -> restore(target, origin, previousMotion);
            }
        };
    }

    private Entity requireEntityTarget(Object value) {
        if (!(value instanceof Entity entity) || !targets.sameUsableLevel(entity)) {
            throw new IllegalArgumentException("Entity target is invalid");
        }
        return entity;
    }

    private void requireCasterReady(Skill skill) {
        if (!player.isAlive()
                || player.hasDisconnected()
                || player.isSpectator()
                || !skill.isEnabled(player)) {
            throw new IllegalStateException("Required Teleport skill is unavailable");
        }
    }

    private void requireTeleportable(Entity target, boolean externallyMoved) {
        if (!targets.sameUsableLevel(target)
                || target.isSpectator()
                || target.isPassenger()
                || target.isVehicle()) {
            throw new IllegalArgumentException("Entity cannot be teleported by this program");
        }
        if (externallyMoved && !EntityMotionGuard.canApplyMotionFrom(player, target)) {
            throw new IllegalArgumentException("Entity rejected forced teleportation");
        }
    }

    private void requireMovementAllowed(Entity target) {
        if (target instanceof LivingEntity living
                && CtaFriendlyFireWhitelist.shouldProtect(player, living)) {
            throw new IllegalArgumentException("Friendly-fire policy protects the target");
        }
    }

    private Vec3 requireSafeDestination(
            Entity target,
            Vec3 requested,
            double maximumDisplacement
    ) {
        var safe = TeleportSafety.findSafe(target, targets.level(), requested);
        if (safe == null) {
            throw new IllegalArgumentException("Teleport destination is not safe and loaded");
        }
        if (safe.distanceToSqr(target.position())
                > maximumDisplacement * maximumDisplacement) {
            throw new IllegalArgumentException("Teleport exceeds its power limit");
        }
        return safe;
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
            throw new IllegalStateException("Insufficient CP for Teleport program action");
        }
    }

    private boolean teleport(Entity entity, Vec3 destination) {
        return Boolean.TRUE.equals(EntityMotionGuard.callWithMotionSource(
                player,
                () -> TeleportSync.teleportInstantly(entity, destination)
        ));
    }

    private void setMotion(Entity entity, Vec3 motion) {
        EntityMotionGuard.runWithMotionSource(player, () -> entity.setDeltaMovement(motion));
        entity.hurtMarked = true;
        syncMotion(entity);
    }

    private static void restore(Entity entity, Vec3 position, Vec3 motion) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel)
                || entity.isRemoved()) {
            return;
        }
        EntityMotionGuard.runInternalCorrection(entity, () -> {
            if (!TeleportSync.teleportInstantly(entity, position)) {
                throw new IllegalStateException("Unable to restore teleported entity");
            }
            entity.setDeltaMovement(motion);
        });
        entity.hurtMarked = true;
        syncMotion(entity);
    }

    private static void syncMotion(Entity entity) {
        if (entity instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
        }
    }

    private static double selfRange(float power) {
        return ProgramPowerScale.interpolate(power, 8.0, 16.0, 32.0);
    }

    private static float selfCost(float power) {
        return ProgramPowerScale.cost(10.0f, power);
    }

    private static double entityTargetRange(float power) {
        return ProgramPowerScale.interpolate(power, 8.0, 16.0, 32.0);
    }

    private static double entityMoveRange(float power) {
        return ProgramPowerScale.interpolate(power, 8.0, 16.0, 32.0);
    }

    private static float entityCost(float power) {
        return ProgramPowerScale.cost(30.0f, power);
    }
}
