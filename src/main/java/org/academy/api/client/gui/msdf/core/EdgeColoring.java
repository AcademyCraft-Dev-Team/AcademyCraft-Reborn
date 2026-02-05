package org.academy.api.client.gui.msdf.core;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class EdgeColoring {
    private static int symmetricalTrichotomy(int position, int n) {
        return (int) (3 + 2.875 * position / (n - 1) - 1.4375 + 0.5) - 3;
    }

    private static boolean isCorner(Vector2 aDir, Vector2 bDir, double crossThreshold) {
        return Vector2.dotProduct(aDir, bDir) <= 0 || Math.abs(Vector2.crossProduct(aDir, bDir)) > crossThreshold;
    }

    private static int seedExtract2(long[] seed) {
        var v = (int) (seed[0] & 1);
        seed[0] >>= 1;
        return v;
    }

    private static int seedExtract3(long[] seed) {
        var v = (int) (seed[0] % 3);
        seed[0] /= 3;
        return v;
    }

    private static int initColor(long[] seed) {
        var colors = new int[]{EdgeColor.CYAN, EdgeColor.MAGENTA, EdgeColor.YELLOW};
        return colors[seedExtract3(seed)];
    }

    private static int switchColor(int color, long[] seed) {
        var shifted = color << (1 + seedExtract2(seed));
        return (shifted | (shifted >> 3)) & EdgeColor.WHITE;
    }

    private static int switchColor(int color, long[] seed, int banned) {
        var combined = color & banned;
        if (combined == EdgeColor.RED || combined == EdgeColor.GREEN || combined == EdgeColor.BLUE) {
            return combined ^ EdgeColor.WHITE;
        } else {
            return switchColor(color, seed);
        }
    }

    public static void edgeColoringSimple(Shape shape, double angleThreshold, long seed) {
        var crossThreshold = Mth.sin(angleThreshold);
        var seedRef = new long[]{seed};
        var color = initColor(seedRef);
        List<Integer> corners = new ArrayList<>();

        for (var contour : shape.contours) {
            if (contour.edges.isEmpty()) {
                continue;
            }

            corners.clear();
            var prevDirection = contour.edges.getLast().get().direction(1);
            var index = 0;
            for (var edgeHolder : contour.edges) {
                if (isCorner(prevDirection.normalize(true), edgeHolder.get().direction(0).normalize(true), crossThreshold)) {
                    corners.add(index);
                }
                prevDirection = edgeHolder.get().direction(1);
                index++;
            }

            if (corners.isEmpty()) {
                color = switchColor(color, seedRef);
                for (var edgeHolder : contour.edges) {
                    edgeHolder.get().color = color;
                }
            } else if (corners.size() == 1) {
                var colors = new int[3];
                color = switchColor(color, seedRef);
                colors[0] = color;
                colors[1] = EdgeColor.WHITE;
                color = switchColor(color, seedRef);
                colors[2] = color;
                int corner = corners.getFirst();
                if (contour.edges.size() >= 3) {
                    var m = contour.edges.size();
                    for (var i = 0; i < m; i++) {
                        contour.edges.get((corner + i) % m).get().color = colors[1 + symmetricalTrichotomy(i, m)];
                    }
                } else if (!contour.edges.isEmpty()) {
                    var parts = new EdgeSegment[7];
                    contour.edges.getFirst().get().splitInThirds()[0] = parts[3 * corner];
                    contour.edges.get(0).get().splitInThirds()[1] = parts[1 + 3 * corner];
                    contour.edges.get(0).get().splitInThirds()[2] = parts[2 + 3 * corner];
                    if (contour.edges.size() >= 2) {
                        contour.edges.get(1).get().splitInThirds()[0] = parts[3 - 3 * corner];
                        contour.edges.get(1).get().splitInThirds()[1] = parts[4 - 3 * corner];
                        contour.edges.get(1).get().splitInThirds()[2] = parts[5 - 3 * corner];
                        parts[0].color = parts[1].color = colors[0];
                        parts[2].color = parts[3].color = colors[1];
                        parts[4].color = parts[5].color = colors[2];
                    } else {
                        parts[0].color = colors[0];
                        parts[1].color = colors[1];
                        parts[2].color = colors[2];
                    }
                    contour.edges.clear();
                    for (var part : parts) {
                        if (part != null) {
                            contour.edges.add(new EdgeHolder(part));
                        }
                    }
                }
            } else {
                var cornerCount = corners.size();
                var spline = 0;
                int start = corners.getFirst();
                var m = contour.edges.size();
                color = switchColor(color, seedRef);
                var initialColor = color;
                for (var i = 0; i < m; i++) {
                    var current_index = (start + i) % m;
                    if (spline + 1 < cornerCount && corners.get(spline + 1) == current_index) {
                        spline++;
                        color = switchColor(color, seedRef, (spline == cornerCount - 1) ? initialColor : 0);
                    }
                    contour.edges.get(current_index).get().color = color;
                }
            }
        }
    }
}