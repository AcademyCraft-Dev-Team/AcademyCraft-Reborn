package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;

/** Seeded discharge in emitter space (+Y towards the cloud), reusable by arbitrary entity emitters. */
public final class SkyDischargeGeometry {
    private SkyDischargeGeometry() {
    }

    /** Pinned endpoints; coarse angular bends and fine stepped leaders in both horizontal axes. */
    public static Vector3f sample(float u, float time, long seed, float height, float spread,
                                  Vector3f destination) {
        return sample(u, time, seed, height, spread, 7f, 1f, destination);
    }

    /** Reform large bends as well as fine leaders; endpoints remain fixed while the channel twists. */
    public static Vector3f sample(float u, float time, long seed, float height, float spread,
                                  float twistRate, float twistStrength, Vector3f destination) {
        u = clamp(u);
        if (u == 0f || u == 1f) return destination.set(0f, height * u, 0f);
        float t = Float.isFinite(time) ? Math.max(0f, time) : 0f;
        float pin = (float) Math.sin(Math.PI * u);
        float phase = t * Math.max(0f, twistRate);
        int shape = (int) Math.floor(phase);
        float blend = smooth((phase - shape) / 0.72f);
        float strength = clamp(twistStrength);
        float x = noise(u * 7f, seed) * (1f - strength)
                + morph(u * 7f, seed, shape, blend) * strength;
        float z = noise(u * 8f, seed + 37) * (1f - strength)
                + morph(u * 8f, seed + 37, shape, blend) * strength;
        float turn = t * 1.8f + u * 8f;
        x = x * 0.86f + (float) Math.sin(turn) * strength * 0.22f;
        z = z * 0.80f + (float) Math.cos(turn) * strength * 0.22f;
        int frame = (int) Math.floor(t * 24f);
        x += noise(u * 29f, seed + 91) * 0.17f + noise(u * 43f, seed + frame * 17L) * 0.13f;
        z += noise(u * 31f, seed + 153) * 0.17f + noise(u * 47f, seed + frame * 31L + 19) * 0.13f;
        return destination.set(x * spread * pin, height * u, z * spread * pin);
    }

    private static float morph(float position, long seed, int shape, float blend) {
        return noise(position, seed + shape * 401L) * (1f - blend)
                + noise(position, seed + (shape + 1L) * 401L) * blend;
    }

    /** Local impact pulse for width, cloud illumination and ground bloom, without a screen flash. */
    public static float impactPulse(float time) {
        return pulse(time, 0.065f, 0.16f) + pulse(time, 0.26f, 0.09f) * 0.45f;
    }

    private static float pulse(float time, float center, float width) {
        float distance = Math.abs(time - center) / width;
        float amplitude = clamp(1f - distance);
        return amplitude * amplitude;
    }

    /** Sharp attack, sustained irregular pulses, then soft extinction. */
    public static float current(float time, float sustain, float decay) {
        if (!Float.isFinite(time) || time < 0f) return 0f;
        float tail = 1f - smooth((time - Math.max(0f, sustain)) / Math.max(0.01f, decay));
        float beat = 0.82f + 0.12f * (float) Math.sin(time * 73f)
                + 0.06f * (float) Math.sin(time * 137f + 0.8f);
        return clamp(tail * beat);
    }

    public static float smooth(float value) {
        float u = clamp(value);
        return u * u * (3f - 2f * u);
    }

    public static float clamp(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : 0f;
    }

    /** Stable [0,1) value without consuming another emitter's random stream. */
    public static float unit(long seed, int index) {
        long x = seed + index * 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 40) / 16777216f;
    }

    private static float noise(float position, long seed) {
        int i = (int) Math.floor(position);
        float fraction = position - i;
        return (unit(seed, i) * (1f - fraction) + unit(seed, i + 1) * fraction) * 2f - 1f;
    }
}
