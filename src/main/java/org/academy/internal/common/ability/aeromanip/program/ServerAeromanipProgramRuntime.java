package org.academy.internal.common.ability.aeromanip.program;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.charge.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.targeting.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.vfx.AeromanipVfx;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.ability.aeromanip.skills.lv3.LaminarCutter;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.StructureProgramActions;
import org.academy.internal.common.ability.program.StructureStaticizationRunner;
import org.academy.internal.common.ability.program.registry.AbilityProgramSpatialRanges;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.sublevel.SubLevel;
import org.academy.internal.common.sublevel.SubLevelActivationRegistry;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelSkillSupport;
import org.academy.internal.common.sublevel.SubLevelStructureHandle;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative Minecraft-server adapter for Aeromanip programs.
 */
public final class ServerAeromanipProgramRuntime implements AeromanipProgramRuntime, ForwardingProgramTargetResolver {
    public static final double MAX_QUERY_RANGE = AbilityProgramSpatialRanges.forCategory(
            AeromanipProgramNodeCatalog.AEROMANIP).queryRange();
    public static final double MAX_ACTION_RANGE = AbilityProgramSpatialRanges.forCategory(
            AeromanipProgramNodeCatalog.AEROMANIP).actionRange();
    public static final int MAX_QUERY_RESULTS = 128;
    private static final float STRUCTURE_EXTRACT_COST = 30.0f;
    private static final float STRUCTURE_CONTROL_COST = 10.0f;
    private static final float STRUCTURE_MOVE_COST = 5.0f;
    private static final float STRUCTURE_LAUNCH_COST = 15.0f;
    private static final double MAXIMUM_MOVE_SECONDS = 10.0;
    private static final double MAXIMUM_LAUNCH_SECONDS = 60.0;
    private static final boolean SUB_LEVEL_NODES = Boolean.getBoolean("academy.sublevelProgramNodes");

    private final ServerPlayer player;
    private final float costMultiplier;
    private final ServerProgramTargetResolver targets;
    private final UUID runtimeToken = UUID.randomUUID();
    private @Nullable StructureRef pendingExtraction;
    private long extractionDeadline;
    private final Map<UUID, ProgramStructureControl> issuedControls = new HashMap<>();
    @Override
    public ProgramTargetResolver targetResolver() {
        return targets;
    }

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
                        bladePlane,
                        ProgramPowerScale.costMultiplier(power)
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
    public int ownedJetNozzleCount(
            ProgramWorldPosition center,
            double radius,
            @Nullable ProgramDirection direction
    ) {
        requireCasterReady(Skills.HIGH_SPEED_JET.get());
        var resolvedCenter = targets.requireLocalPosition(center);
        if (resolvedCenter.distanceToSqr(player.position())
                > MAX_QUERY_RANGE * MAX_QUERY_RANGE) {
            throw new IllegalArgumentException("Jet nozzle query center is outside program range");
        }
        if (!Double.isFinite(radius) || radius < 1.0 || radius > 32.0) {
            throw new IllegalArgumentException("Jet nozzle query radius must be between 1 and 32");
        }
        return HighSpeedJetStructureService.countOwnedNozzles(
                player,
                resolvedCenter,
                radius,
                direction == null ? null : normalizedVector(direction)
        );
    }

    @Override
    public ProgramActionTransaction.ProgramAction launchBlockStructure(
            ProgramBlockPosition seedReference,
            ProgramDirection direction,
            AeromanipProgramNodeCatalog.BlockStructureLaunchConfiguration configuration
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private BlockPos seed;
            private Vec3 launchDirection;

            @Override
            public void validate() {
                requireCasterReady(Skills.HIGH_SPEED_JET.get());
                seed = requireLaunchSeed(seedReference);
                launchDirection = normalizedVector(direction);
                Objects.requireNonNull(configuration, "configuration");
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                requireCasterReady(Skills.HIGH_SPEED_JET.get());
                seed = requireLaunchSeed(seedReference);
                launchDirection = normalizedVector(direction);
                var launched = HighSpeedJetStructureService.launch(
                        player,
                        new HighSpeedJetStructureService.LaunchRequest(
                                seed,
                                null,
                                launchDirection,
                                configuration.structureRadius(),
                                configuration.power(),
                                configuration.duration() * 20,
                                configuration.restoreWhenSettled()
                        ),
                        costMultiplier
                ).orElseThrow(() -> new IllegalStateException(
                        "Block structure launch was rejected"));
                return launched::close;
            }
        };
    }

    @Override
    public StructureOperation extractStructure(ProgramBlockPosition host, int radius, int budget) {
        requireCasterReady(Skills.HIGH_SPEED_JET.get());
        if (SUB_LEVEL_NODES) return extractSubLevelStructure(host, radius, budget);
        if (pendingExtraction == null) {
            var seed = requireLaunchSeed(host);
            var result = StructureProgramActions.execute(player, Skills.HIGH_SPEED_JET.get(),
                STRUCTURE_EXTRACT_COST * costMultiplier,
                () -> HighSpeedJetStructureService.programExtract(player, seed, radius, budget),
                value -> value.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED
                            || value.status() == HighSpeedJetStructureService.ProgramStatus.PENDING);
            if (result.status() != HighSpeedJetStructureService.ProgramStatus.COMPLETED
                    && result.status() != HighSpeedJetStructureService.ProgramStatus.PENDING) {
                return StructureOperation.result(operationResult(result));
            }
            pendingExtraction = HighSpeedJetStructureService.diagnostic(player)
                    .map(state -> state.reference()).orElse(null);
            extractionDeadline = player.level().getGameTime() + 400;
            if (pendingExtraction == null) return StructureOperation.rejected("capture_session_unavailable");
        }
        var reference = pendingExtraction;
        var state = HighSpeedJetStructureService.diagnostic(player).orElse(null);
        if (state == null || !state.reference().equals(reference)
                || player.level().getGameTime() > extractionDeadline) {
            pendingExtraction = null;
            return StructureOperation.rejected("capture_session_ended");
        }
        if (state.stage() == HighSpeedJetStructureService.Stage.CAPTURING
                || org.academy.api.common.structure.StructureDynamics.motion(
                        (ServerLevel) player.level(), reference).isEmpty()) {
            return StructureOperation.result(ProgramStructureOperationResult.pending("capture_pending"));
        }
        pendingExtraction = null;
        return StructureOperation.structure(programReference(reference),
                ProgramStructureOperationResult.completed("committed", reference));
    }

    @Override
    public StructureOperation controlStructure(ProgramStructureReference structure) {
        requireCasterReady(Skills.HIGH_SPEED_JET.get());
        Objects.requireNonNull(structure, "structure");
        if (SubLevelStructureRefs.isSubLevel(structure.reference())) return controlSubLevelStructure(structure);
        var result = StructureProgramActions.execute(player, Skills.HIGH_SPEED_JET.get(),
                STRUCTURE_CONTROL_COST * costMultiplier,
                () -> HighSpeedJetStructureService.programControl(
                player, structure.reference(), structure.runtimeId()),
                value -> value.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED);
        if (result.status() != HighSpeedJetStructureService.ProgramStatus.COMPLETED) {
            return StructureOperation.result(operationResult(result));
        }
        var session = HighSpeedJetStructureService.programSession(player).orElse(null);
        if (session == null || !session.reference().equals(structure.reference())) {
            return StructureOperation.rejected("control_session_unavailable");
        }
        var token = new ProgramStructureControl(structure.reference(), structure.runtimeId(),
                session.controllerId(), player.getUUID(), runtimeToken);
        issuedControls.put(token.controlId(), token);
        return StructureOperation.control(token,
                ProgramStructureOperationResult.completed(result.detail(), structure.reference()));
    }

    @Override
    public StructureOperation moveStructure(ProgramStructureControl control,
                                            @Nullable ProgramWorldPosition target,
                                            @Nullable ProgramStructureAnchor anchor,
                                            double seconds) {
        requireCasterReady(Skills.HIGH_SPEED_JET.get());
        var rejection = validateControl(control);
        if (rejection != null) return StructureOperation.rejected(rejection);
        if (SubLevelStructureRefs.isSubLevel(control.reference())) {
            return moveSubLevelStructure(control, target, anchor, seconds);
        }
        if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > MAXIMUM_MOVE_SECONDS) {
            return StructureOperation.rejected("invalid_duration");
        }
        if (anchor != null && resolveAnchorWorld(control, anchor) == null) {
            return StructureOperation.rejected("anchor_unavailable");
        }
        Vec3 destination = null;
        if (target != null) {
            destination = targets.requireLocalPosition(target);
            if (destination.distanceToSqr(player.position()) > MAX_ACTION_RANGE * MAX_ACTION_RANGE) {
                return StructureOperation.rejected("target_out_of_range");
            }
        }
        var moveTarget = destination;
        var result = StructureProgramActions.execute(player, Skills.HIGH_SPEED_JET.get(),
                STRUCTURE_MOVE_COST * costMultiplier,
                () -> HighSpeedJetStructureService.programMove(player, control.reference(),
                control.controlId(), moveTarget,
                anchor == null ? null : anchor.localPosition(),
                (long) Math.ceil(seconds * 20.0)),
                value -> value.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED);
        return StructureOperation.result(operationResult(result));
    }

    @Override
    public StructureOperation rotateStructure(ProgramStructureControl control,
                                              @Nullable ProgramStructurePose pose) {
        var rejection = validateControl(control);
        if (rejection != null) return StructureOperation.rejected(rejection);
        if (SubLevelStructureRefs.isSubLevel(control.reference())) {
            return rotateSubLevelStructure(control, pose);
        }
        return StructureOperation.unavailable("finite_torque_control_unavailable");
    }

    @Override
    public StructureOperation launchStructure(ProgramStructureControl control,
                                              ProgramDirection direction,
                                              float power,
                                              double seconds) {
        requireCasterReady(Skills.HIGH_SPEED_JET.get());
        var rejection = validateControl(control);
        if (rejection != null) return StructureOperation.rejected(rejection);
        if (SubLevelStructureRefs.isSubLevel(control.reference())) {
            return launchSubLevelStructure(control, direction, power);
        }
        if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > MAXIMUM_LAUNCH_SECONDS) {
            return StructureOperation.rejected("invalid_duration");
        }
        var normalized = normalizedVector(direction);
        var result = StructureProgramActions.execute(player, Skills.HIGH_SPEED_JET.get(),
                STRUCTURE_LAUNCH_COST * costMultiplier,
                () -> HighSpeedJetStructureService.programLaunch(player, control.reference(),
                control.controlId(), normalized, power, (long) Math.ceil(seconds * 20.0)),
                value -> value.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED);
        return StructureOperation.result(operationResult(result));
    }

    @Override
    public StructureOperation releaseStructure(ProgramStructureControl control) {
        var rejection = validateControl(control);
        if (rejection != null) return StructureOperation.rejected(rejection);
        if (SubLevelStructureRefs.isSubLevel(control.reference())) {
            issuedControls.remove(control.controlId());
            return StructureOperation.result(ProgramStructureOperationResult.completed("released",
                    control.reference()));
        }
        var result = HighSpeedJetStructureService.programRelease(
                player, control.reference(), control.controlId());
        return StructureOperation.result(operationResult(result));
    }

    @Override
    public StructureOperation staticizeStructure(ProgramStructureReference structure,
                                                 @Nullable ProgramBlockPosition target) {
        Objects.requireNonNull(structure, "structure");
        if (SubLevelStructureRefs.isSubLevel(structure.reference())) {
            var id = SubLevelStructureRefs.id(structure.reference()).orElse(null);
            if (id != null) org.academy.internal.common.sublevel.SubLevelMotionController.cancel(id);
            var targetPosition = target == null ? null : new BlockPos(target.x(), target.y(), target.z());
            var restored = id == null ? new org.academy.internal.common.sublevel.SubLevelRestoreService.Result(0, "invalid")
                    : SubLevelSkillSupport.staticize(targets.level().getServer(), id, targetPosition);
            return restored.succeeded()
                    ? StructureOperation.result(ProgramStructureOperationResult.completed(restored.detail(),
                    structure.reference()))
                    : StructureOperation.rejected(restored.detail());
        }
        var cells = HighSpeedJetStructureService.programSession(player)
                .filter(session -> session.reference().equals(structure.reference()))
                .map(HighSpeedJetStructureService.Session::capturedCells)
                .orElse(List.of());
        return StructureOperation.result(StructureStaticizationRunner.staticize(
                player, structure, target, cells));
    }

    private StructureOperation extractSubLevelStructure(ProgramBlockPosition hostReference, int radius, int budget) {
        var seed = requireLaunchSeed(hostReference);
        var level = targets.level();
        int extent = Math.max(1, Math.min(radius, 8));
        int limit = Math.max(1, Math.min(budget <= 0 ? 256 : budget, 256));
        var positions = new ArrayList<BlockPos>();
        for (var cursor : BlockPos.betweenClosed(seed.offset(-extent, -extent, -extent),
                seed.offset(extent, extent, extent))) {
            var position = cursor.immutable();
            if (!level.hasChunkAt(position) || !level.isInWorldBounds(position)) continue;
            if (level.getBlockState(position).isAir() || !level.mayInteract(player, position)) continue;
            positions.add(position);
            if (positions.size() >= limit) break;
        }
        if (positions.isEmpty()) return StructureOperation.rejected("capture_empty");
        var id = SubLevelSkillSupport.capture(level, positions, (world, position) ->
                world.mayInteract(player, position)).orElse(null);
        if (id == null) return StructureOperation.rejected("capture_rejected");
        var runtime = SubLevelServer.runtime(level.getServer()).orElse(null);
        var subLevel = runtime == null ? null : runtime.get(id).orElse(null);
        if (runtime == null || subLevel == null) return StructureOperation.rejected("sublevel_unavailable");
        var handle = new SubLevelStructureHandle(subLevel, runtime.storage(), level.dimension().identifier());
        var reference = handle.reference();
        return StructureOperation.structure(new ProgramStructureReference(reference, handle.runtimeId()),
                ProgramStructureOperationResult.completed("committed", reference));
    }

    private StructureOperation controlSubLevelStructure(ProgramStructureReference structure) {
        var id = SubLevelStructureRefs.id(structure.reference()).orElse(null);
        var activation = id == null ? null : SubLevelActivationRegistry.find(id).orElse(null);
        if (id == null || activation == null || activation.generation() != structure.reference().generation()
                || !activation.runtimeId().equals(structure.runtimeId())) {
            return StructureOperation.rejected("structure_epoch_stale");
        }
        var runtime = SubLevelServer.runtime(targets.level().getServer()).orElse(null);
        var subLevel = runtime == null ? null : runtime.get(id).orElse(null);
        if (subLevel == null || subLevel.state() != org.academy.internal.common.sublevel.SubLevelState.ACTIVE
                && subLevel.state() != org.academy.internal.common.sublevel.SubLevelState.SLEEPING) {
            return StructureOperation.rejected("structure_unavailable");
        }
        var control = new ProgramStructureControl(structure.reference(), structure.runtimeId(), UUID.randomUUID(),
                player.getUUID(), runtimeToken);
        issuedControls.put(control.controlId(), control);
        return StructureOperation.control(control,
                ProgramStructureOperationResult.completed("controlled", structure.reference()));
    }

    private StructureOperation launchSubLevelStructure(ProgramStructureControl control,
                                                       ProgramDirection direction, float power) {
        var id = SubLevelStructureRefs.id(control.reference()).orElse(null);
        if (id != null) org.academy.internal.common.sublevel.SubLevelMotionController.cancel(id);
        if (id == null || !SubLevelSkillSupport.launch(targets.level().getServer(), id,
                normalizedVector(direction), Math.max(0.5, power))) {
            return StructureOperation.rejected("sublevel_launch_failed");
        }
        return StructureOperation.result(ProgramStructureOperationResult.completed("launched",
                control.reference()));
    }

    private StructureOperation moveSubLevelStructure(ProgramStructureControl control,
                                                      @Nullable ProgramWorldPosition target,
                                                      @Nullable ProgramStructureAnchor anchor,
                                                      double seconds) {
        if (target == null) return StructureOperation.rejected("sublevel_move_target_required");
        if (anchor != null) return StructureOperation.unavailable("sublevel_anchor_pending");
        if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > MAXIMUM_MOVE_SECONDS) {
            return StructureOperation.rejected("invalid_duration");
        }
        var destination = targets.requireLocalPosition(target);
        if (destination.distanceToSqr(player.position()) > MAX_ACTION_RANGE * MAX_ACTION_RANGE) {
            return StructureOperation.rejected("target_out_of_range");
        }
        var id = SubLevelStructureRefs.id(control.reference()).orElse(null);
        if (id == null || !SubLevelSkillSupport.moveTo(targets.level().getServer(), id, destination, seconds)) {
            return StructureOperation.rejected("sublevel_move_failed");
        }
        return StructureOperation.result(ProgramStructureOperationResult.completed("moving", control.reference()));
    }

    private StructureOperation rotateSubLevelStructure(ProgramStructureControl control,
                                                       @Nullable ProgramStructurePose pose) {
        if (pose == null) return StructureOperation.rejected("sublevel_pose_required");
        var axis = normalizedVector(pose.axis());
        if (Math.abs(axis.y) < 0.9) {
            return StructureOperation.unavailable("sublevel_only_yaw_rotation_supported");
        }
        var id = SubLevelStructureRefs.id(control.reference()).orElse(null);
        if (id != null) org.academy.internal.common.sublevel.SubLevelMotionController.cancel(id);
        if (id == null || !SubLevelSkillSupport.rotate(targets.level().getServer(), id,
                Math.toDegrees(pose.angleRadians()))) {
            return StructureOperation.rejected("sublevel_rotate_failed");
        }
        return StructureOperation.result(ProgramStructureOperationResult.completed("rotated", control.reference()));
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
        var adjusted = cost * costMultiplier;
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

    private BlockPos requireLaunchSeed(ProgramBlockPosition block) {
        if (block == null) {
            throw new IllegalArgumentException("Block structure seed is missing");
        }
        var center = targets.requireLocalPosition(new ProgramWorldPosition(
                block.dimension(), block.x() + 0.5, block.y() + 0.5, block.z() + 0.5));
        if (center.distanceToSqr(player.position()) > MAX_ACTION_RANGE * MAX_ACTION_RANGE) {
            throw new IllegalArgumentException("Block structure seed is outside program range");
        }
        var position = new BlockPos(block.x(), block.y(), block.z());
        if (!targets.level().hasChunkAt(position)
                || !targets.level().isInWorldBounds(position)) {
            throw new IllegalArgumentException("Block structure seed is unavailable");
        }
        return position;
    }

    private static int requireJetDuration(int durationSeconds) {
        if (durationSeconds < 1 || durationSeconds > 60) {
            throw new IllegalArgumentException("Jet duration must be between 1 and 60 seconds");
        }
        return durationSeconds * 20;
    }

    private ProgramStructureReference programReference(StructureRef reference) {
        var handle = StructureSpaces.resolve(targets.level().getServer(), reference).orElseThrow(() ->
                new IllegalStateException("Committed structure handle is unavailable"));
        return new ProgramStructureReference(reference, handle.runtimeId());
    }

    private static ProgramStructureOperationResult operationResult(
            HighSpeedJetStructureService.ProgramResult result) {
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

    /** World point of an explicit local anchor; an unloaded or replaced epoch is rejected. */
    private @Nullable Vec3 resolveAnchorWorld(ProgramStructureControl control,
                                              ProgramStructureAnchor anchor) {
        if (!anchor.structure().reference().equals(control.reference())
                || !anchor.structure().runtimeId().equals(control.structureRuntimeId())) {
            return null;
        }
        var state = StructureDynamics.motion(targets.level(), control.reference()).orElse(null);
        if (state == null) return null;
        return state.pose().localToWorldPoint(Vec3.atCenterOf(anchor.localPosition()));
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
