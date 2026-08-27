package org.academy.internal.common.ability.darkmatter.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterDisassemble;
import org.academy.internal.common.ability.darkmatter.skills.lv2.DarkmatterCut;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative Minecraft-server adapter for Darkmatter programs.
 */
public final class ServerDarkmatterProgramRuntime implements DarkmatterProgramRuntime {
    public static final double MAX_QUERY_RANGE = 32.0;
    public static final int MAX_QUERY_RESULTS = 128;

    private final ServerPlayer player;
    private final float costMultiplier;
    private final ServerProgramTargetResolver targets;

    public ServerDarkmatterProgramRuntime(ServerPlayer player) {
        this(player, 1.0f);
    }

    public ServerDarkmatterProgramRuntime(ServerPlayer player, float costMultiplier) {
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
    public ProgramActionTransaction.ProgramAction disassembleBlock(
            ProgramBlockPosition block,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private BlockPos target;

            @Override
            public void validate() {
                requireCasterReady(Skills.DARKMATTER_DISASSEMBLE.get());
                target = requireLocalBlock(block);
                if (!DarkmatterDisassemble.Server.canProgramDestroyBlock(
                        player, target, disassembleBlockRange(power))) {
                    throw new IllegalArgumentException(
                            "Darkmatter program cannot disassemble this block");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(Skills.DARKMATTER_DISASSEMBLE.get());
                target = requireLocalBlock(block);
                if (!DarkmatterDisassemble.Server.tryProgramDestroyBlock(
                        player,
                        target,
                        disassembleBlockRange(power),
                        disassembleCost(power) * costMultiplier
                )) {
                    throw new IllegalStateException(
                            "Darkmatter block disassembly was rejected");
                }
                return ProgramActionTransaction.Undo.NONE;
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction darkmatterCut(
            ProgramDirection direction,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Vec3 normalizedDirection;

            @Override
            public void validate() {
                requireCasterReady(Skills.DARKMATTER_CUT.get());
                normalizedDirection = requireHorizontalDirection(direction);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(Skills.DARKMATTER_CUT.get());
                normalizedDirection = requireHorizontalDirection(direction);
                if (!DarkmatterCut.Server.tryProgramCast(
                        player,
                        normalizedDirection,
                        cutRadius(power),
                        cutDamageScale(power),
                        cutCost(power) * costMultiplier
                )) {
                    throw new IllegalStateException("Darkmatter Cut program cast was rejected");
                }
                return ProgramActionTransaction.Undo.NONE;
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction disassembleEntity(
            Object entity,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private LivingEntity target;

            @Override
            public void validate() {
                requireCasterReady(Skills.DARKMATTER_DISASSEMBLE.get());
                target = requireDisassembleTarget(entity, power);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(Skills.DARKMATTER_DISASSEMBLE.get());
                target = requireDisassembleTarget(entity, power);
                if (!DarkmatterDisassemble.Server.tryProgramAttack(
                        player,
                        target,
                        disassembleEntityRange(power),
                        disassembleDamageScale(power),
                        disassembleCost(power) * costMultiplier
                )) {
                    throw new IllegalStateException(
                            "Darkmatter entity disassembly was rejected");
                }
                return ProgramActionTransaction.Undo.NONE;
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction createBeetle(
            ProgramWorldPosition position,
            float power
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Vec3 spawn;

            @Override
            public void validate() {
                requireCasterReady(Skills.DARKMATTER_CREATION.get());
                spawn = targets.requireLocalPosition(position);
                if (!DarkmatterCreation.Server.canProgramCreate(
                        player, spawn, creationRange(power))) {
                    throw new IllegalArgumentException(
                            "Darkmatter beetle cannot be created at this position");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(Skills.DARKMATTER_CREATION.get());
                spawn = targets.requireLocalPosition(position);
                var created = DarkmatterCreation.Server.tryProgramCreate(
                        player,
                        spawn,
                        creationRange(power),
                        creationCost(power) * costMultiplier
                ).orElseThrow(() -> new IllegalStateException(
                        "Darkmatter beetle creation was rejected"));
                return () -> DarkmatterCreation.Server.discardProgramCreated(player, created);
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

    private BlockPos requireLocalBlock(ProgramBlockPosition block) {
        Objects.requireNonNull(block, "block");
        if (!block.dimension().equals(targets.level().dimension().identifier())) {
            throw new IllegalArgumentException("Block is in another dimension");
        }
        return new BlockPos(block.x(), block.y(), block.z());
    }

    private LivingEntity requireDisassembleTarget(
            Object value,
            float power
    ) {
        if (!(value instanceof LivingEntity target)
                || !targets.sameUsableLevel(target)
                || !DarkmatterDisassemble.Server.canProgramAttack(
                player, target, disassembleEntityRange(power))) {
            throw new IllegalArgumentException(
                    "Entity cannot be disassembled by this program");
        }
        return target;
    }

    private void requireCasterReady(Skill skill) {
        if (!player.isAlive()
                || player.hasDisconnected()
                || player.isSpectator()
                || !skill.isEnabled(player)) {
            throw new IllegalStateException("Required Darkmatter skill is unavailable");
        }
    }

    private static float requireCostMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0f) {
            throw new IllegalArgumentException("Program cost multiplier must be positive");
        }
        return multiplier;
    }

    private static Vec3 requireHorizontalDirection(ProgramDirection direction) {
        Objects.requireNonNull(direction, "direction");
        var horizontal = new Vec3(direction.x(), 0.0, direction.z());
        if (horizontal.lengthSqr() < 1.0E-8) {
            throw new IllegalArgumentException(
                    "Darkmatter Cut requires a non-vertical direction");
        }
        return horizontal.normalize();
    }

    private static double disassembleBlockRange(float power) {
        return ProgramPowerScale.interpolate(power, 8.0, 16.0, 32.0);
    }

    private static double disassembleEntityRange(float power) {
        ProgramPowerScale.require(power);
        return 16.0;
    }

    private static float disassembleCost(float power) {
        return ProgramPowerScale.cost(10.0f, power);
    }

    private static float disassembleDamageScale(float power) {
        return ProgramPowerScale.damageMultiplier(power);
    }

    private static double cutRadius(float power) {
        ProgramPowerScale.require(power);
        return 10.0;
    }

    private static float cutDamageScale(float power) {
        return ProgramPowerScale.damageMultiplier(power);
    }

    private static float cutCost(float power) {
        return ProgramPowerScale.cost(20.0f, power);
    }

    private static double creationRange(float power) {
        return ProgramPowerScale.interpolate(power, 8.0, 16.0, 32.0);
    }

    private static float creationCost(float power) {
        return ProgramPowerScale.cost(60.0f, power);
    }
}
