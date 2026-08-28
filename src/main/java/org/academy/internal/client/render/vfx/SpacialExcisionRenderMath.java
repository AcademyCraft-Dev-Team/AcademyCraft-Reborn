package org.academy.internal.client.render.vfx;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/** Pure helpers shared by the spatial-cut CPU layout and its regression tests. */
final class SpacialExcisionRenderMath {
    private static final float SCREEN_DIRECTION_EPSILON = 1.0e-6f;
    private static final float FALLOFF_DISTANCE_FACTOR = 2.5f;
    static final float MAX_NEAR_FALLOFF_SHORT_SIDE_FRACTION = 0.04f;
    static final float MIN_NEAR_FALLOFF_WORLD_WIDTH = 0.05f;
    static final float AFFINE_SOURCE_W_MINIMUM = 1.0e-1f;
    static final float AFFINE_MAX_SHIFT_SHORT_SIDE = 1.8e-1f;
    static final float AFFINE_MAX_SUPPORT_SCALE = 4096.0f;
    static final int AFFINE_SUPPORT_SEARCH_STEPS = 24;
    private static final float MIN_FLOW_SPEED = 0.035f;
    private static final float MAX_FLOW_SPEED = 0.085f;
    private static final float MIN_BREATH_SPEED = 0.012f;
    private static final float MAX_BREATH_SPEED = 0.028f;
    private static final float MIN_BREATH_AMPLITUDE = 0.035f;
    private static final float MAX_BREATH_AMPLITUDE = 0.060f;

    private SpacialExcisionRenderMath() {
    }

    static WorldOrientationWeights worldOrientationWeights(Vector3fc tangent) {
        if (tangent == null || !tangent.isFinite()) return WorldOrientationWeights.NONE;
        var length = tangent.length();
        if (!Float.isFinite(length) || length <= SCREEN_DIRECTION_EPSILON) {
            return WorldOrientationWeights.NONE;
        }
        var horizontal = (float) Math.hypot(tangent.x(), tangent.z()) / length;
        var vertical = Math.abs(tangent.y()) / length;
        if (!Float.isFinite(horizontal) || !Float.isFinite(vertical)) {
            return WorldOrientationWeights.NONE;
        }
        return new WorldOrientationWeights(
                Math.clamp(horizontal, 0.0f, 1.0f),
                Math.clamp(vertical, 0.0f, 1.0f));
    }

    static Vector3f worldDisplacement(
            Vector3fc tangent,
            Vector3fc planeUp,
            float horizontalDistance,
            float verticalDistance,
            float progress
    ) {
        if (tangent == null || planeUp == null || !tangent.isFinite() || !planeUp.isFinite()) {
            return new Vector3f();
        }
        var tangentLength = tangent.length();
        var planeUpLength = planeUp.length();
        if (!Float.isFinite(tangentLength) || !Float.isFinite(planeUpLength)
                || tangentLength <= SCREEN_DIRECTION_EPSILON
                || planeUpLength <= SCREEN_DIRECTION_EPSILON) {
            return new Vector3f();
        }
        var weights = worldOrientationWeights(tangent);
        var horizontal = finiteNonNegative(horizontalDistance) * weights.horizontal();
        var vertical = finiteNonNegative(verticalDistance) * weights.vertical();
        var amount = Float.isFinite(progress) ? Math.clamp(progress, 0.0f, 1.0f) : 0.0f;
        return new Vector3f(tangent).div(tangentLength).mul(horizontal)
                .add(new Vector3f(planeUp).div(planeUpLength).mul(vertical))
                .mul(amount);
    }

    static float adaptiveFalloffWidth(
            float minimumWidth,
            float horizontalDistance,
            float verticalDistance
    ) {
        var minimum = finiteNonNegative(minimumWidth);
        var requested = finiteNonNegative(horizontalDistance)
                + finiteNonNegative(verticalDistance);
        return Math.max(minimum, FALLOFF_DISTANCE_FACTOR * requested);
    }

    /**
     * Limits a world-space recovery band by its projected width while retaining
     * the configured width at ordinary distances. The caller supplies the
     * largest measured screen-space size of one world unit along the cut's
     * tangent or plane-up direction.
     */
    static float screenBoundedFalloffWidth(
            float requestedWorldWidth,
            float projectedPixelsPerWorldUnit,
            int viewportWidth,
            int viewportHeight
    ) {
        if (!Float.isFinite(requestedWorldWidth) || requestedWorldWidth <= 0.0f) {
            return 0.0f;
        }
        if (viewportWidth < 2 || viewportHeight < 2
                || !Float.isFinite(projectedPixelsPerWorldUnit)
                || projectedPixelsPerWorldUnit <= SCREEN_DIRECTION_EPSILON) {
            return requestedWorldWidth;
        }
        var maximumPixels = MAX_NEAR_FALLOFF_SHORT_SIDE_FRACTION
                * Math.min(viewportWidth, viewportHeight);
        var projectedLimit = maximumPixels / projectedPixelsPerWorldUnit;
        if (!Float.isFinite(projectedLimit)) return requestedWorldWidth;
        return Math.min(requestedWorldWidth,
                Math.max(MIN_NEAR_FALLOFF_WORLD_WIDTH, projectedLimit));
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(value, 0.0f) : 0.0f;
    }

    /**
     * Produces one source-UV translation for each physical half of a cut. The
     * support point may move only along its existing view ray, preserving its
     * destination UV while reducing an unsafe near-camera projection. The
     * returned offsets are therefore cut-wide constants rather than a
     * per-pixel physical-plane homography.
     */
    static @Nullable AffineCutMapping regularizedAffineCutMapping(
            Vector3fc supportViewPosition,
            Vector3fc displacementView,
            Matrix4fc projection,
            int viewportWidth,
            int viewportHeight
    ) {
        if (supportViewPosition == null || displacementView == null || projection == null
                || !supportViewPosition.isFinite() || !displacementView.isFinite()
                || !projection.isFinite() || viewportWidth < 2 || viewportHeight < 2) {
            return null;
        }
        var shortSide = Math.min(viewportWidth, viewportHeight);
        var maximumPixels = AFFINE_MAX_SHIFT_SHORT_SIDE * shortSide;
        if (!Float.isFinite(maximumPixels) || maximumPixels <= 0.0f) return null;

        var mapping = evaluateAffineOffsets(
                supportViewPosition, displacementView, projection,
                viewportWidth, viewportHeight, 1.0f);
        if (affineMappingSafe(mapping, maximumPixels)) return mapping;

        var lowerScale = 1.0f;
        var upperScale = 2.0f;
        AffineCutMapping upperMapping = null;
        while (upperScale <= AFFINE_MAX_SUPPORT_SCALE) {
            upperMapping = evaluateAffineOffsets(
                    supportViewPosition, displacementView, projection,
                    viewportWidth, viewportHeight, upperScale);
            if (affineMappingSafe(upperMapping, maximumPixels)) break;
            lowerScale = upperScale;
            upperScale *= 2.0f;
        }
        if (!affineMappingSafe(upperMapping, maximumPixels)) return null;

        for (var step = 0; step < AFFINE_SUPPORT_SEARCH_STEPS; step++) {
            var middleScale = (lowerScale + upperScale) * 0.5f;
            var middleMapping = evaluateAffineOffsets(
                    supportViewPosition, displacementView, projection,
                    viewportWidth, viewportHeight, middleScale);
            if (affineMappingSafe(middleMapping, maximumPixels)) {
                upperScale = middleScale;
                upperMapping = middleMapping;
            } else {
                lowerScale = middleScale;
            }
        }
        return upperMapping;
    }

    private static @Nullable AffineCutMapping evaluateAffineOffsets(
            Vector3fc supportViewPosition,
            Vector3fc displacementView,
            Matrix4fc projection,
            int viewportWidth,
            int viewportHeight,
            float supportScale
    ) {
        if (!Float.isFinite(supportScale) || supportScale < 1.0f) return null;
        var support = new Vector3f(supportViewPosition).mul(supportScale);
        var destinationUv = projectViewPointToUv(support, projection);
        var positiveUv = projectViewPointToUv(
                new Vector3f(support).add(displacementView), projection);
        var negativeUv = projectViewPointToUv(
                new Vector3f(support).sub(displacementView), projection);
        if (destinationUv == null || positiveUv == null || negativeUv == null) return null;

        var positiveOffset = positiveUv.sub(destinationUv, new Vector2f());
        var negativeOffset = negativeUv.sub(destinationUv, new Vector2f());
        if (!positiveOffset.isFinite() || !negativeOffset.isFinite()) return null;
        var positivePixels = Math.hypot(
                positiveOffset.x * viewportWidth,
                positiveOffset.y * viewportHeight);
        var negativePixels = Math.hypot(
                negativeOffset.x * viewportWidth,
                negativeOffset.y * viewportHeight);
        var maximumOffsetPixels = (float) Math.max(positivePixels, negativePixels);
        if (!Float.isFinite(maximumOffsetPixels)) return null;
        return new AffineCutMapping(
                positiveOffset, negativeOffset, maximumOffsetPixels);
    }

    private static @Nullable Vector2f projectViewPointToUv(
            Vector3fc viewPosition,
            Matrix4fc projection
    ) {
        var clip = new Vector4f(viewPosition, 1.0f).mul(projection);
        if (!clip.isFinite() || clip.w < AFFINE_SOURCE_W_MINIMUM) return null;
        var inverseW = 1.0f / clip.w;
        var uv = new Vector2f(
                Math.fma(clip.x * inverseW, 0.5f, 0.5f),
                Math.fma(clip.y * inverseW, 0.5f, 0.5f));
        return uv.isFinite() ? uv : null;
    }

    private static boolean affineMappingSafe(
            @Nullable AffineCutMapping mapping,
            float maximumPixels
    ) {
        return mapping != null
                && mapping.positiveUvOffset().isFinite()
                && mapping.negativeUvOffset().isFinite()
                && Float.isFinite(mapping.maximumOffsetPixels())
                && mapping.maximumOffsetPixels() <= maximumPixels;
    }

    static List<Long> selectBackgroundIds(
            List<BackgroundCandidate> candidates,
            int maximum
    ) {
        if (maximum <= 0 || candidates.isEmpty()) return List.of();
        var selected = new ArrayList<BackgroundCandidate>(
                Math.min(maximum, candidates.size()));
        for (var candidate : candidates) {
            if (!validCandidate(candidate)) continue;
            var insertion = 0;
            while (insertion < selected.size()
                    && compareCandidates(selected.get(insertion), candidate) <= 0) {
                insertion++;
            }
            if (insertion >= maximum) continue;
            selected.add(insertion, candidate);
            if (selected.size() > maximum) selected.removeLast();
        }
        var ids = new ArrayList<Long>(selected.size());
        for (var candidate : selected) ids.add(candidate.stableId());
        return List.copyOf(ids);
    }

    private static boolean validCandidate(BackgroundCandidate candidate) {
        return candidate != null
                && Double.isFinite(candidate.projectedArea())
                && candidate.projectedArea() > 0.0
                && Double.isFinite(candidate.distanceSquared())
                && candidate.distanceSquared() >= 0.0;
    }

    private static int compareCandidates(
            BackgroundCandidate first, BackgroundCandidate second
    ) {
        var byArea = Double.compare(second.projectedArea(), first.projectedArea());
        if (byArea != 0) return byArea;
        var byDistance = Double.compare(first.distanceSquared(), second.distanceSquared());
        return byDistance != 0 ? byDistance
                : Long.compare(first.stableId(), second.stableId());
    }

    /** Source classification must flip as soon as the camera crosses the plane. */
    static int sourceValidationSide(double signedDistance, int previousSide) {
        if (!Double.isFinite(signedDistance)) return previousSide < 0 ? -1 : 1;
        if (signedDistance > 1.0e-6) return 1;
        if (signedDistance < -1.0e-6) return -1;
        return previousSide < 0 ? -1 : 1;
    }

    /**
     * Prepares a cut that intersects the camera/near gap. A footprint crossing
     * the camera plane must first be clipped at w=0 so translating it cannot
     * resurrect geometry that was behind the camera.
     */
    static NearProxyPlan nearMaskProxyPlan(
            float minimumClipW,
            float maximumClipW,
            float nearClipW
    ) {
        if (!Float.isFinite(minimumClipW) || !Float.isFinite(maximumClipW)
                || !Float.isFinite(nearClipW) || nearClipW <= 0.0f
                || minimumClipW > maximumClipW || maximumClipW <= 0.0f) {
            return NearProxyPlan.NONE;
        }
        var clipsCameraFront = minimumClipW <= 0.0f;
        var sourceMinimumW = clipsCameraFront ? 0.0f : minimumClipW;
        var proxyTargetW = nearClipW + 0.01f;
        if (sourceMinimumW >= proxyTargetW) return NearProxyPlan.NONE;
        return new NearProxyPlan(
                proxyTargetW - sourceMinimumW,
                clipsCameraFront);
    }

    /**
     * Clips a perimeter-ordered clip-space polygon to the near plane and the
     * viewport, then returns its NDC area. A partial near-plane intersection
     * must remain selectable instead of invalidating the entire cut.
     */
    static double clippedProjectedArea(List<ClipPoint> input, float nearW) {
        if (input.size() < 3 || !Float.isFinite(nearW) || nearW <= 0.0f) return 0.0;
        var polygon = List.copyOf(input);
        for (var boundary = 0; boundary < 5 && polygon.size() >= 3; boundary++) {
            polygon = clipPolygon(polygon, boundary, nearW);
        }
        if (polygon.size() < 3) return 0.0;

        var twiceArea = 0.0;
        for (var i = 0; i < polygon.size(); i++) {
            var current = polygon.get(i);
            var next = polygon.get((i + 1) % polygon.size());
            if (!current.isFinite() || !next.isFinite()
                    || current.w() < nearW || next.w() < nearW) {
                return 0.0;
            }
            var currentX = current.x() / current.w();
            var currentY = current.y() / current.w();
            var nextX = next.x() / next.w();
            var nextY = next.y() / next.w();
            if (!Double.isFinite(currentX) || !Double.isFinite(currentY)
                    || !Double.isFinite(nextX) || !Double.isFinite(nextY)) {
                return 0.0;
            }
            twiceArea += currentX * nextY - nextX * currentY;
        }
        var area = Math.abs(twiceArea) * 0.5;
        return Double.isFinite(area) ? area : 0.0;
    }

    private static List<ClipPoint> clipPolygon(
            List<ClipPoint> input, int boundary, float nearW
    ) {
        var output = new ArrayList<ClipPoint>(input.size() + 1);
        var previous = input.get(input.size() - 1);
        var previousDistance = clipDistance(previous, boundary, nearW);
        if (!Float.isFinite(previousDistance)) return List.of();
        var previousInside = previousDistance >= 0.0f;
        for (var current : input) {
            var currentDistance = clipDistance(current, boundary, nearW);
            if (!Float.isFinite(currentDistance)) return List.of();
            var currentInside = currentDistance >= 0.0f;
            if (currentInside != previousInside) {
                var denominator = previousDistance - currentDistance;
                if (!Float.isFinite(denominator) || Math.abs(denominator) <= 1.0e-8f) {
                    return List.of();
                }
                var t = previousDistance / denominator;
                var intersection = previous.lerp(current, t);
                if (boundary == 0) {
                    intersection = new ClipPoint(
                            intersection.x(), intersection.y(), nearW);
                }
                if (!intersection.isFinite()) return List.of();
                output.add(intersection);
            }
            if (currentInside) output.add(current);
            previous = current;
            previousDistance = currentDistance;
            previousInside = currentInside;
        }
        return output;
    }

    private static float clipDistance(ClipPoint point, int boundary, float nearW) {
        if (!point.isFinite()) return Float.NaN;
        return switch (boundary) {
            case 0 -> point.w() - nearW;
            case 1 -> point.x() + point.w();
            case 2 -> point.w() - point.x();
            case 3 -> point.y() + point.w();
            case 4 -> point.w() - point.y();
            default -> Float.NaN;
        };
    }

    static FlowParams flowParams(long seed) {
        var directionHash = mix64(seed ^ 0x9E3779B97F4A7C15L);
        var speedHash = mix64(seed ^ 0xD1B54A32D192ED03L);
        var phaseHash = mix64(seed ^ 0x94D049BB133111EBL);
        var direction = (directionHash & 1L) == 0L ? -1.0f : 1.0f;
        var speedUnit = unitFloat(speedHash);
        var speed = direction * (MIN_FLOW_SPEED
                + (MAX_FLOW_SPEED - MIN_FLOW_SPEED) * speedUnit);
        return new FlowParams(speed, unitFloat(phaseHash));
    }

    static BreathParams breathParams(long seed) {
        var speedHash = mix64(seed ^ 0x632BE59BD9B4E019L);
        var phaseHash = mix64(seed ^ 0x8CB92BA72F3D8DD7L);
        var amplitudeHash = mix64(seed ^ 0xA0F2EC75A1FE1575L);
        var speed = MIN_BREATH_SPEED
                + (MAX_BREATH_SPEED - MIN_BREATH_SPEED) * unitFloat(speedHash);
        var amplitude = MIN_BREATH_AMPLITUDE
                + (MAX_BREATH_AMPLITUDE - MIN_BREATH_AMPLITUDE) * unitFloat(amplitudeHash);
        return new BreathParams(speed, unitFloat(phaseHash), amplitude);
    }

    static float breathScale(BreathParams params, float gameTime) {
        if (params == null) return 1.0f;
        if (!Float.isFinite(gameTime)) return 1.0f;
        var angle = gameTime * params.speed() * (float) (2.0 * Math.PI)
                + params.phase() * (float) (2.0 * Math.PI);
        var scale = 1.0f + params.amplitude() * (float) Math.sin(angle);
        return Float.isFinite(scale) ? scale : 1.0f;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static float unitFloat(long value) {
        return (float) ((value >>> 40) * 0x1.0p-24);
    }

    record FlowParams(float speed, float phase) {
    }

    record BreathParams(float speed, float phase, float amplitude) {
    }

    record WorldOrientationWeights(float horizontal, float vertical) {
        private static final WorldOrientationWeights NONE =
                new WorldOrientationWeights(0.0f, 0.0f);
    }

    record AffineCutMapping(
            Vector2f positiveUvOffset,
            Vector2f negativeUvOffset,
            float maximumOffsetPixels
    ) {
    }

    record ClipPoint(float x, float y, float w) {
        private boolean isFinite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(w);
        }

        private ClipPoint lerp(ClipPoint other, float t) {
            return new ClipPoint(
                    Math.fma(other.x - x, t, x),
                    Math.fma(other.y - y, t, y),
                    Math.fma(other.w - w, t, w));
        }
    }

    record NearProxyPlan(float offset, boolean clipOriginalToCameraFront) {
        private static final NearProxyPlan NONE = new NearProxyPlan(0.0f, false);
    }

    record BackgroundCandidate(long stableId, double projectedArea, double distanceSquared) {
    }
}
