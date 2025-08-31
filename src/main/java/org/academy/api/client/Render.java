package org.academy.api.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import static com.mojang.blaze3d.pipeline.RenderPipeline.builder;
import static net.minecraft.client.renderer.RenderStateShard.EmptyTextureStateShard;
import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.Resource.Textures.*;
import static org.academy.api.client.render.post.BloomEffect.BLOOM_TARGET;

public final class Render {
    public static final class RenderPipelines extends net.minecraft.client.renderer.RenderPipelines {
        public static final RenderPipeline.Snippet BLIT_SCREEN_SNIPPET = builder()
                .withLocation(academy("pipeline/blit_screen"))
                .withVertexShader(Resource.Shaders.SCREEN_BLIT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .withDepthWrite(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .buildSnippet();

        public static final RenderPipeline MASKED_BLUR_SHADER = RenderPipeline.builder()
                .withLocation(academy("masked_blur_shader"))
                .withVertexShader(Resource.Shaders.SCREEN_BLIT)
                .withFragmentShader(Resource.Shaders.Fragment.MASKED_BLUR)
                .withSampler("DiffuseSampler")
                .withSampler("MaskSampler")
                .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline BLIT_SCREEN = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen"))
                .withFragmentShader(Resource.Shaders.SCREEN_BLIT)
                .withSampler("DiffuseSampler")
                .withBlend(BlendFunction.TRANSLUCENT)
                .build();

        public static final RenderPipeline GAUSSIAN_BLUR = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/gaussian_blur"))
                .withFragmentShader(Resource.Shaders.Fragment.GAUSSIAN_BLUR)
                .withSampler("DiffuseSampler")
                .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
                .build();

        public static final RenderPipeline BLOOM_BLEND = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/bloom_blend"))
                .withFragmentShader(Resource.Shaders.Fragment.BLOOM_BLEND)
                .withSampler("DiffuseSampler")
                .withSampler("BlurTexture1")
                .withSampler("BlurTexture2")
                .withSampler("BlurTexture3")
                .withUniform("BloomInfo", UniformType.UNIFORM_BUFFER)
                .build();

        public static final RenderPipeline IMAGE = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/image"))
                .withVertexShader(Resource.Shaders.POS_TEX_COLOR)
                .withFragmentShader(Resource.Shaders.POS_TEX_COLOR)
                .withSampler("Sampler0")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline POS_COLOR = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/pos_color"))
                .withVertexShader(Resource.Shaders.POS_COLOR)
                .withFragmentShader(Resource.Shaders.POS_COLOR)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline SDF_SHARP_MARGIN = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/sdf_sharp_margin"))
                .withFragmentShader(Resource.Shaders.Fragment.SDF_SHARP_MARGIN)
                .withVertexShader(Resource.Shaders.POS_TEX)
                .withUniform("SdfUniforms", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline SDF_CIRCLE_GLOW = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/sdf_circle_glow"))
                .withFragmentShader(Resource.Shaders.Fragment.SDF_CIRCLE_GLOW)
                .withVertexShader(Resource.Shaders.POS_TEX)
                .withUniform("GlowUniforms", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline LEVEL_POS_TEX_COLOR = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color"))
                .withFragmentShader(Resource.Shaders.POS_TEX_COLOR)
                .withVertexShader(Resource.Shaders.POS_TEX_COLOR)
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color"))
                .withFragmentShader(Resource.Shaders.POS_COLOR)
                .withVertexShader(Resource.Shaders.POS_COLOR)
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();

        private RenderPipelines() {
        }
    }

    public static final class TextureStateShards {
        public static final EmptyTextureStateShard ARC = blur(ARC_TEXTURE);

        public static EmptyTextureStateShard pixel(ResourceLocation texture) {
            return new EmptyTextureStateShard(() -> {
                var texturemanager = Minecraft.getInstance().getTextureManager();
                var abstracttexture = texturemanager.getTexture(texture);
                abstracttexture.setFilter(false, false);
                RenderSystem.setShaderTexture(0, abstracttexture.getTextureView());
            }, () -> {
            });
        }

        public static EmptyTextureStateShard blur(ResourceLocation texture) {
            return new EmptyTextureStateShard(() -> {
                var texturemanager = Minecraft.getInstance().getTextureManager();
                var abstracttexture = texturemanager.getTexture(texture);
                abstracttexture.setFilter(true, true);
                RenderSystem.setShaderTexture(0, abstracttexture.getTextureView());
            }, () -> {
            });
        }
    }

    public abstract static class RenderTypes extends net.minecraft.client.renderer.RenderType {
        public static final RenderType ARC = create(
                "arc",
                1536,
                RenderPipelines.LEVEL_POS_TEX_COLOR,
                CompositeState.builder()
                        .setTextureState(TextureStateShards.ARC)
                        .setOutputState(BLOOM_TARGET)
                        .createCompositeState(false)
        );

        public static final RenderType BOX = create(
                "box",
                1536,
                RenderPipelines.LEVEL_POS_COLOR,
                CompositeState.builder()
                        .setOutputState(BLOOM_TARGET)
                        .createCompositeState(false)
        );

        private RenderTypes(Runnable a, Runnable b) {
            super("", -1, false, false, a, b);
        }
    }

    private Render() {
    }
}