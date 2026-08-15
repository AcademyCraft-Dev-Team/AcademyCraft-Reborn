package org.academy.internal.common.ability.meltdowner.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Authoritative Minecraft-server adapter for Meltdowner programs. */
public final class ServerMeltdownerProgramRuntime implements MeltdownerProgramRuntime {
    public static final double MAX_QUERY_RANGE = 48.0;
    public static final int MAX_QUERY_RESULTS = 128;

    private final ServerPlayer player;
    private final ServerProgramTargetResolver targets;

    public ServerMeltdownerProgramRuntime(ServerPlayer player) {
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
    public ProgramActionTransaction.ProgramAction fireElectronBeam(
            ProgramDirection direction,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Vec3 normalizedDirection;

            @Override
            public void validate() {
                requireCasterReady(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get());
                normalizedDirection = normalize(direction);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                var skill = Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get();
                requireCasterReady(skill);
                normalizedDirection = normalize(direction);
                charge(skill, electronBeamCost(power));
                var beam = spawnBeam(
                        skill,
                        normalizedDirection,
                        electronBeamLength(power),
                        SingleHighSpeedElectronBeam.BASE_DAMAGE * damageScale(power),
                        SingleHighSpeedElectronBeam.MAX_HEALTH_DAMAGE_RATIO
                                * damageScale(power),
                        DestroyBlocksSetting.canDestroyBlocks(player, skill),
                        Skills.RADIATION_INTENSIFY.get().isEnabled(player),
                        electronBeamScale(power)
                );
                return () -> discard(beam);
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction fireMiningBeam(
            ProgramBlockPosition block,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private MiningPlan plan;

            @Override
            public void validate() {
                requireCasterReady(Skills.MINING_BEAM.get());
                plan = requireMiningPlan(block, miningBeamRange(power));
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                var skill = Skills.MINING_BEAM.get();
                requireCasterReady(skill);
                plan = requireMiningPlan(block, miningBeamRange(power));
                charge(skill, miningBeamCost(power));
                var beam = spawnBeam(
                        skill,
                        plan.direction(),
                        plan.length(),
                        0.0f,
                        0.0f,
                        true,
                        false,
                        miningBeamScale(power)
                );
                return () -> discard(beam);
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
            ProgramBlockPosition target,
            double maximumRange
    ) {
        Objects.requireNonNull(target, "target");
        var level = targets.level();
        if (!target.dimension().equals(level.dimension().identifier())) {
            throw new IllegalArgumentException("Mining target is in another dimension");
        }
        var position = new BlockPos(target.x(), target.y(), target.z());
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
        var eye = player.getEyePosition();
        var center = Vec3.atCenterOf(position);
        var offset = center.subtract(eye);
        var distance = offset.length();
        if (!Double.isFinite(distance) || distance < 1.0e-6 || distance > maximumRange) {
            throw new IllegalArgumentException("Mining target is outside program range");
        }
        return new MiningPlan(offset.scale(1.0 / distance), distance);
    }

    private HighSpeedElectronBeam spawnBeam(
            Skill skill,
            Vec3 direction,
            double length,
            float baseDamage,
            float maximumHealthRatio,
            boolean destroysBlocks,
            boolean radiationEnabled,
            float scale
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
        beam.setAttackDelayTicks(0);
        beam.setBeamLength((float) length);
        beam.setBeamScale(scale);
        beam.setPos(player.getEyePosition().add(direction.scale(0.75)));
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
        if (!AbilitySystemServer.getSystem(player).tryTimedOccupation(player, cost, skill)) {
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

    private record MiningPlan(Vec3 direction, double length) {
    }
}
