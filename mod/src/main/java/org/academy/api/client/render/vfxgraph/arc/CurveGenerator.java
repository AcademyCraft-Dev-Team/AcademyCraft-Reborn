package org.academy.api.client.render.vfxgraph.arc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CurveGenerator {
    private static final int JAGGED_DEPTH = 4;

    private CurveGenerator() {
    }

    public static void generate(ArcCurve arc,
                                float px, float py, float pz,
                                float nx, float ny, float nz,
                                float width, int segments,
                                float r, float g, float b, float a,
                                float lifetime, long seed,
                                int branchDepth, int branchCount, float branchAngle,
                                float branchLengthScale, float branchWidthScale,
                                float branchBrightnessScale, float height) {
        var half = height * 0.5f;
        generateFromTo(arc,
                px - nx * half, py - ny * half, pz - nz * half,
                px + nx * half, py + ny * half, pz + nz * half,
                nx, ny, nz,
                width, segments,
                r, g, b, a, lifetime, seed,
                branchDepth, branchCount, branchAngle,
                branchLengthScale, branchWidthScale, branchBrightnessScale);
    }

    public static void generateSurfaceArc(ArcCurve arc,
                                          float px, float py, float pz,
                                          float nx, float ny, float nz,
                                          float height, float curve, float width, int segments,
                                          float r, float g, float b, float a,
                                          float lifetime, long seed) {
        arc.clearPoints();
        arc.setColor(r, g, b, a);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);

        var random = new Random(seed);
        var az = random.nextFloat() * 2f * (float) Math.PI;
        var refAxis = Math.abs(ny) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        var dot = refAxis[0] * nx + refAxis[1] * ny + refAxis[2] * nz;
        float t1x = refAxis[0] - dot * nx, t1y = refAxis[1] - dot * ny, t1z = refAxis[2] - dot * nz;
        var t1l = (float) Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
        if (t1l < 1e-6f) {
            t1x = 1;
            t1y = 0;
            t1z = 0;
            t1l = 1;
        } else {
            t1x /= t1l;
            t1y /= t1l;
            t1z /= t1l;
        }
        float c = (float) Math.cos(az), s = (float) Math.sin(az);
        var rx = t1x * c + (ny * t1z - nz * t1y) * s;
        var ry = t1y * c + (nz * t1x - nx * t1z) * s;
        var rz = t1z * c + (nx * t1y - ny * t1x) * s;

        var handleRandom = 0.4f + 0.8f * random.nextFloat();
        var spanScale = 0.4f + 0.8f * random.nextFloat();

        arc.setArchBase(px, py, pz, nx, ny, nz, rx, ry, rz, handleRandom, height);
        arc.setArchSpanScale(spanScale);
        arc.setArchResample(curve, width, segments);

        sampleSurfaceArch(arc);
    }

    public static void sampleSurfaceArch(ArcCurve arc) {
        var lifetime = Math.max(1e-3f, arc.lifetime());
        var ageFrac = Math.clamp(arc.age() / lifetime, 0f, 1f);

        float nx = arc.archNx(), ny = arc.archNy(), nz = arc.archNz();
        float dx = arc.archDx(), dy = arc.archDy(), dz = arc.archDz();
        var half = arc.archHalf() * arc.archSpanScale();
        var ax = arc.archX() + arc.wanderX();
        var ay = arc.archY() + arc.wanderY();
        var az = arc.archZ() + arc.wanderZ();
        var width = arc.archWidth();
        var segments = arc.archSegments();

        float sx = ax - dx * half, sy = ay - dy * half, sz = az - dz * half;
        float ex = ax + dx * half, ey = ay + dy * half, ez = az + dz * half;

        var growth = BlenderArcCurves.sample(BlenderArcCurves.ARCH_GROWTH, ageFrac);
        var handle = growth * arc.archRandom() * arc.archHeight();
        float hx = nx * handle, hy = ny * handle, hz = nz * handle;

        var n = Math.max(3, segments);
        arc.clearPoints();
        for (var i = 0; i < n; i++) {
            var t = (float) i / (n - 1);
            var inv = 1f - t;
            var w0 = inv * inv * inv;
            var w1 = 3f * inv * inv * t;
            var w2 = 3f * inv * t * t;
            var w3 = t * t * t;
            var bx = w0 * sx + w1 * (sx + hx) + w2 * (ex + hx) + w3 * ex;
            var by = w0 * sy + w1 * (sy + hy) + w2 * (ey + hy) + w3 * ey;
            var bz = w0 * sz + w1 * (sz + hz) + w2 * (ez + hz) + w3 * ez;

            var radiusAge = arc.flatRadius()
                    ? BlenderArcCurves.sample(BlenderArcCurves.CONTACT_RADIUS_AGE, ageFrac)
                    : BlenderArcCurves.sample(BlenderArcCurves.RADIUS_AGE, ageFrac);
            var radiusProfile = arc.flatRadius()
                    ? 1f
                    : BlenderArcCurves.sample(BlenderArcCurves.RADIUS_PROFILE, t);
            arc.addPoint(bx, by, bz, width * radiusProfile * radiusAge, 0, 0);
            var paVal = BlenderArcCurves.sample(BlenderArcCurves.NOISE_PA, t) * paRandom(arc.seed(), i);
            arc.setPa(arc.size() - 1, paVal);
        }
    }

    private static float paRandom(long seed, int i) {
        var h = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return 0.4f + 1.8f * ((h & 0xFFFFFFFFL) / (float) 0xFFFFFFFFL);
    }

    public static void generateContactArc(ArcCurve arc,
                                          float px, float py, float pz,
                                          float contactNx, float contactNy, float contactNz,
                                          float radius, int segments,
                                          float r, float g, float b, float a,
                                          float lifetime, long seed) {
        arc.clearPoints();
        arc.setColor(r, g, b, a);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);

        float dx = contactNx - px, dy = contactNy - py, dz = contactNz - pz;
        var len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) len = 1f;
        dx /= len;
        dy /= len;
        dz /= len;

        var cx = (px + contactNx) * 0.5f;
        var cy = (py + contactNy) * 0.5f;
        var cz = (pz + contactNz) * 0.5f;
        arc.setArchBase(cx, cy, cz, 0, 0, 0, dx, dy, dz, 1f, len);
        arc.setArchResample(0f, radius, segments);
        arc.setFlatRadius(true);
        arc.setPinStart(true);

        sampleSurfaceArch(arc);
    }

    public static void generateFromTo(ArcCurve arc,
                                      float fromX, float fromY, float fromZ,
                                      float toX, float toY, float toZ,
                                      float nx, float ny, float nz,
                                      float width, int segments,
                                      float r, float g, float b, float a,
                                      float lifetime, long seed,
                                      int branchDepth, int branchCount, float branchAngle,
                                      float branchLengthScale, float branchWidthScale,
                                      float branchBrightnessScale) {
        arc.clearPoints();
        arc.setColor(r, g, b, a);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);

        var random = new Random(seed);
        int[] segmentCounter = {0};

        generateBolt(arc, fromX, fromY, fromZ, toX, toY, toZ,
                nx, ny, nz, width, segments, 0, random, segmentCounter);

        if (arc.size() >= 2) {
            arc.setPa(0, 0f);
            arc.setPa(arc.size() - 1, 0f);
        }

        if (branchDepth > 0 && branchCount > 0) {
            generateBranchesRecursive(arc, 0, branchDepth, branchCount,
                    branchAngle, branchLengthScale, branchWidthScale,
                    branchBrightnessScale, segments,
                    fromX, fromY, fromZ, toX, toY, toZ, nx, ny, nz,
                    r, g, b, a, random, segmentCounter);
        }
    }

    private static void generateBolt(ArcCurve arc,
                                     float fromX, float fromY, float fromZ,
                                     float toX, float toY, float toZ,
                                     float nx, float ny, float nz,
                                     float width, int segments, float generation,
                                     Random random, int[] segmentCounter) {
        var segment = segmentCounter[0]++;
        float dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        var chordLen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (chordLen < 1e-6f) chordLen = 1f;

        float tx = dx / chordLen, ty = dy / chordLen, tz = dz / chordLen;
        float rx, ry, rz;
        if (Math.abs(ty) < 0.9f) {
            rx = 0;
            ry = 1;
        } else {
            rx = 1;
            ry = 0;
        }
        rz = 0;
        var dot = rx * tx + ry * ty + rz * tz;
        float ux = rx - dot * tx, uy = ry - dot * ty, uz = rz - dot * tz;
        var ulen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ulen < 1e-6f) {
            ux = 1;
            uy = 0;
            uz = 0;
        } else {
            ux /= ulen;
            uy /= ulen;
            uz /= ulen;
        }

        var planeAngle = random.nextFloat() * 2f * (float) Math.PI;
        var pc = (float) Math.cos(planeAngle);
        var ps = (float) Math.sin(planeAngle);
        float rux = ux * pc + (ty * uz - tz * uy) * ps;
        float ruy = uy * pc + (tz * ux - tx * uz) * ps;
        float ruz = uz * pc + (tx * uy - ty * ux) * ps;

        var pts = new ArrayList<float[]>();
        pts.add(new float[]{fromX, fromY, fromZ});
        midpointDisplace(pts, fromX, fromY, fromZ, toX, toY, toZ,
                rux, ruy, ruz, nx, ny, nz, chordLen, 0, random);
        pts.add(new float[]{toX, toY, toZ});

        var n = Math.max(3, segments);
        var sampled = new ArrayList<float[]>(n);
        for (var i = 0; i < n; i++) {
            var t = (float) i / (n - 1);
            sampled.add(sampleAlong(pts, t));
        }

        for (var i = 0; i < n; i++) {
            var t = (float) i / (n - 1);
            var taper = (float) Math.sin(t * Math.PI);
            taper = 0.3f + 0.7f * taper;
            var w = width * taper;
            var p = sampled.get(i);
            arc.addPoint(p[0], p[1], p[2], w, generation, segment);
        }
    }

    private static void midpointDisplace(List<float[]> pts,
                                         float ax, float ay, float az,
                                         float bx, float by, float bz,
                                         float ux, float uy, float uz,
                                         float nx, float ny, float nz,
                                         float chordLen, int depth, Random random) {
        if (depth >= JAGGED_DEPTH) return;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        var amp = chordLen * (0.12f / (depth + 1));
        var ang = random.nextFloat() * 2f * (float) Math.PI;
        var px = ux * (float) Math.cos(ang) + nx * 0.3f;
        var py = uy * (float) Math.cos(ang) + ny * 0.3f;
        var pz = uz * (float) Math.cos(ang) + nz * 0.3f;
        var plen = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (plen < 1e-6f) {
            px = ux;
            py = uy;
            pz = uz;
        } else {
            px /= plen;
            py /= plen;
            pz /= plen;
        }
        float ox = mx + px * amp, oy = my + py * amp, oz = mz + pz * amp;
        midpointDisplace(pts, ax, ay, az, ox, oy, oz, ux, uy, uz, nx, ny, nz, chordLen, depth + 1, random);
        pts.add(new float[]{ox, oy, oz});
        midpointDisplace(pts, ox, oy, oz, bx, by, bz, ux, uy, uz, nx, ny, nz, chordLen, depth + 1, random);
    }

    private static float[] sampleAlong(List<float[]> pts, float t) {
        var total = 0f;
        var segLen = new float[Math.max(1, pts.size() - 1)];
        for (var i = 0; i < pts.size() - 1; i++) {
            var d = (float) Math.sqrt(
                    Math.pow(pts.get(i + 1)[0] - pts.get(i)[0], 2)
                            + Math.pow(pts.get(i + 1)[1] - pts.get(i)[1], 2)
                            + Math.pow(pts.get(i + 1)[2] - pts.get(i)[2], 2));
            segLen[i] = d;
            total += d;
        }
        if (total < 1e-6f) return pts.getFirst();
        var target = t * total;
        var acc = 0f;
        for (var i = 0; i < segLen.length; i++) {
            if (acc + segLen[i] >= target || i == segLen.length - 1) {
                var lt = segLen[i] < 1e-6f ? 0f : (target - acc) / segLen[i];
                lt = Math.clamp(lt, 0f, 1f);
                float[] a = pts.get(i), b = pts.get(i + 1);
                return new float[]{
                        a[0] + (b[0] - a[0]) * lt,
                        a[1] + (b[1] - a[1]) * lt,
                        a[2] + (b[2] - a[2]) * lt};
            }
            acc += segLen[i];
        }
        return pts.getLast();
    }

    private static void generateBranchesRecursive(ArcCurve arc,
                                                  int currentGen, int maxDepth,
                                                  int branchCount, float branchAngle,
                                                  float lengthScale, float widthScale,
                                                  float brightnessScale, int segments,
                                                  float fromX, float fromY, float fromZ,
                                                  float toX, float toY, float toZ,
                                                  float nx, float ny, float nz,
                                                  float r, float g, float b, float a,
                                                  Random random, int[] segmentCounter) {
        if (currentGen >= maxDepth) return;

        float childGen = currentGen + 1;

        var genPoints = new ArrayList<int[]>();
        for (var i = 0; i < arc.size(); i++) {
            if (Math.abs(arc.generation(i) - currentGen) < 0.01f) {
                genPoints.add(new int[]{i});
            }
        }
        if (genPoints.isEmpty()) return;

        var total = genPoints.size();
        var attachCount = Math.clamp(total - 2, 0, branchCount);
        if (attachCount <= 0) return;

        float fx = toX - fromX, fy = toY - fromY, fz = toZ - fromZ;
        var flen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (flen < 1e-6f) {
            fx = 0;
            fy = 1;
            fz = 0;
        } else {
            fx /= flen;
            fy /= flen;
            fz /= flen;
        }

        for (var bi = 0; bi < attachCount; bi++) {
            var t = 0.2f + 0.6f * (float) bi / Math.max(1, attachCount - 1);
            var idx = Math.max(1, Math.min(total - 2, Math.round(t * (total - 1))));
            var pointIdx = genPoints.get(idx)[0];

            var bx = arc.x(pointIdx);
            var by = arc.y(pointIdx);
            var bz = arc.z(pointIdx);

            var branchDir = SurfaceDistributor.coneDirection(fx, fy, fz, branchAngle, random);
            float branchNx = branchDir[0], branchNy = branchDir[1], branchNz = branchDir[2];

            var parentWidth = arc.width(pointIdx) / (0.3f + 0.7f * (float) Math.sin(
                    (float) idx / Math.max(1, total - 1) * Math.PI));
            var cw = parentWidth * widthScale;

            float lx = toX - fromX, ly = toY - fromY, lz = toZ - fromZ;
            var chordLen = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
            var childLen = Math.max(chordLen * lengthScale, 1e-3f);

            var eX = bx + branchNx * childLen;
            var eY = by + branchNy * childLen;
            var eZ = bz + branchNz * childLen;

            generateBolt(arc, bx, by, bz, eX, eY, eZ,
                    branchNx, branchNy, branchNz,
                    cw, segments, childGen, random, segmentCounter);

            generateBranchesRecursive(arc, (int) childGen, maxDepth,
                    branchCount, branchAngle, lengthScale, widthScale,
                    brightnessScale, segments,
                    bx, by, bz, eX, eY, eZ, branchNx, branchNy, branchNz,
                    r, g, b, a, random, segmentCounter);
        }
    }
}
