package org.academy.internal.server.misaka;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Misaka compute settlement for unit tests and the server tick pipeline.
 * All inputs/outputs use MSk until {@code cpPerMsk} conversion at CP sinks.
 */
public final class MisakaComputeSettle {
    private MisakaComputeSettle() {
    }

    public record ClosestBucket(String networkKey, String closestName, float groupMskTick) {
    }

    public record NetworkPoolInput(
            String networkKey,
            float poolSeedMskTick,
            int[] percents,
            @Nullable String reconstructionPrivilegeName
    ) {
    }

    public record PlayerCpState(float available, float max) {
    }

    public record Result(
            Map<String, Float> personalCpByName,
            Map<String, Float> networkCpByName,
            Map<String, Float> poolMskByNetwork
    ) {
    }

    public static Result settle(
            List<ClosestBucket> buckets,
            Map<String, Float> usageMskByName,
            List<NetworkPoolInput> networks,
            Map<String, Boolean> onlineByName,
            float cpPerMsk
    ) {
        var remainingUsage = new HashMap<String, Float>();
        if (usageMskByName != null) {
            usageMskByName.forEach((name, value) -> {
                if (name != null && value != null && value > 0.0f) {
                    remainingUsage.put(name, value);
                }
            });
        }

        var poolByNetwork = new HashMap<String, Float>();
        if (networks != null) {
            for (var network : networks) {
                if (network != null && network.networkKey != null) {
                    poolByNetwork.put(network.networkKey, Math.max(0.0f, network.poolSeedMskTick));
                }
            }
        }

        var personalCp = new HashMap<String, Float>();
        var orderedBuckets = new ArrayList<>(buckets == null ? List.<ClosestBucket>of() : buckets);
        orderedBuckets.sort(Comparator
                .comparing(ClosestBucket::networkKey, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ClosestBucket::closestName, Comparator.nullsFirst(String::compareTo)));

        for (var bucket : orderedBuckets) {
            if (bucket == null || bucket.networkKey == null) {
                continue;
            }
            float group = Math.max(0.0f, bucket.groupMskTick);
            String closest = bucket.closestName == null ? MisakaComputeIndex.UNASSIGNED : bucket.closestName;
            boolean online = closest.isEmpty()
                    ? false
                    : Boolean.TRUE.equals(onlineByName == null ? null : onlineByName.get(closest));
            if (!online || closest.isEmpty()) {
                poolByNetwork.merge(bucket.networkKey, group, Float::sum);
                continue;
            }
            float remaining = remainingUsage.getOrDefault(closest, 0.0f);
            float take = Math.min(0.75f * group, remaining);
            if (take > 0.0f) {
                remainingUsage.put(closest, remaining - take);
                personalCp.merge(closest, take * cpPerMsk, Float::sum);
            }
            poolByNetwork.merge(bucket.networkKey, group - take, Float::sum);
        }

        var networkCp = new HashMap<String, Float>();
        if (networks != null) {
            var orderedNetworks = new ArrayList<>(networks);
            orderedNetworks.sort(Comparator.comparing(
                    input -> input == null ? null : input.networkKey,
                    Comparator.nullsFirst(String::compareTo)
            ));
            for (var network : orderedNetworks) {
                if (network == null || network.networkKey == null) {
                    continue;
                }
                float pool = poolByNetwork.getOrDefault(network.networkKey, 0.0f);
                poolByNetwork.put(network.networkKey, pool);
                String recon = network.reconstructionPrivilegeName;
                if (recon == null || recon.isEmpty()) {
                    continue;
                }
                boolean online = Boolean.TRUE.equals(onlineByName == null ? null : onlineByName.get(recon));
                if (!online) {
                    continue;
                }
                int[] percents = MisakaComputeSink.clampAllocations(network.percents);
                float cpShare = pool * (percents[MisakaComputeSink.CP.id()] / 100.0f) * cpPerMsk;
                if (cpShare > 0.0f) {
                    networkCp.merge(recon, cpShare, Float::sum);
                }
                // ITERATION / DAMAGE / DISASSEMBLE: intentionally no-op this release.
            }
        }

        return new Result(Map.copyOf(personalCp), Map.copyOf(networkCp), Map.copyOf(poolByNetwork));
    }

    /** Apply personal + network CP deltas, clamping each player to max. */
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
        mergeCp(after, before, result == null ? null : result.networkCpByName());
        return Map.copyOf(after);
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

    public static float cpToUsageMsk(float cpAmount, float cpPerMsk) {
        if (!(cpAmount > 0.0f) || !(cpPerMsk > 0.0f)) {
            return 0.0f;
        }
        return cpAmount / cpPerMsk;
    }
}
