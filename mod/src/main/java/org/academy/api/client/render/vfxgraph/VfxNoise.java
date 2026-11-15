package org.academy.api.client.render.vfxgraph;

public final class VfxNoise {
    private VfxNoise() {
    }

    public static float smooth(float value) {
        var u = clamp(value);
        return u * u * (3f - 2f * u);
    }

    public static float clamp(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : 0f;
    }

    public static float unit(long seed, int index) {
        var x = seed + index * 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 40) / 16777216f;
    }
}
