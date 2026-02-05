package org.academy.api.client.gui.msdf.core;

import net.minecraft.util.Mth;

import java.util.stream.IntStream;

public class MSDFErrorCorrection {
    public static final double ARTIFACT_T_EPSILON = 0.01;
    private final ByteBitmapRef stencil;
    private final SDFTransformation transformation;
    private double minDeviationRatio;
    private double minImproveRatio;

    public MSDFErrorCorrection(ByteBitmapRef stencil, SDFTransformation transformation) {
        this.stencil = stencil;
        this.transformation = transformation;
        minDeviationRatio = ErrorCorrectionConfig.defaultMinDeviationRatio;
        minImproveRatio = ErrorCorrectionConfig.defaultMinImproveRatio;
        for (var y = 0; y < stencil.height; ++y) {
            for (var x = 0; x < stencil.width; ++x) {
                stencil.pixels[stencil.getIndex(x, y)] = 0;
            }
        }
    }

    private static boolean edgeBetweenTexelsChannel(float[] a, float[] b, int channel) {
        var t = (a[channel] - 0.5f) / (a[channel] - b[channel]);
        if (t > 0 && t < 1) {
            var c = new float[]{
                    Arithmetic.mix(a[0], b[0], t),
                    Arithmetic.mix(a[1], b[1], t),
                    Arithmetic.mix(a[2], b[2], t)
            };
            return Arithmetic.median(c[0], c[1], c[2]) == c[channel];
        }
        return false;
    }

    private static int edgeBetweenTexels(float[] a, float[] b) {
        return (EdgeColor.RED * (edgeBetweenTexelsChannel(a, b, 0) ? 1 : 0)) +
                (EdgeColor.GREEN * (edgeBetweenTexelsChannel(a, b, 1) ? 1 : 0)) +
                (EdgeColor.BLUE * (edgeBetweenTexelsChannel(a, b, 2) ? 1 : 0));
    }

    private static void protectExtremeChannels(ByteBitmapRef stencil, int x, int y, float[] msd, float m, int mask) {
        if ((mask & EdgeColor.RED) != 0 && msd[0] != m ||
                (mask & EdgeColor.GREEN) != 0 && msd[1] != m ||
                (mask & EdgeColor.BLUE) != 0 && msd[2] != m) {
            stencil.pixels[stencil.getIndex(x, y)] |= (byte) Flags.PROTECTED.value;
        }
    }

    private static float interpolatedMedian(float[] a, float[] b, float t) {
        return Arithmetic.median(
                Arithmetic.mix(a[0], b[0], t),
                Arithmetic.mix(a[1], b[1], t),
                Arithmetic.mix(a[2], b[2], t)
        );
    }

    private static float interpolatedMedian(float[] a, float[] l, float[] q, double t) {
        return (float) Arithmetic.median(
                t * (t * q[0] + l[0]) + a[0],
                t * (t * q[1] + l[1]) + a[1],
                t * (t * q[2] + l[2]) + a[2]
        );
    }

    private static <T extends BaseArtifactClassifier> boolean hasLinearArtifact(T artifactClassifier, float am, float[] a, float[] b) {
        var bm = Arithmetic.median(b[0], b[1], b[2]);
        return (Math.abs(am - 0.5f) >= Math.abs(bm - 0.5f) &&
                (hasLinearArtifactInner(artifactClassifier, am, bm, a, b, a[1] - a[0], b[1] - b[0]) ||
                        hasLinearArtifactInner(artifactClassifier, am, bm, a, b, a[2] - a[1], b[2] - b[1]) ||
                        hasLinearArtifactInner(artifactClassifier, am, bm, a, b, a[0] - a[2], b[0] - b[2])));
    }

    private static <T extends BaseArtifactClassifier> boolean hasLinearArtifactInner(T artifactClassifier, float am, float bm, float[] a, float[] b, float dA, float dB) {
        final var ARTIFACT_T_EPSILON = 0.01;
        if (dA == dB) return false;
        var t = dA / (dA - dB);
        if (t > ARTIFACT_T_EPSILON && t < 1 - ARTIFACT_T_EPSILON) {
            var xm = interpolatedMedian(a, b, t);
            return artifactClassifier.evaluate(t, xm, artifactClassifier.rangeTest(0, 1, t, am, bm, xm));
        }
        return false;
    }

    private static <T extends BaseArtifactClassifier> boolean hasDiagonalArtifact(T artifactClassifier, float am, float[] a, float[] b, float[] c, float[] d) {
        var dm = Arithmetic.median(d[0], d[1], d[2]);
        if (Math.abs(am - 0.5f) >= Math.abs(dm - 0.5f)) {
            var abc = new float[]{a[0] - b[0] - c[0], a[1] - b[1] - c[1], a[2] - b[2] - c[2]};
            var l = new float[]{-a[0] - abc[0], -a[1] - abc[1], -a[2] - abc[2]};
            var q = new float[]{d[0] + abc[0], d[1] + abc[1], d[2] + abc[2]};
            var tEx = new double[]{q[0] == 0 ? -1 : -0.5 * l[0] / q[0], q[1] == 0 ? -1 : -0.5 * l[1] / q[1], q[2] == 0 ? -1 : -0.5 * l[2] / q[2]};

            return (hasDiagonalArtifactInner(artifactClassifier, am, dm, a, l, q, a[1] - a[0], b[1] - b[0] + c[1] - c[0], d[1] - d[0], tEx[0], tEx[1]) ||
                    hasDiagonalArtifactInner(artifactClassifier, am, dm, a, l, q, a[2] - a[1], b[2] - b[1] + c[2] - c[1], d[2] - d[1], tEx[1], tEx[2]) ||
                    hasDiagonalArtifactInner(artifactClassifier, am, dm, a, l, q, a[0] - a[2], b[0] - b[2] + c[0] - c[2], d[0] - d[2], tEx[2], tEx[0]));
        }
        return false;
    }

    private static <T extends BaseArtifactClassifier> boolean hasDiagonalArtifactInner(T artifactClassifier, float am, float dm, float[] a, float[] l, float[] q, float dA, float dBC, float dD, double tEx0, double tEx1) {
        var t = new double[2];
        var solutions = EquationSolver.solveQuadratic(t, dD - dBC + dA, dBC - dA - dA, dA);
        for (var i = 0; i < solutions; ++i) {
            if (t[i] > ARTIFACT_T_EPSILON && t[i] < 1 - ARTIFACT_T_EPSILON) {
                var xm = interpolatedMedian(a, l, q, t[i]);
                var rangeFlags = artifactClassifier.rangeTest(0, 1, t[i], am, dm, xm);

                var tEnd = new double[2];
                var em = new float[2];

                if (tEx0 > 0 && tEx0 < 1) {
                    tEnd[0] = 0;
                    tEnd[1] = 1;
                    em[0] = am;
                    em[1] = dm;
                    tEnd[tEx0 > t[i] ? 1 : 0] = tEx0;
                    em[tEx0 > t[i] ? 1 : 0] = interpolatedMedian(a, l, q, tEx0);
                    rangeFlags |= artifactClassifier.rangeTest(tEnd[0], tEnd[1], t[i], em[0], em[1], xm);
                }
                if (tEx1 > 0 && tEx1 < 1) {
                    tEnd[0] = 0;
                    tEnd[1] = 1;
                    em[0] = am;
                    em[1] = dm;
                    tEnd[tEx1 > t[i] ? 1 : 0] = tEx1;
                    em[tEx1 > t[i] ? 1 : 0] = interpolatedMedian(a, l, q, tEx1);
                    rangeFlags |= artifactClassifier.rangeTest(tEnd[0], tEnd[1], t[i], em[0], em[1], xm);
                }

                if (artifactClassifier.evaluate(t[i], xm, rangeFlags))
                    return true;
            }
        }
        return false;
    }

    public void setMinDeviationRatio(double minDeviationRatio) {
        this.minDeviationRatio = minDeviationRatio;
    }

    public void setMinImproveRatio(double minImproveRatio) {
        this.minImproveRatio = minImproveRatio;
    }

    public void protectCorners(Shape shape) {
        stencil.reorient(YAxisOrientation.values()[shape.getYAxisOrientation()]);
        for (var contour : shape.contours) {
            if (!contour.edges.isEmpty()) {
                var prevEdge = contour.edges.getLast().get();
                for (var edgeHolder : contour.edges) {
                    var edge = edgeHolder.get();
                    var commonColor = prevEdge.color & edge.color;
                    if ((commonColor & (commonColor - 1)) == 0) {
                        var p = transformation.project(edge.point(0));
                        var l = Mth.floor(p.x - 0.5);
                        var b = Mth.floor(p.y - 0.5);
                        var r = l + 1;
                        var t = b + 1;
                        if (l < stencil.width && b < stencil.height && r >= 0 && t >= 0) {
                            if (l >= 0 && b >= 0)
                                stencil.pixels[stencil.getIndex(l, b)] |= (byte) Flags.PROTECTED.value;
                            if (r < stencil.width && b >= 0)
                                stencil.pixels[stencil.getIndex(r, b)] |= (byte) Flags.PROTECTED.value;
                            if (l >= 0 && t < stencil.height)
                                stencil.pixels[stencil.getIndex(l, t)] |= (byte) Flags.PROTECTED.value;
                            if (r < stencil.width && t < stencil.height)
                                stencil.pixels[stencil.getIndex(r, t)] |= (byte) Flags.PROTECTED.value;
                        }
                    }
                    prevEdge = edge;
                }
            }
        }
    }

    public void protectEdges(FloatBitmapRef sdf) {
        stencil.reorient(sdf.yOrientation);
        final var PROTECTION_RADIUS_TOLERANCE = 1.001;

        var radius = (float) (PROTECTION_RADIUS_TOLERANCE * transformation.unprojectVector(new Vector2(transformation.distanceMapping.apply(new DistanceMapping.Delta(1)), 0)).length());
        for (var y = 0; y < sdf.height; ++y) {
            for (var x = 0; x < sdf.width - 1; ++x) {
                var left = getPixel(sdf, x, y);
                var right = getPixel(sdf, x + 1, y);

                var lm = Arithmetic.median(left[0], left[1], left[2]);
                var rm = Arithmetic.median(right[0], right[1], right[2]);
                if (Math.abs(lm - 0.5f) + Math.abs(rm - 0.5f) < radius) {
                    var mask = edgeBetweenTexels(left, right);
                    protectExtremeChannels(stencil, x, y, left, lm, mask);
                    protectExtremeChannels(stencil, x + 1, y, right, rm, mask);
                }
            }
        }

        radius = (float) (PROTECTION_RADIUS_TOLERANCE * transformation.unprojectVector(new Vector2(0, transformation.distanceMapping.apply(new DistanceMapping.Delta(1)))).length());
        for (var y = 0; y < sdf.height - 1; ++y) {
            for (var x = 0; x < sdf.width; ++x) {
                var bottom = getPixel(sdf, x, y);
                var top = getPixel(sdf, x, y + 1);

                var bm = Arithmetic.median(bottom[0], bottom[1], bottom[2]);
                var tm = Arithmetic.median(top[0], top[1], top[2]);
                if (Math.abs(bm - 0.5f) + Math.abs(tm - 0.5f) < radius) {
                    var mask = edgeBetweenTexels(bottom, top);
                    protectExtremeChannels(stencil, x, y, bottom, bm, mask);
                    protectExtremeChannels(stencil, x, y + 1, top, tm, mask);
                }
            }
        }

        radius = (float) (PROTECTION_RADIUS_TOLERANCE * transformation.unprojectVector(new Vector2(transformation.distanceMapping.apply(new DistanceMapping.Delta(1)), transformation.distanceMapping.apply(new DistanceMapping.Delta(1)))).length());
        for (var y = 0; y < sdf.height - 1; ++y) {
            for (var x = 0; x < sdf.width - 1; ++x) {
                var lb = getPixel(sdf, x, y);
                var rb = getPixel(sdf, x + 1, y);
                var lt = getPixel(sdf, x, y + 1);
                var rt = getPixel(sdf, x + 1, y + 1);

                var mlb = Arithmetic.median(lb[0], lb[1], lb[2]);
                var mrb = Arithmetic.median(rb[0], rb[1], rb[2]);
                var mlt = Arithmetic.median(lt[0], lt[1], lt[2]);
                var mrt = Arithmetic.median(rt[0], rt[1], rt[2]);
                if (Math.abs(mlb - 0.5f) + Math.abs(mrt - 0.5f) < radius) {
                    var mask = edgeBetweenTexels(lb, rt);
                    protectExtremeChannels(stencil, x, y, lb, mlb, mask);
                    protectExtremeChannels(stencil, x + 1, y + 1, rt, mrt, mask);
                }
                if (Math.abs(mrb - 0.5f) + Math.abs(mlt - 0.5f) < radius) {
                    var mask = edgeBetweenTexels(rb, lt);
                    protectExtremeChannels(stencil, x + 1, y, rb, mrb, mask);
                    protectExtremeChannels(stencil, x, y + 1, lt, mlt, mask);
                }
            }
        }
    }

    public void protectAll() {
        for (var y = 0; y < stencil.height; ++y) {
            for (var x = 0; x < stencil.width; ++x) {
                stencil.pixels[stencil.getIndex(x, y)] |= (byte) Flags.PROTECTED.value;
            }
        }
    }

    public void findErrors(FloatBitmapRef sdf) {
        stencil.reorient(sdf.yOrientation);
        var hSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(transformation.distanceMapping.apply(new DistanceMapping.Delta(1)), 0)).length();
        var vSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(0, transformation.distanceMapping.apply(new DistanceMapping.Delta(1)))).length();
        var dSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(transformation.distanceMapping.apply(new DistanceMapping.Delta(1)), transformation.distanceMapping.apply(new DistanceMapping.Delta(1)))).length();

        IntStream.range(0, sdf.height).parallel().forEach(y -> {
            for (var x = 0; x < sdf.width; ++x) {
                var c = getPixel(sdf, x, y);
                var cm = Arithmetic.median(c[0], c[1], c[2]);
                var protectedFlag = (stencil.pixels[stencil.getIndex(x, y)] & Flags.PROTECTED.value) != 0;

                var l = x > 0 ? getPixel(sdf, x - 1, y) : null;
                var b = y > 0 ? getPixel(sdf, x, y - 1) : null;
                var r = x < sdf.width - 1 ? getPixel(sdf, x + 1, y) : null;
                var t = y < sdf.height - 1 ? getPixel(sdf, x, y + 1) : null;

                var hasError =
                        (l != null && hasLinearArtifact(new BaseArtifactClassifier(hSpan, protectedFlag), cm, c, l)) ||
                                (b != null && hasLinearArtifact(new BaseArtifactClassifier(vSpan, protectedFlag), cm, c, b)) ||
                                (r != null && hasLinearArtifact(new BaseArtifactClassifier(hSpan, protectedFlag), cm, c, r)) ||
                                (t != null && hasLinearArtifact(new BaseArtifactClassifier(vSpan, protectedFlag), cm, c, t)) ||
                                (l != null && b != null && hasDiagonalArtifact(new BaseArtifactClassifier(dSpan, protectedFlag), cm, c, l, b, getPixel(sdf, x - 1, y - 1))) ||
                                (r != null && b != null && hasDiagonalArtifact(new BaseArtifactClassifier(dSpan, protectedFlag), cm, c, r, b, getPixel(sdf, x + 1, y - 1))) ||
                                (l != null && t != null && hasDiagonalArtifact(new BaseArtifactClassifier(dSpan, protectedFlag), cm, c, l, t, getPixel(sdf, x - 1, y + 1))) ||
                                (r != null && t != null && hasDiagonalArtifact(new BaseArtifactClassifier(dSpan, protectedFlag), cm, c, r, t, getPixel(sdf, x + 1, y + 1)));

                if (hasError) {
                    stencil.pixels[stencil.getIndex(x, y)] |= (byte) Flags.ERROR.value;
                }
            }
        });
    }

    public void findErrors(FloatBitmapRef sdf, Shape shape, boolean overlapSupport) {
        sdf.reorient(YAxisOrientation.values()[shape.getYAxisOrientation()]);
        stencil.reorient(sdf.yOrientation);
        var hSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(transformation.distanceMapping.apply(new DistanceMapping.Delta(1)), 0)).length();
        var vSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(0, transformation.distanceMapping.apply(new DistanceMapping.Delta(1)))).length();
        var dSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(transformation.distanceMapping.apply(new DistanceMapping.Delta(1)), transformation.distanceMapping.apply(new DistanceMapping.Delta(1)))).length();

        IntStream.range(0, sdf.height).parallel().forEach(y -> {
            var combiner = overlapSupport ? new OverlappingPSDFCombiner(shape) : new SimplePSDFCombiner();
            var shapeDistanceChecker = new ShapeDistanceChecker(sdf, shape, transformation, minImproveRatio, combiner);

            var xDirection = (y % 2 == 0) ? 1 : -1;
            var x = xDirection < 0 ? sdf.width - 1 : 0;

            for (var col = 0; col < sdf.width; ++col, x += xDirection) {
                if ((stencil.pixels[stencil.getIndex(x, y)] & Flags.ERROR.value) != 0) continue;

                var c = getPixel(sdf, x, y);
                var protectedFlag = (stencil.pixels[stencil.getIndex(x, y)] & Flags.PROTECTED.value) != 0;

                shapeDistanceChecker.shapeCoord = transformation.unproject(new Point2(x + 0.5, y + 0.5));
                shapeDistanceChecker.sdfCoord = new Point2(x + 0.5, y + 0.5);
                shapeDistanceChecker.msd = c;
                shapeDistanceChecker.protectedFlag = protectedFlag;
                var cm = Arithmetic.median(c[0], c[1], c[2]);

                var l = x > 0 ? getPixel(sdf, x - 1, y) : null;
                var b = y > 0 ? getPixel(sdf, x, y - 1) : null;
                var r = x < sdf.width - 1 ? getPixel(sdf, x + 1, y) : null;
                var t = y < sdf.height - 1 ? getPixel(sdf, x, y + 1) : null;

                var hasError =
                        (l != null && hasLinearArtifact(shapeDistanceChecker.classifier(new Vector2(-1, 0), hSpan), cm, c, l)) ||
                                (b != null && hasLinearArtifact(shapeDistanceChecker.classifier(new Vector2(0, -1), vSpan), cm, c, b)) ||
                                (r != null && hasLinearArtifact(shapeDistanceChecker.classifier(new Vector2(1, 0), hSpan), cm, c, r)) ||
                                (t != null && hasLinearArtifact(shapeDistanceChecker.classifier(new Vector2(0, 1), vSpan), cm, c, t)) ||
                                (l != null && b != null && hasDiagonalArtifact(shapeDistanceChecker.classifier(new Vector2(-1, -1), dSpan), cm, c, l, b, getPixel(sdf, x - 1, y - 1))) ||
                                (r != null && b != null && hasDiagonalArtifact(shapeDistanceChecker.classifier(new Vector2(1, -1), dSpan), cm, c, r, b, getPixel(sdf, x + 1, y - 1))) ||
                                (l != null && t != null && hasDiagonalArtifact(shapeDistanceChecker.classifier(new Vector2(-1, 1), dSpan), cm, c, l, t, getPixel(sdf, x - 1, y + 1))) ||
                                (r != null && t != null && hasDiagonalArtifact(shapeDistanceChecker.classifier(new Vector2(1, 1), dSpan), cm, c, r, t, getPixel(sdf, x + 1, y + 1)));

                if (hasError) {
                    stencil.pixels[stencil.getIndex(x, y)] |= (byte) Flags.ERROR.value;
                }
            }
        });
    }

    public void apply(FloatBitmapRef sdf) {
        sdf.reorient(stencil.yOrientation);
        IntStream.range(0, sdf.height).parallel().forEach(y -> {
            for (var x = 0; x < sdf.width; ++x) {
                if ((stencil.pixels[stencil.getIndex(x, y)] & Flags.ERROR.value) != 0) {
                    var pixel = getPixel(sdf, x, y);
                    var m = Arithmetic.median(pixel[0], pixel[1], pixel[2]);
                    pixel[0] = m;
                    pixel[1] = m;
                    pixel[2] = m;
                    System.arraycopy(pixel, 0, sdf.pixels, sdf.getIndex(x, y), pixel.length);
                }
            }
        });
    }

    private float[] getPixel(FloatBitmapRef sdf, int x, int y) {
        var pixel = new float[sdf.nChannels];
        System.arraycopy(sdf.pixels, sdf.getIndex(x, y), pixel, 0, sdf.nChannels);
        return pixel;
    }

    public enum Flags {
        ERROR(1),
        PROTECTED(2);

        public final int value;

        Flags(int value) {
            this.value = value;
        }
    }

    private static class BaseArtifactClassifier {
        protected static final int CLASSIFIER_FLAG_CANDIDATE = 0x01;
        protected static final int CLASSIFIER_FLAG_ARTIFACT = 0x02;
        protected final double span;
        protected final boolean protectedFlag;

        public BaseArtifactClassifier(double span, boolean protectedFlag) {
            this.span = span;
            this.protectedFlag = protectedFlag;
        }

        public int rangeTest(double at, double bt, double xt, float am, float bm, float xm) {
            if ((am > 0.5f && bm > 0.5f && xm <= 0.5f) || (am < 0.5f && bm < 0.5f && xm >= 0.5f) || (!protectedFlag && Arithmetic.median(am, bm, xm) != xm)) {
                var axSpan = (xt - at) * span;
                var bxSpan = (bt - xt) * span;
                if (!(xm >= am - axSpan && xm <= am + axSpan && xm >= bm - bxSpan && xm <= bm + bxSpan)) {
                    return CLASSIFIER_FLAG_CANDIDATE | CLASSIFIER_FLAG_ARTIFACT;
                }
                return CLASSIFIER_FLAG_CANDIDATE;
            }
            return 0;
        }

        public boolean evaluate(double t, float m, int flags) {
            return (flags & CLASSIFIER_FLAG_ARTIFACT) != 0;
        }
    }

    private static class ShapeDistanceChecker {
        private final FloatBitmapRef sdf;
        private final Vector2 texelSize;
        private final double minImproveRatio;
        private final ShapeDistanceFinder<Double> distanceFinder;
        private final SDFTransformation transformation;
        public Point2 shapeCoord, sdfCoord;
        public float[] msd;
        public boolean protectedFlag;

        public ShapeDistanceChecker(FloatBitmapRef sdf, Shape shape, SDFTransformation transformation, double minImproveRatio, ContourCombiner<Double> combiner) {
            this.sdf = sdf;
            this.transformation = transformation;
            this.minImproveRatio = minImproveRatio;
            distanceFinder = new ShapeDistanceFinder<>(shape, combiner);
            texelSize = transformation.unprojectVector(new Vector2(1, 1));
        }

        public ArtifactClassifier classifier(Vector2 direction, double span) {
            return new ArtifactClassifier(this, direction, span);
        }

        private static class ArtifactClassifier extends BaseArtifactClassifier {
            private final ShapeDistanceChecker parent;
            private final Vector2 direction;

            public ArtifactClassifier(ShapeDistanceChecker parent, Vector2 direction, double span) {
                super(span, parent.protectedFlag);
                this.parent = parent;
                this.direction = direction;
            }

            @Override
            public boolean evaluate(double t, float m, int flags) {
                if ((flags & CLASSIFIER_FLAG_CANDIDATE) != 0) {
                    if ((flags & CLASSIFIER_FLAG_ARTIFACT) != 0) return true;

                    var tVector = direction.multiply(t);
                    var oldMSD = new float[parent.sdf.nChannels];
                    var newMSD = new float[3];
                    var sdfCoord = new Point2(parent.sdfCoord.add(tVector));
                    BitmapInterpolation.interpolate(oldMSD, parent.sdf, sdfCoord);

                    var aWeight = (1 - Math.abs(tVector.x)) * (1 - Math.abs(tVector.y));
                    var aPSD = Arithmetic.median(parent.msd[0], parent.msd[1], parent.msd[2]);
                    newMSD[0] = (float) (oldMSD[0] + aWeight * (aPSD - parent.msd[0]));
                    newMSD[1] = (float) (oldMSD[1] + aWeight * (aPSD - parent.msd[1]));
                    newMSD[2] = (float) (oldMSD[2] + aWeight * (aPSD - parent.msd[2]));

                    var oldPSD = Arithmetic.median(oldMSD[0], oldMSD[1], oldMSD[2]);
                    var newPSD = Arithmetic.median(newMSD[0], newMSD[1], newMSD[2]);
                    var dist = parent.distanceFinder.distance(new Point2(parent.shapeCoord.add(tVector.multiply(parent.texelSize))));
                    var refPSD = (float) parent.transformation.distanceMapping.apply(dist);

                    return parent.minImproveRatio * Math.abs(newPSD - refPSD) < Math.abs(oldPSD - refPSD);
                }
                return false;
            }
        }
    }
}