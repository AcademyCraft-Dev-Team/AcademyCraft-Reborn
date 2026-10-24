package org.academy.internal.server.misaka;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Misaka compute settlement (design §9–10).
 * Two layers: 75% priority Max-Min among benevolent group members, then Shared Pool
 * weighted fair among all online consumers with remaining demand.
 * Inputs/outputs use MSk until {@code cpPerMsk} conversion.
 */
public final class MisakaComputeSettle {
    private static final float EPS = 1.0e-5f;

    private MisakaComputeSettle() {
    }

    /** One benevolent-name set's aggregated MSk for a single settle window. */
    public record GroupBucket(List<String> groupNames, float mskTick) {
        public GroupBucket {
            groupNames = groupNames == null ? List.of() : List.copyOf(groupNames);
            mskTick = Math.max(0.0f, mskTick);
        }
    }

    public record PlayerCpState(float available, float max) {
    }

    public record Result(
            Map<String, Float> personalAllocatedMskByName,
            Map<String, Float> sharedAllocatedMskByName,
            Map<String, Float> satisfactionByName,
            Map<String, Float> personalCpByName,
            Map<String, Float> sharedCpByName,
            Map<String, Float> networkCpByName,
            float poolLeftoverMsk
    ) {
    }

    /**
     * Settle one network's groups against demand/online maps for that network only.
     *
     * @param groups                         benevolent-set buckets (msk in caller units)
     * @param demandMskByName                remaining demand by player name
     * @param onlineByName                   coverage/online for this network
     * @param consumerWeights                shared-pool weights (default 1.0); null → all 1.0
     * @param sinkPercents                   optional CP-sink % of leftover shared pool
     * @param reconstructionPrivilegeName    recipient of leftover CP sink (nullable)
     * @param cpPerMsk                       MSk → CP ratio
     */
    public static Result settle(
            List<GroupBucket> groups,
            Map<String, Float> demandMskByName,
            Map<String, Boolean> onlineByName,
            @Nullable Map<String, Float> consumerWeights,
            @Nullable int[] sinkPercents,
            @Nullable String reconstructionPrivilegeName,
            float cpPerMsk
    ) {
        return settle(
                groups,
                demandMskByName,
                onlineByName,
                consumerWeights,
                sinkPercents,
                reconstructionPrivilegeName,
                cpPerMsk,
                null
        );
    }

    /**
     * @param sharedAccessNames when non-null, Shared Pool consumers must be in this set (ACCESS holders)
     */
    public static Result settle(
            List<GroupBucket> groups,
            Map<String, Float> demandMskByName,
            Map<String, Boolean> onlineByName,
            @Nullable Map<String, Float> consumerWeights,
            @Nullable int[] sinkPercents,
            @Nullable String reconstructionPrivilegeName,
            float cpPerMsk,
            @Nullable Set<String> sharedAccessNames
    ) {
        var remainingDemand = new HashMap<String, Float>();
        if (demandMskByName != null) {
            demandMskByName.forEach((name, value) -> {
                if (name != null && !name.isEmpty() && value != null && value > 0.0f) {
                    remainingDemand.put(name, value);
                }
            });
        }
        var demandSnapshot = Map.copyOf(remainingDemand);

        var personalMsk = new HashMap<String, Float>();
        float sharedPool = 0.0f;

        for (var bucket : sortedGroups(groups)) {
            if (bucket == null) {
                continue;
            }
            float groupMsk = bucket.mskTick();
            if (!(groupMsk > 0.0f)) {
                continue;
            }
            float priorityPool = 0.75f * groupMsk;
            sharedPool += 0.25f * groupMsk;

            var eligible = new ArrayList<String>();
            for (String name : bucket.groupNames()) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (!isOnline(onlineByName, name)) {
                    continue;
                }
                if (remainingDemand.getOrDefault(name, 0.0f) > EPS) {
                    eligible.add(name);
                }
            }
            eligible.sort(String::compareTo);

            var fromPriority = maxMinFairAllocate(priorityPool, eligible, remainingDemand);
            float usedPriority = 0.0f;
            for (var entry : fromPriority.entrySet()) {
                float take = entry.getValue();
                if (!(take > 0.0f)) {
                    continue;
                }
                personalMsk.merge(entry.getKey(), take, Float::sum);
                usedPriority += take;
            }
            sharedPool += Math.max(0.0f, priorityPool - usedPriority);
        }

        var sharedEligible = new ArrayList<String>();
        for (var entry : remainingDemand.entrySet()) {
            String name = entry.getKey();
            if (!(entry.getValue() > EPS) || !isOnline(onlineByName, name)) {
                continue;
            }
            if (sharedAccessNames != null && !sharedAccessNames.contains(name)) {
                continue;
            }
            sharedEligible.add(name);
        }
        sharedEligible.sort(String::compareTo);

        var sharedMsk = weightedFairAllocate(sharedPool, sharedEligible, remainingDemand, consumerWeights);
        float usedShared = 0.0f;
        for (float take : sharedMsk.values()) {
            usedShared += take;
        }
        float leftover = Math.max(0.0f, sharedPool - usedShared);

        var networkCp = new HashMap<String, Float>();
        if (leftover > EPS
                && reconstructionPrivilegeName != null
                && !reconstructionPrivilegeName.isEmpty()
                && isOnline(onlineByName, reconstructionPrivilegeName)) {
            int[] percents = MisakaComputeSink.clampAllocations(sinkPercents);
            float cpShare = leftover * (percents[MisakaComputeSink.CP.id()] / 100.0f) * cpPerMsk;
            if (cpShare > 0.0f) {
                networkCp.put(reconstructionPrivilegeName, cpShare);
            }
            // ITERATION / DAMAGE / DISASSEMBLE: intentionally no-op this release.
        }

        var personalCp = scaleCp(personalMsk, cpPerMsk);
        var sharedCp = scaleCp(sharedMsk, cpPerMsk);
        var satisfaction = new HashMap<String, Float>();
        for (var entry : demandSnapshot.entrySet()) {
            String name = entry.getKey();
            float demand = entry.getValue();
            if (!(demand > 0.0f)) {
                continue;
            }
            float allocated = personalMsk.getOrDefault(name, 0.0f) + sharedMsk.getOrDefault(name, 0.0f);
            satisfaction.put(name, allocated / demand);
        }

        return new Result(
                Map.copyOf(personalMsk),
                Map.copyOf(sharedMsk),
                Map.copyOf(satisfaction),
                Map.copyOf(personalCp),
                Map.copyOf(sharedCp),
                Map.copyOf(networkCp),
                leftover
        );
    }

    /**
     * Settle a network that has no reconstruction work (design §7.1).
     * Without a reconstruction work the network cannot be drawn as one pool, so every
     * benevolent-set bucket settles on its own: its 25% and its unused priority share stay
     * available to that bucket's own members only, and whatever they cannot use goes idle.
     */
    public static Result settleUnintegrated(
            List<GroupBucket> groups,
            Map<String, Float> demandMskByName,
            Map<String, Boolean> onlineByName,
            @Nullable Map<String, Float> consumerWeights,
            float cpPerMsk,
            @Nullable Set<String> sharedAccessNames
    ) {
        var remainingDemand = new HashMap<String, Float>();
        if (demandMskByName != null) {
            demandMskByName.forEach((name, value) -> {
                if (name != null && !name.isEmpty() && value != null && value > 0.0f) {
                    remainingDemand.put(name, value);
                }
            });
        }
        var demandSnapshot = Map.copyOf(remainingDemand);

        var personalMsk = new HashMap<String, Float>();
        var sharedMsk = new HashMap<String, Float>();
        float leftover = 0.0f;

        for (var bucket : sortedGroups(groups)) {
            if (bucket == null || !(bucket.mskTick() > 0.0f)) {
                continue;
            }
            var bucketAccess = new HashSet<String>();
            for (String name : bucket.groupNames()) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (sharedAccessNames == null || sharedAccessNames.contains(name)) {
                    bucketAccess.add(name);
                }
            }
            var partial = settle(
                    List.of(bucket),
                    remainingDemand,
                    onlineByName,
                    consumerWeights,
                    null,
                    null,
                    cpPerMsk,
                    bucketAccess
            );
            drainDemand(remainingDemand, partial.personalAllocatedMskByName(), personalMsk);
            drainDemand(remainingDemand, partial.sharedAllocatedMskByName(), sharedMsk);
            leftover += partial.poolLeftoverMsk();
        }

        var satisfaction = new HashMap<String, Float>();
        for (var entry : demandSnapshot.entrySet()) {
            String name = entry.getKey();
            float demand = entry.getValue();
            if (!(demand > 0.0f)) {
                continue;
            }
            float allocated = personalMsk.getOrDefault(name, 0.0f) + sharedMsk.getOrDefault(name, 0.0f);
            satisfaction.put(name, allocated / demand);
        }

        return new Result(
                Map.copyOf(personalMsk),
                Map.copyOf(sharedMsk),
                Map.copyOf(satisfaction),
                scaleCp(personalMsk, cpPerMsk),
                scaleCp(sharedMsk, cpPerMsk),
                Map.of(),
                leftover
        );
    }

    private static List<GroupBucket> sortedGroups(@Nullable List<GroupBucket> groups) {
        var ordered = new ArrayList<>(groups == null ? List.<GroupBucket>of() : groups);
        ordered.sort(Comparator.comparing(
                bucket -> String.join("\u0001", bucket.groupNames()),
                Comparator.nullsFirst(String::compareTo)
        ));
        return ordered;
    }

    private static void drainDemand(
            Map<String, Float> remainingDemand,
            Map<String, Float> allocated,
            Map<String, Float> sink
    ) {
        for (var entry : allocated.entrySet()) {
            float take = entry.getValue() == null ? 0.0f : entry.getValue();
            if (!(take > 0.0f)) {
                continue;
            }
            sink.merge(entry.getKey(), take, Float::sum);
            float left = remainingDemand.getOrDefault(entry.getKey(), 0.0f) - take;
            remainingDemand.put(entry.getKey(), Math.max(0.0f, left));
        }
    }

    /** Apply personal + shared + network CP deltas, clamping each player to max. */
    public static Map<String, Float> applyCpDeltas(
            Map<String, PlayerCpState> before,
            Result result
    ) {
        var after = new HashMap<String, Float>();
        if (before != null) {
            before.forEach((name, state) -> {
                if (name != null && state != null) {
                    after.put(name, state.available());
                }
            });
        }
        mergeCp(after, before, result == null ? null : result.personalCpByName());
        mergeCp(after, before, result == null ? null : result.sharedCpByName());
        mergeCp(after, before, result == null ? null : result.networkCpByName());
        return Map.copyOf(after);
    }

    public static float cpToUsageMsk(float cpAmount, float cpPerMsk) {
        if (!(cpAmount > 0.0f) || !(cpPerMsk > 0.0f)) {
            return 0.0f;
        }
        return cpAmount / cpPerMsk;
    }

    /**
     * Progressive-filling Max-Min fairness: sort by demand ascending; saturate small demands first,
     * then split the remainder equally among the rest.
     */
    static Map<String, Float> maxMinFairAllocate(
            float pool,
            List<String> eligible,
            Map<String, Float> remainingDemand
    ) {
        var allocated = new HashMap<String, Float>();
        if (!(pool > EPS) || eligible == null || eligible.isEmpty()) {
            return allocated;
        }
        var sorted = new ArrayList<>(eligible);
        sorted.sort(Comparator
                .comparingDouble((String n) -> remainingDemand.getOrDefault(n, 0.0f))
                .thenComparing(n -> n));

        float remaining = pool;
        int i = 0;
        while (i < sorted.size() && remaining > EPS) {
            int nLeft = sorted.size() - i;
            float fair = remaining / nLeft;
            String name = sorted.get(i);
            float demand = remainingDemand.getOrDefault(name, 0.0f);
            if (demand <= fair + EPS) {
                if (demand > EPS) {
                    allocated.merge(name, demand, Float::sum);
                    remainingDemand.put(name, 0.0f);
                    remaining -= demand;
                }
                i++;
            } else {
                for (int j = i; j < sorted.size(); j++) {
                    String nj = sorted.get(j);
                    allocated.merge(nj, fair, Float::sum);
                    remainingDemand.put(nj, remainingDemand.getOrDefault(nj, 0.0f) - fair);
                }
                remaining = 0.0f;
                break;
            }
        }
        return allocated;
    }

    /**
     * Weighted progressive filling among unsaturated consumers (default weight 1.0).
     */
    static Map<String, Float> weightedFairAllocate(
            float pool,
            List<String> eligible,
            Map<String, Float> remainingDemand,
            @Nullable Map<String, Float> consumerWeights
    ) {
        var allocated = new HashMap<String, Float>();
        if (!(pool > EPS) || eligible == null || eligible.isEmpty()) {
            return allocated;
        }
        var active = new ArrayList<>(eligible);
        float remaining = pool;
        while (!active.isEmpty() && remaining > EPS) {
            float totalW = 0.0f;
            for (String name : active) {
                totalW += weightOf(name, consumerWeights);
            }
            if (!(totalW > EPS)) {
                break;
            }
            var saturated = new ArrayList<String>();
            for (String name : active) {
                float offer = remaining * (weightOf(name, consumerWeights) / totalW);
                float demand = remainingDemand.getOrDefault(name, 0.0f);
                if (offer + EPS >= demand) {
                    saturated.add(name);
                }
            }
            if (saturated.isEmpty()) {
                for (String name : active) {
                    float take = remaining * (weightOf(name, consumerWeights) / totalW);
                    if (take > EPS) {
                        allocated.merge(name, take, Float::sum);
                        remainingDemand.put(name, remainingDemand.getOrDefault(name, 0.0f) - take);
                    }
                }
                remaining = 0.0f;
                break;
            }
            saturated.sort(String::compareTo);
            for (String name : saturated) {
                float demand = remainingDemand.getOrDefault(name, 0.0f);
                if (demand > EPS) {
                    allocated.merge(name, demand, Float::sum);
                    remaining -= demand;
                    remainingDemand.put(name, 0.0f);
                }
                active.remove(name);
            }
        }
        return allocated;
    }

    private static float weightOf(String name, @Nullable Map<String, Float> weights) {
        if (weights == null) {
            return 1.0f;
        }
        Float w = weights.get(name);
        if (w == null || !(w > 0.0f)) {
            return 1.0f;
        }
        return w;
    }

    private static boolean isOnline(@Nullable Map<String, Boolean> onlineByName, String name) {
        return Boolean.TRUE.equals(onlineByName == null ? null : onlineByName.get(name));
    }

    private static Map<String, Float> scaleCp(Map<String, Float> mskByName, float cpPerMsk) {
        if (!(cpPerMsk > 0.0f) || mskByName.isEmpty()) {
            return Map.of();
        }
        var cp = new HashMap<String, Float>();
        for (var entry : mskByName.entrySet()) {
            float msk = entry.getValue() == null ? 0.0f : entry.getValue();
            if (msk > 0.0f) {
                cp.put(entry.getKey(), msk * cpPerMsk);
            }
        }
        return cp;
    }

    private static void mergeCp(
            Map<String, Float> available,
            @Nullable Map<String, PlayerCpState> before,
            @Nullable Map<String, Float> deltas
    ) {
        if (deltas == null) {
            return;
        }
        for (var entry : deltas.entrySet()) {
            String name = entry.getKey();
            float delta = entry.getValue() == null ? 0.0f : entry.getValue();
            if (name == null || !(delta > 0.0f)) {
                continue;
            }
            float current = available.getOrDefault(name, 0.0f);
            float max = before != null && before.containsKey(name)
                    ? before.get(name).max()
                    : Float.MAX_VALUE;
            available.put(name, Math.min(max, current + delta));
        }
    }
}
