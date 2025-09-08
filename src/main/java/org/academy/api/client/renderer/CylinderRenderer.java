package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.academy.api.client.render.post.PostEffect;
import org.joml.Matrix4f;

import static org.academy.api.client.Render.RenderTypes.BOX;

public final class CylinderRenderer {
    private CylinderRenderer() {
    }

    static {
        PostEffect.addFixedBuffer(BOX);
    }

    public static void renderCylinder(PoseStack poseStack, MultiBufferSource buffer, float[][] vertexBuffer,
                                      float red, float green, float blue, float alpha) {
        var pose = poseStack.last();
        var matrix = pose.pose();
        var vertexConsumer = buffer.getBuffer(BOX);
        renderCylinder(matrix, vertexConsumer, vertexBuffer, red, green, blue, alpha);
    }

    public static void renderCylinder(Matrix4f matrix4f, VertexConsumer vertexConsumer, float[][] vertexBuffer,
                                      float red, float green, float blue, float alpha) {
        for (var floats : vertexBuffer) {
            var x = floats[0];
            var y = floats[1];
            var z = floats[2];
            vertexConsumer.addVertex(matrix4f, x, y, z).setColor(red, green, blue, alpha).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT);
        }
    }
}
