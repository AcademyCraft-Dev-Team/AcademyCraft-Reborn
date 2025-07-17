package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.util.VertexUtil;

import static net.minecraft.client.renderer.RenderStateShard.ITEM_ENTITY_TARGET;
import static org.academy.api.client.util.RenderStateUtil.*;

public final class BoxRenderer {
    public static final RenderType FILLED_BOX_RENDER_TYPE = new RenderType.CompositeRenderType(
            "filled_box_render_type",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .createCompositeState(false)
    );

    public static void renderFilledBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                       float r, float g, float b, float a) {
        final var vertexConsumer = bufferSource.getBuffer(FILLED_BOX_RENDER_TYPE);
        final var matrix4f = poseStack.last().pose();

        var faces = VertexUtil.Box.getBoxVertices(box);

        for (var face : faces) {
            vertexConsumer.vertex(matrix4f, face[0][0], face[0][1], face[0][2]).color(r, g, b, a).endVertex();
            vertexConsumer.vertex(matrix4f, face[1][0], face[1][1], face[1][2]).color(r, g, b, a).endVertex();
            vertexConsumer.vertex(matrix4f, face[2][0], face[2][1], face[2][2]).color(r, g, b, a).endVertex();
            vertexConsumer.vertex(matrix4f, face[3][0], face[3][1], face[3][2]).color(r, g, b, a).endVertex();
        }
    }
}
