package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.common.util.MathUtil;
import org.joml.Matrix4f;

import static net.minecraft.client.renderer.RenderStateShard.ITEM_ENTITY_TARGET;
import static org.academy.api.client.util.RenderStateUtil.*;

public final class CylinderRenderer {
    public static final RenderType CYLINDER_RENDER_TYPE = new RenderType.CompositeRenderType(
            "cylinder_render_type",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_STRIP,
            128,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );

    public static void renderCylinder(final PoseStack poseStack, final MultiBufferSource multiBufferSource,
                                      final float red, final float green, float blue, float alpha, final float yBottom, final float yTop, final float radius, final int faces) {
        final var pose = poseStack.last();
        final var matrix4f = pose.pose();
        final var vertexConsumer = multiBufferSource.getBuffer(CYLINDER_RENDER_TYPE);
        final var angleStep = MathUtil.TWO_PI / faces;

        for (var i = 0; i <= faces; i++) {
            final var angle = i * angleStep;
            final var x = (float) (radius * Math.cos(angle));
            final var z = (float) (radius * Math.sin(angle));
            vertexConsumer.vertex(matrix4f, x, yTop, z).color(red, green, blue, alpha).endVertex();
            vertexConsumer.vertex(matrix4f, x, yBottom, z).color(red, green, blue, alpha).endVertex();
        }
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
            vertexConsumer.vertex(matrix4f, x, y, z).color(red, green, blue, alpha).endVertex();
        }
    }
}
