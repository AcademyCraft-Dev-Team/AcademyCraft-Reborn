package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;

public final class CylinderRenderer {
    private CylinderRenderer() {
    }

    public static void renderCylinder(PoseStack.Pose pose, VertexConsumer vertexConsumer, float[][] vertexBuffer,
                                      float red, float green, float blue, float alpha, int packedLight, int packedOverlay) {
        for (var vertexData : vertexBuffer) {
            var x = vertexData[0];
            var y = vertexData[1];
            var z = vertexData[2];
            var u = vertexData[3];
            var v = vertexData[4];
            var nx = vertexData[5];
            var ny = vertexData[6];
            var nz = vertexData[7];

            vertexConsumer.addVertex(pose.pose(), x, y, z)
                    .setColor(red, green, blue, alpha)
                    .setUv(u, v)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    public static void renderCylinderWireframe(PoseStack poseStack, VertexConsumer vertexConsumer, float[][] vertexBuffer,
                                               float red, float green, float blue, float alpha) {
        for (var vertexData : vertexBuffer) {
            var x = vertexData[0];
            var y = vertexData[1];
            var z = vertexData[2];
            var nx = vertexData[3];
            var ny = vertexData[4];
            var nz = vertexData[5];

            vertexConsumer.addVertex(poseStack.last().pose(), x, y, z)
                    .setColor(red, green, blue, alpha)
                    .setNormal(poseStack.last(), nx, ny, nz)
                    .setLineWidth(Minecraft.getInstance().getWindow().getAppropriateLineWidth());
        }
    }
}
