package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.academy.api.client.render.post.Phase;
import org.joml.Matrix4f;

import static org.academy.api.client.Render.RenderTypes.POS_COLOR_QUADS_BLOOM;

public final class CylinderRenderer {
    private CylinderRenderer() {
    }

    public static void renderCylinder(PoseStack poseStack, Phase phase, float[][] vertexBuffer,
                                      float red, float green, float blue, float alpha) {
        var pose = poseStack.last();
        var matrix = pose.pose();
        var vertexConsumer = phase.getBuffer(POS_COLOR_QUADS_BLOOM);
        renderCylinder(matrix, vertexConsumer, vertexBuffer, red, green, blue, alpha);
    }

    public static void renderCylinder(Matrix4f matrix4f, VertexConsumer vertexConsumer, float[][] vertexBuffer,
                                      float red, float green, float blue, float alpha) {
        for (var floats : vertexBuffer) {
            var x = floats[0];
            var y = floats[1];
            var z = floats[2];
            vertexConsumer.addVertex(matrix4f, x, y, z).setColor(red, green, blue, alpha).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT);
        }
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