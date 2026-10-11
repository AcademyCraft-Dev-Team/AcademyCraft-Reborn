package org.academy.internal.server.misaka;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure window arithmetic for ACTUAL multi-caller flush and FORCE end-of-window delivery.
 * Kept free of Minecraft types (except Identifier) for unit tests.
 */
public final class MisakaComputeWindowMath {
    private MisakaComputeWindowMath() {
    }

    public record Call(Identifier id, float want, int weight) {
    }

    public record Grant(Identifier id, float amount) {
    }

    /**
     * Single caller may take the whole remaining pool; multiple callers split by weight
     * (pass1 soft caps, pass2 fill remaining want).
     */
    public static List<Grant> flushActualCalls(float remaining, List<Call> calls) {
        var out = new ArrayList<Grant>();
        if (calls == null || calls.isEmpty() || !(remaining > 0.0f)) {
            return out;
        }
        if (calls.size() == 1) {
            var call = calls.getFirst();
            float grant = Math.min(Math.max(0.0f, call.want()), remaining);
            if (grant > 0.0f) {
                out.add(new Grant(call.id(), grant));
            }
            return out;
        }
        float weightSum = 0.0f;
        for (var call : calls) {
            weightSum += Math.max(0, call.weight());
        }
        if (!(weightSum > 0.0f)) {
            return out;
        }
        float[] grant = new float[calls.size()];
        float rem = remaining;
        // Pass 1: soft caps by weight
        for (int i = 0; i < calls.size(); i++) {
            var call = calls.get(i);
            float soft = remaining * (Math.max(0, call.weight()) / weightSum);
            float g = Math.min(Math.max(0.0f, call.want()), Math.min(soft, rem));
            grant[i] = g;
            rem -= g;
        }
        // Pass 2: fill leftover among callers still wanting
        float weightLeft = 0.0f;
        for (int i = 0; i < calls.size(); i++) {
            float left = Math.max(0.0f, calls.get(i).want() - grant[i]);
            if (left > 1.0e-7f) {
                weightLeft += Math.max(0, calls.get(i).weight());
            }
        }
        if (rem > 1.0e-7f && weightLeft > 0.0f) {
            float rem2 = rem;
            float pool2 = rem;
            for (int i = 0; i < calls.size(); i++) {
                float left = Math.max(0.0f, calls.get(i).want() - grant[i]);
                if (!(left > 1.0e-7f)) {
                    continue;
                }
                float soft = pool2 * (Math.max(0, calls.get(i).weight()) / weightLeft);
                float extra = Math.min(left, Math.min(soft, rem2));
                grant[i] += extra;
                rem2 -= extra;
            }
            rem = rem2;
        }
        for (int i = 0; i < calls.size(); i++) {
            if (grant[i] > 1.0e-7f) {
                out.add(new Grant(calls.get(i).id(), grant[i]));
            }
        }
        return out;
    }

    public static float totalGranted(List<Grant> grants) {
        float sum = 0.0f;
        if (grants == null) {
            return 0.0f;
        }
        for (var g : grants) {
            sum += Math.max(0.0f, g.amount());
        }
        return sum;
    }

    /**
     * FORCE devices receive reserved share + leftover ACTUAL pool split by weight.
     * Example: ACTUAL used 10 of 40 → leftover 30; FORCE 60 → onCompute(60+30)=90.
     */
    public static float forceDelivery(float reserved, float leftover, int selfWeight, int forceWeightSum) {
        float extra = forceWeightSum <= 0
                ? 0.0f
                : leftover * (Math.max(0, selfWeight) / (float) forceWeightSum);
        return Math.max(0.0f, reserved) + Math.max(0.0f, extra);
    }
}
