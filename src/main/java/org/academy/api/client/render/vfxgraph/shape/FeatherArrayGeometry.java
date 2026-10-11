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
                time, flexibility, attack, 0, root, longitudinal, normal);
    }

    private static void samplePose(int pair, int pairs, int side, int ordinal, int count, boolean covert,
                                   float span, float rootHeight, float rootDepth, float spread, float time,
                                   float flexibility, float attack, float grounded, Vector3f root,
                                   Vector3f longitudinal, Vector3f normal) {
        float t = count <= 1 ? 0.5f : ordinal / (float) (count - 1);
        float pairPosition = pairs <= 1 ? 0 : 1 - pair * 2f / (pairs - 1);
        // Build the same broad fan in a wing-local frame before rotating each pair. World-Y
        // feather directions flatten the upper pair into a narrow chain of overlapping leaves.
        float flyingAngle = 0.065f + pairPosition * 0.61f;
        // Whole-wing tip directions stay close to the existing +16/+3/-10 degree standing pose.
        float standingAngle = 0.13f + pairPosition * 0.235f;
        float angle = flyingAngle + (standingAngle - flyingAngle) * Math.clamp(grounded, 0, 1);
        float forwardX = (float) Math.cos(angle), forwardY = (float) Math.sin(angle);
        float lateral = span * (1 - Math.abs(pairPosition) * 0.10f);
        float opening = Math.clamp(spread, 0, 1) * (1 - Math.clamp(attack, 0, 1) * 0.52f);
        float flutter = (float) Math.sin(time * 2.2f + pair * 1.2f + t * 0.9f) * flexibility;
        float station = covert ? 0.025f + t * 0.91f : t;
        float u = covert ? 0.005f + t * 0.72f : 0.015f + t * 0.75f;
        float arch = 0.135f * (float) Math.sin(station * Math.PI);
        // A continuous overlapping row follows the shoulder. Its shallower fan covers the long
        // feathers' exposed quills instead of making two sparse rows of large isolated scales.
        float trailing = -arch - (covert ? 0.02f : 0);
        root.set(side * (0.17f + lateral * (forwardX * u + forwardY * trailing) * opening),
                rootHeight + pairPosition * 0.24f
                        + lateral * (forwardY * u - forwardX * trailing) * opening - (1 - opening) * u * 0.65f,
                rootDepth - u * 0.3f + flutter * u + (covert ? -0.075f : 0));
        float length = covert
                ? 0.21f + 0.10f * (float) Math.sin(station * Math.PI * 0.82f)
                : 0.14f + 0.39f * (float) Math.sin(t * Math.PI * 0.68f) + 0.07f * smooth((t - 0.60f) / 0.40f);
        length *= lateral;
        // Long secondary feathers open across the wing; outer primaries turn progressively into
        // distinct swept tips. A curved angle progression avoids a single broad paddle at the end.
        float fanAngle = 0.15f + 0.98f * (float) Math.pow(1 - station, 0.70f);
        if (covert) fanAngle = 0.22f + 0.20f * (1 - station);
        float along = (float) Math.cos(fanAngle), across = (float) Math.sin(fanAngle);
        longitudinal.set(side * (forwardX * along + forwardY * across) * opening,
                (forwardY * along - forwardX * across) * opening - (1 - opening) * 0.85f,
                -0.065f + flutter * 0.12f).normalize().mul(length);
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
                flexibility, attack, grounded, root, longitudinal, normal);
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

    /** Capsule radius around a whole-wing query segment enclosing one complete curved feather. */
    public static float surfaceQueryRadius(Vector3f queryRoot, Vector3f queryTip, Vector3f root,
                                           Vector3f longitudinal, float widthRatio, float edgeActivity) {
        float rootDistance = segmentDistance(queryRoot, queryTip, root.x, root.y, root.z);
        float tipDistance = segmentDistance(queryRoot, queryTip, root.x + longitudinal.x,
                root.y + longitudinal.y, root.z + longitudinal.z);
        float surfaceMargin = longitudinal.length() * (Math.abs(widthRatio) * 0.5f
                + 0.073f + Math.clamp(edgeActivity, 0, 1) * 0.025f);
        return Math.max(rootDistance, tipDistance) + surfaceMargin;
    }

    private static float segmentDistance(Vector3f start, Vector3f end, float x, float y, float z) {
        float dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
        x -= start.x; y -= start.y; z -= start.z;
        float lengthSquared = dx * dx + dy * dy + dz * dz;
        float t = lengthSquared > 0.000001f ? Math.clamp((x * dx + y * dy + z * dz) / lengthSquared, 0, 1) : 0;
        x -= dx * t; y -= dy * t; z -= dz * t;
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static float smooth(float value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }
}
