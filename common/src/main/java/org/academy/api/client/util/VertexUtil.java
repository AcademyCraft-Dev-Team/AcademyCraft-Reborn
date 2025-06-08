package org.academy.api.client.util;

import net.minecraft.world.phys.AABB;
import org.academy.api.common.util.MathUtil;

public class VertexUtil {
    public static final class Ring {
        public static float[][][] getRingVertexBuffer(float radius, int segments, float yBottom, float yTop) {
            if (segments <= 0) return null;
            float[][][] vertexBuffer = new float[segments][4][4];
            final float twoPi = MathUtil.TWO_PI;

            for (int i = 0; i < segments; i++) {
                float angle1 = (i * twoPi) / segments;
                float angle2 = ((i + 1) * twoPi) / segments;
                float u0 = (float) i / segments;
                float u1 = (float) (i + 1) / segments;

                float cos1 = (float) Math.cos(angle1);
                float sin1 = (float) Math.sin(angle1);
                float cos2 = (float) Math.cos(angle2);
                float sin2 = (float) Math.sin(angle2);

                float x1 = cos1 * radius;
                float z1 = sin1 * radius;
                float x2 = cos2 * radius;
                float z2 = sin2 * radius;

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
            int sideVertexCount = (faces + 1) * 2;
            int capVertexCount = capped ? (faces + 2) : 0;
            int totalVertices = sideVertexCount + capVertexCount * 2;
            float[][] vertexBuffer = new float[totalVertices][3];
            double angleStep = MathUtil.TWO_PI / faces;
            for (int i = 0; i <= faces; i++) {
                double angle = i * angleStep;
                float x = (float) (radius * Math.cos(angle));
                float z = (float) (radius * Math.sin(angle));
                int topIdx = i * 2;
                int botIdx = topIdx + 1;
                vertexBuffer[topIdx][0] = x;
                vertexBuffer[topIdx][1] = yTop;
                vertexBuffer[topIdx][2] = z;
                vertexBuffer[botIdx][0] = x;
                vertexBuffer[botIdx][1] = yBottom;
                vertexBuffer[botIdx][2] = z;
            }

            if (capped) {
                int offset = sideVertexCount;
                vertexBuffer[offset][0] = 0f;
                vertexBuffer[offset][1] = yTop;
                vertexBuffer[offset][2] = 0f;
                for (int i = 0; i <= faces; i++) {
                    double angle = i * angleStep;
                    float x = (float) (radius * Math.cos(angle));
                    float z = (float) (radius * Math.sin(angle));
                    vertexBuffer[offset + 1 + i][0] = x;
                    vertexBuffer[offset + 1 + i][1] = yTop;
                    vertexBuffer[offset + 1 + i][2] = z;
                }
                offset += capVertexCount;
                vertexBuffer[offset][0] = 0f;
                vertexBuffer[offset][1] = yBottom;
                vertexBuffer[offset][2] = 0f;
                for (int i = 0; i <= faces; i++) {
                    double angle = i * angleStep;
                    float x = (float) (radius * Math.cos(angle));
                    float z = (float) (radius * Math.sin(angle));
                    vertexBuffer[offset + 1 + i][0] = x;
                    vertexBuffer[offset + 1 + i][1] = yBottom;
                    vertexBuffer[offset + 1 + i][2] = z;
                }
            }

            return vertexBuffer;
        }
    }

    public static final class Box {
        public static float[][][] getBoxVertices(AABB box) {
            float minX = (float) box.minX;
            float minY = (float) box.minY;
            float minZ = (float) box.minZ;
            float maxX = (float) box.maxX;
            float maxY = (float) box.maxY;
            float maxZ = (float) box.maxZ;

            float[][][] faces = new float[6][4][3];

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