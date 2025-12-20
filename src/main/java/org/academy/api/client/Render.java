package org.academy.api.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UiLightmap;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.neoforged.neoforge.client.stencil.StencilFunction;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.PostEffect;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static com.mojang.blaze3d.pipeline.RenderPipeline.builder;
import static net.minecraft.client.renderer.rendertype.RenderType.create;
import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.render.post.BloomEffect.BLOOM_TARGET;

public final class Render {
    public static void init() {
        Buffers.init();
        TextureViews.init();
    }

    public static void resize() {
        Buffers.getResourcePool().clear();
        if (Buffers.instance != null) {
            Buffers.getInstance().recreateSDC();
        }
    }

    public static void runBlitPassNDC(
            GpuTextureView color, RenderPipeline pipeline,
            List<TextureBinding> textures, List<UniformBinding> uniforms,
            boolean clear
    ) {
        runBlitPass(color, pipeline, Buffers.getInstance().getFullScreenQuadVBNDC(), textures, uniforms, clear);
    }

    public static void runBlitPassSDC(
            GpuTextureView color, RenderPipeline pipeline,
            List<TextureBinding> textures, List<UniformBinding> uniforms,
            boolean clear
    ) {
        runBlitPass(color, pipeline, Buffers.getInstance().getFullScreenQuadVBSDC(), textures, uniforms, clear);
    }

    public static void runBlitPassColorSDC(
            GpuTextureView color, RenderPipeline pipeline,
            List<TextureBinding> textures, List<UniformBinding> uniforms,
            boolean clear
    ) {
        runBlitPass(color, pipeline, Buffers.getInstance().getFullScreenQuadColorVBSDC(), textures, uniforms, clear);
    }

    public static void runBlitPass(
            GpuTextureView color, RenderPipeline pipeline, GpuBuffer fullscreenQuadVertexBuffer,
            List<TextureBinding> textures, List<UniformBinding> uniforms,
            boolean clear
    ) {
        runBlitPass(color, null, clear, false, pipeline, fullscreenQuadVertexBuffer, textures, uniforms);
    }

    /**
     * @param color 输出喵
     * @param depth 模板喵
     * @param pipeline 管线喵
     * @param fullscreenQuadVertexBuffer 顶点缓冲区喵
     * @param textures Textures 喵
     * @param uniforms Uniforms 喵
     * @param clearColor 是否在输出前清除颜色喵
     * @param clearDepth 是否在输出前清除深度喵
     */
    public static void runBlitPass(
            GpuTextureView color, @Nullable GpuTextureView depth,
            boolean clearColor, boolean clearDepth,
            RenderPipeline pipeline, GpuBuffer fullscreenQuadVertexBuffer,
            List<TextureBinding> textures, List<UniformBinding> uniforms
    ) {
        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        try (
                var renderPass = depth == null
                        ? commandEncoder.createRenderPass
                        (
                                () -> "Blit Pass to " + color,
                                color,
                                clearColor ? OptionalInt.of(0) : OptionalInt.empty()
                        )
                        : commandEncoder.createRenderPass
                        (
                                () -> "Blit Pass to " + color + depth,
                                color,
                                clearColor ? OptionalInt.of(0) : OptionalInt.empty(),
                                depth,
                                clearDepth ? OptionalDouble.of(1) : OptionalDouble.empty()
                        )
        ) {
            IrisCompat.enableBypass();
            renderPass.setPipeline(pipeline);
            for (var texture : textures) {
                renderPass.bindTexture(texture.name(), texture.view(), texture.sampler());
            }
            for (var uniform : uniforms) {
                renderPass.setUniform(uniform.name(), uniform.slice());
            }
            renderPass.setVertexBuffer(0, fullscreenQuadVertexBuffer);
            var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            renderPass.setIndexBuffer(sequentialBuffer.getBuffer(6), sequentialBuffer.type());
            renderPass.drawIndexed(0, 0, 6, 1);
            IrisCompat.resetBypass();
        }
    }

    public static final class Buffers {
        private static final CrossFrameResourcePool RESOURCE_POOL = new CrossFrameResourcePool(3);
        private static final ByteBufferBuilder BYTE_BUFFER_BUILDER = new ByteBufferBuilder(786432);
        public static final int PROJECTION_UBO_SIZE = new Std140SizeCalculator().putMat4f().get();

        @Nullable
        private static Buffers instance;

        /**
         * -1~1
         */
        private final GpuBuffer fullScreenQuadVBNDC;
        private final GpuBuffer projectionUB;

        /**
         * 0~scaled
         */
        private GpuBuffer fullScreenQuadVBSDC;
        private GpuBuffer fullScreenQuadColorVBSDC;

        private Buffers() {
            fullScreenQuadVBNDC = createNDC();
            createSDC();
            projectionUB = createProjection();
        }

        public static void init() {
            if (instance == null) instance = new Buffers();
        }

        public static void close() {
            if (instance != null) {
                instance.closeInternal();
                instance = null;
            }
        }

        public static Buffers getInstance() {
            if (instance == null) {
                throw new IllegalStateException(
                        "Render.Buffers has not been initialized."
                );
            }
            return instance;
        }

        private void closeInternal() {
            fullScreenQuadVBNDC.close();
            fullScreenQuadVBSDC.close();
            fullScreenQuadColorVBSDC.close();
            projectionUB.close();
        }

        public void recreateSDC() {
            fullScreenQuadVBSDC.close();
            fullScreenQuadColorVBSDC.close();
            createSDC();
        }

        private void createSDC() {
            var mc = Minecraft.getInstance();
            var window = mc.getWindow();
            var width = window.getGuiScaledWidth();
            var height = window.getGuiScaledHeight();

            try (
                    var byteBufferBuilder = ByteBufferBuilder.exactlySized(
                            DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4
                    )
            ) {
                var bufferBuilder = new BufferBuilder(
                        byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX
                );
                bufferBuilder.addVertex(0, 0, 0.0F).setUv(0.0F, 0.0F);
                bufferBuilder.addVertex(width, 0, 0.0F).setUv(1.0F, 0.0F);
                bufferBuilder.addVertex(width, height, 0.0F).setUv(1.0F, 1.0F);
                bufferBuilder.addVertex(0, height, 0.0F).setUv(0.0F, 1.0F);

                try (var meshData = bufferBuilder.buildOrThrow()) {
                    fullScreenQuadVBSDC = RenderSystem.getDevice().createBuffer(
                            () -> "Fullscreen Quad SDC", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer()
                    );
                }
            }

            try (
                    var byteBufferBuilder = ByteBufferBuilder.exactlySized(
                            DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 4
                    )
            ) {
                var bufferBuilder = new BufferBuilder(
                        byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR
                );
                var white = -1;
                bufferBuilder.addVertex(0, height, 0.0F).setUv(0.0F, 1.0F).setColor(white);
                bufferBuilder.addVertex(0, 0, 0.0F).setUv(0.0F, 0.0F).setColor(white);
                bufferBuilder.addVertex(width, 0, 0.0F).setUv(1.0F, 0.0F).setColor(white);
                bufferBuilder.addVertex(width, height, 0.0F).setUv(1.0F, 1.0F).setColor(white);

                try (var meshData = bufferBuilder.buildOrThrow()) {
                    fullScreenQuadColorVBSDC = RenderSystem.getDevice().createBuffer(
                            () -> "Fullscreen Quad Color SDC", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer()
                    );
                }
            }
        }

        private GpuBuffer createNDC() {
            try (
                    var byteBufferBuilder = ByteBufferBuilder.exactlySized(
                            DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4
                    )
            ) {
                var bufferBuilder = new BufferBuilder(
                        byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX
                );
                bufferBuilder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
                bufferBuilder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
                bufferBuilder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
                bufferBuilder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);

                try (var meshData = bufferBuilder.buildOrThrow()) {
                    return RenderSystem.getDevice().createBuffer(
                            () -> "Fullscreen Quad NDC", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer()
                    );
                }
            }
        }

        private GpuBuffer createProjection() {
            var device = RenderSystem.getDevice();
            var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
            return device.createBuffer(
                    () -> "Projection UBO", uboUsage, PROJECTION_UBO_SIZE
            );
        }

        public GpuBuffer getProjectionUB(Matrix4f matrix4f) {
            try (var memoryStack = MemoryStack.stackPush()) {
                var builder = Std140Builder.onStack(memoryStack, PROJECTION_UBO_SIZE);
                builder.putMat4f(matrix4f);
                var byteBuffer = builder.get();
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(projectionUB.slice(), byteBuffer);
            }
            return projectionUB;
        }

        public GpuBuffer getFullScreenQuadVBNDC() {
            return fullScreenQuadVBNDC;
        }

        public GpuBuffer getFullScreenQuadVBSDC() {
            return fullScreenQuadVBSDC;
        }

        public GpuBuffer getFullScreenQuadColorVBSDC() {
            return fullScreenQuadColorVBSDC;
        }

        public static ByteBufferBuilder getByteBufferBuilder() {
            return BYTE_BUFFER_BUILDER;
        }

        public static CrossFrameResourcePool getResourcePool() {
            return RESOURCE_POOL;
        }
    }

    public static final class TextureViews {
        @Nullable
        private static TextureViews instance;
        private final UiLightmap uiLightmap = new UiLightmap();

        public static void init() {
            if (instance == null) instance = new TextureViews();
        }

        public static void close() {
            if (instance != null) {
                instance.closeInternal();
                instance = null;
            }
        }

        public static TextureViews getInstance() {
            if (instance == null) {
                throw new IllegalStateException(
                        "Render.Buffers has not been initialized."
                );
            }
            return instance;
        }

        public GpuTextureView getUiLightmapTextureView() {
            return uiLightmap.getTextureView();
        }

        private void closeInternal() {
            uiLightmap.close();
        }

        private TextureViews() {
        }
    }

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

        public static final RenderPipeline BLIT_SCREEN_WITHOUT_BLEND_INVERSE_CUTOUT = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen"))
                .withFragmentShader(Resource.Shaders.SCREEN_BLIT)
                .withSampler("DiffuseSampler")
                .withoutBlend()
                .withStencilTest(
                        new StencilTest(
                                new StencilPerFaceTest(
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        StencilFunction.EQUAL
                                ),
                                0XFF,
                                0XFF,
                                0
                        )
                )
                .build();


        public static final RenderPipeline GAUSSIAN_BLUR = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/gaussian_blur"))
                .withFragmentShader(Resource.Shaders.Fragment.GAUSSIAN_BLUR)
                .withSampler("DiffuseSampler")
                .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
                .build();

        public static final RenderPipeline CUTOUT_GAUSSIAN_BLUR = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/cutout_gaussian_blur"))
                .withFragmentShader(Resource.Shaders.Fragment.GAUSSIAN_BLUR)
                .withSampler("DiffuseSampler")
                .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
                .withStencilTest(
                        new StencilTest(
                                new StencilPerFaceTest(
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        StencilFunction.EQUAL
                                ),
                                0XFF,
                                0XFF,
                                1
                        )
                )
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
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline IMAGE_NO_DEPTH = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/image"))
                .withVertexShader(Resource.Shaders.POS_TEX_COLOR)
                .withFragmentShader(Resource.Shaders.POS_TEX_COLOR)
                .withSampler("Sampler0")
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline IMAGE_STENCIL = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/image"))
                .withVertexShader(Resource.Shaders.POS_TEX_COLOR)
                .withFragmentShader(Resource.Shaders.POS_TEX_COLOR)
                .withSampler("Sampler0")
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withStencilTest(
                        new StencilTest(
                                new StencilPerFaceTest(
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        StencilOperation.REPLACE,
                                        StencilFunction.ALWAYS
                                ),
                                0XFF,
                                0XFF,
                                1
                        )
                )
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
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
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


    public abstract static class RenderTypes extends net.minecraft.client.renderer.rendertype.RenderTypes {
        public static final RenderType ARC = create(
                "arc",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_TEX_COLOR)
                        .withTexture(
                                "Sampler0", Resource.Textures.ARC,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .setOutputTarget(BLOOM_TARGET)
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType POS_COLOR_QUADS = create(
                "pos_color_quads",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_QUADS)
                        .createRenderSetup()
        );

        public static final RenderType POS_COLOR_TRANGLES = create(
                "pos_color_trangles",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_TRANGLES)
                        .createRenderSetup()
        );

        // 记得使用对应的 BufferSource 喵

        /**
         * 同时输出到 Main 与 INPUT 喵
         */
        public static final RenderType POS_COLOR_QUADS_BLOOM = create(
                "pos_color_quads_bloom",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_QUADS)
                        .setOutputTarget(BLOOM_TARGET)
                        .createRenderSetup()
        );

        /**
         * 同时输出到 Main 与 INPUT 喵
         */
        public static final RenderType POS_COLOR_TRANGLES_BLOOM = create(
                "pos_color_trangles_bloom",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_TRANGLES)
                        .setOutputTarget(BLOOM_TARGET)
                        .createRenderSetup()
        );

        /**
         * 只输出到 INPUT 喵
         */
        public static final RenderType POS_COLOR_QUADS_BLOOM_POST = create(
                "pos_color_quads_bloom_post",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_QUADS)
                        .setOutputTarget(BLOOM_TARGET)
                        .createRenderSetup()
        );

        /**
         * 只输出到 INPUT 喵
         */
        public static final RenderType POS_COLOR_TRANGLES_BLOOM_POST = create(
                "pos_color_trangles_bloom_post",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_TRANGLES)
                        .setOutputTarget(BLOOM_TARGET)
                        .createRenderSetup()
        );

        public static final RenderType DISTORTION_RING;
        public static final RenderType ABILITY_DEVELOPER = entityTranslucent(Resource.Textures.MODEL_ABILITY_DEVELOPER);
        public static final RenderType CAT_ENGINE = entityTranslucent(Resource.Textures.CAT_ENGINE);
        public static final RenderType CLEANING_ROBOT = entitySolid(Resource.Textures.CLEANING_ROBOT);

        static {
            var id = academy("render/distortion_ring");
            Minecraft.getInstance().getTextureManager().register(
                    id,
                    new AbstractTexture() {
                        {
                            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                        }

                        @Override
                        public GpuTexture getTexture() {
                            var tex = PostEffect.MAIN_SCENE.getColorTexture();
                            return tex
                                    == null
                                    ?
                                    Minecraft.getInstance().getTextureManager().getTexture(
                                            MissingTextureAtlasSprite.getLocation()
                                    ).getTexture()
                                    :
                                    tex;
                        }

                        @Override
                        public GpuTextureView getTextureView() {
                            var tex = PostEffect.MAIN_SCENE.getColorTextureView();
                            return tex
                                    == null
                                    ?
                                    Minecraft.getInstance().getTextureManager().getTexture(
                                            MissingTextureAtlasSprite.getLocation()
                                    ).getTextureView()
                                    :
                                    tex;
                        }
                    }
            );
            DISTORTION_RING = create(
                    "distortion_ring",
                    RenderSetup.builder(RenderPipelines.DISTORTION_RING)
                            .withTexture("Sampler0", id)
                            .createRenderSetup()
            );
            PostEffect.addFixedBuffer(POS_COLOR_QUADS);
            PostEffect.addFixedBuffer(POS_COLOR_TRANGLES);
            PostEffect.addFixedBuffer(POS_COLOR_QUADS_BLOOM);
            PostEffect.addFixedBuffer(POS_COLOR_TRANGLES_BLOOM);
            BloomEffect.addFixedBuffer(POS_COLOR_QUADS_BLOOM_POST);
            BloomEffect.addFixedBuffer(POS_COLOR_TRANGLES_BLOOM_POST);
        }

        private RenderTypes() {
        }
    }

    private Render() {
    }
}