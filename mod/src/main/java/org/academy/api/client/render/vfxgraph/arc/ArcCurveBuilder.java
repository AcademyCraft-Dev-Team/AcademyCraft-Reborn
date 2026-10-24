package org.academy.api.client.render.vfxgraph.arc;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;

public final class ArcCurveBuilder {
    private ArcCurveBuilder() {
    }

    public static @Nullable ArcCurve line(ArcEmission out, float fromX, float fromY, float fromZ,
                                          float toX, float toY, float toZ,
                                          float width, int segments,
                                          float r, float g, float b, float a, float lifetime, long seed,
                                          int branchDepth, int branchCount, float branchAngle,
                                          float branchLengthScale, float branchWidthScale) {
        var arc = out.add();
        if (arc == null) return null;
        line(arc, fromX, fromY, fromZ, toX, toY, toZ, width, segments, r, g, b, a, lifetime, seed,
                branchDepth, branchCount, branchAngle, branchLengthScale, branchWidthScale);
        return arc;
    }

    public static void line(ArcCurve arc, float fromX, float fromY, float fromZ,
                            float toX, float toY, float toZ,
                            float width, int segments,
                            float r, float g, float b, float a, float lifetime, long seed,
                            int branchDepth, int branchCount, float branchAngle,
                            float branchLengthScale, float branchWidthScale) {
        var dx = toX - fromX;
        var dy = toY - fromY;
        var dz = toZ - fromZ;
        var length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = 0;
        float ny = 1;
        float nz = 0;
        if (length > 1e-6f) {
            nx = dx / length;
            ny = dy / length;
            nz = dz / length;
        }
        CurveGenerator.generateFromTo(arc, fromX, fromY, fromZ, toX, toY, toZ,
                nx, ny, nz, width, segments, r, g, b, a, lifetime, seed,
                branchDepth, branchCount, branchAngle, branchLengthScale, branchWidthScale, 1f);
        arc.setNoiseStrength(0f);
        arc.setDriftSpeed(0f);
    }

    public static @Nullable ArcCurve polyline(ArcEmission out, List<? extends Vector3fc> points,
                                              float startWidth, float endWidth,
                                              float r, float g, float b, float a, float lifetime, long seed) {
        if (points.size() < 2) return null;
        var arc = out.add();
        if (arc == null) return null;
        polyline(arc, points, startWidth, endWidth, r, g, b, a, lifetime, seed);
        return arc;
    }

    public static void polyline(ArcCurve arc, List<? extends Vector3fc> points,
                                float startWidth, float endWidth,
                                float r, float g, float b, float a, float lifetime, long seed) {
        arc.clearPoints();
        arc.setColor(r, g, b, a);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);
        arc.setNoiseStrength(0f);
        arc.setDriftSpeed(0f);
        var random = new Random(seed);
        var last = Math.max(1, points.size() - 1);
        for (var i = 0; i <= last; i++) {
            var point = points.get(i);
            var t = (float) i / last;
            var width = startWidth + (endWidth - startWidth) * t;
            var jitter = i == 0 || i == last ? 0f : 0.012f;
            arc.addPoint(
                    point.x() + (random.nextFloat() - 0.5f) * jitter,
                    point.y() + (random.nextFloat() - 0.5f) * jitter,
                    point.z() + (random.nextFloat() - 0.5f) * jitter,
                    width, 0f);
        }
    }

    public static @Nullable ArcCurve ring(ArcEmission out, float centerX, float centerY, float centerZ,
                                          float nx, float ny, float nz, float radius, int segments,
                                          float width, float r, float g, float b, float a,
                                          float lifetime, long seed) {
        var arc = out.add();
        if (arc == null) return null;
        ring(arc, centerX, centerY, centerZ, nx, ny, nz, radius, segments, width, r, g, b, a, lifetime, seed);
        return arc;
    }

    public static void ring(ArcCurve arc, float centerX, float centerY, float centerZ,
                            float nx, float ny, float nz, float radius, int segments,
                            float width, float r, float g, float b, float a,
                            float lifetime, long seed) {
        arc.clearPoints();
        arc.setColor(r, g, b, a);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);
        arc.setNoiseStrength(0f);
        arc.setDriftSpeed(0f);
        var normalLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (normalLength < 1e-6f) {
            nx = 0;
            ny = 1;
            nz = 0;
        } else {
            nx /= normalLength;
            ny /= normalLength;
            nz /= normalLength;
        }
        var refX = Math.abs(ny) < 0.9f ? 0f : 1f;
        var refY = Math.abs(ny) < 0.9f ? 1f : 0f;
        var tx = refY * nz;
        var ty = -refX * nz;
        var tz = refX * ny - refY * nx;
        var tangentLength = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tangentLength < 1e-6f) {
            tx = 1;
            ty = 0;
            tz = 0;
        } else {
            tx /= tangentLength;
            ty /= tangentLength;
            tz /= tangentLength;
        }
        var bx = ny * tz - nz * ty;
        var by = nz * tx - nx * tz;
        var bz = nx * ty - ny * tx;
        var count = Math.max(3, segments);
        for (var i = 0; i <= count; i++) {
            var angle = (float) (Math.PI * 2.0 * i / count);
            var cos = (float) Math.cos(angle);
            var sin = (float) Math.sin(angle);
            arc.addPoint(
                    centerX + (tx * cos + bx * sin) * radius,
                    centerY + (ty * cos + by * sin) * radius,
                    centerZ + (tz * cos + bz * sin) * radius,
                    width, 0f);
        }
    }

    public static void blueWhiteLine(ArcEmission out, float fromX, float fromY, float fromZ,
                                     float toX, float toY, float toZ, float width, int segments,
                                     float opacity, float lifetime, long seed,
                                     int branchDepth, int branchCount, float branchAngle,
                                     float branchLengthScale, float branchWidthScale) {
        line(out, fromX, fromY, fromZ, toX, toY, toZ, width * BlueWhiteArcStyle.widthScale(0), segments,
                0.12f, 0.48f, 1f, opacity * 0.48f, lifetime, seed,
                branchDepth, branchCount, branchAngle, branchLengthScale, branchWidthScale);
        line(out, fromX, fromY, fromZ, toX, toY, toZ, width * BlueWhiteArcStyle.widthScale(1), segments,
                0.78f, 0.94f, 1f, opacity * 0.92f, lifetime, seed + 1,
                branchDepth, branchCount, branchAngle, branchLengthScale, branchWidthScale);
    }

    public static void blueWhitePolyline(ArcEmission out, List<? extends Vector3fc> points,
                                         float startWidth, float endWidth, float opacity,
                                         float lifetime, long seed) {
        polyline(out, points, startWidth * BlueWhiteArcStyle.widthScale(0), endWidth * BlueWhiteArcStyle.widthScale(0),
                0.12f, 0.48f, 1f, opacity * 0.48f, lifetime, seed);
        polyline(out, points, startWidth * BlueWhiteArcStyle.widthScale(1), endWidth * BlueWhiteArcStyle.widthScale(1),
                0.78f, 0.94f, 1f, opacity * 0.92f, lifetime, seed + 1);
    }

    public static @Nullable ArcCurve helix(ArcEmission out, float fromX, float fromY, float fromZ,
                                           float toX, float toY, float toZ, float radius, int turns,
                                           int segments, float width, float r, float g, float b, float a,
                                           float lifetime, long seed) {
        var arc = out.add();
        if (arc == null) return null;
        helix(arc, fromX, fromY, fromZ, toX, toY, toZ, radius, turns, segments, width,
                r, g, b, a, lifetime, seed);
        return arc;
    }

    public static void helix(ArcCurve arc, float fromX, float fromY, float fromZ,
                             float toX, float toY, float toZ, float radius, int turns,
                             int segments, float width, float r, float g, float b, float a,
                             float lifetime, long seed) {
        arc.clearPoints();
        arc.setColor(r, g, b, a);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);
        arc.setNoiseStrength(0f);
        arc.setDriftSpeed(0f);
        var dx = toX - fromX;
        var dy = toY - fromY;
        var dz = toZ - fromZ;
        var length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6f) return;
        var nx = dx / length;
        var ny = dy / length;
        var nz = dz / length;
        var refX = Math.abs(ny) < 0.9f ? 0f : 1f;
        var refY = Math.abs(ny) < 0.9f ? 1f : 0f;
        var tx = refY * nz;
        var ty = -refX * nz;
        var tz = refX * ny - refY * nx;
        var tangentLength = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        tx /= tangentLength;
        ty /= tangentLength;
        tz /= tangentLength;
        var bx = ny * tz - nz * ty;
        var by = nz * tx - nx * tz;
        var bz = nx * ty - ny * tx;
        var count = Math.max(8, segments);
        var turnCount = Math.max(1, turns);
        for (var i = 0; i <= count; i++) {
            var t = (float) i / count;
            var angle = (float) (Math.PI * 2.0 * turnCount * t);
            var cos = (float) Math.cos(angle);
            var sin = (float) Math.sin(angle);
            arc.addPoint(
                    fromX + dx * t + (tx * cos + bx * sin) * radius,
                    fromY + dy * t + (ty * cos + by * sin) * radius,
                    fromZ + dz * t + (tz * cos + bz * sin) * radius,
                    width, 0f);
        }
    }
}
