package org.academy.internal.common.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramStructureAnchor;
import org.academy.api.common.ability.program.ProgramStructureControl;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.ability.program.ProgramStructurePose;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.common.ability.aeromanip.JetStructureControl;
import org.academy.api.common.ability.accelerator.KineticStructureControl;
import org.academy.api.common.ability.electromaster.MagneticStructureControl;
import org.academy.api.common.ability.electromaster.MagneticFieldTuning;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.api.common.ability.electromaster.MagneticTargets;
import org.academy.internal.common.sublevel.SubLevelActivationRegistry;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelSkillSupport;
import org.academy.internal.common.sublevel.SubLevelState;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared program semantics over the same exclusive lease used by direct structure skills. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SubLevelProgramControl {
    private static final long LEASE_TICKS = 1_200;
    private static final Map<UUID, LeaseDeadline> DEADLINES = new HashMap<>();
    private static final Map<StructureRef, JetMove> JET_MOVES = new HashMap<>();
    private static final Map<StructureRef, ForceMove> FORCE_MOVES = new HashMap<>();

    private final ServerPlayer player;
    private final UUID runtimeToken;
    private final double maximumRange;
    private final Map<UUID, ProgramStructureControl> controls = new HashMap<>();
    private final Map<StructureRef, Integer> kineticTiers = new HashMap<>();
    private final Map<StructureRef, Long> magnetization = new HashMap<>();

    public SubLevelProgramControl(ServerPlayer player, UUID runtimeToken, double maximumRange) {
        this.player = player;
        this.runtimeToken = runtimeToken;
        this.maximumRange = maximumRange;
    }

    public Acquisition acquire(ProgramStructureReference structure) {
        var rejection = validateReference(structure);
        if (rejection != null) return new Acquisition(null, rejected(rejection));
        var control = new ProgramStructureControl(structure.reference(), structure.runtimeId(),
                UUID.randomUUID(), player.getUUID(), runtimeToken);
        var status = StructureDirectControl.acquire(player.level(), control.reference(), player, control.controlId());
        if (status != StructureDirectControl.Status.ACTIVE) {
            return new Acquisition(null, rejected(status == StructureDirectControl.Status.OCCUPIED
                    ? "structure_controlled" : "structure_unavailable"));
        }
        controls.put(control.controlId(), control);
        renew(control);
        return new Acquisition(control, completed("controlled", control));
    }

    /** A runtime token alone is insufficient: every operation rechecks the body and standing lease. */
    public @Nullable String validate(ProgramStructureControl control) {
        if (!control.equals(controls.get(control.controlId()))) return "control_token_stale";
        var rejection = validateReference(new ProgramStructureReference(control.reference(), control.structureRuntimeId()));
        if (rejection != null) return rejection;
        if (StructureDirectControl.validate(player.level(), control.reference(), player, control.controlId())
                != StructureDirectControl.Status.ACTIVE) return "control_lease_lost";
        var deadline = DEADLINES.get(control.controlId());
        if (deadline == null || player.level().getGameTime() >= deadline.expiresAt()) {
            release(control);
            return "control_expired";
        }
        renew(control);
        return null;
    }

    public ProgramStructureOperationResult release(ProgramStructureControl control) {
        withdrawLease(control);
        stopJet(control);
        var forceMove = FORCE_MOVES.get(control.reference());
        if (forceMove != null && forceMove.control().equals(control)) FORCE_MOVES.remove(control.reference());
        return completed("released", control);
    }

    private void withdrawLease(ProgramStructureControl control) {
        // StructureDirectControl checks the exact token before cancelling motion. An old token
        // must never cancel a newer controller, even when the actor is unchanged.
        boolean heldLease = StructureDirectControl.validate(player.level(), control.reference(), player,
                control.controlId()) == StructureDirectControl.Status.ACTIVE;
        StructureDirectControl.release(control.reference(), control.controlId());
        controls.remove(control.controlId());
        DEADLINES.remove(control.controlId());
        if (heldLease) magnetization.remove(control.reference());
    }

    public ProgramStructureOperationResult move(ProgramStructureControl control,
                                                @Nullable ProgramWorldPosition target,
                                                @Nullable ProgramStructureAnchor anchor,
                                                double seconds) {
        return move(control, target, anchor, seconds, false);
    }

    public ProgramStructureOperationResult moveMagnetic(ProgramStructureControl control,
                                                        @Nullable ProgramWorldPosition target,
                                                        @Nullable ProgramStructureAnchor anchor,
                                                        double seconds) {
        return move(control, target, anchor, seconds, true);
    }

    private ProgramStructureOperationResult move(ProgramStructureControl control,
                                                 @Nullable ProgramWorldPosition target,
                                                 @Nullable ProgramStructureAnchor anchor,
                                                 double seconds, boolean magnetic) {
        if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > 10.0) return rejected("invalid_duration");
        if (target == null && anchor == null) return rejected("move_target_unavailable");
        var rejection = validate(control);
        if (rejection != null) return rejected(rejection);
        if (anchor != null && !matchesAnchor(control, anchor)) return rejected("anchor_unavailable");
        var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
        var id = SubLevelStructureRefs.id(control.reference()).orElseThrow();
        var subLevel = runtime.get(id).orElseThrow();
        var motion = runtime.universe().motion(id).orElse(null);
        if (motion == null) return rejected("motion_unavailable");
        var center = motion.localCenterOfMass();
        var localPoint = anchor == null ? new Vec3(center.x(), center.y(), center.z())
                : Vec3.atCenterOf(anchor.localPosition());
        if (anchor != null && (!subLevel.localBounds().contains(localPoint)
                || runtime.storage().blockState(subLevel.plot(), anchor.localPosition()).isAir())) {
            return rejected("anchor_unavailable");
        }
        if (target != null && !target.dimension().equals(player.level().dimension().identifier())) {
            return rejected("target_dimension_mismatch");
        }
        var destination = target == null ? subLevel.pose().localToWorldPoint(localPoint)
                : new Vec3(target.x(), target.y(), target.z());
        var block = BlockPos.containing(destination);
        if (!player.level().hasChunkAt(block) || !player.level().isInWorldBounds(block)
                || !player.level().getWorldBorder().isWithinBounds(block)) return rejected("target_unavailable");
        if (destination.distanceToSqr(player.position()) > maximumRange * maximumRange) {
            return rejected("target_out_of_range");
        }
        var nativeParticipation = magnetic ? magneticParticipation(control.reference()) : 1.0;
        var magnetizeUntil = magnetization.getOrDefault(control.reference(), 0L);
        if (magnetic && nativeParticipation <= 0.0 && player.level().getGameTime() >= magnetizeUntil) {
            return rejected("no_magnetic_participation");
        }
        FORCE_MOVES.put(control.reference(), new ForceMove(this, control, localPoint, destination,
                kineticTiers.getOrDefault(control.reference(), 0), magnetic, nativeParticipation,
                magnetizeUntil, player.level().getGameTime() + (long) Math.ceil(seconds * 20.0)));
        return completed("moving", control);
    }

    public void recordKineticCapture(StructureRef reference, int tier) {
        kineticTiers.put(reference, tier);
    }

    public void recordMagneticCapture(StructureRef reference, boolean forceMagnetize) {
        if (forceMagnetize) magnetization.put(reference,
                player.level().getGameTime() + MagneticStructureService.MAGNETIZATION_TICKS);
    }

    public boolean hasMagneticParticipation(ProgramStructureReference structure) {
        return player.level().getGameTime() < magnetization.getOrDefault(structure.reference(), 0L)
                || magneticParticipation(structure.reference()) > 0.0;
    }

    private double magneticParticipation(StructureRef reference) {
        var runtime = SubLevelServer.runtime(player.level()).orElse(null);
        var id = SubLevelStructureRefs.id(reference).orElse(null);
        var subLevel = runtime == null || id == null ? null : runtime.get(id).orElse(null);
        if (subLevel == null) return 0.0;
        var bounds = subLevel.localBounds();
        int total = 0;
        int magnetic = 0;
        for (var position : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(Math.ceil(bounds.maxX) - 1, Math.ceil(bounds.maxY) - 1, Math.ceil(bounds.maxZ) - 1))) {
            var state = runtime.storage().blockState(subLevel.plot(), position);
            if (state.isAir()) continue;
            total++;
            if (MagneticTargets.isMagnetic(state)) magnetic++;
        }
        return total == 0 ? 0.0 : MagneticStructureControl.magneticParticipation(magnetic, total);
    }

    /** Jet move preserves the node's nozzle-group thrust semantics, including an omitted target. */
    public ProgramStructureOperationResult moveJet(ProgramStructureControl control,
                                                   @Nullable ProgramWorldPosition target,
                                                   @Nullable ProgramStructureAnchor anchor,
                                                   double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > 10.0) return rejected("invalid_duration");
        var rejection = validate(control);
        if (rejection != null) return rejected(rejection);
        if (anchor != null && !matchesAnchor(control, anchor)) return rejected("anchor_unavailable");
        if (target != null && !target.dimension().equals(player.level().dimension().identifier())) {
            return rejected("target_dimension_mismatch");
        }
        var destination = target == null ? null : new Vec3(target.x(), target.y(), target.z());
        if (destination != null && destination.distanceToSqr(player.position()) > maximumRange * maximumRange) {
            return rejected("target_out_of_range");
        }
        var nozzles = jetNozzles(control.reference()).stream().filter(nozzle -> anchor == null
                || nozzle.structureMount().orElseThrow().offset().equals(anchor.localPosition())).toList();
        if (nozzles.isEmpty()) return rejected(anchor == null ? "support_unresolved" : "anchor_unresolved");
        var previous = JET_MOVES.remove(control.reference());
        if (previous != null) previous.restoreNozzles();
        var ticks = (int) Math.ceil(seconds * 20.0);
        var activeTicks = new HashMap<HighSpeedJetNozzle, Integer>();
        for (var nozzle : nozzles) {
            activeTicks.put(nozzle, nozzle.activeTicks());
            nozzle.activate(ticks);
        }
        JET_MOVES.put(control.reference(), new JetMove(this, control, destination, activeTicks,
                player.level().getGameTime() + ticks));
        return completed("moving", control);
    }

    public boolean hasJetNozzles(ProgramStructureReference structure) {
        return !jetNozzles(structure.reference()).isEmpty();
    }

    private List<HighSpeedJetNozzle> jetNozzles(StructureRef reference) {
        return HighSpeedJet.Server.ownedNozzles(player.level(), player).stream()
                .filter(nozzle -> nozzle.structureMount().filter(mount -> mount.reference().equals(reference)).isPresent())
                .toList();
    }

    public ProgramStructureOperationResult rotate(ProgramStructureControl control, @Nullable ProgramStructurePose pose) {
        if (pose == null) return rejected("sublevel_pose_required");
        var rejection = validate(control);
        if (rejection != null) return rejected(rejection);
        var id = SubLevelStructureRefs.id(control.reference()).orElseThrow();
        var target = requestedRotation(pose);
        return SubLevelSkillSupport.rotateTo(player.level().getServer(), id, target)
                ? completed("rotated", control) : rejected("sublevel_rotate_failed");
    }

    /** Converts the node's absolute axis/angle request, including negative and non-yaw axes. */
    static StructurePose requestedRotation(ProgramStructurePose pose) {
        var axis = pose.axis();
        var value = new Vec3(axis.x(), axis.y(), axis.z());
        if (!Double.isFinite(value.lengthSqr()) || value.lengthSqr() <= 1.0e-12) {
            throw new IllegalArgumentException("Structure rotation axis is invalid");
        }
        var normalized = value.normalize();
        var halfAngle = Math.IEEEremainder(pose.angleRadians(), Math.PI * 2.0) * 0.5;
        var sine = Math.sin(halfAngle);
        return new StructurePose(Vec3.ZERO, normalized.x * sine, normalized.y * sine,
                normalized.z * sine, Math.cos(halfAngle));
    }

    static boolean matchesAnchor(ProgramStructureControl control, ProgramStructureAnchor anchor) {
        return anchor.structure().reference().equals(control.reference())
                && anchor.structure().runtimeId().equals(control.structureRuntimeId());
    }

    public ProgramStructureOperationResult launch(ProgramStructureControl control, Vec3 direction, double speed) {
        var rejection = validate(control);
        if (rejection != null) return rejected(rejection);
        var id = SubLevelStructureRefs.id(control.reference()).orElseThrow();
        if (!SubLevelSkillSupport.launch(player.level().getServer(), id, direction, speed)) {
            return rejected("sublevel_launch_failed");
        }
        // Launch hands the body to free flight; retain the issued token only for idempotent release.
        release(control);
        return completed("launched", control);
    }

    public ProgramStructureOperationResult staticize(ProgramStructureReference structure,
                                                     @Nullable ProgramBlockPosition target) {
        var rejection = validateReference(structure);
        if (rejection != null) return rejected(rejection);
        if (target != null && !target.dimension().equals(player.level().dimension().identifier())) {
            return rejected("target_dimension_mismatch");
        }
        if (StructureDirectControl.occupied(structure.reference(), structure.runtimeId())
                && controls.values().stream().noneMatch(control -> control.reference().equals(structure.reference())
                && StructureDirectControl.validate(player.level(), control.reference(), player, control.controlId())
                == StructureDirectControl.Status.ACTIVE)) return rejected("structure_controlled");
        var id = SubLevelStructureRefs.id(structure.reference()).orElseThrow();
        var position = target == null ? null : new BlockPos(target.x(), target.y(), target.z());
        var restored = SubLevelSkillSupport.staticize(player.level().getServer(), id, position, player, maximumRange);
        if (!restored.succeeded()) return rejected(restored.detail());
        for (var control : controls.values().stream()
                .filter(value -> value.reference().equals(structure.reference())).toList()) release(control);
        return ProgramStructureOperationResult.completed(restored.detail(), structure.reference());
    }

    private @Nullable String validateReference(ProgramStructureReference structure) {
        if (!player.isAlive() || player.hasDisconnected() || player.isSpectator()) return "actor_unavailable";
        if (!structure.reference().dimension().equals(player.level().dimension().identifier())) {
            return "structure_dimension_mismatch";
        }
        var id = SubLevelStructureRefs.id(structure.reference()).orElse(null);
        if (id == null || !SubLevelActivationRegistry.matches(id, structure.runtimeId(),
                structure.reference().generation())) return "structure_epoch_stale";
        var runtime = SubLevelServer.runtime(player.level()).orElse(null);
        if (runtime == null || runtime.host() != player.level()) return "structure_dimension_mismatch";
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null || subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING) {
            return "structure_unavailable";
        }
        if (subLevel.worldBounds().distanceToSqr(player.position()) > maximumRange * maximumRange) {
            return "structure_out_of_range";
        }
        return null;
    }

    private void renew(ProgramStructureControl control) {
        DEADLINES.put(control.controlId(), new LeaseDeadline(control, player.level().getGameTime() + LEASE_TICKS));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var iterator = DEADLINES.values().iterator();
        while (iterator.hasNext()) {
            var lease = iterator.next();
            var control = lease.control();
            var actor = server.getPlayerList().getPlayer(control.actorId());
            if (actor == null || !actor.isAlive() || actor.hasDisconnected()
                    || !actor.level().dimension().identifier().equals(control.reference().dimension())
                    || actor.level().getGameTime() >= lease.expiresAt()) {
                StructureDirectControl.release(control.reference(), control.controlId());
                iterator.remove();
            }
        }
        var jetIterator = JET_MOVES.values().iterator();
        while (jetIterator.hasNext()) {
            var move = jetIterator.next();
            if (!move.tick()) {
                boolean restore = move.owner().mayRestoreNozzles(move.control());
                move.owner().withdrawLease(move.control());
                if (restore) move.restoreNozzles();
                jetIterator.remove();
            }
        }
        var forceIterator = FORCE_MOVES.values().iterator();
        while (forceIterator.hasNext()) {
            var move = forceIterator.next();
            if (!move.tick()) {
                move.owner().withdrawLease(move.control());
                forceIterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DEADLINES.clear();
        JET_MOVES.clear();
        FORCE_MOVES.clear();
    }

    private static void stopJet(ProgramStructureControl control) {
        var move = JET_MOVES.get(control.reference());
        if (move != null && move.control().equals(control)) {
            JET_MOVES.remove(control.reference());
            if (move.owner().mayRestoreNozzles(control)) move.restoreNozzles();
        }
    }

    private boolean mayRestoreNozzles(ProgramStructureControl control) {
        // A replacement skill can already have activated these same physical nozzles.
        return !StructureDirectControl.occupied(control.reference(), control.structureRuntimeId())
                || StructureDirectControl.validate(player.level(), control.reference(), player, control.controlId())
                == StructureDirectControl.Status.ACTIVE;
    }

    private static ProgramStructureOperationResult rejected(String reason) {
        return ProgramStructureOperationResult.rejected(reason);
    }

    private static ProgramStructureOperationResult completed(String detail, ProgramStructureControl control) {
        return ProgramStructureOperationResult.completed(detail, control.reference());
    }

    public record Acquisition(@Nullable ProgramStructureControl control, ProgramStructureOperationResult result) {
    }

    private record LeaseDeadline(ProgramStructureControl control, long expiresAt) {
    }

    private record ForceMove(SubLevelProgramControl owner, ProgramStructureControl control, Vec3 localPoint,
                             Vec3 target, int tier, boolean magnetic, double nativeParticipation,
                             long magnetizeExpiresAt, long expiresAt) {
        private boolean tick() {
            var player = owner.player;
            var skill = magnetic ? Skills.MAGNET_MANIPULATION.get() : Skills.KINETIC_THROW.get();
            if (player.level().getGameTime() >= expiresAt || !player.isAlive() || player.hasDisconnected()
                    || !skill.isEnabled(player)
                    || StructureDirectControl.validate(player.level(), control.reference(), player, control.controlId())
                    != StructureDirectControl.Status.ACTIVE) return false;
            var motion = StructureDynamics.motion(player.level(), control.reference()).orElse(null);
            if (motion == null) return false;
            var origin = motion.pose().localToWorldPoint(localPoint);
            if (origin.distanceToSqr(player.position()) > owner.maximumRange * owner.maximumRange) return false;
            StructurePhysicsControl command;
            if (magnetic) {
                var participation = player.level().getGameTime() < magnetizeExpiresAt ? 1.0 : nativeParticipation;
                if (participation <= 0.0) return false;
                var speed = MagneticFieldTuning.targetPullSpeed(skill.getEffectiveProficiencyMilestone(player));
                var force = MagneticStructureControl.pullForce(motion, localPoint, target, Vec3.ZERO,
                        speed, MagneticStructureService.PROGRAM_STOP_DISTANCE, participation);
                command = new StructurePhysicsControl.Force(localPoint, force);
            } else {
                var settings = KineticStructureControl.settings(motion.mass(), tier, 0, owner.maximumRange);
                command = new StructurePhysicsControl.Anchor(localPoint, target, Vec3.ZERO,
                        settings.stiffness(), settings.damping(), settings.maxForce());
            }
            return StructureDirectControl.submit(player.level(), control.reference(), player, control.controlId(), command);
        }
    }

    private record JetMove(SubLevelProgramControl owner, ProgramStructureControl control,
                           @Nullable Vec3 target, Map<HighSpeedJetNozzle, Integer> previousTicks, long expiresAt) {
        private boolean tick() {
            var player = owner.player;
            if (player.level().getGameTime() >= expiresAt || !player.isAlive() || player.hasDisconnected()
                    || !Skills.HIGH_SPEED_JET.get().isEnabled(player)
                    || StructureDirectControl.validate(player.level(), control.reference(), player, control.controlId())
                    != StructureDirectControl.Status.ACTIVE) return false;
            var motion = StructureDynamics.motion(player.level(), control.reference()).orElse(null);
            if (motion == null) return false;
            if (motion.pose().localToWorldPoint(motion.localCenterOfMass()).distanceToSqr(player.position())
                    > owner.maximumRange * owner.maximumRange) return false;
            var directions = new java.util.ArrayList<Vec3>();
            var localCenter = Vec3.ZERO;
            for (var nozzle : previousTicks.keySet()) {
                var mount = nozzle.structureMount().orElse(null);
                if (nozzle.isRemoved() || mount == null || !mount.reference().equals(control.reference())) continue;
                if (!HighSpeedJetStructureService.resolvesSupportCell(player.level(), control.reference(), mount.offset())) {
                    continue;
                }
                var local = Vec3.atCenterOf(mount.offset());
                var world = motion.pose().localToWorldPoint(local);
                var direction = target == null ? motion.pose().localToWorldDirection(
                        HighSpeedJetStructureService.localThrustDirection(mount.face())) : target.subtract(world);
                if (direction.lengthSqr() <= 1.0e-12) continue;
                directions.add(direction.normalize());
                localCenter = localCenter.add(local);
            }
            if (directions.isEmpty()) return false;
            var force = JetStructureControl.limitedCombinedThrustForce(directions, motion.linearVelocity(),
                    motion.mass(), 1.0f, Skills.HIGH_SPEED_JET.get().getEffectiveProficiencyMilestone(player));
            return StructureDirectControl.submit(player.level(), control.reference(), player, control.controlId(),
                    new StructurePhysicsControl.Force(localCenter.scale(1.0 / directions.size()), force));
        }

        private void restoreNozzles() {
            previousTicks.forEach((nozzle, ticks) -> {
                if (!nozzle.isRemoved() && nozzle.structureMount()
                        .filter(mount -> mount.reference().equals(control.reference())).isPresent()) {
                    nozzle.restoreActiveTicks(ticks);
                }
            });
        }
    }
}
