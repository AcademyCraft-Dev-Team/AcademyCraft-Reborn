package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.ProgramRunStatus;
import org.academy.api.server.ability.AbilitySystemServer;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Implementation bridge. Observation never executes a VM slice, action, receipt poll or transaction. */
public final class ProgramRunObservations {
    private static final Map<MinecraftServer, ProgramRunStatusTracker> TRACKERS = new IdentityHashMap<>();
    private ProgramRunObservations() {}

    public static boolean subscribe(ServerPlayer player, UUID token, ProgramRunStatus.Target target) {
        try {
            var server = player.level().getServer();
            if (!server.isSameThread() || !player.level().dimension().identifier().equals(target.dimension())) return false;
            var tracker = TRACKERS.computeIfAbsent(server, _ -> new ProgramRunStatusTracker());
            long now = tick(server);
            boolean renewed = tracker.matches(player.getUUID(), token, target, now);
            if (!tracker.subscribe(player.getUUID(), token, target, now, player.level().getGameTime())) return false;
            if (!renewed) {
                var active = ServerProgramScheduler.observation(server, new ServerProgramScheduler.SessionKey(
                        player.getUUID(), target.category(), target.programId(), target.slot()));
                if (active != null && originMatches(active.invocation(), target)) {
                    bind(player, tracker, target, active.invocation());
                    vmSlice(active.session(), active.session().observationStatus(), active.frame());
                }
            }
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    public static boolean unsubscribe(ServerPlayer player, UUID token) {
        var tracker = TRACKERS.get(player.level().getServer());
        return tracker != null && tracker.unsubscribe(player.getUUID(), token);
    }

    public static void begin(ServerPlayer player, Identifier category, int slot, UUID programId,
                             long bookRevision, ProgramInvocationContext invocation) {
        try {
            if (invocation.slot() != slot || !invocation.programId().equals(programId)) return;
            invocation.observationDimension = player.level().dimension().identifier();
            invocation.observationCategory = category; invocation.observationBookRevision = bookRevision;
            invocation.observation = null;
            var tracker = TRACKERS.get(player.level().getServer());
            if (tracker == null || tracker.target(player.getUUID(), tick(player.level().getServer())) == null) return;
            bind(player, tracker, new ProgramRunStatus.Target(invocation.observationDimension, category, slot, programId, bookRevision), invocation);
        } catch (RuntimeException ignored) { }
    }

    private static void bind(ServerPlayer player, ProgramRunStatusTracker tracker, ProgramRunStatus.Target target,
                             ProgramInvocationContext invocation) {
        var handle = tracker.begin(player.getUUID(), target, tick(player.level().getServer()), player.level().getGameTime());
        invocation.observation = handle == null ? null : new Binding(player.level().getServer(), handle);
    }

    static void vmSlice(ProgramVm.Session session, ProgramVmResult result, ProgramExecutionFrame frame) {
        var invocation = frame.invocation().orElse(null);
        if (invocation == null || invocation.observation == null) return;
        try {
            var binding = invocation.observation;
            var tracker = TRACKERS.get(binding.server);
            if (tracker == null || !tracker.active(binding.handle, tick(binding.server))) {
                invocation.observation = null; return;
            }
            var view = session.observationView();
            var phase = result.status() == ProgramVmResult.Status.SUSPENDED || result.status() == ProgramVmResult.Status.FUEL_EXHAUSTED
                    ? ProgramRunStatus.Phase.WAITING : ProgramRunStatus.Phase.RUNNING;
            String reason = switch (result.status()) {
                case COMPLETED -> "settling_transaction";
                case FAILED -> "aborting_transaction";
                case FUEL_EXHAUSTED -> "fuel_slice";
                case SUSPENDED -> view.kind() == ProgramRunStatus.WaitKind.WAIT ? "wait_logical_ticks" : "retry_action_pending";
            };
            tracker.update(binding.handle, new ProgramRunStatusTracker.Progress(phase, view.kind(), result.nodeId(),
                    view.sourceNodeId(), view.nodeType(), view.logicalTick(), view.wakeAt(), frame.gameTime().orElse(-1),
                    result.diagnostic().name(), result.port(), reason, false), tick(binding.server));
        } catch (RuntimeException ignored) { }
    }

    public static void rejected(ProgramInvocationContext invocation, int nodeId, ProgramVmDiagnostic diagnostic,
                                @Nullable String port, @Nullable String detail) {
        settled(invocation, ProgramRunStatus.Phase.FAILED, nodeId, diagnostic, port, detail, false, -1);
    }

    /** Only call after the real commit/release, rollback or abort has finished. */
    public static void settled(@Nullable ProgramInvocationContext invocation, ProgramRunStatus.Phase phase, int nodeId,
                               ProgramVmDiagnostic diagnostic, @Nullable String port, @Nullable String detail,
                               boolean rollbackIncomplete, long worldTick) {
        if (invocation == null || invocation.observation == null) return;
        try {
            var binding = invocation.observation; var tracker = TRACKERS.get(binding.server);
            if (tracker != null) tracker.finish(binding.handle, phase, nodeId, diagnostic.name(), port, detail,
                    rollbackIncomplete, worldTick, tick(binding.server));
        } catch (RuntimeException ignored) { }
    }

    public static void flush(MinecraftServer server, BiConsumer<ServerPlayer, ProgramRunStatus> sender) {
        var tracker = TRACKERS.get(server);
        if (tracker == null) return;
        for (var owner : tracker.owners()) {
            try {
                var target = tracker.target(owner, tick(server));
                if (target == null) continue;
                var player = server.getPlayerList().getPlayer(owner);
                var category = player == null ? null : AbilitySystemServer.getSystem(player).getPlayerAbilityCategory(owner);
                if (player == null || player.hasDisconnected() || !player.level().dimension().identifier().equals(target.dimension())
                        || category == null || !category.getKey().equals(target.category())) { tracker.invalidate(owner); continue; }
                var status = tracker.poll(owner, tick(server));
                if (status != null) sender.accept(player, status);
            } catch (RuntimeException ignored) { tracker.invalidate(owner); }
        }
    }

    public static void invalidate(ServerPlayer player) {
        var tracker = TRACKERS.get(player.level().getServer());
        if (tracker != null) tracker.invalidate(player.getUUID());
    }
    public static void clear(MinecraftServer server) { TRACKERS.remove(server); }
    public static void clearAll() { TRACKERS.clear(); }
    private static long tick(MinecraftServer server) { return Integer.toUnsignedLong(server.getTickCount()); }
    private static boolean originMatches(ProgramInvocationContext invocation, ProgramRunStatus.Target target) {
        return invocation.programId().equals(target.programId()) && invocation.slot() == target.slot()
                && invocation.observationBookRevision == target.bookRevision()
                && target.dimension().equals(invocation.observationDimension) && target.category().equals(invocation.observationCategory);
    }
    record Binding(MinecraftServer server, ProgramRunStatusTracker.Handle handle) {}
}
