package org.academy.api.client.util;

import net.minecraft.world.phys.AABB;
import org.academy.api.common.util.MathUtil;

public class VertexUtil {
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
            if (radius <= 0 || faces < 3) {
                return new float[0][];
            }

            int totalVertices = faces * 6;
            if (capped) {
                totalVertices += faces * 3 * 2;
            }

            var vertexBuffer = new float[totalVertices][3];
            var angleStep = MathUtil.TWO_PI / faces;
            int vertexIndex = 0;

            for (int i = 0; i < faces; i++) {
                float angle1 = i * angleStep;
                float angle2 = (i + 1) * angleStep;

                float x1 = (float) (radius * Math.cos(angle1));
                float z1 = (float) (radius * Math.sin(angle1));
                float x2 = (float) (radius * Math.cos(angle2));
                float z2 = (float) (radius * Math.sin(angle2));

                vertexBuffer[vertexIndex++] = new float[]{x1, yBottom, z1};
                vertexBuffer[vertexIndex++] = new float[]{x2, yBottom, z2};
                vertexBuffer[vertexIndex++] = new float[]{x2, yTop, z2};

                vertexBuffer[vertexIndex++] = new float[]{x1, yBottom, z1};
                vertexBuffer[vertexIndex++] = new float[]{x2, yTop, z2};
                vertexBuffer[vertexIndex++] = new float[]{x1, yTop, z1};
            }

            if (capped) {
                float centerX_top = 0f;
                float centerZ_top = 0f;
                float centerX_bottom = 0f;
                float centerZ_bottom = 0f;


                for (int i = 0; i < faces; i++) {
                    float angle1 = i * angleStep;
                    float angle2 = (i + 1) * angleStep;

                    float x1 = (float) (radius * Math.cos(angle1));
                    float z1 = (float) (radius * Math.sin(angle1));
                    float x2 = (float) (radius * Math.cos(angle2));
                    float z2 = (float) (radius * Math.sin(angle2));

                    vertexBuffer[vertexIndex++] = new float[]{centerX_top, yTop, centerZ_top};
                    vertexBuffer[vertexIndex++] = new float[]{x1, yTop, z1};
                    vertexBuffer[vertexIndex++] = new float[]{x2, yTop, z2};
                }

                for (int i = 0; i < faces; i++) {
                    float angle1 = i * angleStep;
                    float angle2 = (i + 1) * angleStep;

                    float x1 = (float) (radius * Math.cos(angle1));
                    float z1 = (float) (radius * Math.sin(angle1));
                    float x2 = (float) (radius * Math.cos(angle2));
                    float z2 = (float) (radius * Math.sin(angle2));

                    vertexBuffer[vertexIndex++] = new float[]{centerX_bottom, yBottom, centerZ_bottom};
                    vertexBuffer[vertexIndex++] = new float[]{x2, yBottom, z2};
                    vertexBuffer[vertexIndex++] = new float[]{x1, yBottom, z1};
                }
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

            faces[0][0] = new float[]{ minX, minY, maxZ };
            faces[0][1] = new float[]{ maxX, minY, maxZ };
            faces[0][2] = new float[]{ maxX, maxY, maxZ };
            faces[0][3] = new float[]{ minX, maxY, maxZ };

            faces[1][0] = new float[]{ maxX, minY, minZ };
            faces[1][1] = new float[]{ minX, minY, minZ };
            faces[1][2] = new float[]{ minX, maxY, minZ };
            faces[1][3] = new float[]{ maxX, maxY, minZ };

            faces[2][0] = new float[]{ minX, maxY, maxZ };
            faces[2][1] = new float[]{ maxX, maxY, maxZ };
            faces[2][2] = new float[]{ maxX, maxY, minZ };
            faces[2][3] = new float[]{ minX, maxY, minZ };

            faces[3][0] = new float[]{ minX, minY, minZ };
            faces[3][1] = new float[]{ maxX, minY, minZ };
            faces[3][2] = new float[]{ maxX, minY, maxZ };
            faces[3][3] = new float[]{ minX, minY, maxZ };

            faces[4][0] = new float[]{ maxX, minY, maxZ };
            faces[4][1] = new float[]{ maxX, minY, minZ };
            faces[4][2] = new float[]{ maxX, maxY, minZ };
            faces[4][3] = new float[]{ maxX, maxY, maxZ };

            faces[5][0] = new float[]{ minX, minY, minZ };
            faces[5][1] = new float[]{ minX, minY, maxZ };
            faces[5][2] = new float[]{ minX, maxY, maxZ };
            faces[5][3] = new float[]{ minX, maxY, minZ };

            return faces;
        }
    }

    private VertexUtil() {
    }
}