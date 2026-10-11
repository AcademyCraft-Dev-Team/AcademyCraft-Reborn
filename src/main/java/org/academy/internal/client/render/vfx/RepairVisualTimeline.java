package org.academy.internal.client.render.vfx;

import org.academy.api.common.vfx.RepairVisualParts;

import java.util.Arrays;

/** Independent presentation clocks for successful repair sites; pulses extend the bridge, not its growth. */
final class RepairVisualTimeline {
    static final int PATCH_COUNT = 6;
    static final float DURATION_TICKS = 7.6f;
    static final float BRIDGE_TICKS = 5.2f;
    static final float BRIDGE_PROGRESS = BRIDGE_TICKS / DURATION_TICKS;
    static final float REJOIN_TICKS = 1;
    private static final long NEVER = Long.MIN_VALUE / 2;
    private final long[] started = new long[PATCH_COUNT];
    private final long[] refreshed = new long[PATCH_COUNT];
    private final float[] rejoinFrom = new float[PATCH_COUNT];

    RepairVisualTimeline() {
        Arrays.fill(started, NEVER);
        Arrays.fill(refreshed, NEVER);
        Arrays.fill(rejoinFrom, BRIDGE_PROGRESS);
    }

    /** Stable humanoid patch order: torso, head, right arm, left arm, right leg, left leg. */
    static int patchMask(int parts, boolean rightMainArm) {
        int patches = 0;
        if ((parts & (RepairVisualParts.BODY | RepairVisualParts.CHEST)) != 0) patches |= 1;
        if ((parts & RepairVisualParts.HEAD) != 0) patches |= 1 << 1;
        if ((parts & (RepairVisualParts.BODY | (rightMainArm ? RepairVisualParts.MAIN_HAND : RepairVisualParts.OFF_HAND))) != 0) patches |= 1 << 2;
        if ((parts & (RepairVisualParts.BODY | (rightMainArm ? RepairVisualParts.OFF_HAND : RepairVisualParts.MAIN_HAND))) != 0) patches |= 1 << 3;
        if ((parts & (RepairVisualParts.LEGS | RepairVisualParts.FEET)) != 0) patches |= (1 << 4) | (1 << 5);
        return patches;
    }

    void pulse(int patches, long tick) {
        for (int patch = 0; patch < PATCH_COUNT; patch++) {
            if ((patches & (1 << patch)) == 0) continue;
            if (tick < refreshed[patch] || tick - refreshed[patch] >= DURATION_TICKS) {
                started[patch] = tick;
                rejoinFrom[patch] = BRIDGE_PROGRESS;
            } else {
                // Arrival jitter may briefly enter settling. Rejoin the fixed bridge instead of
                // promoting each late tail value to a new plateau and eventually erasing the shape.
                rejoinFrom[patch] = Math.max(BRIDGE_PROGRESS, progress(patch, tick));
            }
            refreshed[patch] = tick;
        }
    }

    float progress(int patch, double tick) {
        if (patch < 0 || patch >= PATCH_COUNT || started[patch] == NEVER) return 1;
        double age = Math.max(0, tick - started[patch]);
        double quiet = Math.max(0, tick - refreshed[patch]);
        double growth = Math.min(BRIDGE_PROGRESS, age / DURATION_TICKS);
        if (age >= BRIDGE_TICKS && quiet < REJOIN_TICKS) {
            double t = Math.clamp(quiet / REJOIN_TICKS, 0, 1);
            double smooth = t * t * (3 - 2 * t);
            growth += (rejoinFrom[patch] - BRIDGE_PROGRESS) * (1 - smooth);
        }
        double settling = Math.max(0, quiet - BRIDGE_TICKS) / (DURATION_TICKS - BRIDGE_TICKS) * (1 - BRIDGE_PROGRESS);
        return (float) Math.clamp(growth + settling, 0, 1);
    }

    boolean active(int patch, double tick) {
        return patch >= 0 && patch < PATCH_COUNT && started[patch] != NEVER
                && tick >= refreshed[patch] && tick - refreshed[patch] < DURATION_TICKS;
    }
}
