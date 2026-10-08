package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;

/** Public local skeleton sampler for paired feather arrays, independent of skills and entities. */
public final class FeatherArrayGeometry {
    private FeatherArrayGeometry() { }

    /** Samples a feather root and a longitudinal vector whose magnitude is its material length. */
    public static void sample(int pair, int pairs, int side, int ordinal, int count, boolean covert,
                              float span, float rootHeight, float rootDepth, float spread, float time,
                              float flexibility, float attack, Vector3f root, Vector3f longitudinal,
                              Vector3f normal) {
        samplePose(pair, pairs, side, ordinal, count, covert, span, rootHeight, rootDepth, spread,
                time, flexibility, attack, 0, true, root, longitudinal, normal);
    }

    private static void samplePose(int pair, int pairs, int side, int ordinal, int count, boolean covert,
                                   float span, float rootHeight, float rootDepth, float spread, float time,
                                   float flexibility, float attack, float grounded, boolean legacyFlight, Vector3f root,
                                   Vector3f longitudinal, Vector3f normal) {
        float t = count <= 1 ? 0.5f : ordinal / (float) (count - 1);
        float pairPosition = pairs <= 1 ? 0 : 1 - pair * 2f / (pairs - 1);
        float flyingSlope = pairPosition * 0.67f + (legacyFlight ? 0 : 0.045f * (1 - pairPosition * pairPosition));
        // At rest complete wing axes are approximately +16/+3/-10 degrees; the upper pair
        // lowers and the middle pair rises slightly while the lower vanes still clear the feet.
        float standingSlope = 0.16f + pairPosition * 0.28f - pairPosition * pairPosition * 0.01f;
        float slope = flyingSlope + (standingSlope - flyingSlope) * Math.clamp(grounded, 0, 1);
        float lateral = span * (1 - Math.abs(pairPosition) * 0.13f);
        float opening = Math.clamp(spread, 0, 1) * (1 - Math.clamp(attack, 0, 1) * 0.52f);
        float flutter = (float) Math.sin(time * 2.2f + pair * 1.2f + t * 0.9f) * flexibility;
        float u = 0.015f + t * (covert ? 0.78f : 0.88f);
        root.set(side * (0.17f + lateral * u * opening),
                rootHeight + pairPosition * 0.24f + slope * lateral * u * opening - (1 - opening) * u * 0.65f,
                rootDepth - u * 0.3f + flutter * u + (covert ? -0.035f : 0));
        float length = lateral * (covert ? 0.20f + t * 0.08f : 0.30f + t * 0.20f);
        longitudinal.set(side * (0.25f + t * 0.5f) * opening,
                (slope * 0.65f - 0.35f + t * 0.14f) * opening - (1 - opening) * 0.85f,
                -0.12f + flutter * 0.12f).normalize().mul(length);
        normal.set(-longitudinal.z * side * 0.05f, 0, 1).normalize();
    }

    /**
     * Samples an entity-neutral stance. Heights use the anchor's local +Y, with zero at its feet.
     * Grounded blends from the unrestricted flying pose (0) to complete ground clearance (1).
     */
    public static void sample(int pair, int pairs, int side, int ordinal, int count, boolean covert,
                              float span, float rootHeight, float rootDepth, float spread, float time,
                              float flexibility, float attack, float grounded, float minimumHeight,
                              float widthRatio, float roll, float edgeActivity, Vector3f root,
                              Vector3f longitudinal, Vector3f normal) {
        samplePose(pair, pairs, side, ordinal, count, covert, span, rootHeight, rootDepth, spread, time,
                flexibility, attack, grounded, false, root, longitudinal, normal);
        float pairPosition = pairs <= 1 ? 0 : 1 - pair * 2f / (pairs - 1);
        constrainMinimumHeight(root, longitudinal, normal, rootHeight + pairPosition * 0.24f,
                grounded, minimumHeight, widthRatio, roll, edgeActivity);
    }

    /** Compresses downward extension around each pair's existing body attachment. */
    public static void constrainMinimumHeight(Vector3f root, Vector3f longitudinal, Vector3f normal,
                                              float attachmentHeight, float grounded, float minimumHeight,
                                              float widthRatio, float roll, float edgeActivity) {
        grounded = Math.clamp(grounded, 0, 1);
        if (grounded == 0) return;
        float minimum = minimumSurfaceHeight(root, longitudinal, normal, widthRatio, roll, edgeActivity);
        if (minimum >= minimumHeight) return;
        float originalRoot = root.y, originalAxis = longitudinal.y;
        float pivot = Math.max(attachmentHeight, minimumHeight + 0.001f);
        // Surface width and curvature also change when the spine is compressed. Recompute the
        // analytic bound instead of clamping only the root and tip or lifting all three pairs.
        for (int iteration = 0; iteration < 8 && minimum < minimumHeight - 0.00001f; iteration++) {
            float scale = Math.clamp((pivot - minimumHeight) / Math.max(0.00001f, pivot - minimum), 0, 1);
            root.y = pivot + (root.y - pivot) * scale;
            longitudinal.y *= scale;
            minimum = minimumSurfaceHeight(root, longitudinal, normal, widthRatio, roll, edgeActivity);
        }
        root.y += Math.max(0, minimumHeight - minimum);
        root.y = originalRoot + (root.y - originalRoot) * grounded;
        longitudinal.y = originalAxis + (longitudinal.y - originalAxis) * grounded;
    }

    /** Exact vertical bound of the curved feather surface used by vfxgraph_feather_surface.vsh. */
    public static float minimumSurfaceHeight(Vector3f root, Vector3f longitudinal, Vector3f suggestedNormal,
                                             float widthRatio, float roll, float edgeActivity) {
        float length = Math.max(0.00001f, longitudinal.length());
        float ax = longitudinal.x / length, ay = longitudinal.y / length, az = longitudinal.z / length;
        float sx = ay * suggestedNormal.z - az * suggestedNormal.y;
        float sy = az * suggestedNormal.x - ax * suggestedNormal.z;
        float sz = ax * suggestedNormal.y - ay * suggestedNormal.x;
        float sideLength = Math.max(0.00001f, (float) Math.sqrt(sx * sx + sy * sy + sz * sz));
        sx /= sideLength; sy /= sideLength; sz /= sideLength;
        float ny = sz * ax - sx * az;
        float rightY = sy * (float) Math.cos(roll) + ny * (float) Math.sin(roll);
        float crossLinear = rightY * length * widthRatio * 0.5f;
        float crossQuadratic = ny * length * 0.018f;
        float crossMinimum = crossQuadratic - Math.abs(crossLinear);
        if (crossQuadratic > 0) {
            float u = Math.clamp(-crossLinear / (2 * crossQuadratic), -1, 1);
            crossMinimum = Math.min(crossMinimum, crossLinear * u + crossQuadratic * u * u);
        }
        float camber = ny * length * (0.055f + Math.clamp(edgeActivity, 0, 1) * 0.025f);
        float alongMinimum = Math.min(0, longitudinal.y);
        if (Math.abs(camber) > 0.000001f) {
            float cosine = -longitudinal.y / (camber * (float) Math.PI);
            if (Math.abs(cosine) < 1) {
                float v = (float) (Math.acos(cosine) / Math.PI);
                alongMinimum = Math.min(alongMinimum,
                        longitudinal.y * v + camber * (float) Math.sin(v * Math.PI));
            }
        }
        return root.y + alongMinimum + crossMinimum;
    }

    /** CPU counterpart of the GPU surface, useful for picking, bounds, and geometry validation. */
    public static void sampleSurface(Vector3f root, Vector3f longitudinal, Vector3f suggestedNormal,
                                     float widthRatio, float roll, float edgeActivity,
                                     float u, float v, Vector3f position) {
        float length = Math.max(0.00001f, longitudinal.length());
        var axis = new Vector3f(longitudinal).div(length);
        var side = axis.cross(suggestedNormal, new Vector3f()).normalize();
        var normal = side.cross(axis, new Vector3f()).normalize();
        var right = new Vector3f(side).mul((float) Math.cos(roll)).fma((float) Math.sin(roll), normal);
        float camber = (float) Math.sin(v * Math.PI) * (0.055f + Math.clamp(edgeActivity, 0, 1) * 0.025f);
        position.set(root).fma(v, longitudinal).fma(u * widthRatio * length * 0.5f, right)
                .fma(length * (camber + u * u * 0.018f), normal);
    }

    /** Conservative local bound for the fully spread skeleton and long primary feathers. */
    public static float boundsRadius(float span, float rootHeight, float rootDepth) {
        return Math.max(0.1f, Math.abs(span)) * 1.75f + Math.abs(rootHeight) + Math.abs(rootDepth) + 0.5f;
    }
}
