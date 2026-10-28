package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.*;

/** Server-owned scoped groups, sharing the existing angular solver, skill gates and CP occupation. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ServerStructureGroupControl implements ProgramStructureGroupRuntime {
    private static final int MAX_SERVER_MEMBERS = 256;
    private static final Set<ServerStructureGroupControl> ACTIVE = new HashSet<>();
    private final ServerPlayer player;
    private final double maximumRange;
    private final Skill skill;
    private final float costPerMember;
    private final Runnable requireReady;
    private final Function<ProgramStructureReference, @Nullable String> eligibility;
    private final Function<ProgramStructureReference, ServerStructureGroupMovement.Profile> movementProfile;
    private @Nullable ProgramStructureGroupControl group;
    private @Nullable ServerLevel host;
    private @Nullable Operation lastResult;
    private long deadline;

    public ServerStructureGroupControl(ServerPlayer player, double maximumRange, Skill skill, float costPerMember,
                                       Runnable requireReady, Function<ProgramStructureReference, @Nullable String> eligibility,
                                       Function<ProgramStructureReference, ServerStructureGroupMovement.Profile> movementProfile) {
        this.player = Objects.requireNonNull(player);
        this.maximumRange = maximumRange;
        this.skill = Objects.requireNonNull(skill);
        if (!Float.isFinite(costPerMember) || costPerMember < 0 || !Double.isFinite(maximumRange) || maximumRange <= 0) {
            throw new IllegalArgumentException("Invalid group resource limits");
        }
        this.costPerMember = costPerMember;
        this.requireReady = Objects.requireNonNull(requireReady);
        this.eligibility = Objects.requireNonNull(eligibility);
        this.movementProfile = Objects.requireNonNull(movementProfile);
    }

    @Override public Operation moveStructures(List<ProgramStructureReference> structures, ProgramStructureMoveRequest request) {
        Objects.requireNonNull(request, "request");
        return start(structures, request.durationTicks(), null, request);
    }

    @Override public Operation rotateStructures(List<ProgramStructureReference> structures, ProgramStructurePose pose) {
        var target = SubLevelProgramControl.requestedRotation(Objects.requireNonNull(pose, "pose"));
        var limits = ProgramOrientationControl.limits(ProgramOrientationControl.DEFAULT_ROTATE_TICKS);
        return start(structures, limits, (context, id) -> StructureOrientations.service().rotateTo(context,
                new StructureOrientation.RotateRequest(id, target, limits)));
    }
    @Override public Operation resetStructures(List<ProgramStructureReference> structures, ResetRequest request) {
        var limits = ProgramOrientationControl.limits(request.timeoutTicks());
        return start(structures, limits, (context, id) -> StructureOrientations.service().reset(context,
                new StructureOrientation.ResetRequest(id, request.mode(), request.missingBaselinePolicy(), limits)));
    }
    @Override public Operation spinStructures(List<ProgramStructureReference> structures, SpinRequest request) {
        var rejection = ProgramOrientationControl.spinRejection(request);
        if (rejection != null) return Operation.rejected(rejection);
        var limits = ProgramOrientationControl.limits(request.durationTicks() + ProgramOrientationControl.BRAKE_TICKS);
        var axis = new Vec3(request.axis().x(), request.axis().y(), request.axis().z());
        return start(structures, limits, (context, id) -> StructureOrientations.service().spin(context,
                new StructureOrientation.SpinRequest(id, axis, request.frame(), request.angularSpeed(), request.durationTicks(), request.finishMode(), limits)));
    }

    private Operation start(List<ProgramStructureReference> supplied, StructureOrientation.Limits limits, Command command) {
        return start(supplied, limits.timeoutTicks(), command, null);
    }

    private Operation start(List<ProgramStructureReference> supplied, int timeoutTicks, @Nullable Command command,
                            @Nullable ProgramStructureMoveRequest movement) {
        requireThread(); requireReady.run();
        final List<ProgramStructureReference> targets;
        try { targets = ProgramStructureGroupControl.canonical(supplied); }
        catch (IllegalArgumentException error) { return Operation.rejected(error.getMessage()); }
        if (group != null) return Operation.rejected("group_receipt_pending");
        if (targets.isEmpty()) return Operation.completed("group_empty");
        int actorMembers = 0, serverMembers = 0;
        for (var active : ACTIVE) {
            if (active.host.getServer() != player.level().getServer()) continue;
            serverMembers += active.group.members().size();
            if (active.player.getUUID().equals(player.getUUID())) actorMembers += active.group.members().size();
        }
        if (actorMembers + targets.size() > ProgramStructureGroupControl.MAX_MEMBERS) return Operation.rejected("actor_group_budget");
        if (serverMembers + targets.size() > MAX_SERVER_MEMBERS) return Operation.rejected("server_group_budget");
        host = player.level();
        deadline = host.getGameTime() + timeoutTicks + 2L;
        var backend = new Backend(host, command, movement);
        group = new ProgramStructureGroupControl(targets, backend);
        // Reserve the whole group before invoking callbacks or charging resources.
        ACTIVE.add(this);
        try {
            lastResult = StructureProgramActions.execute(player, skill, costPerMember * targets.size(),
                    this::startOwnedGroup, result -> group.workStarted() || ProgramStructureGroupControl.accepted(result));
            if (group.finished()) { ACTIVE.remove(this); group = null; }
            return lastResult;
        } catch (RuntimeException | Error failure) {
            try { closeStructureGroups(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private Operation startOwnedGroup() {
        try { return group.start(); }
        catch (RuntimeException failure) {
            // Preserve payment for accepted/uncertain partial work after the core has cancelled it.
            AcademyCraft.LOGGER.warn("Structure group {} rejected during submission", group.receipt(), failure);
            return Operation.rejected("group_backend_exception");
        }
    }

    @Override public Operation structureGroupStatus(UUID receipt) {
        requireThread();
        if (group == null || !group.receipt().equals(receipt)) return Operation.rejected("group_receipt_not_owned");
        advance();
        var result = Objects.requireNonNull(lastResult);
        if (group.finished()) group = null;
        return result;
    }

    private void advance() {
        if (group == null || group.finished()) return;
        try {
            if (host.getGameTime() > deadline) {
                group.close(); lastResult = Operation.rejected("group_timeout");
            } else lastResult = group.poll();
        } catch (RuntimeException failure) {
            lastResult = Operation.rejected("group_backend_exception");
            try { group.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            AcademyCraft.LOGGER.warn("Structure group {} stopped after a backend failure", group.receipt(), failure);
        } finally { if (group.finished()) ACTIVE.remove(this); }
    }

    @Override public void closeStructureGroups() {
        requireThread();
        try { if (group != null) group.close(); }
        finally { ACTIVE.remove(this); group = null; lastResult = null; }
    }
    private void requireThread() {
        if (!player.level().getServer().isSameThread()) throw new IllegalStateException("Structure groups are server-thread confined");
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (var active : List.copyOf(ACTIVE)) if (active.host.getServer() == event.getServer()) active.advance();
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        for (var active : List.copyOf(ACTIVE)) if (active.host.getServer() == event.getServer()) {
            try { active.closeStructureGroups(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("Could not close structure group during shutdown", failure); }
        }
    }

    /** Read-only server diagnostics, also used by isolated angular fixtures for linear gravity support. */
    static List<ProgramStructureGroupControl.Member> members(ServerPlayer actor) {
        return ACTIVE.stream().filter(value -> value.player == actor).flatMap(value -> value.group.members().stream()).toList();
    }

    public static @Nullable String magneticRejection(ServerPlayer player, ProgramStructureReference target) {
        var summary = StructureSpaces.resolve(player.level().getServer(), target.reference()).filter(target::matches)
                .flatMap(handle -> handle.world().summarize(4_096));
        return summary.isEmpty() ? "magnetic_content_unavailable"
                : summary.get().matches(org.academy.api.common.ability.electromaster.MagneticTargets::isMagnetic, false)
                ? null : "no_magnetic_participation";
    }
    public static @Nullable String jetRejection(ServerPlayer player, ProgramStructureReference target) {
        return HighSpeedJet.Server.ownedNozzles(player.level(), player).stream().filter(nozzle -> !nozzle.isRemoved())
                .anyMatch(nozzle -> nozzle.structureMount().filter(mount -> mount.reference().equals(target.reference())
                        && HighSpeedJetStructureService.resolvesSupportCell(player.level(), target.reference(), mount.offset())).isPresent())
                ? null : "support_unresolved";
    }

    @FunctionalInterface private interface Command {
        StructureOperation.Result<StructureOrientation.Snapshot> start(StructureOperation.Context context, UUID control);
    }
    private final class Backend implements ProgramStructureGroupControl.Backend {
        private final ServerLevel level;
        private final @Nullable Command command;
        private final @Nullable ProgramStructureMoveRequest movement;
        private final Map<ProgramStructureGroupControl.Member, ServerStructureGroupMovement> moves = new HashMap<>();
        private final Map<ProgramStructureGroupControl.Member, StructureOperation.Context> contexts = new HashMap<>();
        private Backend(ServerLevel level, @Nullable Command command, @Nullable ProgramStructureMoveRequest movement) {
            this.level = level; this.command = command; this.movement = movement;
        }
        @Override public @Nullable String rejection(ProgramStructureGroupControl.Member member, boolean acquired) {
            if (player.level() != level || player.hasDisconnected() || !player.isAlive() || player.isRemoved() || player.isSpectator()) {
                return "group_actor_unavailable";
            }
            try { requireReady.run(); } catch (RuntimeException unavailable) { return "group_skill_unavailable"; }
            var target = member.target();
            var handle = StructureSpaces.resolve(level.getServer(), target.reference()).orElse(null);
            if (!target.reference().dimension().equals(level.dimension().identifier()) || !target.matches(handle)
                    || handle.lifecycle() != StructureLifecycle.ACTIVE) return "structure_unavailable";
            var motion = StructureDynamics.motion(level, target.reference()).orElse(null);
            if (motion == null || motion.pose().localToWorldPoint(motion.localCenterOfMass()).distanceToSqr(player.position()) > maximumRange * maximumRange) {
                return "structure_out_of_range_or_unavailable";
            }
            if (acquired && StructureDirectControl.validate(level, target.reference(), player, member.controlId())
                    != StructureDirectControl.Status.ACTIVE) return "control_lease_lost";
            var rejected = eligibility.apply(target);
            if (rejected != null) return rejected;
            var context = StructureOrientations.context(level, target.reference(), player).orElse(null);
            if (context == null || !context.runtimeId().equals(target.runtimeId())) return "orientation_context_unavailable";
            var previous = contexts.putIfAbsent(member, context);
            if (previous != null && previous.expectedContentRevision() != context.expectedContentRevision()) return "structure_content_changed";
            if (movement != null) {
                var move = moves.computeIfAbsent(member, key -> new ServerStructureGroupMovement(player, level, key,
                        movement, maximumRange, () -> movementProfile.apply(key.target())));
                return move.rejection();
            }
            return null;
        }
        @Override public boolean acquire(ProgramStructureGroupControl.Member member) {
            // Even another session of the same actor remains untouched by a scoped group.
            return !StructureDirectControl.occupied(member.target().reference(), member.target().runtimeId())
                    && StructureDirectControl.acquire(level, member.target().reference(), player, member.controlId()) == StructureDirectControl.Status.ACTIVE;
        }
        @Override public Operation start(ProgramStructureGroupControl.Member member) {
            var rejection = rejection(member, true);
            if (rejection != null) return Operation.rejected(rejection);
            return movement != null ? moves.get(member).start()
                    : ProgramOrientationControl.operation(Objects.requireNonNull(command).start(contexts.get(member), member.controlId()));
        }
        @Override public Operation status(ProgramStructureGroupControl.Member member, UUID receipt) {
            if (movement != null) return moves.get(member).poll(receipt);
            return ProgramOrientationControl.operation(StructureOrientations.service().status(contexts.get(member),
                    new StructureOrientation.StatusRequest(member.controlId(), receipt)));
        }
        @Override public void release(ProgramStructureGroupControl.Member member) {
            try {
                var move = moves.remove(member);
                if (move != null) move.close();
                StructureOrientations.cancel(level, member.target().reference(), member.controlId());
            }
            finally { StructureDirectControl.release(member.target().reference(), member.controlId()); }
        }
    }
}
