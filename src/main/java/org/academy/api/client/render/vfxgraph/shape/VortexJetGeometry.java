package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;

/**
 * Continuous, curved vortex jet in emitter space (+Y up, -Z behind the emitter).
 * Independent of players and abilities; callers can attach it to any entity or transform.
 */
public final class VortexJetGeometry {
    private VortexJetGeometry() {
    }

    /** Samples an outward-moving helical filament; radiusFraction zero samples the spine. */
    public static Vector3f sample(float u, float time, float phase, float radiusFraction,
                                  float length, float rise, float back, float radius,
                                  float turns, float speed, Vector3f destination) {
        float envelope = radius(u, radius) * (1f + 0.22f * (float) Math.sin(u * 23f - time * 1.2f));
        float travel = u * 18f - time * speed;
        float angle = u * turns * (float) (Math.PI * 2) - time * speed + phase;
        float turbulence = 1f + 0.12f * (float) Math.sin(travel + phase)
                + 0.07f * (float) Math.sin(u * 43f - time * speed * 1.7f + phase * 3f);
        float orbit = envelope * radiusFraction * turbulence;
        // Approximate orthogonal frame around the oblique spine, rather than horizontal rings.
        float bendPhase = time * 0.55f;
        float bend = 0.48f * u * (float) Math.sin(u * 13f - bendPhase);
        float ty = rise * (0.55f + 0.9f * u)
                + 0.48f * (float) Math.sin(u * 13f - bendPhase)
                + 6.24f * u * (float) Math.cos(u * 13f - bendPhase);
        float tx = length;
        float inverse = 1f / (float) Math.sqrt(tx * tx + ty * ty);
        float nx = -ty * inverse;
        float ny = tx * inverse;
        float curl = (float) Math.cos(angle) * orbit;
        return destination.set(
                length * u + nx * curl,
                rise * (0.55f * u + 0.45f * u * u) + bend + ny * curl
                        + envelope * 0.12f * (float) Math.sin(u * 11f - time * 1.4f),
                -back * (0.7f * u + 0.3f * u * u)
                        + 0.5f * u * (float) Math.sin(u * 10f - bendPhase * 0.8f)
                        + (float) Math.sin(angle) * orbit
                        + envelope * 0.15f * (float) Math.sin(u * 9f - time * 1.1f));
    }

    /** Narrow scapula nozzle expanding into a round storm funnel, never a feathered fan. */
    public static float radius(float u, float radius) {
        return 0.035f + radius * (float) Math.pow(Math.max(0f, u), 0.85f);
    }

    /** Local openings through the dense core; a few outer filaments bridge each opening. */
    public static boolean hollow(float u, float time, int strand, float amount) {
        if (amount <= 0f || (strand > 0 && strand % 4 == 0)) return false;
        float first = 0.34f + 0.035f * (float) Math.sin(time * 0.8f);
        float second = 0.70f + 0.045f * (float) Math.sin(time * 0.63f + 1.7f);
        return Math.abs(u - first) < 0.052f * amount
                || Math.abs(u - second) < 0.062f * amount;
    }
}
