package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.util.VertexUtil;

import static net.minecraft.client.renderer.RenderStateShard.*;
import static org.academy.api.client.render.post.BloomEffect.BUFFER_SOURCE;
import static org.academy.api.client.util.RenderStateUtil.BLOOM_TARGET;

public final class BoxRenderer {
    public static final RenderType FILLED_BOX_RENDER_TYPE = RenderType.create(
            "filled_box_render_type",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setOutputState(BLOOM_TARGET)
                    .setCullState(NO_CULL)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );

    static {
        BloomEffect.addFixedBuffer(FILLED_BOX_RENDER_TYPE);
    }

    public static void renderFilledBox(PoseStack poseStack, AABB box,
                                       float r, float g, float b, float a) {
        final var vertexConsumer = BUFFER_SOURCE.getBuffer(FILLED_BOX_RENDER_TYPE);
        final var matrix4f = poseStack.last().pose();

        var faces = VertexUtil.Box.getBoxVertices(box);

        for (var face : faces) {
            vertexConsumer.addVertex(matrix4f, face[0][0], face[0][1], face[0][2]).setColor(r, g, b, a);
            vertexConsumer.addVertex(matrix4f, face[1][0], face[1][1], face[1][2]).setColor(r, g, b, a);
            vertexConsumer.addVertex(matrix4f, face[2][0], face[2][1], face[2][2]).setColor(r, g, b, a);
            vertexConsumer.addVertex(matrix4f, face[3][0], face[3][1], face[3][2]).setColor(r, g, b, a);
        }
    }
}
