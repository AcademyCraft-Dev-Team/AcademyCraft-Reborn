package org.academy.api.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.PostEffect;

import static com.mojang.blaze3d.pipeline.RenderPipeline.builder;
import static net.minecraft.client.renderer.RenderStateShard.EmptyTextureStateShard;
import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.render.post.BloomEffect.BLOOM_TARGET;

public final class Render {
    public static final class RenderPipelines extends net.minecraft.client.renderer.RenderPipelines {
        public static final RenderPipeline NO_DEPTH_OPAQUE_PARTICLE = builder(PARTICLE_SNIPPET)
                .withLocation(academy("pipeline/opaque_particle"))
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build();

        public static final RenderPipeline.Snippet BLIT_SCREEN_SNIPPET = builder()
                .withLocation(academy("pipeline/blit_screen"))
                .withVertexShader(Resource.Shaders.SCREEN_BLIT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .withDepthWrite(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .buildSnippet();

        public static final RenderPipeline MASKED_BLUR_SHADER = builder()
                .withLocation(academy("masked_blur_shader"))
                .withVertexShader(Resource.Shaders.SCREEN_BLIT)
                .withFragmentShader(Resource.Shaders.Fragment.MASKED_BLUR)
                .withSampler("DiffuseSampler")
                .withSampler("MaskSampler")
                .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline MASK_BRUSH = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/pos_color"))
                .withVertexShader(Resource.Shaders.POS_COLOR)
                .withFragmentShader(Resource.Shaders.POS_COLOR)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline BLIT_SCREEN_WITH_BLEND = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen"))
                .withFragmentShader(Resource.Shaders.SCREEN_BLIT)
                .withSampler("DiffuseSampler")
                .withBlend(BlendFunction.TRANSLUCENT)
                .build();

        public static final RenderPipeline BLIT_SCREEN_WITHOUT_BLEND = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen"))
                .withFragmentShader(Resource.Shaders.SCREEN_BLIT)
                .withSampler("DiffuseSampler")
                .withoutBlend()
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

        public static final RenderPipeline GLOW_CIRCLE = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/glow_circle"))
                .withFragmentShader(Resource.Shaders.Fragment.GLOW_CIRCLE)
                .withVertexShader(Resource.Shaders.POS_TEX)
                .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
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
                .withSampler("Sampler0")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_QUADS = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color"))
                .withFragmentShader(Resource.Shaders.POS_COLOR)
                .withVertexShader(Resource.Shaders.POS_COLOR)
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_TRANGLES = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color"))
                .withFragmentShader(Resource.Shaders.POS_COLOR)
                .withVertexShader(Resource.Shaders.POS_COLOR)
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_TRANGLES_NO_DEPTH = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color"))
                .withFragmentShader(Resource.Shaders.POS_COLOR)
                .withVertexShader(Resource.Shaders.POS_COLOR)
                .withCull(false)
                .withDepthWrite(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .build();

        public static final RenderPipeline DISTORTION_RING = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/distortion_ring"))
                .withVertexShader(Resource.Shaders.DISTORTION_RING)
                .withFragmentShader(Resource.Shaders.DISTORTION_RING)
                .withSampler("Sampler0")
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("Normal", VertexFormatElement.NORMAL)
                        .padding(1)
                        .build(), VertexFormat.Mode.QUADS)
                .build();

        private RenderPipelines() {
        }
    }

    public static final class TextureStateShards {
        public static final EmptyTextureStateShard ARC = blur(Resource.Textures.ARC);

        public static final EmptyTextureStateShard MAIN_SCENE = new EmptyTextureStateShard(
                () -> RenderSystem.setShaderTexture(0, PostEffect.MAIN_SCENE.getColorTextureView()),
                () -> {
                }
        );

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
            return new RenderStateShard.TextureStateShard(texture, true);
        }
    }

    public abstract static class RenderTypes extends RenderType {
        public static final RenderType ARC = create(
                "arc",
                1536,
                RenderPipelines.LEVEL_POS_TEX_COLOR,
                CompositeState.builder()
                        .setTextureState(TextureStateShards.ARC)
                        .setOutputState(BLOOM_TARGET)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false)
        );

        public static final RenderType POS_COLOR_QUADS = create(
                "pos_color_quads",
                1536,
                RenderPipelines.LEVEL_POS_COLOR_QUADS,
                CompositeState.builder()
                        .createCompositeState(false)
        );

        public static final RenderType POS_COLOR_TRANGLES = create(
                "pos_color_trangles",
                1536,
                RenderPipelines.LEVEL_POS_COLOR_TRANGLES,
                CompositeState.builder()
                        .createCompositeState(false)
        );

        // 记得使用对应的 BufferSource 喵

        /**
         * 同时输出到 Main 与 INPUT 喵
         */
        public static final RenderType POS_COLOR_QUADS_BLOOM = create(
                "pos_color_quads_bloom",
                1536,
                RenderPipelines.LEVEL_POS_COLOR_QUADS,
                CompositeState.builder()
                        .setOutputState(BLOOM_TARGET)
                        .createCompositeState(false)
        );

        /**
         * 同时输出到 Main 与 INPUT 喵
         */
        public static final RenderType POS_COLOR_TRANGLES_BLOOM = create(
                "pos_color_trangles_bloom",
                1536,
                RenderPipelines.LEVEL_POS_COLOR_TRANGLES,
                CompositeState.builder()
                        .setOutputState(BLOOM_TARGET)
                        .createCompositeState(false)
        );

        /**
         * 只输出到 INPUT 喵
         */
        public static final RenderType POS_COLOR_QUADS_BLOOM_POST = create(
                "pos_color_quads_bloom_post",
                1536,
                RenderPipelines.LEVEL_POS_COLOR_QUADS,
                CompositeState.builder()
                        .setOutputState(BLOOM_TARGET)
                        .createCompositeState(false)
        );

        /**
         * 只输出到 INPUT 喵
         */
        public static final RenderType POS_COLOR_TRANGLES_BLOOM_POST = create(
                "pos_color_trangles_bloom_post",
                1536,
                RenderPipelines.LEVEL_POS_COLOR_TRANGLES,
                CompositeState.builder()
                        .setOutputState(BLOOM_TARGET)
                        .createCompositeState(false)
        );

        public static final RenderType DISTORTION_RING = create(
                "distortion_ring",
                1536,
                RenderPipelines.DISTORTION_RING,
                CompositeState.builder()
                        .setTextureState(TextureStateShards.MAIN_SCENE)
                        .createCompositeState(false)
        );

        static {
            PostEffect.addFixedBuffer(POS_COLOR_QUADS);
            PostEffect.addFixedBuffer(POS_COLOR_TRANGLES);
            PostEffect.addFixedBuffer(POS_COLOR_QUADS_BLOOM);
            PostEffect.addFixedBuffer(POS_COLOR_TRANGLES_BLOOM);
            BloomEffect.addFixedBuffer(POS_COLOR_QUADS_BLOOM_POST);
            BloomEffect.addFixedBuffer(POS_COLOR_TRANGLES_BLOOM_POST);
        }

        private RenderTypes(Runnable a, Runnable b) {
            super("", -1, false, false, a, b);
        }
    }

    private Render() {
    }
}