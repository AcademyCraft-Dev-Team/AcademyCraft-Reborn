package org.academy.internal.client.render.vfx;

import net.minecraft.util.Mth;
import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;

import java.util.ArrayList;
import java.util.List;

public final class CameraShakeManager {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final List<Shake> SHAKES = new ArrayList<>();

    private CameraShakeManager() {
    }

    public static synchronized void add(
            SkyStrikeProfile profile,
            long seed,
            float distanceAttenuation,
            float settingIntensity
    ) {
        add(profile, seed, distanceAttenuation, settingIntensity, System.nanoTime());
    }

    static synchronized void add(
            SkyStrikeProfile profile,
            long seed,
            float distanceAttenuation,
            float settingIntensity,
            long now
    ) {
        var amplitude = profile.shakeDegrees()
                * clamp01(distanceAttenuation)
                * clamp01(settingIntensity);
        if (amplitude <= 0.0001f) return;
        SHAKES.add(new Shake(
                now,
                now + (long) (profile.shakeDurationTicks() * NANOS_PER_TICK),
                seed,
                amplitude,
                profile.shakeCapDegrees()
        ));
    }

    public static synchronized Offset sample() {
        return sample(System.nanoTime());
    }

    static synchronized Offset sample(long now) {
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

    public static synchronized void clear() {
        SHAKES.clear();
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0.0f, 1.0f) : 0.0f;
    }

    public record Offset(float yaw, float pitch) {
        public boolean isZero() {
            return Math.abs(yaw) <= 1.0E-5f && Math.abs(pitch) <= 1.0E-5f;
        }
    }

    private record Shake(long startNanos, long endNanos, long seed, float amplitude, float cap) {
    }
}
