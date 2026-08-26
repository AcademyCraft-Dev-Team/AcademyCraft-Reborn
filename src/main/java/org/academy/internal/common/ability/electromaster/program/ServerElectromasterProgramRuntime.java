package org.academy.internal.common.ability.electromaster.program;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.lv3.CurrentRecharge;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.util.EnergyChargeHelper;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;

import java.util.*;

/**
 * Authoritative Minecraft-server adapter for Electromaster programs.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ServerElectromasterProgramRuntime implements ElectromasterProgramRuntime {
    public static final double MAX_QUERY_RANGE = 32.0;
    public static final int MAX_QUERY_RESULTS = 128;
    private static final Map<UUID, Map<String, Entity>> CONTROLLED = new HashMap<>();
    private static final Map<UUID, ControlDestination> CONTROL_DESTINATIONS = new HashMap<>();

    private final ServerPlayer player;
    private final float costMultiplier;
    private final ServerProgramTargetResolver targets;

    public ServerElectromasterProgramRuntime(ServerPlayer player) {
        this(player, 1.0f);
    }

    public ServerElectromasterProgramRuntime(ServerPlayer player, float costMultiplier) {
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
            Object targetReference,
            ProgramWorldPosition destination,
            float power,
            ElectromasterProgramNodeCatalog.EnergyTargetType targetType,
            ElectromasterProgramNodeCatalog.MagneticMode mode
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Entity target;
            private BlockPos sourceBlock;
            private BlockState sourceState;
            private String targetKey;
            private Vec3 targetPosition;
            private boolean createdBlockEntity;
            private boolean wasControlled;
            private ControlDestination previousControl;

            @Override
            public void validate() {
                requireCasterReady(Skills.MAGNET_MANIPULATION.get());
                targetPosition = targets.requireLocalPosition(destination);
                requirePositionInRange(targetPosition, MAX_QUERY_RANGE);
                if (targetType == ElectromasterProgramNodeCatalog.EnergyTargetType.ENTITY) {
                    target = requireEntityTarget(targetReference);
                    targetKey = entityKey(target);
                    if (!MagnetManipulation.isMagnetic(target)) {
                        throw new IllegalArgumentException("Entity target is not magnetic");
                    }
                    requireEntityInRange(target, MAX_QUERY_RANGE);
                    requireMovementAllowed(target);
                } else {
                    sourceBlock = requireLocalBlock(targetReference);
                    targetKey = blockKey(sourceBlock);
                    target = controlled().get(targetKey);
                    if (target == null) {
                        sourceState = targets.level().getBlockState(sourceBlock);
                        if (!MagnetManipulation.isMagnetic(sourceState)
                                || sourceState.isAir()
                                || sourceState.hasBlockEntity()
                                || sourceState.getDestroySpeed(targets.level(), sourceBlock) < 0.0f
                                || !targets.level().mayInteract(player, sourceBlock)) {
                            throw new IllegalArgumentException("Block target is not magnetically movable");
                        }
                    }
                }
                if (mode == ElectromasterProgramNodeCatalog.MagneticMode.LAUNCH
                        && (target == null || !target.isAlive())) {
                    throw new IllegalArgumentException("Magnetic launch target is not controlled");
                }
                var origin = target == null
                        ? Vec3.atCenterOf(sourceBlock)
                        : target.getBoundingBox().getCenter();
                if (origin.distanceTo(targetPosition) > magneticMoveRange(power)) {
                    throw new IllegalArgumentException("Magnetic move exceeds its power limit");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                var roster = controlled();
                wasControlled = roster.containsKey(targetKey);
                previousControl = CONTROL_DESTINATIONS.get(player.getUUID());
                if (target == null) {
                    if (!targets.level().getBlockState(sourceBlock).equals(sourceState)) {
                        throw new IllegalStateException("Magnetic block changed before execution");
                    }
                    target = FallingBlockEntity.fall(targets.level(), sourceBlock, sourceState);
                    target.setNoGravity(true);
                    createdBlockEntity = true;
                    roster.put(targetKey, target);
                } else if (!EntityMotionGuard.canApplyMotionFrom(player, target)) {
                    throw new IllegalStateException("Target rejected magnetic movement");
                }
                var previous = new HashMap<Entity, Vec3>();
                charge(Skills.MAGNET_MANIPULATION.get(), magneticMoveCost(power));
                if (mode == ElectromasterProgramNodeCatalog.MagneticMode.PULL) {
                    roster.put(targetKey, target);
                    roster.entrySet().removeIf(entry -> !entry.getValue().isAlive()
                            || entry.getValue().level() != targets.level());
                    CONTROL_DESTINATIONS.put(player.getUUID(),
                            new ControlDestination(player, targetPosition, power));
                    moveControlledTargets(player, roster, targetPosition, power, previous);
                } else {
                    previous.put(target, target.getDeltaMovement());
                    var origin = target.getBoundingBox().getCenter();
                    var difference = targetPosition.subtract(origin);
                    if (difference.lengthSqr() < 1.0E-12) {
                        throw new IllegalStateException("Magnetic launch produced no velocity");
                    }
                    damageAlongTrajectory(target, origin, targetPosition, power);
                    if (target instanceof FallingBlockEntity) target.setNoGravity(false);
                    setVelocity(target, difference.normalize().scale(magneticLaunchSpeed(power)));
                    roster.remove(targetKey);
                    if (roster.isEmpty()) {
                        CONTROLLED.remove(player.getUUID(), roster);
                        CONTROL_DESTINATIONS.remove(player.getUUID());
                    }
                }
                return () -> {
                    for (var entry : previous.entrySet()) {
                        if (targets.sameUsableLevel(entry.getKey())) {
                            setVelocity(entry.getKey(), entry.getValue());
                        }
                    }
                    if (mode == ElectromasterProgramNodeCatalog.MagneticMode.LAUNCH
                            && targets.sameUsableLevel(target)) {
                        target.setNoGravity(target instanceof FallingBlockEntity);
                        controlled().put(targetKey, target);
                    } else if (!wasControlled) {
                        roster.remove(targetKey);
                    }
                    restoreControlDestination(player.getUUID(), previousControl);
                    if (createdBlockEntity && target.isAlive()) {
                        target.discard();
                        if (targets.level().getBlockState(sourceBlock).isAir()) {
                            targets.level().setBlock(sourceBlock, sourceState, 3);
                        }
                        controlled().remove(targetKey);
                    }
                };
            }
        };
    }

    @Override
    public List<ProgramBlockPosition> chargeableBlocksAround(
            ProgramWorldPosition center,
            double radius
    ) {
        var origin = targets.requireLocalPosition(center);
        var bounded = Math.clamp(radius, 0.0, MAX_QUERY_RANGE);
        var minimum = BlockPos.containing(origin.add(-bounded, -bounded, -bounded));
        var maximum = BlockPos.containing(origin.add(bounded, bounded, bounded));
        var result = new ArrayList<ProgramBlockPosition>();
        var dimension = targets.level().dimension().identifier();
        for (var pos : BlockPos.betweenClosed(minimum, maximum)) {
            if (result.size() >= MAX_QUERY_RESULTS) break;
            if (Vec3.atCenterOf(pos).distanceToSqr(origin) > bounded * bounded
                    || !targets.level().hasChunkAt(pos)
                    || !EnergyChargeHelper.hasBlockEnergyStorage(targets.level(), pos)) continue;
            result.add(new ProgramBlockPosition(dimension, pos.getX(), pos.getY(), pos.getZ()));
        }
        return List.copyOf(result);
    }

    @Override
    public OptionalDouble entityEnergyFraction(Object entityReference) {
        var entity = requireEntityTarget(entityReference);
        return entity instanceof LivingEntity living
                ? EnergyChargeHelper.entityEnergyFraction(living)
                : OptionalDouble.empty();
    }

    @Override
    public OptionalDouble blockEnergyFraction(ProgramBlockPosition block) {
        return EnergyChargeHelper.blockEnergyFraction(targets.level(), requireLocalBlock(block));
    }

    @Override
    public int redstonePower(ProgramBlockPosition block) {
        return targets.level().getBestNeighborSignal(requireLocalBlock(block));
    }

    @Override
    public ProgramActionTransaction.ProgramAction currentRecharge(
            Object targetReference,
            ElectromasterProgramNodeCatalog.EnergyTargetType targetType
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private LivingEntity entity;
            private BlockPos block;

            @Override
            public void validate() {
                requireCasterReady(Skills.CURRENT_RECHARGE.get());
                if (targetType == ElectromasterProgramNodeCatalog.EnergyTargetType.ENTITY) {
                    var resolved = requireEntityTarget(targetReference);
                    if (!(resolved instanceof LivingEntity living)) {
                        throw new IllegalArgumentException("Current Recharge needs a living entity");
                    }
                    entity = living;
                    requireEntityInRange(entity, MAX_QUERY_RANGE);
                } else {
                    block = requireLocalBlock(targetReference);
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                validate();
                charge(Skills.CURRENT_RECHARGE.get(), 15.0f);
                var context = CurrentRecharge.Server.startProgramCharge(player, entity, block);
                return context::unregister;
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

    private BlockPos requireLocalBlock(Object value) {
        if (!(value instanceof ProgramBlockPosition(net.minecraft.resources.Identifier dimension, int x, int y, int z))
                || !dimension.equals(targets.level().dimension().identifier())) {
            throw new IllegalArgumentException("Block target is in another dimension");
        }
        var position = new BlockPos(x, y, z);
        if (!targets.level().hasChunkAt(position)
                || position.getY() < targets.level().getMinY()
                || position.getY() >= targets.level().getMaxY()
                || Vec3.atCenterOf(position).distanceToSqr(player.position())
                > MAX_QUERY_RANGE * MAX_QUERY_RANGE) {
            throw new IllegalArgumentException("Block target is outside program range");
        }
        return position;
    }

    private Map<String, Entity> controlled() {
        return CONTROLLED.computeIfAbsent(player.getUUID(), _ -> new HashMap<>());
    }

    private static String entityKey(Entity entity) {
        return "entity:" + entity.getUUID();
    }

    private String blockKey(BlockPos position) {
        return "block:" + targets.level().dimension().identifier() + ':'
                + position.getX() + ':' + position.getY() + ':' + position.getZ();
    }

    private void damageAlongTrajectory(
            Entity launched,
            Vec3 origin,
            Vec3 destination,
            float power
    ) {
        var system = AbilitySystemServer.getSystem(player);
        var damage = 8.0f
                * system.getPlayerAbilityPowerMultiplier(player.getUUID())
                * system.getPlayerDamageMultiplier(player.getUUID())
                * ProgramPowerScale.damageMultiplier(power);
        if (damage <= 0.0f) return;
        var bounds = new AABB(origin, destination).inflate(1.0);
        var source = SkillDamageSource.of(player, Skills.MAGNET_MANIPULATION.get());
        for (var living : targets.level().getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                value -> value != player
                        && value != launched
                        && value.isAlive()
                        && !CtaFriendlyFireWhitelist.shouldProtect(player, value)
        )) {
            if (distanceToSegmentSqr(living.getBoundingBox().getCenter(), origin, destination)
                    <= 1.0) {
                living.hurtServer(targets.level(), source, damage);
            }
        }
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        var delta = end.subtract(start);
        var lengthSquared = delta.lengthSqr();
        if (lengthSquared < 1.0E-12) return point.distanceToSqr(start);
        var t = Math.clamp(point.subtract(start).dot(delta) / lengthSquared, 0.0, 1.0);
        return point.distanceToSqr(start.add(delta.scale(t)));
    }

    public static void releaseControlled(ServerPlayer player) {
        var controlled = CONTROLLED.remove(player.getUUID());
        CONTROL_DESTINATIONS.remove(player.getUUID());
        if (controlled == null) return;
        for (var entity : controlled.values()) {
            if (entity instanceof FallingBlockEntity && entity.isAlive()) entity.setNoGravity(false);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        for (var entry : List.copyOf(CONTROL_DESTINATIONS.entrySet())) {
            var playerId = entry.getKey();
            var control = entry.getValue();
            var controller = control.controller();
            var roster = CONTROLLED.get(playerId);
            if (roster == null || roster.isEmpty()
                    || controller.hasDisconnected()
                    || !controller.isAlive()
                    || !Skills.MAGNET_MANIPULATION.get().isEnabled(controller)) {
                releaseControlled(controller);
                continue;
            }
            roster.entrySet().removeIf(targetEntry -> {
                var target = targetEntry.getValue();
                var invalid = !target.isAlive()
                        || target.level() != controller.level()
                        || !EntityMotionGuard.canApplyMotionFrom(controller, target);
                if (invalid && target instanceof FallingBlockEntity && target.isAlive()) {
                    target.setNoGravity(false);
                }
                return invalid;
            });
            if (roster.isEmpty()) {
                releaseControlled(controller);
                continue;
            }
            moveControlledTargets(
                    controller, roster, control.destination(), control.power(), null);
        }
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
        if (!AbilitySystemServer.getSystem(player).tryTimedOccupation(
                player, cost * costMultiplier, skill)) {
            throw new IllegalStateException("Insufficient CP for Electromaster program action");
        }
    }

    private static float requireCostMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0f) {
            throw new IllegalArgumentException("Program cost multiplier must be positive");
        }
        return multiplier;
    }

    private void setVelocity(Entity entity, Vec3 velocity) {
        setVelocity(player, entity, velocity);
    }

    private static void setVelocity(ServerPlayer controller, Entity entity, Vec3 velocity) {
        EntityMotionGuard.runWithMotionSource(controller, () -> entity.setDeltaMovement(velocity));
        entity.hurtMarked = true;
        entity.resetFallDistance();
        if (entity instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
        }
    }

    private static void moveControlledTargets(
            ServerPlayer controller,
            Map<String, Entity> roster,
            Vec3 destination,
            float power,
            Map<Entity, Vec3> previous
    ) {
        for (var controlledTarget : roster.values()) {
            if (previous != null) {
                previous.put(controlledTarget, controlledTarget.getDeltaMovement());
            }
            var origin = controlledTarget.getBoundingBox().getCenter();
            var difference = destination.subtract(origin);
            var velocity = MagnetManipulation.calculateControlledBlockVelocity(
                    controlledTarget.getDeltaMovement(), origin, destination,
                    difference, magneticMoveSpeed(power), 0.65);
            if (finiteNonZero(velocity)) {
                setVelocity(controller, controlledTarget, velocity);
            }
        }
    }

    private static void restoreControlDestination(
            UUID playerId,
            ControlDestination previous
    ) {
        if (previous == null) CONTROL_DESTINATIONS.remove(playerId);
        else CONTROL_DESTINATIONS.put(playerId, previous);
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

    private static double magneticLaunchSpeed(float power) {
        return ProgramPowerScale.interpolate(power, 0.8, 1.4, 2.1);
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

    private record ControlDestination(
            ServerPlayer controller,
            Vec3 destination,
            float power
    ) {
    }
}
