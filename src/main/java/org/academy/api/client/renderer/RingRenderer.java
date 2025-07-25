package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.function.Function;

import static net.minecraft.client.renderer.RenderStateShard.*;

public final class RingRenderer {
    public static final Function<ResourceLocation, RenderType> RING_RENDER_TYPE = resourceLocation
            -> RenderType.create(
            "ring_render_type",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            512,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false, false))
                    .setShaderState(POSITION_TEX_SHADER)
                    .setCullState(NO_CULL)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );

    public static void renderRing(Matrix4f matrix, VertexConsumer vertexConsumer,
                                  int segments, float[][][] vertexBuffer) {
        for (var i = 0; i < segments; i++) {
            var v0 = vertexBuffer[i][0];
            var v1 = vertexBuffer[i][1];
            var v2 = vertexBuffer[i][2];
            var v3 = vertexBuffer[i][3];

            vertexConsumer.addVertex(matrix, v0[0], v0[1], v0[2]).setUv(v0[3], 0);
            vertexConsumer.addVertex(matrix, v1[0], v1[1], v1[2]).setUv(v1[3], 0);
            vertexConsumer.addVertex(matrix, v2[0], v2[1], v2[2]).setUv(v2[3], 1);
            vertexConsumer.addVertex(matrix, v3[0], v3[1], v3[2]).setUv(v3[3], 1);
        }
    }
}
