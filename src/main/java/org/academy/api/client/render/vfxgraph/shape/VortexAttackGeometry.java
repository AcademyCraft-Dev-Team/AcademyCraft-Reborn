package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Reusable vortex deformation around an animated cubic spine. +Y is up, +Z is forward.
 * Configure once per emitter branch, then reuse for core, filaments, highlights and shreds.
 * No camera/player dependency: target coordinates are in the emitter's local space.
 */
public final class VortexAttackGeometry {
    private final Vector3f idle = new Vector3f();
    private final Vector3f animated = new Vector3f();
    private final Vector3f before = new Vector3f();
    private final Vector3f after = new Vector3f();
    private final Vector3f tangent = new Vector3f();
    private final Vector3f newTangent = new Vector3f();
    private final Quaternionf transport = new Quaternionf();
    private int mode, branch;
    private float progress, activity, strike;
    private float targetX, targetY, targetZ;
    private float length, rise, back, radius, turns, speed, time;

    public void configure(int mode, float progress, int branch,
                          float targetX, float targetY, float targetZ,
                          float length, float rise, float back, float radius,
                          float turns, float speed, float time) {
        this.mode = mode;
        this.progress = Math.clamp(progress, 0f, 1f);
        this.branch = branch;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.length = length;
        this.rise = rise;
        this.back = back;
        this.radius = radius;
        this.turns = turns;
        this.speed = speed;
        this.time = time;
        activity = mode == 0 ? 0f : smooth(0f, 0.24f, this.progress)
                * (1f - smooth(0.74f, 1f, this.progress));
        float delay = mode == 3 ? branch * 0.045f : 0f;
        strike = smooth(0.32f + delay, 0.65f + delay, this.progress);
    }

    public float activity() { return activity; }

    public float widthScale(float u) {
        float attackWidth = switch (mode) {
            case 2 -> (1.35f - 0.80f * strike) * (1f - 0.94f * strike * smooth(0.66f, 1f, u));
            case 3 -> 0.70f;
            default -> 1f - 0.25f * strike;
        };
        return 1f + (attackWidth - 1f) * activity;
    }

    public Vector3f sample(float u, float orbit, float phase, Vector3f destination) {
        VortexJetGeometry.sample(u, time, phase, orbit,
                length, rise, back, radius, turns, speed, destination);
        if (activity <= 0f) return destination;
        VortexJetGeometry.sample(u, time, phase, 0f,
                length, rise, back, radius, turns, speed, idle);
        destination.sub(idle);
        float a = Math.max(0f, u - 0.003f);
        float b = Math.min(1f, u + 0.003f);
        VortexJetGeometry.sample(a, time, phase, 0f,
                length, rise, back, radius, turns, speed, before);
        VortexJetGeometry.sample(b, time, phase, 0f,
                length, rise, back, radius, turns, speed, after);
        after.sub(before, tangent).normalize();
        animatedSpine(a, before);
        animatedSpine(b, after);
        after.sub(before, newTangent).normalize();
        transport.identity().rotationTo(tangent, newTangent);
        transport.transform(destination);
        destination.mul(widthScale(u));
        animatedSpine(u, animated);
        return destination.add(animated);
    }

    /** Includes smooth return to the exact idle spine; endpoints never detach from the nozzle. */
    private Vector3f animatedSpine(float u, Vector3f out) {
        float c1x, c1y, c1z, c2x, c2y, c2z, ex, ey, ez;
        if (mode == 2) {
            c1x = 1.0f;
            c1y = 0.65f;
            c1z = -1.4f * (1f - strike) + targetZ * 0.18f * strike;
            c2x = mix(2.1f, targetX * 0.7f + 0.5f, strike);
            c2y = mix(1.6f, targetY * 0.65f, strike);
            c2z = mix(-1.8f, targetZ * 0.70f, strike);
            ex = mix(1.25f, targetX + 0.30f, strike);
            ey = mix(0.8f, targetY, strike);
            ez = mix(-0.45f, targetZ, strike);
        } else {
            float high = mode == 3 ? 12f + branch * 1.2f : 8.5f;
            float spread = mode == 3 ? 1.2f + branch * 2.3f : 1.1f;
            c1x = mode == 3 ? 1.6f + branch : 1.7f;
            c1y = high * 0.48f;
            c1z = -1.2f;
            c2x = mix(3.0f + branch, targetX * 0.7f + spread, strike);
            c2y = high + Math.max(0f, targetY) * strike;
            c2z = mix(-1.5f + branch, targetZ * 0.85f, strike);
            ex = mix(1.3f + branch * 2.5f, targetX + spread, strike);
            ey = mix(high, targetY, strike);
            ez = mix(0.5f + branch, targetZ * (mode == 3 && branch == 1 ? 0.86f : 1f), strike);
        }
        float v = 1f - u;
        float w1 = 3f * v * v * u;
        float w2 = 3f * v * u * u;
        float w3 = u * u * u;
        float coil = (mode == 2 ? 0.55f : 0.80f) * (1f - strike * 0.75f)
                * (float) Math.sin(Math.PI * u);
        float angle = u * (mode == 2 ? 15f : 9f) - progress * 12f + branch * 2.2f;
        float x = w1 * c1x + w2 * c2x + w3 * ex + coil * (float) Math.cos(angle);
        float y = w1 * c1y + w2 * c2y + w3 * ey + coil * (float) Math.sin(angle);
        float z = w1 * c1z + w2 * c2z + w3 * ez;
        VortexJetGeometry.sample(u, time, 0f, 0f,
                length, rise, back, radius, turns, speed, out);
        return out.set(mix(out.x, x, activity), mix(out.y, y, activity), mix(out.z, z, activity));
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    private static float smooth(float a, float b, float t) {
        float u = Math.clamp((t - a) / (b - a), 0f, 1f);
        return u * u * (3f - 2f * u);
    }
}
