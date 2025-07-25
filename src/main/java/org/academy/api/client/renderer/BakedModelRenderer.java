package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;

import static net.minecraft.client.renderer.RenderStateShard.*;

@SuppressWarnings("deprecation")
public final class BakedModelRenderer {
    public static final RenderType BAKED_MODEL_NO_TRANSPARENCY_RENDER_TYPE = RenderType.create(
            "baked_model_no_transparency_render_type",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1024,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            TextureAtlas.LOCATION_BLOCKS,
                            false,
                            true
                    ))
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)
    );
    public static final RenderType BAKED_MODEL_TRANSPARENCY_RENDER_TYPE = RenderType.create(
            "baked_model_transparency_render_type",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1024,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            TextureAtlas.LOCATION_BLOCKS,
                            false,
                            true
                    ))
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setCullState(NO_CULL)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(true)
    );

    public static void render(PoseStack poseStack, BakedModel bakedModel, MultiBufferSource multiBufferSource, RandomSource randomSource, boolean transparency, int light, int overlay) {
        var renderType = transparency ? BAKED_MODEL_TRANSPARENCY_RENDER_TYPE : BAKED_MODEL_NO_TRANSPARENCY_RENDER_TYPE;
        var vertexConsumer = multiBufferSource.getBuffer(renderType);
        var pose = poseStack.last();
        for (var bakedQuad : bakedModel.getQuads(null, null, randomSource)) {
            vertexConsumer.putBulkData(pose, bakedQuad, 1f, 1f, 1f, 1f, light, overlay);
        }
    }
}
