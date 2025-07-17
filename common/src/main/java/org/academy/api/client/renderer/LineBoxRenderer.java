package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class LineBoxRenderer {
    public static void renderWireframeBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                          float r, float g, float b, float a) {
        final var vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        final var pose = poseStack.last();
        final var matrix4f = pose.pose();
        final var matrix3f = pose.normal();

        var minX = (float) box.minX;
        var minY = (float) box.minY;
        var minZ = (float) box.minZ;
        var maxX = (float) box.maxX;
        var maxY = (float) box.maxY;
        var maxZ = (float) box.maxZ;

        drawLine(vertexConsumer, matrix4f, matrix3f, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(vertexConsumer, matrix4f, matrix3f, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(vertexConsumer, matrix4f, matrix3f, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(vertexConsumer, matrix4f, matrix3f, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        drawLine(vertexConsumer, matrix4f, matrix3f, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(vertexConsumer, matrix4f, matrix3f, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(vertexConsumer, matrix4f, matrix3f, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(vertexConsumer, matrix4f, matrix3f, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        drawLine(vertexConsumer, matrix4f, matrix3f, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(vertexConsumer, matrix4f, matrix3f, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(vertexConsumer, matrix4f, matrix3f, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(vertexConsumer, matrix4f, matrix3f, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void drawLine(VertexConsumer vc, Matrix4f mat, Matrix3f normMat,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float r, float g, float b, float a) {
        var nx = x2 - x1;
        var ny = y2 - y1;
        var nz = z2 - z1;
        var len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0;
            ny = 1;
            nz = 0;
        }
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(normMat, nx, ny, nz).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a).normal(normMat, nx, ny, nz).endVertex();
    }
}
