package org.academy.internal.client.render.vfxgraph;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class CameraShakeManager {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final List<Active> SHAKES = new ArrayList<>();

    private CameraShakeManager() {
    }

    public static void add(Shake shake) {
        add(shake, System.nanoTime());
    }

    public static void add(Shake shake, long now) {
        if (!(shake.amplitudeDegrees() > 0.0001f) || shake.durationTicks() <= 0.0f) return;
        SHAKES.add(new Active(
                now,
                now + (long) (shake.durationTicks() * NANOS_PER_TICK),
                shake.seed(),
                shake.amplitudeDegrees(),
                shake.capDegrees()
        ));
    }

    public static Offset sample() {
        return sample(System.nanoTime());
    }

    public static Offset sample(long now) {
        SHAKES.removeIf(shake -> shake.endNanos <= now);
        var yaw = 0.0f;
        var pitch = 0.0f;
        var cap = 0.0f;
        for (var shake : SHAKES) {
            var duration = Math.max(1L, shake.endNanos - shake.startNanos);
            var progress = Mth.clamp((now - shake.startNanos) / (double) duration, 0.0, 1.0);
            var envelope = (float) ((1.0 - progress) * (1.0 - progress));
            var ticks = (now - shake.startNanos) / (double) NANOS_PER_TICK;
            var phase = (shake.seed & 0xffffL) * 0.0017 + ticks * 2.35;
            yaw += Mth.sin(phase) * shake.amplitude * envelope;
            pitch += Mth.cos(phase * 1.31 + 0.73) * shake.amplitude * 0.72f * envelope;
            cap = Math.max(cap, shake.cap);
        }
        return new Offset(Mth.clamp(yaw, -cap, cap), Mth.clamp(pitch, -cap, cap));
    }

    public static void clear() {
        SHAKES.clear();
    }

    public static float clampUnit(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0.0f, 1.0f) : 0.0f;
    }

    public record Shake(
            long seed,
            float amplitudeDegrees,
            float durationTicks,
            float capDegrees
    ) {
    }

    public record Offset(float yaw, float pitch) {
        public boolean isZero() {
            return Math.abs(yaw) <= 1.0E-5f && Math.abs(pitch) <= 1.0E-5f;
        }
    }

    private record Active(long startNanos, long endNanos, long seed, float amplitude, float cap) {
    }
}