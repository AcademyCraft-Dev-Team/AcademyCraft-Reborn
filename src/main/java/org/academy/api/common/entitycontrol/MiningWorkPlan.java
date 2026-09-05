package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Shared, server-thread excavation accounting. A temporarily blocked target is never completed. */
public final class MiningWorkPlan {
    public enum Eligibility { ELIGIBLE, EXCLUDED, UNLOADED }
    public record Progress(int completed, int remaining, int blocked, int active) {}
    private static final class Target {
        UUID owner;
        boolean resolved;
        long retryAt;
        String reason = "";
    }
    private final BlockWorkRegion region;
    private final Map<BlockPos, Target> targets = new LinkedHashMap<>();
    private long lastScan = Long.MIN_VALUE;
    private Progress cachedProgress;

    public MiningWorkPlan(BlockWorkRegion region) { this.region = region; }

    /** A full region check is required before completion, including currently unloaded cells. */
    public void refresh(long now, boolean finalCheck, Function<BlockPos, Eligibility> eligibility) {
        if (lastScan == now || !finalCheck && lastScan != Long.MIN_VALUE && now - lastScan < 20) return;
        lastScan = now;
        for (var mutable : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
            var pos = mutable.immutable();
            var result = eligibility.apply(pos);
            var target = targets.get(pos);
            if (result == Eligibility.EXCLUDED) {
                if (target != null && !target.resolved) resolve(pos);
                continue;
            }
            if (target == null) { target = new Target(); targets.put(pos, target); cachedProgress = null; }
            if (target.resolved) { target.resolved = false; target.retryAt = 0; cachedProgress = null; }
            if (result == Eligibility.UNLOADED) block(pos, "unloaded", now + 20);
            else if (target.reason.equals("unloaded")) {
                target.reason = ""; target.retryAt = 0; cachedProgress = null;
            }
        }
    }

    /** Upper layers and exposed faces first; distribute workers across four-block work faces. */
    public BlockPos claim(UUID worker, Vec3 position, long now, Predicate<BlockPos> exposed) {
        var faces = new HashMap<Long, Integer>();
        targets.forEach((pos, target) -> { if (target.owner != null) faces.merge(face(pos), 1, Integer::sum); });
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (var entry : targets.entrySet()) {
            var pos = entry.getKey();
            var target = entry.getValue();
            if (target.resolved || target.owner != null || now < target.retryAt) continue;
            var score = (region.maximum().getY() - pos.getY()) * 100000.0
                    + (exposed.test(pos) ? 0 : 10000)
                    + faces.getOrDefault(face(pos), 0) * 256.0 + Vec3.atCenterOf(pos).distanceToSqr(position);
            if (score < bestScore) { best = pos; bestScore = score; }
        }
        if (best != null) {
            var target = targets.get(best);
            target.owner = worker;
            target.reason = "";
            cachedProgress = null;
        }
        return best;
    }

    private static long face(BlockPos pos) { return ((long) (pos.getX() >> 2) << 32) ^ ((pos.getZ() >> 2) & 0xffffffffL); }

    public void block(BlockPos pos, String reason, long retryAt) {
        var target = targets.get(pos);
        if (target == null) return;
        target.owner = null;
        target.reason = reason;
        target.retryAt = retryAt;
        cachedProgress = null;
    }

    public void resolve(BlockPos pos) {
        var target = targets.get(pos);
        if (target == null) return;
        target.owner = null;
        target.resolved = true;
        target.reason = "";
        cachedProgress = null;
        // Local terrain changes may open a previously unreachable work face.
        targets.forEach((other, pending) -> {
            if (pending.reason.equals("path") && other.distSqr(pos) <= 16) pending.retryAt = 0;
        });
    }

    public void release(UUID worker) {
        targets.values().forEach(target -> {
            if (worker.equals(target.owner)) { target.owner = null; cachedProgress = null; }
        });
    }

    public String blockedReason() {
        return targets.values().stream().filter(target -> !target.resolved && !target.reason.isEmpty())
                .map(target -> target.reason).findFirst().orElse("waiting");
    }

    public Progress progress() {
        if (cachedProgress != null) return cachedProgress;
        int completed = 0, remaining = 0, blocked = 0, active = 0;
        for (var target : targets.values()) {
            if (target.resolved) { completed++; continue; }
            remaining++;
            if (target.owner != null) active++;
            if (!target.reason.isEmpty()) blocked++;
        }
        return cachedProgress = new Progress(completed, remaining, blocked, active);
    }
}
