package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/** Shared incremental excavation accounting. Unscanned, claimed and blocked cells prevent completion. */
public final class MiningWorkPlan {
    public enum Eligibility { ELIGIBLE, EXCLUDED, UNLOADED }
    public record Progress(int completed, int remaining, int blocked, int active) {}
    private static final class Target {
        @Nullable UUID owner;
        boolean resolved;
        long retryAt;
        String reason = "";
    }
    private final BlockWorkRegion region;
    private final Map<BlockPos, Target> targets = new HashMap<>();
    private final TreeMap<Integer, LinkedHashSet<BlockPos>> available = new TreeMap<>(Comparator.reverseOrder());
    private final Map<UUID, Set<BlockPos>> owned = new HashMap<>();
    private final Map<Long, Integer> faces = new HashMap<>();
    private final Map<String, Integer> reasons = new HashMap<>();
    private final LinkedHashSet<BlockPos> dirty = new LinkedHashSet<>();
    private record Retry(BlockPos position, long tick) implements Comparable<Retry> {
        @Override public int compareTo(Retry other) { return Long.compare(tick, other.tick); }
    }
    private final PriorityQueue<Retry> retries = new PriorityQueue<>();
    private long lastScan = Long.MIN_VALUE, lastSlice = Long.MIN_VALUE;
    private int scanCursor, completed, remaining, blocked, active;
    private boolean scanning, scanned, finalVerified;

    public MiningWorkPlan(BlockWorkRegion region) { this.region = region; }

    /** Compatibility entry point for small synchronous callers and deterministic model tests. */
    public void refresh(long now, boolean finalCheck, Function<BlockPos, Eligibility> eligibility) {
        refresh(now, finalCheck, eligibility, region.volume(), () -> true);
    }

    public void refresh(long now, boolean finalCheck, Function<BlockPos, Eligibility> eligibility,
                        int maximum, BooleanSupplier permit) {
        if (lastSlice == now) return;
        if (!scanning && dirty.isEmpty() && scanned && !finalCheck && now - lastScan < 20) return;
        if (!scanning && (!scanned || finalCheck && !finalVerified || now - lastScan >= 20)) {
            scanning = true;
            scanCursor = 0;
            finalVerified = false;
        }
        lastSlice = now;
        int count = 0;
        while (count < maximum && !dirty.isEmpty() && permit.getAsBoolean()) {
            classify(dirty.removeFirst(), eligibility);
            count++;
        }
        while (count < maximum && scanning && permit.getAsBoolean()) {
            classify(position(scanCursor++), eligibility);
            count++;
            if (scanCursor == region.volume()) {
                scanning = false;
                scanned = true;
                lastScan = now;
                finalVerified = remaining == 0 && dirty.isEmpty();
            }
        }
    }

    private BlockPos position(int index) {
        int layerSize = region.sizeX() * region.sizeZ();
        return new BlockPos(region.minimum().getX() + index % region.sizeX(),
                region.maximum().getY() - index / layerSize,
                region.minimum().getZ() + index % layerSize / region.sizeX());
    }

    private void classify(BlockPos pos, Function<BlockPos, Eligibility> eligibility) {
        var result = eligibility.apply(pos);
        var target = targets.get(pos);
        if (result == Eligibility.EXCLUDED) { if (target != null && !target.resolved) resolve(pos); return; }
        if (target == null) {
            target = new Target(); targets.put(pos, target); remaining++; enqueue(pos);
        } else if (target.resolved) {
            target.resolved = false; target.retryAt = 0; completed--; remaining++; enqueue(pos);
        }
        if (result == Eligibility.UNLOADED) block(pos, "unloaded", lastSlice + 20);
        else if (target.reason.equals("unloaded")) { reason(target, ""); target.retryAt = 0; enqueue(pos); }
    }

    public boolean scanning() { return scanning || !scanned || !dirty.isEmpty(); }
    public boolean completionVerified() { return finalVerified && !scanning() && remaining == 0; }
    public boolean owns(UUID worker, BlockPos pos) {
        var target = targets.get(pos);
        return target != null && !target.resolved && worker.equals(target.owner);
    }
    public void changed(BlockPos pos) {
        if (pos.getX() < region.minimum().getX() || pos.getX() > region.maximum().getX()
                || pos.getY() < region.minimum().getY() || pos.getY() > region.maximum().getY()
                || pos.getZ() < region.minimum().getZ() || pos.getZ() > region.maximum().getZ()) return;
        dirty.add(pos.immutable());
        finalVerified = false;
    }

    public @Nullable BlockPos claim(UUID worker, Vec3 position, long now, Predicate<BlockPos> exposed) {
        return claim(worker, position, now, exposed, region.volume(), () -> true);
    }

    /** Bounded candidate comparison, with rotation so blocked faces cannot starve later targets. */
    public @Nullable BlockPos claim(UUID worker, Vec3 position, long now, Predicate<BlockPos> exposed,
                                    int maximum, BooleanSupplier permit) {
        BlockPos best = null;
        double score = Double.MAX_VALUE;
        int count = 0;
        while (!retries.isEmpty() && retries.peek().tick <= now && count < maximum) {
            if (!permit.getAsBoolean()) return null;
            count++;
            var retry = retries.remove();
            var target = targets.get(retry.position);
            if (target != null && !target.resolved && target.owner == null && target.retryAt == retry.tick) enqueue(retry.position);
        }
        for (var layer : available.values()) {
            int size = layer.size();
            while (size-- > 0 && count < maximum) {
                if (!permit.getAsBoolean()) return best == null ? null : assign(worker, best);
                count++;
                var pos = layer.removeFirst();
                layer.add(pos);
                var target = targets.get(pos);
                if (now < target.retryAt) continue;
                double candidate = (region.maximum().getY() - pos.getY()) * 100000.0
                        + (exposed.test(pos) ? 0 : 10000) + faces.getOrDefault(face(pos), 0) * 256.0
                        + Vec3.atCenterOf(pos).distanceToSqr(position);
                if (candidate < score) { score = candidate; best = pos; }
            }
            if (best != null || count == maximum) break;
        }
        return best == null ? null : assign(worker, best);
    }

    private BlockPos assign(UUID worker, BlockPos pos) {
        var target = targets.get(pos);
        dequeue(pos);
        target.owner = worker;
        owned.computeIfAbsent(worker, ignored -> new HashSet<>()).add(pos);
        faces.merge(face(pos), 1, Integer::sum);
        active++;
        reason(target, "");
        return pos;
    }
    private static long face(BlockPos pos) { return ((long) (pos.getX() >> 2) << 32) ^ ((pos.getZ() >> 2) & 0xffffffffL); }
    private void enqueue(BlockPos pos) { available.computeIfAbsent(pos.getY(), ignored -> new LinkedHashSet<>()).add(pos); }
    private void dequeue(BlockPos pos) {
        var layer = available.get(pos.getY());
        if (layer == null) return;
        layer.remove(pos);
        if (layer.isEmpty()) available.remove(pos.getY());
    }
    private void releaseOwner(BlockPos pos, Target target) {
        if (target.owner == null) return;
        var positions = owned.get(target.owner);
        positions.remove(pos);
        if (positions.isEmpty()) owned.remove(target.owner);
        target.owner = null;
        faces.computeIfPresent(face(pos), (key, value) -> value == 1 ? null : value - 1);
        active--;
    }
    private void reason(Target target, String value) {
        if (target.reason.equals(value)) return;
        if (!target.reason.isEmpty()) {
            blocked--;
            reasons.computeIfPresent(target.reason, (key, count) -> count == 1 ? null : count - 1);
        }
        target.reason = value;
        if (!value.isEmpty()) { blocked++; reasons.merge(value, 1, Integer::sum); }
    }

    public void block(BlockPos pos, String reason, long retryAt) {
        var target = targets.get(pos);
        if (target == null || target.resolved) return;
        releaseOwner(pos, target);
        reason(target, reason);
        if (target.retryAt != retryAt) retries.add(new Retry(pos, retryAt));
        target.retryAt = retryAt;
        dequeue(pos);
    }

    public void resolve(BlockPos pos) {
        var target = targets.get(pos);
        if (target == null || target.resolved) return;
        releaseOwner(pos, target);
        dequeue(pos);
        reason(target, "");
        target.resolved = true;
        remaining--; completed++;
        finalVerified = false;
        // Bound invalidation by the local neighbourhood, independent of total region size.
        if (reasons.containsKey("path")) {
            for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++) {
                if (x * x + y * y + z * z > 16) continue;
                var neighbourPos = pos.offset(x, y, z);
                var neighbour = targets.get(neighbourPos);
                if (neighbour != null && neighbour.reason.equals("path")) { neighbour.retryAt = 0; enqueue(neighbourPos); }
            }
        }
    }

    public void release(UUID worker) {
        var positions = owned.get(worker);
        if (positions == null) return;
        for (var pos : List.copyOf(positions)) {
            releaseOwner(pos, targets.get(pos)); enqueue(pos);
        }
    }
    public String blockedReason() { return reasons.keySet().stream().findFirst().orElse(scanning() ? "scanning" : "waiting"); }
    public Progress progress() { return new Progress(completed, remaining, blocked, active); }
}
