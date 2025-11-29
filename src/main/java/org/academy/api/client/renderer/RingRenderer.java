package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.academy.api.client.Render;
import org.joml.Matrix4f;

import java.util.function.Function;

public final class RingRenderer {
    public static final Function<Identifier, RenderType> RING_RENDER_TYPE = tex -> RenderType.create(
            "ring_render_type",
            RenderSetup.builder(Render.RenderPipelines.LEVEL_POS_TEX_COLOR)
                    .withTexture("Sampler0", tex)
                    .bufferSize(65536)
                    .createRenderSetup()
    );

    public static void renderRing(Matrix4f matrix, VertexConsumer vertexConsumer,
                                  int segments, float[][][] vertexBuffer, float red, float green, float blue, float alpha) {
        for (var i = 0; i < segments; i++) {
            var v0 = vertexBuffer[i][0];
            var v1 = vertexBuffer[i][1];
            var v2 = vertexBuffer[i][2];
            var v3 = vertexBuffer[i][3];

            vertexConsumer.addVertex(matrix, v0[0], v0[1], v0[2]).setUv(v0[3], 0).setColor(red, green, blue, alpha);
            vertexConsumer.addVertex(matrix, v1[0], v1[1], v1[2]).setUv(v1[3], 0).setColor(red, green, blue, alpha);
            vertexConsumer.addVertex(matrix, v2[0], v2[1], v2[2]).setUv(v2[3], 1).setColor(red, green, blue, alpha);
            vertexConsumer.addVertex(matrix, v3[0], v3[1], v3[2]).setUv(v3[3], 1).setColor(red, green, blue, alpha);
        }
    }
}
