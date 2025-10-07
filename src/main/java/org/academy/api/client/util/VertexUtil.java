package org.academy.api.client.util;

import net.minecraft.world.phys.AABB;
import org.academy.api.common.util.MathUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VertexUtil {
    private VertexUtil() {
    }

    public static final class Ring {
        public static float[][][] getRingVertexBuffer(float radius, int segments, float yBottom, float yTop) {
            var vertexBuffer = new float[segments][4][4];
            final var twoPi = MathUtil.TWO_PI;

            for (var i = 0; i < segments; i++) {
                var angle1 = (i * twoPi) / segments;
                var angle2 = ((i + 1) * twoPi) / segments;
                var u0 = (float) i / segments;
                var u1 = (float) (i + 1) / segments;

                var cos1 = (float) Math.cos(angle1);
                var sin1 = (float) Math.sin(angle1);
                var cos2 = (float) Math.cos(angle2);
                var sin2 = (float) Math.sin(angle2);

                var x1 = cos1 * radius;
                var z1 = sin1 * radius;
                var x2 = cos2 * radius;
                var z2 = sin2 * radius;

                vertexBuffer[i][0] = new float[]{x1, yBottom, z1, u0};
                vertexBuffer[i][1] = new float[]{x2, yBottom, z2, u1};
                vertexBuffer[i][2] = new float[]{x2, yTop, z2, u1};
                vertexBuffer[i][3] = new float[]{x1, yTop, z1, u0};
            }
            return vertexBuffer;
        }

        public static float[][][] getVerticalVertexBuffer(float radius, float height, int segments) {
            return getRingVertexBuffer(radius, segments, 0f, height);
        }
    }

    public static final class Cylinder {
        public static float[][] getCylinderVertexBuffer(
                final float yBottom,
                final float yTop,
                final float radius,
                final int faces,
                final boolean capped) {
            if (radius <= 0f || faces < 3) {
                return new float[0][];
            }

            var sideVertices = faces * 4;
            var capVertices = capped ? faces * 4 * 2 : 0;
            var totalVertices = sideVertices + capVertices;
            var vertexComponents = 8;

            var vertexBuffer = new float[totalVertices][vertexComponents];
            var angleStepd = 2.0d * Math.PI / faces;
            var vertexIndex = 0;

            for (var i = 0; i < faces; i++) {
                var angle1d = i * angleStepd;
                var angle2d = (i + 1) * angleStepd;

                var x1d = radius * Math.cos(angle1d);
                var z1d = radius * Math.sin(angle1d);
                var x2d = radius * Math.cos(angle2d);
                var z2d = radius * Math.sin(angle2d);

                var u1f = (float) i / faces;
                var u2f = (float) (i + 1) / faces;
                var vBottomf = 0.0f;
                var vTopf = 1.0f;

                var midAngled = (angle1d + angle2d) / 2.0d;
                var faceNormalXd = Math.cos(midAngled);
                var faceNormalZd = Math.sin(midAngled);

                var nx = (float) faceNormalXd;
                var nz = (float) faceNormalZd;

                vertexBuffer[vertexIndex++] = new float[]{(float) x1d, yBottom, (float) z1d, u1f, vBottomf, nx, 0f, nz};
                vertexBuffer[vertexIndex++] = new float[]{(float) x2d, yBottom, (float) z2d, u2f, vBottomf, nx, 0f, nz};
                vertexBuffer[vertexIndex++] = new float[]{(float) x2d, yTop, (float) z2d, u2f, vTopf, nx, 0f, nz};
                vertexBuffer[vertexIndex++] = new float[]{(float) x1d, yTop, (float) z1d, u1f, vTopf, nx, 0f, nz};
            }

            if (capped) {
                var centerXd = 0.0d;
                var centerZd = 0.0d;
                var uCenterf = 0.5f;
                var vCenterf = 0.5f;

                for (var i = 0; i < faces; i++) {
                    var angle1d = i * angleStepd;
                    var angle2d = (i + 1) * angleStepd;

                    var x1d = radius * Math.cos(angle1d);
                    var z1d = radius * Math.sin(angle1d);
                    var x2d = radius * Math.cos(angle2d);
                    var z2d = radius * Math.sin(angle2d);

                    var u1f = (float) (x1d / (2.0d * radius) + 0.5d);
                    var v1f = (float) (z1d / (2.0d * radius) + 0.5d);
                    var u2f = (float) (x2d / (2.0d * radius) + 0.5d);
                    var v2f = (float) (z2d / (2.0d * radius) + 0.5d);

                    var topCapNormal = new float[]{0f, 1f, 0f};
                    var v1 = new float[]{(float) centerXd, yTop, (float) centerZd, uCenterf, vCenterf, topCapNormal[0], topCapNormal[1], topCapNormal[2]};
                    var v2 = new float[]{(float) x1d, yTop, (float) z1d, u1f, v1f, topCapNormal[0], topCapNormal[1], topCapNormal[2]};
                    var v3 = new float[]{(float) x2d, yTop, (float) z2d, u2f, v2f, topCapNormal[0], topCapNormal[1], topCapNormal[2]};

                    vertexBuffer[vertexIndex++] = v1;
                    vertexBuffer[vertexIndex++] = v2;
                    vertexBuffer[vertexIndex++] = v3;
                    vertexBuffer[vertexIndex++] = v3;
                }

                for (var i = 0; i < faces; i++) {
                    var angle1d = i * angleStepd;
                    var angle2d = (i + 1) * angleStepd;

                    var x1d = radius * Math.cos(angle1d);
                    var z1d = radius * Math.sin(angle1d);
                    var x2d = radius * Math.cos(angle2d);
                    var z2d = radius * Math.sin(angle2d);

                    var u1f = (float) (x1d / (2.0d * radius) + 0.5d);
                    var v1f = (float) (z1d / (2.0d * radius) + 0.5d);
                    var u2f = (float) (x2d / (2.0d * radius) + 0.5d);
                    var v2f = (float) (z2d / (2.0d * radius) + 0.5d);

                    var bottomCapNormal = new float[]{0f, -1f, 0f};
                    var v1 = new float[]{(float) centerXd, yBottom, (float) centerZd, uCenterf, vCenterf, bottomCapNormal[0], bottomCapNormal[1], bottomCapNormal[2]};
                    var v2 = new float[]{(float) x2d, yBottom, (float) z2d, u2f, v2f, bottomCapNormal[0], bottomCapNormal[1], bottomCapNormal[2]};
                    var v3 = new float[]{(float) x1d, yBottom, (float) z1d, u1f, v1f, bottomCapNormal[0], bottomCapNormal[1], bottomCapNormal[2]};

                    vertexBuffer[vertexIndex++] = v1;
                    vertexBuffer[vertexIndex++] = v2;
                    vertexBuffer[vertexIndex++] = v3;
                    vertexBuffer[vertexIndex++] = v3;
                }
            }

            return vertexBuffer;
        }

        public static float[][] getCylinderWireframeBuffer(
                final float yBottom,
                final float yTop,
                final float radius,
                final int faces) {
            if (radius <= 0f || faces < 3) {
                return new float[0][];
            }

            var totalLines = faces * 3;
            var totalVertices = totalLines * 2;
            var vertexBuffer = new float[totalVertices][6];
            var vertexIndex = 0;

            var angleStepd = 2.0d * Math.PI / faces;

            for (var i = 0; i < faces; i++) {
                var angle1d = i * angleStepd;
                var angle2d = (i + 1) * angleStepd;

                var x1d = radius * Math.cos(angle1d);
                var z1d = radius * Math.sin(angle1d);
                var x2d = radius * Math.cos(angle2d);
                var z2d = radius * Math.sin(angle2d);

                var nx1f = (float) Math.cos(angle1d);
                var nz1f = (float) Math.sin(angle1d);
                var nx2f = (float) Math.cos(angle2d);
                var nz2f = (float) Math.sin(angle2d);

                var x1f = (float) x1d;
                var z1f = (float) z1d;
                var x2f = (float) x2d;
                var z2f = (float) z2d;

                vertexBuffer[vertexIndex++] = new float[]{x1f, yTop, z1f, nx1f, 0f, nz1f};
                vertexBuffer[vertexIndex++] = new float[]{x2f, yTop, z2f, nx2f, 0f, nz2f};

                vertexBuffer[vertexIndex++] = new float[]{x1f, yBottom, z1f, nx1f, 0f, nz1f};
                vertexBuffer[vertexIndex++] = new float[]{x2f, yBottom, z2f, nx2f, 0f, nz2f};

                vertexBuffer[vertexIndex++] = new float[]{x1f, yTop, z1f, nx1f, 0f, nz1f};
                vertexBuffer[vertexIndex++] = new float[]{x1f, yBottom, z1f, nx1f, 0f, nz1f};
            }

            return vertexBuffer;
        }
    }

    public static final class Box {
        public static float[][][] getBoxVertices(AABB box) {
            var minX = (float) box.minX;
            var minY = (float) box.minY;
            var minZ = (float) box.minZ;
            var maxX = (float) box.maxX;
            var maxY = (float) box.maxY;
            var maxZ = (float) box.maxZ;

            var faces = new float[6][4][3];

            faces[0][0] = new float[]{minX, minY, maxZ};
            faces[0][1] = new float[]{maxX, minY, maxZ};
            faces[0][2] = new float[]{maxX, maxY, maxZ};
            faces[0][3] = new float[]{minX, maxY, maxZ};

            faces[1][0] = new float[]{maxX, minY, minZ};
            faces[1][1] = new float[]{minX, minY, minZ};
            faces[1][2] = new float[]{minX, maxY, minZ};
            faces[1][3] = new float[]{maxX, maxY, minZ};

            faces[2][0] = new float[]{minX, maxY, maxZ};
            faces[2][1] = new float[]{maxX, maxY, maxZ};
            faces[2][2] = new float[]{maxX, maxY, minZ};
            faces[2][3] = new float[]{minX, maxY, minZ};

            faces[3][0] = new float[]{minX, minY, minZ};
            faces[3][1] = new float[]{maxX, minY, minZ};
            faces[3][2] = new float[]{maxX, minY, maxZ};
            faces[3][3] = new float[]{minX, minY, maxZ};

            faces[4][0] = new float[]{maxX, minY, maxZ};
            faces[4][1] = new float[]{maxX, minY, minZ};
            faces[4][2] = new float[]{maxX, maxY, minZ};
            faces[4][3] = new float[]{maxX, maxY, maxZ};

            faces[5][0] = new float[]{minX, minY, minZ};
            faces[5][1] = new float[]{minX, minY, maxZ};
            faces[5][2] = new float[]{minX, maxY, maxZ};
            faces[5][3] = new float[]{minX, maxY, minZ};

            return faces;
        }
    }

    public static final class Ball {
        private record TriangleIndices(int v1, int v2, int v3) {
        }

        private static float[] normalize(float[] v) {
            var length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (length > 1e-6) {
                return new float[]{v[0] / length, v[1] / length, v[2] / length};
            }
            return new float[]{0, 0, 0};
        }

        private static float[] cross(float[] a, float[] b) {
            return new float[]{
                    a[1] * b[2] - a[2] * b[1],
                    a[2] * b[0] - a[0] * b[2],
                    a[0] * b[1] - a[1] * b[0]
            };
        }

        private static float[] subtract(float[] a, float[] b) {
            return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
        }

        private static int getMiddlePoint(int p1, int p2, List<float[]> vertices, Map<Long, Integer> cache) {
            var smallerIndex = Math.min(p1, p2);
            var greaterIndex = Math.max(p1, p2);
            var key = ((long) smallerIndex << 32) + greaterIndex;

            var cachedIndex = cache.get(key);
            if (cachedIndex != null) {
                return cachedIndex;
            }

            var point1 = vertices.get(p1);
            var point2 = vertices.get(p2);
            var middle = new float[]{
                    (point1[0] + point2[0]) / 2.0f,
                    (point1[1] + point2[1]) / 2.0f,
                    (point1[2] + point2[2]) / 2.0f
            };

            var index = vertices.size();
            vertices.add(normalize(middle));
            cache.put(key, index);
            return index;
        }

        public static float[][] getIcosphereVertexBuffer(float radius, int subdivisions, boolean smoothNormals) {
            var vertices = new ArrayList<float[]>();
            var faces = new ArrayList<TriangleIndices>();
            var t = (1.0f + (float) Math.sqrt(5.0)) / 2.0f;

            vertices.add(normalize(new float[]{-1, t, 0}));
            vertices.add(normalize(new float[]{1, t, 0}));
            vertices.add(normalize(new float[]{-1, -t, 0}));
            vertices.add(normalize(new float[]{1, -t, 0}));
            vertices.add(normalize(new float[]{0, -1, t}));
            vertices.add(normalize(new float[]{0, 1, t}));
            vertices.add(normalize(new float[]{0, -1, -t}));
            vertices.add(normalize(new float[]{0, 1, -t}));
            vertices.add(normalize(new float[]{t, 0, -1}));
            vertices.add(normalize(new float[]{t, 0, 1}));
            vertices.add(normalize(new float[]{-t, 0, -1}));
            vertices.add(normalize(new float[]{-t, 0, 1}));

            faces.add(new TriangleIndices(0, 11, 5));
            faces.add(new TriangleIndices(0, 5, 1));
            faces.add(new TriangleIndices(0, 1, 7));
            faces.add(new TriangleIndices(0, 7, 10));
            faces.add(new TriangleIndices(0, 10, 11));
            faces.add(new TriangleIndices(1, 5, 9));
            faces.add(new TriangleIndices(5, 11, 4));
            faces.add(new TriangleIndices(11, 10, 2));
            faces.add(new TriangleIndices(10, 7, 6));
            faces.add(new TriangleIndices(7, 1, 8));
            faces.add(new TriangleIndices(3, 9, 4));
            faces.add(new TriangleIndices(3, 4, 2));
            faces.add(new TriangleIndices(3, 2, 6));
            faces.add(new TriangleIndices(3, 6, 8));
            faces.add(new TriangleIndices(3, 8, 9));
            faces.add(new TriangleIndices(4, 9, 5));
            faces.add(new TriangleIndices(2, 4, 11));
            faces.add(new TriangleIndices(6, 2, 10));
            faces.add(new TriangleIndices(8, 6, 7));
            faces.add(new TriangleIndices(9, 8, 1));

            var middlePointCache = new HashMap<Long, Integer>();
            for (var i = 0; i < subdivisions; i++) {
                var faces2 = new ArrayList<TriangleIndices>();
                for (var tri : faces) {
                    var a = getMiddlePoint(tri.v1, tri.v2, vertices, middlePointCache);
                    var b = getMiddlePoint(tri.v2, tri.v3, vertices, middlePointCache);
                    var c = getMiddlePoint(tri.v3, tri.v1, vertices, middlePointCache);
                    faces2.add(new TriangleIndices(tri.v1, a, c));
                    faces2.add(new TriangleIndices(tri.v2, b, a));
                    faces2.add(new TriangleIndices(tri.v3, c, b));
                    faces2.add(new TriangleIndices(a, b, c));
                }
                faces = faces2;
            }

            var buffer = new float[faces.size() * 3][6];
            var bufferIndex = 0;
            for (var tri : faces) {
                var v1 = vertices.get(tri.v1);
                var v2 = vertices.get(tri.v2);
                var v3 = vertices.get(tri.v3);

                var n1 = v1;
                var n2 = v2;
                var n3 = v3;

                if (!smoothNormals) {
                    var faceNormal = normalize(cross(subtract(v2, v1), subtract(v3, v1)));
                    n1 = n2 = n3 = faceNormal;
                }

                buffer[bufferIndex++] = new float[]{v1[0] * radius, v1[1] * radius, v1[2] * radius, n1[0], n1[1], n1[2]};
                buffer[bufferIndex++] = new float[]{v2[0] * radius, v2[1] * radius, v2[2] * radius, n2[0], n2[1], n2[2]};
                buffer[bufferIndex++] = new float[]{v3[0] * radius, v3[1] * radius, v3[2] * radius, n3[0], n3[1], n3[2]};
            }
            return buffer;
        }
    }
}