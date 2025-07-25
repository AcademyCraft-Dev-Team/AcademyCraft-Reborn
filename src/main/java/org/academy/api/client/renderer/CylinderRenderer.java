package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.render.post.BloomEffect;
import org.joml.Matrix4f;

import static net.minecraft.client.renderer.RenderStateShard.*;
import static org.academy.api.client.util.RenderStateUtil.*;

public final class CylinderRenderer {
    public static final RenderType CYLINDER_RENDER_TYPE = RenderType.create(
            "cylinder_render_type",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            128,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(BLOOM_TARGET)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );

    static {
        BloomEffect.addFixedBuffer(CYLINDER_RENDER_TYPE);
    }

    public static void renderCylinder(PoseStack poseStack, MultiBufferSource buffer, float[][] vertexBuffer,
                                      float red, float green, float blue, float alpha) {
        final var pose = poseStack.last();
        final var matrix = pose.pose();
        final var vertexConsumer = buffer.getBuffer(CYLINDER_RENDER_TYPE);
        renderCylinder(matrix, vertexConsumer, vertexBuffer, red, green, blue, alpha);
    }

    public static void renderCylinder(Matrix4f matrix4f, VertexConsumer vertexConsumer, float[][] vertexBuffer,
                                      float red, float green, float blue, float alpha) {
        for (var floats : vertexBuffer) {
            var x = floats[0];
            var y = floats[1];
            var z = floats[2];
            vertexConsumer.addVertex(matrix4f, x, y, z).setColor(red, green, blue, alpha);
        }
    }
}
