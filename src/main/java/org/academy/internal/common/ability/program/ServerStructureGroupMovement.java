package org.academy.internal.common.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.accelerator.KineticStructureControl;
import org.academy.api.common.ability.aeromanip.JetStructureControl;
import org.academy.api.common.ability.electromaster.MagneticStructureControl;
import org.academy.api.common.ability.electromaster.MagneticTargets;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.*;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.api.common.ability.electromaster.MagneticFieldTuning;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.Operation;

/** One token-bound body session; never reads or replaces the legacy per-player skill sessions. */
public final class ServerStructureGroupMovement implements AutoCloseable {
    // A fixed share also bounds simultaneous groups from different VMs of the same actor.
    static final double FORCE_SHARE = StructurePhysicsControl.MAX_FORCE / ProgramStructureGroupControl.MAX_MEMBERS;
    private final ServerPlayer player;
    private final ServerLevel host;
    private final ProgramStructureGroupControl.Member member;
    private final ProgramStructureMoveRequest request;
    private final Supplier<Profile> profile;
    private final double maximumRange;
    private final UUID receipt = UUID.randomUUID();
    private final Vec3 target;
    private final List<HighSpeedJetNozzle> nozzles;
    private final Map<HighSpeedJetNozzle, Integer> previousTicks = new HashMap<>();
    private long startedAt = -1, lastTick = Long.MIN_VALUE;

    public enum Mode { KINETIC, MAGNETIC, JET }
    public record Profile(Mode mode, int tier, double participation) {
        public Profile {
            Objects.requireNonNull(mode, "mode");
            if (tier < 0 || tier > 2 || !Double.isFinite(participation) || participation < 0 || participation > 1) {
                throw new IllegalArgumentException("Invalid movement profile");
            }
        }
        public static Profile kinetic(int tier) { return new Profile(Mode.KINETIC, Math.clamp(tier, 0, 2), 1); }
        public static Profile magnetic(double participation) { return new Profile(Mode.MAGNETIC, 0, participation); }
        public static Profile jet() { return new Profile(Mode.JET, 0, 1); }
    }

    ServerStructureGroupMovement(ServerPlayer player, ServerLevel host, ProgramStructureGroupControl.Member member,
                                 ProgramStructureMoveRequest request, double maximumRange, Supplier<Profile> profile) {
        this.player = player; this.host = host; this.member = member; this.request = request;
        this.maximumRange = maximumRange; this.profile = profile;
        var motion = StructureDynamics.motion(host, member.target().reference()).orElseThrow();
        var offset = request.displacement();
        target = motion.pose().localToWorldPoint(motion.localCenterOfMass()).add(offset.x(), offset.y(), offset.z());
        nozzles = profile.get().mode() == Mode.JET ? HighSpeedJet.Server.ownedNozzles(host, player).stream()
                .filter(nozzle -> nozzle.structureMount().filter(mount -> mount.reference().equals(member.target().reference())).isPresent())
                .toList() : List.of();
    }

    @Nullable String rejection() {
        var block = BlockPos.containing(target);
        if (!host.isInWorldBounds(block) || !host.hasChunkAt(block) || !host.getWorldBorder().isWithinBounds(block)) return "group_target_unavailable";
        if (target.distanceToSqr(player.position()) > maximumRange * maximumRange) return "group_target_out_of_range";
        var settings = profile.get();
        if (settings.mode() == Mode.MAGNETIC && settings.participation() <= 0) return "no_magnetic_participation";
        if (settings.mode() == Mode.JET && (nozzles.isEmpty() || nozzles.size() > 64 || nozzles.stream().anyMatch(nozzle -> nozzle.isRemoved()
                || !player.getUUID().equals(nozzle.ownerUuid())
                || nozzle.structureMount().filter(mount -> mount.reference().equals(member.target().reference())
                && HighSpeedJetStructureService.resolvesSupportCell(host, mount.reference(), mount.offset())).isEmpty()))) {
            return "group_nozzle_unavailable";
        }
        return null;
    }

    Operation start() {
        startedAt = host.getGameTime();
        for (var nozzle : nozzles) {
            previousTicks.put(nozzle, nozzle.activeTicks());
            nozzle.activate(request.durationTicks());
        }
        return poll(receipt);
    }

    Operation poll(UUID expectedReceipt) {
        if (!receipt.equals(expectedReceipt)) return Operation.rejected("movement_receipt_not_owned");
        if (host.getGameTime() - startedAt >= request.durationTicks()) return Operation.completed("movement_window_completed");
        if (lastTick == host.getGameTime()) return Operation.pending(receipt, "moving");
        lastTick = host.getGameTime();
        var motion = StructureDynamics.motion(host, member.target().reference()).orElseThrow();
        var local = motion.localCenterOfMass();
        var settings = profile.get();
        StructurePhysicsControl command;
        switch (settings.mode()) {
            case KINETIC -> {
                var gains = KineticStructureControl.settings(motion.mass(), settings.tier(), 0, maximumRange);
                var compensated = KineticStructureControl.gravityCompensatedTarget(target, motion.mass(), gains.stiffness());
                command = new StructurePhysicsControl.Anchor(local, compensated, Vec3.ZERO,
                        gains.stiffness(), gains.damping(), Math.min(FORCE_SHARE, gains.maxForce()));
            }
            case MAGNETIC -> {
                var speed = MagneticFieldTuning.targetPullSpeed(Skills.MAGNET_MANIPULATION.get().getEffectiveProficiencyMilestone(player));
                var force = MagneticStructureControl.pullForce(motion, local, target, Vec3.ZERO,
                        speed, MagneticStructureService.PROGRAM_STOP_DISTANCE, settings.participation());
                command = new StructurePhysicsControl.Force(local, limited(force));
            }
            case JET -> {
                var directions = new ArrayList<Vec3>();
                var applicationPoint = Vec3.ZERO;
                for (var nozzle : nozzles) {
                    var offset = Vec3.atCenterOf(nozzle.structureMount().orElseThrow().offset());
                    var direction = target.subtract(motion.pose().localToWorldPoint(offset));
                    if (direction.lengthSqr() > 1.0e-12) { directions.add(direction.normalize()); applicationPoint = applicationPoint.add(offset); }
                }
                var force = JetStructureControl.limitedCombinedThrustForce(directions, motion.linearVelocity(), motion.mass(),
                        1.0f, Skills.HIGH_SPEED_JET.get().getEffectiveProficiencyMilestone(player));
                command = new StructurePhysicsControl.Force(directions.isEmpty() ? local : applicationPoint.scale(1.0 / directions.size()), limited(force));
            }
            default -> throw new IllegalStateException("Unknown movement mode");
        }
        return StructureDirectControl.submit(host, member.target().reference(), player, member.controlId(), command)
                ? Operation.pending(receipt, "moving") : Operation.rejected("group_force_rejected");
    }

    static Vec3 limited(Vec3 force) {
        return force.lengthSqr() > FORCE_SHARE * FORCE_SHARE ? force.normalize().scale(FORCE_SHARE) : force;
    }

    public static double magneticParticipation(ServerPlayer player, ProgramStructureReference reference) {
        var summary = StructureSpaces.resolve(player.level().getServer(), reference.reference()).filter(reference::matches)
                .flatMap(handle -> handle.world().summarize(4096)).orElse(null);
        if (summary == null || summary.counts().isEmpty()) return 0;
        int total = 0, magnetic = 0;
        for (var entry : summary.counts().entrySet()) {
            total += entry.getValue();
            if (MagneticTargets.isMagnetic(entry.getKey())) magnetic += entry.getValue();
        }
        return MagneticStructureControl.magneticParticipation(magnetic, total);
    }

    @Override public void close() {
        // Do not overwrite nozzle state activated by a successor, including the same actor.
        boolean currentBody = StructureSpaces.resolve(host.getServer(), member.target().reference()).filter(member.target()::matches).isPresent();
        if (currentBody && (!StructureDirectControl.occupied(member.target().reference(), member.target().runtimeId())
                || StructureDirectControl.matches(member.target().reference(), player.getUUID(), member.controlId(), member.target().runtimeId()))) {
            previousTicks.forEach((nozzle, ticks) -> {
                if (!nozzle.isRemoved() && player.getUUID().equals(nozzle.ownerUuid()) && nozzle.structureMount()
                        .filter(mount -> mount.reference().equals(member.target().reference())).isPresent()) nozzle.restoreActiveTicks(ticks);
            });
        }
        previousTicks.clear();
    }
}
