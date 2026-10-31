package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramRunStatus;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-thread state machine: no players, worlds, execution callbacks or unbounded history. */
final class ProgramRunStatusTracker {
    static final int MAX_SUBSCRIPTIONS = 256;
    static final long LEASE_TICKS = 400, MIN_SEND_TICKS = 5;
    private final Map<UUID, Subscription> subscriptions = new LinkedHashMap<>();
    // Separate five-tick cooldown survives token replacement and unsubscribe/invalidate.
    // Full cooldown tables delay a new sender instead of evicting another player's rate limit.
    private final Map<UUID, Long> sendTimes = new LinkedHashMap<>();
    private long nextRun = 1;
    private long lastPrunedTick = Long.MIN_VALUE;

    boolean subscribe(UUID owner, UUID token, ProgramRunStatus.Target target, long now, long worldTick) {
        prune(now);
        var current = subscriptions.get(owner);
        if (current != null && current.token.equals(token)) {
            if (!current.target.equals(target)) return false;
            current.renewedAt = now; return true;
        }
        if (current == null && subscriptions.size() >= MAX_SUBSCRIPTIONS) return false;
        subscriptions.put(owner, new Subscription(token, target, now, worldTick));
        return true;
    }

    boolean unsubscribe(UUID owner, UUID token) {
        var current = subscriptions.get(owner);
        return current != null && current.token.equals(token) && subscriptions.remove(owner, current);
    }

    @Nullable Handle begin(UUID owner, ProgramRunStatus.Target target, long now, long worldTick) {
        var sub = current(owner, now);
        if (sub == null || !sub.target.equals(target) || nextRun < 1 || nextRun == Long.MAX_VALUE) return null;
        long serial = nextRun++; sub.currentRun = serial;
        var handle = new Handle(owner, sub.token, target, serial);
        update(handle, new Progress(ProgramRunStatus.Phase.PREPARING, ProgramRunStatus.WaitKind.NONE,
                -1, -1, null, -1, -1, worldTick, "", "", "preparing", false), now);
        return handle;
    }

    boolean update(Handle handle, Progress progress, long now) {
        var sub = current(handle.owner, now);
        if (sub == null || !sub.token.equals(handle.token) || !sub.target.equals(handle.target)
                || sub.currentRun != handle.runSequence
                || sub.latest.runSequence() == handle.runSequence && sub.latest.terminal()) return false;
        sub.latest = new ProgramRunStatus(sub.token, sub.target, handle.runSequence, ++sub.update,
                progress.phase, progress.waitKind, progress.nodeId, progress.sourceNodeId, progress.nodeType,
                progress.logicalTick, progress.wakeAt, progress.worldTick, now,
                progress.diagnostic, progress.port, progress.reason, progress.rollbackIncomplete);
        return true;
    }

    @Nullable ProgramRunStatus poll(UUID owner, long now) {
        prune(now);
        var sub = current(owner, now);
        if (sub == null || sub.latest.updateSequence() <= sub.sentUpdate || sendTimes.containsKey(owner)
                || sendTimes.size() >= MAX_SUBSCRIPTIONS) return null;
        sub.sentUpdate = sub.latest.updateSequence(); sendTimes.put(owner, now);
        return sub.latest;
    }

    boolean matches(UUID owner, UUID token, ProgramRunStatus.Target target, long now) {
        var sub = current(owner, now);
        return sub != null && sub.token.equals(token) && sub.target.equals(target);
    }
    boolean active(Handle handle, long now) {
        var sub = current(handle.owner, now);
        return sub != null && sub.token.equals(handle.token) && sub.target.equals(handle.target)
                && sub.currentRun == handle.runSequence && !sub.latest.terminal();
    }
    void finish(Handle handle, ProgramRunStatus.Phase phase, int nodeId, String diagnostic, String port,
                String reason, boolean rollbackIncomplete, long worldTick, long now) {
        var sub = current(handle.owner, now);
        if (sub == null) return;
        var old = sub.latest;
        update(handle, new Progress(phase, ProgramRunStatus.WaitKind.NONE, nodeId, -1,
                nodeId == old.nodeId() ? old.nodeType() : null, old.logicalTick(), -1,
                worldTick >= 0 ? worldTick : old.worldGameTick(), diagnostic, port, reason, rollbackIncomplete), now);
    }
    ProgramRunStatus.@Nullable Target target(UUID owner, long now) {
        var sub = current(owner, now); return sub == null ? null : sub.target;
    }
    void invalidate(UUID owner) { subscriptions.remove(owner); }
    void clear() { subscriptions.clear(); sendTimes.clear(); nextRun = 1; lastPrunedTick = Long.MIN_VALUE; }
    int size() { return subscriptions.size(); }
    List<UUID> owners() { return List.copyOf(subscriptions.keySet()); }

    private @Nullable Subscription current(UUID owner, long now) {
        var sub = subscriptions.get(owner);
        if (sub != null && (now < sub.renewedAt || now - sub.renewedAt >= LEASE_TICKS)) {
            subscriptions.remove(owner); return null;
        }
        return sub;
    }
    private void prune(long now) {
        if (lastPrunedTick == now) return;
        lastPrunedTick = now;
        subscriptions.values().removeIf(sub -> now < sub.renewedAt || now - sub.renewedAt >= LEASE_TICKS);
        sendTimes.values().removeIf(sent -> now < sent || now - sent >= MIN_SEND_TICKS);
    }

    record Handle(UUID owner, UUID token, ProgramRunStatus.Target target, long runSequence) {}
    record Progress(ProgramRunStatus.Phase phase, ProgramRunStatus.WaitKind waitKind, int nodeId, int sourceNodeId,
                    @Nullable Identifier nodeType, long logicalTick, long wakeAt, long worldTick,
                    String diagnostic, String port, String reason, boolean rollbackIncomplete) {}

    private static final class Subscription {
        final UUID token;
        final ProgramRunStatus.Target target;
        long renewedAt, currentRun, update = 1, sentUpdate;
        ProgramRunStatus latest;
        Subscription(UUID token, ProgramRunStatus.Target target, long now, long worldTick) {
            this.token = token; this.target = target; this.renewedAt = now;
            latest = new ProgramRunStatus(token, target, 0, update, ProgramRunStatus.Phase.IDLE, ProgramRunStatus.WaitKind.NONE,
                    -1, -1, null, -1, -1, worldTick, now, "", "", "no_active_run", false);
        }
    }
}
