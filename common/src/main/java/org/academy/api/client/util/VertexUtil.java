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

        public static float[][][] getHorizontalVertexBuffer(float radius, float height, int segments) {
            return getRingVertexBuffer(radius, segments, -height / 2f, height / 2f);
        }
    }

    private VertexUtil() {
    }
}
