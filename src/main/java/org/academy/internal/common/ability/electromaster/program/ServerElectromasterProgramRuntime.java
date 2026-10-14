package org.academy.internal.common.ability.electromaster.program;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ForwardingProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramStructureAnchor;
import org.academy.api.common.ability.program.ProgramStructureControl;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.ability.program.ProgramStructurePose;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.util.ViewTargetScanner;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.arc.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.lv3.CurrentRecharge;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.registry.AbilityProgramSpatialRanges;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.util.EnergyChargeHelper;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Authoritative Minecraft-server adapter for Electromaster programs.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ServerElectromasterProgramRuntime implements ElectromasterProgramRuntime, ForwardingProgramTargetResolver {
    public static final double MAX_QUERY_RANGE = AbilityProgramSpatialRanges.forCategory(
            ElectromasterProgramNodeCatalog.ELECTROMASTER).queryRange();
    public static final double MAX_ACTION_RANGE = AbilityProgramSpatialRanges.forCategory(
            ElectromasterProgramNodeCatalog.ELECTROMASTER).actionRange();
    public static final int MAX_QUERY_RESULTS = 128;
    static final float FORCED_MAGNETIZATION_COST = 24.0f;
    private static final float STRUCTURE_EXTRACT_COST = 30.0f;
    private static final float STRUCTURE_CONTROL_COST = 10.0f;
    private static final float STRUCTURE_MOVE_COST = 5.0f;
    private static final float STRUCTURE_LAUNCH_COST = 15.0f;
    private static final double MAXIMUM_MOVE_SECONDS = 10.0;
    private static final Map<UUID, Map<String, Entity>> CONTROLLED = new HashMap<>();
    private static final Map<UUID, ControlDestination> CONTROL_DESTINATIONS = new HashMap<>();

    private final ServerPlayer player;
    private final float costMultiplier;
    private final ServerProgramTargetResolver targets;
    private final UUID runtimeToken = UUID.randomUUID();
    private final Map<UUID, ProgramStructureControl> issuedControls = new HashMap<>();
    @Override
    public ProgramTargetResolver targetResolver() {
        return targets;
    }

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

    /**
     * Legacy magnetic entity control. Block targets are no longer turned into
     * {@code FallingBlockEntity} instances here: structure extraction, control, movement and
     * launch live in the {@code action/structure/*} family through
     * {@link MagneticStructureService}, so this node explicitly rejects block targets instead of
     * keeping a second path. The magnetic-entity pull/launch semantics are unchanged and marked
     * as outside the structure migration surface.
     */
    @Override
    public ProgramActionTransaction.ProgramAction magneticMove(
            Object targetReference,
            ProgramWorldPosition destination,
            float power,
            ElectromasterProgramNodeCatalog.EnergyTargetType targetType,
            ElectromasterProgramNodeCatalog.MagneticMode mode,
            boolean forceMagnetize
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Entity target;
            private String targetKey;
            private Vec3 targetPosition;
            private boolean wasControlled;
            private ControlDestination previousControl;

            @Override
            public void validate() {
                requireCasterReady(Skills.MAGNET_MANIPULATION.get());
                if (targetType != ElectromasterProgramNodeCatalog.EnergyTargetType.ENTITY) {
                    throw new IllegalArgumentException(
                            "magnetic_block_target_moved_to_structure_nodes");
                }
                targetPosition = targets.requireLocalPosition(destination);
                requirePositionInRange(targetPosition, MAX_ACTION_RANGE);
                target = requireEntityTarget(targetReference);
                targetKey = entityKey(target);
                if (!MagnetManipulation.isMagnetic(target)) {
                    throw new IllegalArgumentException("Entity target is not magnetic");
                }
                requireEntityInRange(target, MAX_ACTION_RANGE);
                requireMovementAllowed(target);
                if (mode == ElectromasterProgramNodeCatalog.MagneticMode.LAUNCH
                        && !target.isAlive()) {
                    throw new IllegalArgumentException("Magnetic launch target is not controlled");
                }
                var origin = target.getBoundingBox().getCenter();
                if (origin.distanceTo(targetPosition) > magneticMoveRange(power)) {
                    throw new IllegalArgumentException("Magnetic move exceeds its power limit");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                var roster = controlled();
                wasControlled = roster.containsKey(targetKey);
                previousControl = CONTROL_DESTINATIONS.get(player.getUUID());
                charge(Skills.MAGNET_MANIPULATION.get(), magneticMoveCost(power));
                if (!EntityMotionGuard.canApplyMotionFrom(player, target)) {
                    throw new IllegalStateException("Target rejected magnetic movement");
                }
                var previous = new HashMap<Entity, Vec3>();
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
                    // Magnetic-entity launch keeps the legacy path-contact settlement; block
                    // structures settle real contacts through the structure node family instead.
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
                };
            }
        };
    }

    @Override
    public StructureOperation extractStructure(ProgramBlockPosition host, int budget,
                                               boolean forceMagnetize) {
        requireCasterReady(Skills.MAGNET_MANIPULATION.get());
        var seed = requireStructureHost(host);
        var result = MagneticStructureService.programExtract(player, seed, budget, forceMagnetize);
        if (result.status() != MagneticStructureService.ProgramStatus.COMPLETED) {
            return StructureOperation.result(operationResult(result));
        }
        var reference = result.reference();
        if (reference == null) return StructureOperation.rejected("committed_without_reference");
        charge(Skills.MAGNET_MANIPULATION.get(), STRUCTURE_EXTRACT_COST);
        // Forced magnetization is authorized and paid separately from the control token.
        if (forceMagnetize) {
            charge(Skills.MAGNET_MANIPULATION.get(), FORCED_MAGNETIZATION_COST);
        }
        return StructureOperation.structure(programReference(reference),
                ProgramStructureOperationResult.completed(result.detail(), reference));
    }

    @Override
    public StructureOperation controlStructure(ProgramStructureReference structure) {
        requireCasterReady(Skills.MAGNET_MANIPULATION.get());
        Objects.requireNonNull(structure, "structure");
        var result = MagneticStructureService.programControl(
                player, structure.reference(), structure.runtimeId());
        if (result.status() != MagneticStructureService.ProgramStatus.COMPLETED) {
            return StructureOperation.result(operationResult(result));
        }
        var session = MagneticStructureService.programSession(player).orElse(null);
        if (session == null || !session.reference().equals(structure.reference())) {
            return StructureOperation.rejected("control_session_unavailable");
        }
        var token = new ProgramStructureControl(structure.reference(), structure.runtimeId(),
                session.controllerId(), player.getUUID(), runtimeToken);
        issuedControls.put(token.controlId(), token);
        charge(Skills.MAGNET_MANIPULATION.get(), STRUCTURE_CONTROL_COST);
        return StructureOperation.control(token,
                ProgramStructureOperationResult.completed(result.detail(), structure.reference()));
    }

    @Override
    public StructureOperation moveStructure(ProgramStructureControl control,
                                            @Nullable ProgramWorldPosition target,
                                            @Nullable ProgramStructureAnchor anchor,
                                            double seconds) {
        requireCasterReady(Skills.MAGNET_MANIPULATION.get());
        var rejection = validateControl(control);
        if (rejection != null) return StructureOperation.rejected(rejection);
        var destination = resolveMoveTarget(control, target, anchor);
        if (destination == null) return StructureOperation.rejected("move_target_unavailable");
        if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > MAXIMUM_MOVE_SECONDS) {
            return StructureOperation.rejected("invalid_duration");
        }
        var result = MagneticStructureService.programMove(player, control.reference(),
                control.controlId(), destination, (long) Math.ceil(seconds * 20.0),
                anchor == null ? null : anchor.localPosition());
        if (result.status() == MagneticStructureService.ProgramStatus.COMPLETED) {
            charge(Skills.MAGNET_MANIPULATION.get(), STRUCTURE_MOVE_COST);
        }
        return StructureOperation.result(operationResult(result));
    }

    @Override
    public StructureOperation rotateStructure(ProgramStructureControl control,
                                              @Nullable ProgramStructurePose pose) {
        var rejection = validateControl(control);
        if (rejection != null) return StructureOperation.rejected(rejection);
        return StructureOperation.unavailable("finite_torque_control_unavailable");
    }

    @Override
    public StructureOperation launchStructure(ProgramStructureControl control,
                                              ProgramDirection direction,
                                              float power) {
        requireCasterReady(Skills.MAGNET_MANIPULATION.get());
        var rejection = validateControl(control);
        if (rejection != null) return StructureOperation.rejected(rejection);
        var normalized = normalized(direction);
        var speedScale = ProgramPowerScale.interpolate(ProgramPowerScale.require(power), 0.5, 1.0, 1.5);
        var result = MagneticStructureService.programLaunch(player, control.reference(),
                control.controlId(), normalized, speedScale);
        if (result.status() == MagneticStructureService.ProgramStatus.COMPLETED) {
            charge(Skills.MAGNET_MANIPULATION.get(), STRUCTURE_LAUNCH_COST);
        }
        return StructureOperation.result(operationResult(result));
    }

    @Override
    public StructureOperation releaseStructure(ProgramStructureControl control) {
        var rejection = validateControl(control);
        if (rejection != null) return StructureOperation.rejected(rejection);
        var result = MagneticStructureService.programRelease(player, control.reference(), control.controlId());
        return StructureOperation.result(operationResult(result));
    }

    @Override
    public StructureOperation staticizeStructure(ProgramStructureReference structure,
                                                 @Nullable ProgramBlockPosition target) {
        Objects.requireNonNull(structure, "structure");
        return StructureOperation.unavailable("safe_staticization_unavailable");
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
    public List<?> magneticEntitiesAround(ProgramWorldPosition center, double radius) {
        return targets.entitiesAround(center, Math.clamp(radius, 0.0, MAX_QUERY_RANGE)).stream()
                .filter(Entity.class::isInstance)
                .map(Entity.class::cast)
                .filter(MagnetManipulation::isMagnetic)
                .limit(MAX_QUERY_RESULTS)
                .toList();
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
                    requireEntityInRange(entity, MAX_ACTION_RANGE);
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
        if (!(value instanceof ProgramBlockPosition(var dimension, var x, var y, var z))
                || !dimension.equals(targets.level().dimension().identifier())) {
            throw new IllegalArgumentException("Block target is in another dimension");
        }
        var position = new BlockPos(x, y, z);
        if (!targets.level().hasChunkAt(position)
                || position.getY() < targets.level().getMinY()
                || position.getY() >= targets.level().getMaxY()
                || Vec3.atCenterOf(position).distanceToSqr(player.position())
                    > MAX_ACTION_RANGE * MAX_ACTION_RANGE) {
            throw new IllegalArgumentException("Block target is outside program range");
        }
        return position;
    }

    private BlockPos requireStructureHost(ProgramBlockPosition block) {
        if (block == null) {
            throw new IllegalArgumentException("Structure host block is missing");
        }
        var center = targets.requireLocalPosition(new ProgramWorldPosition(
                block.dimension(), block.x() + 0.5, block.y() + 0.5, block.z() + 0.5));
        if (center.distanceToSqr(player.position()) > MAX_ACTION_RANGE * MAX_ACTION_RANGE) {
            throw new IllegalArgumentException("Structure host block is outside program range");
        }
        var position = new BlockPos(block.x(), block.y(), block.z());
        if (!targets.level().hasChunkAt(position) || !targets.level().isInWorldBounds(position)) {
            throw new IllegalArgumentException("Structure host block is unavailable");
        }
        return position;
    }

    private ProgramStructureReference programReference(StructureRef reference) {
        var handle = StructureSpaces.resolve(targets.level().getServer(), reference).orElseThrow(() ->
                new IllegalStateException("Committed structure handle is unavailable"));
        return new ProgramStructureReference(reference, handle.runtimeId());
    }

    private static ProgramStructureOperationResult operationResult(
            MagneticStructureService.ProgramResult result) {
        return switch (result.status()) {
            case COMPLETED -> result.reference() == null
                    ? ProgramStructureOperationResult.completed(result.detail())
                    : ProgramStructureOperationResult.completed(result.detail(), result.reference());
            case PENDING -> ProgramStructureOperationResult.pending(result.detail());
            case REJECTED -> ProgramStructureOperationResult.rejected(result.detail());
            case UNAVAILABLE -> ProgramStructureOperationResult.unavailable(result.detail());
        };
    }

    private @Nullable String validateControl(ProgramStructureControl control) {
        if (control == null) return "missing_structure_control";
        if (!control.actorId().equals(player.getUUID())) return "control_actor_mismatch";
        if (!control.runtimeToken().equals(runtimeToken)) return "control_runtime_expired";
        var issued = issuedControls.get(control.controlId());
        if (issued == null || !issued.equals(control)) return "control_token_stale";
        return null;
    }

    private @Nullable Vec3 resolveMoveTarget(ProgramStructureControl control,
                                             @Nullable ProgramWorldPosition target,
                                             @Nullable ProgramStructureAnchor anchor) {
        Vec3 point = null;
        Vec3 anchorWorld = null;
        if (anchor != null) {
            if (!anchor.structure().reference().equals(control.reference())
                    || !anchor.structure().runtimeId().equals(control.structureRuntimeId())) {
                return null;
            }
            var state = StructureDynamics.motion(targets.level(), control.reference()).orElse(null);
            if (state == null) return null;
            anchorWorld = state.pose().localToWorldPoint(Vec3.atCenterOf(anchor.localPosition()));
            point = anchorWorld;
        }
        if (target != null) {
            point = targets.requireLocalPosition(target);
        }
        if (point == null) return null;
        if (anchorWorld != null && target != null) {
            var state = StructureDynamics.motion(targets.level(), control.reference()).orElse(null);
            if (state == null) return null;
            var center = state.pose().localToWorldPoint(state.localCenterOfMass());
            point = point.add(center.subtract(anchorWorld));
        }
        if (point.distanceToSqr(player.position()) > MAX_ACTION_RANGE * MAX_ACTION_RANGE) return null;
        return point;
    }

    private Map<String, Entity> controlled() {
        return CONTROLLED.computeIfAbsent(player.getUUID(), _ -> new HashMap<>());
    }

    private static String entityKey(Entity entity) {
        return "entity:" + entity.getUUID();
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
        var trajectory = destination.subtract(origin);
        var trajectoryLength = trajectory.length();
        var direction = trajectoryLength > 1.0E-12
                ? trajectory.scale(1.0 / trajectoryLength)
                : new Vec3(0.0, 1.0, 0.0);
        var source = SkillDamageSource.of(player, Skills.MAGNET_MANIPULATION.get());
        for (var living : ViewTargetScanner.scan(
                targets.level(),
                LivingEntity.class,
                origin,
                direction,
                trajectoryLength,
                ViewTargetScanner.centeredCylinder(1.0),
                value -> value != player
                        && value != launched
                        && value.isAlive()
                        && !CtaFriendlyFireWhitelist.shouldProtect(player, value)
        )) {
            living.hurtServer(targets.level(), source, damage);
        }
    }

    public static void releaseControlled(ServerPlayer player) {
        var controlled = CONTROLLED.remove(player.getUUID());
        CONTROL_DESTINATIONS.remove(player.getUUID());
        if (controlled != null) {
            for (var entity : controlled.values()) {
                if (entity instanceof FallingBlockEntity && entity.isAlive()) entity.setNoGravity(false);
            }
        }
        // Program structure sessions release their own field and magnetization overlay while
        // retaining the captured structure, its region and its inertia.
        MagneticStructureService.releasePrograms(player);
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
        MagneticStructureService.tickPrograms(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MagneticStructureService.clearProgramsAll();
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
            var velocity = org.academy.api.common.ability.electromaster.MagneticMovement.calculatePullVelocity(
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

    private static Vec3 normalized(ProgramDirection direction) {
        Objects.requireNonNull(direction, "direction");
        var value = new Vec3(direction.x(), direction.y(), direction.z());
        if (!finiteNonZero(value)) {
            throw new IllegalArgumentException("Direction is invalid");
        }
        return value.normalize();
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
