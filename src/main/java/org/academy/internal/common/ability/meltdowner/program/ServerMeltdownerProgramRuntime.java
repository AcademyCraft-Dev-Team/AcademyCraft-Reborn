package org.academy.internal.common.ability.meltdowner.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.meltdowner.skills.lv1.SingleHighSpeedElectronBeam;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Authoritative Minecraft-server adapter for Meltdowner programs. */
public final class ServerMeltdownerProgramRuntime implements MeltdownerProgramRuntime {
    public static final double MAX_QUERY_RANGE = 48.0;
    public static final int MAX_QUERY_RESULTS = 128;

    private final ServerPlayer player;
    private final float costMultiplier;
    private final ServerProgramTargetResolver targets;

    public ServerMeltdownerProgramRuntime(ServerPlayer player) {
        this(player, 1.0f);
    }

    public ServerMeltdownerProgramRuntime(ServerPlayer player, float costMultiplier) {
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
    public ProgramActionTransaction.ProgramAction fireElectronBeam(
            @Nullable ProgramWorldPosition origin,
            @Nullable ProgramDirection direction,
            @Nullable ProgramWorldPosition target,
            float power,
            boolean destroyBlocks
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private BeamPlan plan;

            @Override
            public void validate() {
                requireCasterReady(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get());
                plan = requireBeamPlan(origin, direction, target, electronBeamLength(power));
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                var skill = Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get();
                requireCasterReady(skill);
                plan = requireBeamPlan(origin, direction, target, electronBeamLength(power));
                charge(skill, electronBeamCost(power));
                var beam = spawnBeam(
                        skill,
                        plan.origin(),
                        plan.direction(),
                        plan.length(),
                        SingleHighSpeedElectronBeam.BASE_DAMAGE * damageScale(power),
                        SingleHighSpeedElectronBeam.MAX_HEALTH_DAMAGE_RATIO
                                * damageScale(power),
                        destroyBlocks && DestroyBlocksSetting.canDestroyBlocks(player, skill),
                        Skills.RADIATION_INTENSIFY.get().isEnabled(player),
                        electronBeamScale(power),
                        configuredBeamAttackDelayTicks()
                );
                return () -> discard(beam);
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction fireMiningBeam(
            @Nullable ProgramWorldPosition origin,
            @Nullable ProgramDirection direction,
            @Nullable ProgramWorldPosition target,
            @Nullable ProgramBlockPosition legacyBlock,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private MiningPlan plan;

            @Override
            public void validate() {
                requireCasterReady(Skills.MINING_BEAM.get());
                plan = requireMiningPlan(origin, direction, target, legacyBlock, miningBeamRange(power));
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                var skill = Skills.MINING_BEAM.get();
                requireCasterReady(skill);
                plan = requireMiningPlan(origin, direction, target, legacyBlock, miningBeamRange(power));
                charge(skill, miningBeamCost(power));
                var beam = spawnBeam(
                        skill,
                        plan.origin(),
                        plan.direction(),
                        plan.length(),
                        0.0f,
                        0.0f,
                        true,
                        false,
                        miningBeamScale(power),
                        configuredBeamAttackDelayTicks()
                );
                return () -> discard(beam);
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction atomicJet(
            Object entityReference,
            ProgramDirection direction,
            float power,
            boolean destroyBlocks
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Entity target;
            private Vec3 launchDirection;

            @Override
            public void validate() {
                requireCasterReady(Skills.JET_STRIKE.get());
                target = requireEntity(entityReference);
                launchDirection = normalize(direction);
                if (!EntityMotionGuard.canApplyMotionFrom(player, target)) {
                    throw new IllegalArgumentException("Atomic Jet target rejected movement");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                validate();
                var skill = Skills.JET_STRIKE.get();
                charge(skill, ProgramPowerScale.cost(20.0f, power));
                var previousMotion = target.getDeltaMovement();
                var origin = target.getBoundingBox().getCenter();
                var beam = spawnBeam(
                        skill,
                        origin,
                        launchDirection,
                        electronBeamLength(power),
                        SingleHighSpeedElectronBeam.BASE_DAMAGE * damageScale(power),
                        0.0f,
                        destroyBlocks && DestroyBlocksSetting.canDestroyBlocks(player, skill),
                        false,
                        electronBeamScale(power),
                        0
                );
                beam.setIgnoredTarget(target);
                setVelocity(player, target, previousMotion.add(launchDirection.scale(-1.1)));
                return () -> {
                    discard(beam);
                    if (targets.sameUsableLevel(target)) {
                        setVelocity(player, target, previousMotion);
                    }
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

    private MiningPlan requireMiningPlan(
            @Nullable ProgramWorldPosition origin,
            @Nullable ProgramDirection direction,
            @Nullable ProgramWorldPosition target,
            @Nullable ProgramBlockPosition legacyTarget,
            double maximumRange
    ) {
        if (legacyTarget == null) {
            var plan = requireBeamPlan(origin, direction, target, maximumRange);
            if (!DestroyBlocksSetting.canDestroyBlocks(player, Skills.MINING_BEAM.get())) {
                throw new IllegalStateException("Mining Beam block destruction is disabled");
            }
            return new MiningPlan(plan.origin(), plan.direction(), plan.length());
        }
        var targetBlock = legacyTarget;
        var level = targets.level();
        if (!targetBlock.dimension().equals(level.dimension().identifier())) {
            throw new IllegalArgumentException("Mining target is in another dimension");
        }
        var position = new BlockPos(targetBlock.x(), targetBlock.y(), targetBlock.z());
        if (position.getY() < level.getMinY()
                || position.getY() >= level.getMaxY()
                || !level.hasChunkAt(position)) {
            throw new IllegalArgumentException("Mining target is outside loaded world bounds");
        }
        var state = level.getBlockState(position);
        if (state.isAir() || !LevelUtil.canBreakBlock(state, 3)) {
            throw new IllegalArgumentException("Mining target cannot be broken");
        }
        if (!DestroyBlocksSetting.canDestroyBlocks(player, Skills.MINING_BEAM.get())) {
            throw new IllegalStateException("Mining Beam block destruction is disabled");
        }
        if (!level.mayInteract(player, position)
                || player.blockActionRestricted(
                level, position, player.gameMode.getGameModeForPlayer())
                || state.getBlock() instanceof GameMasterBlock
                && !player.canUseGameMasterBlocks()) {
            throw new IllegalArgumentException("Mining target is protected");
        }
        var eye = origin == null ? player.getEyePosition() : targets.requireLocalPosition(origin);
        var center = Vec3.atCenterOf(position);
        var offset = center.subtract(eye);
        var distance = offset.length();
        if (!Double.isFinite(distance) || distance < 1.0e-6 || distance > maximumRange) {
            throw new IllegalArgumentException("Mining target is outside program range");
        }
        return new MiningPlan(eye, offset.scale(1.0 / distance), distance);
    }

    private BeamPlan requireBeamPlan(
            @Nullable ProgramWorldPosition requestedOrigin,
            @Nullable ProgramDirection requestedDirection,
            @Nullable ProgramWorldPosition requestedTarget,
            double length
    ) {
        var origin = requestedOrigin == null
                ? player.getEyePosition()
                : targets.requireLocalPosition(requestedOrigin);
        if (origin.distanceToSqr(player.position()) > MAX_QUERY_RANGE * MAX_QUERY_RANGE) {
            throw new IllegalArgumentException("Beam origin is outside program range");
        }
        Vec3 direction;
        if (requestedTarget != null) {
            var target = targets.requireLocalPosition(requestedTarget);
            direction = target.subtract(origin);
        } else if (requestedDirection != null) {
            direction = new Vec3(
                    requestedDirection.x(), requestedDirection.y(), requestedDirection.z());
        } else {
            throw new IllegalArgumentException("Beam direction or target is required");
        }
        return new BeamPlan(origin, normalize(direction), length);
    }

    private HighSpeedElectronBeam spawnBeam(
            Skill skill,
            Vec3 origin,
            Vec3 direction,
            double length,
            float baseDamage,
            float maximumHealthRatio,
            boolean destroysBlocks,
            boolean radiationEnabled,
            float scale,
            int attackDelayTicks
    ) {
        var level = targets.level();
        var system = AbilitySystemServer.getSystem(player);
        var beam = new HighSpeedElectronBeam(
                EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
        beam.configure(
                player,
                skill,
                baseDamage,
                maximumHealthRatio,
                system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                system.getPlayerDamageMultiplier(player.getUUID()),
                radiationEnabled,
                destroysBlocks,
                skill.getEffectiveProficiencyMilestone(player)
        );
        beam.setAttackDelayTicks(attackDelayTicks);
        beam.setBeamLength((float) length);
        beam.setBeamScale(scale);
        beam.setPos(origin);
        beam.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        beam.setXRot((float) Math.toDegrees(-Math.asin(
                Math.clamp(direction.y, -1.0, 1.0))));
        if (!level.addFreshEntity(beam)) {
            throw new IllegalStateException("Unable to spawn Meltdowner program beam");
        }
        level.playSound(
                null,
                player,
                SoundEvents.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                SoundSource.PLAYERS,
                1.0f,
                1.0f
        );
        return beam;
    }

    private void requireCasterReady(Skill skill) {
        if (!player.isAlive()
                || player.hasDisconnected()
                || player.isSpectator()
                || !skill.isEnabled(player)) {
            throw new IllegalStateException("Required Meltdowner skill is unavailable");
        }
    }

    private void charge(Skill skill, float cost) {
        if (!AbilitySystemServer.getSystem(player).tryTimedOccupation(
                player, cost * costMultiplier, skill)) {
            throw new IllegalStateException("Insufficient CP for Meltdowner program action");
        }
    }

    private static Vec3 normalize(ProgramDirection direction) {
        Objects.requireNonNull(direction, "direction");
        var value = new Vec3(direction.x(), direction.y(), direction.z());
        if (!Double.isFinite(value.x)
                || !Double.isFinite(value.y)
                || !Double.isFinite(value.z)
                || value.lengthSqr() < 1.0e-12) {
            throw new IllegalArgumentException("Beam direction is invalid");
        }
        return value.normalize();
    }

    private static Vec3 normalize(Vec3 value) {
        if (value == null
                || !Double.isFinite(value.x)
                || !Double.isFinite(value.y)
                || !Double.isFinite(value.z)
                || value.lengthSqr() < 1.0e-12) {
            throw new IllegalArgumentException("Beam direction is invalid");
        }
        return value.normalize();
    }

    private Entity requireEntity(Object value) {
        if (!(value instanceof Entity entity) || !targets.sameUsableLevel(entity)) {
            throw new IllegalArgumentException("Atomic Jet entity target is invalid");
        }
        if (entity.distanceToSqr(player) > MAX_QUERY_RANGE * MAX_QUERY_RANGE) {
            throw new IllegalArgumentException("Atomic Jet target is outside program range");
        }
        return entity;
    }

    private static void setVelocity(ServerPlayer controller, Entity entity, Vec3 velocity) {
        EntityMotionGuard.runWithMotionSource(
                controller, () -> entity.setDeltaMovement(velocity));
        entity.hurtMarked = true;
        entity.resetFallDistance();
        if (entity instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
        }
    }

    private static void discard(HighSpeedElectronBeam beam) {
        if (beam != null && !beam.isRemoved()) beam.discard();
    }

    private static double electronBeamLength(float power) {
        ProgramPowerScale.require(power);
        return 32.0;
    }

    private static float damageScale(float power) {
        return ProgramPowerScale.damageMultiplier(power);
    }

    private static float electronBeamCost(float power) {
        return ProgramPowerScale.cost(15.0f, power);
    }

    private static double miningBeamRange(float power) {
        return ProgramPowerScale.interpolate(power, 12.0, 28.0, 48.0);
    }

    private static float miningBeamCost(float power) {
        return ProgramPowerScale.cost(20.0f, power);
    }

    private static float electronBeamScale(float power) {
        ProgramPowerScale.require(power);
        return 1.0f;
    }

    private static float miningBeamScale(float power) {
        return ProgramPowerScale.interpolate(power, 0.75f, 1.0f, 1.25f);
    }

    private static float requireCostMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0f) {
            throw new IllegalArgumentException("Program cost multiplier must be positive");
        }
        return multiplier;
    }

    private int configuredBeamAttackDelayTicks() {
        var delay = SingleHighSpeedElectronBeam.getConfiguredAttackDelayTicks(player);
        if (Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get()
                .getEffectiveProficiencyMilestone(player) >= 2) {
            delay = Math.max(0, Math.round(delay * 0.75f));
        }
        return delay;
    }

    private record BeamPlan(Vec3 origin, Vec3 direction, double length) {
    }

    private record MiningPlan(Vec3 origin, Vec3 direction, double length) {
    }
}
