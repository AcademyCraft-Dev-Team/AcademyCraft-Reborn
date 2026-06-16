package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.render.MatrixStack;
import org.joml.Matrix4f;

import java.util.Map;

public final class LineBoxRenderer {
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private static final Map<Direction, int[]> FACE_EDGES = Map.of(
            Direction.DOWN, new int[]{0, 1, 2, 3},
            Direction.UP, new int[]{4, 5, 6, 7},
            Direction.NORTH, new int[]{0, 1, 5, 4},
            Direction.SOUTH, new int[]{3, 2, 6, 7},
            Direction.WEST, new int[]{0, 3, 7, 4},
            Direction.EAST, new int[]{1, 2, 6, 5}
    );

    private static float[][] getVertices(AABB box) {
        return new float[][]{
                {(float) box.minX, (float) box.minY, (float) box.minZ},
                {(float) box.maxX, (float) box.minY, (float) box.minZ},
                {(float) box.maxX, (float) box.minY, (float) box.maxZ},
                {(float) box.minX, (float) box.minY, (float) box.maxZ},
                {(float) box.minX, (float) box.maxY, (float) box.minZ},
                {(float) box.maxX, (float) box.maxY, (float) box.minZ},
                {(float) box.maxX, (float) box.maxY, (float) box.maxZ},
                {(float) box.minX, (float) box.maxY, (float) box.maxZ}
        };
    }

    public static void renderWireframeBox(MatrixStack poseStack, VertexConsumer vertexConsumer, AABB box,
                                          float r, float g, float b, float a) {
        var vertices = getVertices(box);
        var matrix4f = poseStack.lastMatrix();

        for (var edge : EDGES) {
            var v1 = vertices[edge[0]];
            var v2 = vertices[edge[1]];
            drawLine(vertexConsumer, matrix4f, v1[0], v1[1], v1[2], v2[0], v2[1], v2[2], r, g, b, a);
        }
    }

    public static void renderFace(MatrixStack poseStack, VertexConsumer vertexConsumer, AABB box, Direction face,
                                  float r, float g, float b, float a) {
        var vertices = getVertices(box);
        var faceIndices = FACE_EDGES.get(face);
        if (faceIndices == null) return;

        var matrix4f = poseStack.lastMatrix();

        for (var i = 0; i < faceIndices.length; i++) {
            var v1 = vertices[faceIndices[i]];
            var v2 = vertices[faceIndices[(i + 1) % faceIndices.length]];
            drawLine(vertexConsumer, matrix4f, v1[0], v1[1], v1[2], v2[0], v2[1], v2[2], r, g, b, a);
        }
    }

    private static void drawLine(VertexConsumer vc, Matrix4f mat,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float r, float g, float b, float a) {
        var lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        vc.addVertex(mat, x1, y1, z1)
                .setColor(r, g, b, a)
                .setNormal(0, 1, 0).setLineWidth(lineWidth);
        vc.addVertex(mat, x2, y2, z2)
                .setColor(r, g, b, a)
                .setNormal(0, 1, 0).setLineWidth(lineWidth);
    }
}