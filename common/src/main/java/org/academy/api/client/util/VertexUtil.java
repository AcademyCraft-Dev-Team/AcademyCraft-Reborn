package org.academy.api.client.util;

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

    public static final class Ball {
        public static float[][][] getBallVertexBuffer(final float radius, final int faces) {
            if (radius <= 0 || faces < 3) return null;

            final int numTriangles = faces * faces * 2;
            final float[][][] vertexBuffer = new float[numTriangles][3][3];

            final float pi = MathUtil.PI;
            final float twoPi = MathUtil.TWO_PI;
            int triangleIndex = 0;

            for (int lat = 0; lat < faces; lat++) {
                float theta1 = pi * (-0.5f + (float) lat / faces);
                float theta2 = pi * (-0.5f + (float) (lat + 1) / faces);

                float y1 = radius * (float) Math.sin(theta1);
                float y2 = radius * (float) Math.sin(theta2);

                float scale1 = radius * (float) Math.cos(theta1);
                float scale2 = radius * (float) Math.cos(theta2);

                for (int lon = 0; lon < faces; lon++) {
                    float phi1 = twoPi * (float) lon / faces;
                    float phi2 = twoPi * (float) (lon + 1) / faces;

                    float cosPhi1 = (float) Math.cos(phi1);
                    float sinPhi1 = (float) Math.sin(phi1);
                    float cosPhi2 = (float) Math.cos(phi2);
                    float sinPhi2 = (float) Math.sin(phi2);

                    float x1 = scale1 * cosPhi1;
                    float z1 = scale1 * sinPhi1;
                    float x2 = scale1 * cosPhi2;
                    float z2 = scale1 * sinPhi2;
                    float x3 = scale2 * cosPhi1;
                    float z3 = scale2 * sinPhi1;
                    float x4 = scale2 * cosPhi2;
                    float z4 = scale2 * sinPhi2;

                    vertexBuffer[triangleIndex][0][0] = x1;
                    vertexBuffer[triangleIndex][0][1] = y1;
                    vertexBuffer[triangleIndex][0][2] = z1;
                    vertexBuffer[triangleIndex][1][0] = x3;
                    vertexBuffer[triangleIndex][1][1] = y2;
                    vertexBuffer[triangleIndex][1][2] = z3;
                    vertexBuffer[triangleIndex][2][0] = x2;
                    vertexBuffer[triangleIndex][2][1] = y1;
                    vertexBuffer[triangleIndex][2][2] = z2;
                    triangleIndex++;

                    vertexBuffer[triangleIndex][0][0] = x2;
                    vertexBuffer[triangleIndex][0][1] = y1;
                    vertexBuffer[triangleIndex][0][2] = z2;
                    vertexBuffer[triangleIndex][1][0] = x3;
                    vertexBuffer[triangleIndex][1][1] = y2;
                    vertexBuffer[triangleIndex][1][2] = z3;
                    vertexBuffer[triangleIndex][2][0] = x4;
                    vertexBuffer[triangleIndex][2][1] = y2;
                    vertexBuffer[triangleIndex][2][2] = z4;
                    triangleIndex++;
                }
            }
            return vertexBuffer;
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

    private VertexUtil() {
    }
}
