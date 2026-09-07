package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Reusable vortex deformation around an animated spine with a travelling whip bend. +Y is up, +Z is forward.
 * Configure once per emitter branch, then reuse for core, filaments, highlights and shreds.
 * No camera/player dependency: target coordinates are in the emitter's local space.
 */
public final class VortexAttackGeometry {
    private static final int WHIP_SEGMENTS = 96;
    private final float[] whipX = new float[WHIP_SEGMENTS + 1];
    private final float[] whipY = new float[WHIP_SEGMENTS + 1];
    private final float[] whipZ = new float[WHIP_SEGMENTS + 1];
    private final Vector3f idle = new Vector3f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f swingNormal = new Vector3f();
    private final Vector3f before = new Vector3f();
    private final Vector3f after = new Vector3f();
    private final Vector3f tangent = new Vector3f();
    private final Vector3f newTangent = new Vector3f();
    private static final class SampleFrame {
        final Vector3f idle = new Vector3f();
        final Vector3f animated = new Vector3f();
        final Quaternionf transport = new Quaternionf();
        float width;
    }
    private final SampleFrame[] grid = new SampleFrame[193];
    private final SampleFrame offGrid = new SampleFrame();
    private int gridSegments;
    private float offGridU = Float.NaN;
    private int mode, branch;
    private float progress, activity, strike;
    private float targetX, targetY, targetZ;
    private float length, rise, back, radius, turns, speed, time;

    public void configure(int mode, float progress, int branch,
                          float targetX, float targetY, float targetZ,
                          float length, float rise, float back, float radius,
                          float turns, float speed, float time) {
        gridSegments = 0;
        offGridU = Float.NaN;
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
        float delay = mode == 3 ? branch * 0.045f : 0f;
        // Overlap windup with the start of the stroke; start recovery as soon as contact is reached.
        float recoveryStart = mode == 3 ? 0.745f : mode == 2 ? 0.70f : 0.71f;
        activity = mode == 0 ? 0f : ease(0f, 0.20f + delay, this.progress)
                * (1f - ease(recoveryStart, 1f, this.progress));
        strike = ease(0.18f + delay, 0.70f + delay, this.progress);
        if (isWhip() && activity > 0f) buildWhip();
    }

    public float activity() { return activity; }

    public float widthScale(float u) {
        float attackWidth = switch (mode) {
            case 2 -> (1.35f - 0.80f * strike) * (1f - 0.94f * strike * smooth(0.66f, 1f, u));
            case 3 -> 0.70f;
            default -> (1f - 0.35f * strike) * (1f - 0.72f * smooth(0.68f, 1f, u));
        };
        return 1f + (attackWidth - 1f) * activity;
    }

    /** Share exact centerline frames across all strands. Off-grid highlights retain exact sampling. */
    public void prepareGrid(int segments) {
        gridSegments = Math.clamp(segments, 1, grid.length - 1);
        if (activity <= 0f) return;
        for (int i = 0; i <= gridSegments; i++) {
            if (grid[i] == null) grid[i] = new SampleFrame();
            buildFrame(i / (float) gridSegments, grid[i]);
        }
    }

    private void buildFrame(float u, SampleFrame frame) {
        VortexJetGeometry.spine(u, time, length, rise, back, radius, frame.idle);
        float a = Math.max(0f, u - 0.003f);
        float b = Math.min(1f, u + 0.003f);
        VortexJetGeometry.spine(a, time, length, rise, back, radius, before);
        VortexJetGeometry.spine(b, time, length, rise, back, radius, after);
        after.sub(before, tangent).normalize();
        animatedSpine(a, before);
        animatedSpine(b, after);
        after.sub(before, newTangent).normalize();
        frame.transport.identity().rotationTo(tangent, newTangent);
        animatedSpine(u, frame.animated);
        frame.width = widthScale(u);
    }

    public Vector3f sample(float u, float orbit, float phase, Vector3f destination) {
        VortexJetGeometry.sample(u, time, phase, orbit,
                length, rise, back, radius, turns, speed, destination);
        if (activity <= 0f) return destination;
        int index = Math.round(u * gridSegments);
        SampleFrame frame;
        if (gridSegments > 0 && index >= 0 && index <= gridSegments && u == index / (float) gridSegments) {
            frame = grid[index];
        } else {
            if (u != offGridU) { buildFrame(u, offGrid); offGridU = u; }
            frame = offGrid;
        }
        destination.sub(frame.idle);
        frame.transport.transform(destination);
        return destination.mul(frame.width).add(frame.animated);
    }

    /** Includes smooth return to the exact idle spine; endpoints never detach from the nozzle. */
    private Vector3f animatedSpine(float u, Vector3f out) {
        if (isWhip()) {
            float index = Math.clamp(u, 0f, 1f) * WHIP_SEGMENTS;
            int a = Math.min((int) index, WHIP_SEGMENTS - 1);
            float t = index - a;
            return blendIdle(u, mix(whipX[a], whipX[a + 1], t),
                    mix(whipY[a], whipY[a + 1], t), mix(whipZ[a], whipZ[a + 1], t), out);
        }
        float c1x, c1y, c1z, c2x, c2y, c2z, ex, ey, ez;
        if (mode == 2) {
            c1x = 1.0f;
            c1y = 0.65f;
            c1z = -1.4f * (1f - strike) + targetZ * 0.18f * strike;
            c2x = mix(2.1f, targetX * 0.7f + 0.5f, strike);
            c2y = mix(1.6f, targetY * 0.65f, strike);
            c2z = mix(-1.8f, targetZ * 0.70f, strike);
            ex = mix(1.25f, targetX, strike);
            ey = mix(0.8f, targetY, strike);
            ez = mix(-0.45f, targetZ, strike);
        } else {
            float high = 12f + branch * 1.2f;
            float fore = branch == 0 ? 1f : -1f;
            c1x = 1.6f + branch;
            c1y = high * 0.48f;
            c1z = fore * 1.2f;
            c2x = mix(3f + branch, targetX * 0.85f, strike);
            c2y = high + Math.max(0f, targetY) * strike;
            c2z = mix(fore * 3f, targetZ * 0.85f, strike);
            ex = mix(2f + branch * 2.5f, targetX, strike);
            ey = mix(high, targetY, strike);
            ez = mix(fore * 3f, targetZ, strike);
        }
        float v = 1f - u;
        float w1 = 3f * v * v * u;
        float w2 = 3f * v * u * u;
        float w3 = u * u * u;
        float coil = (mode == 2 ? 0.55f : 0.80f) * (1f - strike * 0.75f)
                * (float) Math.sin(Math.PI * u);
        float angle = u * (mode == 2 ? 15f : 9f) - progress * 12f + branch * 2.2f;
        return blendIdle(u, w1 * c1x + w2 * c2x + w3 * ex + coil * (float) Math.cos(angle),
                w1 * c1y + w2 * c2y + w3 * ey + coil * (float) Math.sin(angle),
                w1 * c1z + w2 * c2z + w3 * ez, out);
    }

    private boolean isWhip() { return mode == 1 || mode == 4 || mode == 5; }

    /**
     * Integrate a chain of tangents once per frame. The shoulder leads the stroke by 0.24 of
     * the action, so the bend travels down the ink stream and the tip follows with a snap.
     * Rotating every segment through the swing plane avoids a stationary arch with a falling tip.
     */
    private void buildWhip() {
        direction.set(targetX, targetY, targetZ);
        float reach = direction.length();
        if (reach < 0.001f) direction.set(0f, 0f, 1f);
        else direction.div(reach);
        if (mode == 1) {
            swingNormal.set(0f, 1f, 0f).fma(-direction.y, direction);
            if (swingNormal.lengthSquared() < 0.001f) swingNormal.set(0f, 0f, -1f);
        } else {
            swingNormal.set(direction.z, 0f, -direction.x);
            if (swingNormal.lengthSquared() < 0.001f) swingNormal.set(1f, 0f, 0f);
        }
        swingNormal.normalize();
        float extension = ease(0.18f, 0.71f, progress);
        float segmentLength = mix(11f, Math.max(0.25f, reach), extension) / WHIP_SEGMENTS;
        whipX[0] = whipY[0] = whipZ[0] = 0f;
        for (int i = 1; i <= WHIP_SEGMENTS; i++) {
            float u = (i - 0.5f) / WHIP_SEGMENTS;
            float localStrike = ease(0.14f + 0.24f * u, 0.47f + 0.24f * u, progress);
            float curl = 0.85f + 1.50f * u + 0.5f * (float) Math.sin(u * Math.PI * 2);
            float followThrough = -0.22f * ease(0.71f + 0.02f * u, 0.90f + 0.02f * u, progress);
            float angle = curl * (1f - localStrike) + followThrough;
            float along = (float) Math.cos(angle) * segmentLength;
            float across = (float) Math.sin(angle) * segmentLength;
            whipX[i] = whipX[i - 1] + direction.x * along + swingNormal.x * across;
            whipY[i] = whipY[i - 1] + direction.y * along + swingNormal.y * across;
            whipZ[i] = whipZ[i - 1] + direction.z * along + swingNormal.z * across;
        }
        if (mode == 1) {
            for (int i = 1; i <= WHIP_SEGMENTS; i++) {
                float u = i / (float) WHIP_SEGMENTS;
                // Separate the paired overhead lashes while their tips converge on the same target.
                whipX[i] += 0.65f * (float) Math.sin(Math.PI * u)
                        + 2.3f * (1f - extension) * (float) Math.sin(Math.PI * u * 0.5f);
            }
        }
    }

    private Vector3f blendIdle(float u, float x, float y, float z, Vector3f out) {
        VortexJetGeometry.sample(u, time, 0f, 0f,
                length, rise, back, radius, turns, speed, out);
        return out.set(mix(out.x, x, activity), mix(out.y, y, activity), mix(out.z, z, activity));
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    /** Zero velocity and acceleration at phase boundaries, without inserting a held pose. */
    private static float ease(float a, float b, float t) {
        float u = Math.clamp((t - a) / (b - a), 0f, 1f);
        return u * u * u * (u * (u * 6f - 15f) + 10f);
    }

    private static float smooth(float a, float b, float t) {
        float u = Math.clamp((t - a) / (b - a), 0f, 1f);
        return u * u * (3f - 2f * u);
    }
}
