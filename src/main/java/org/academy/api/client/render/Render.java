package org.academy.api.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.UiLightmap;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;
import org.academy.AcademyCraft;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.resources.R;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static com.mojang.blaze3d.pipeline.RenderPipeline.builder;
import static net.minecraft.client.renderer.rendertype.RenderType.create;
import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.render.Render.GaussianSamples.MAX_GAUSSIAN_SAMPLES;
import static org.academy.api.client.render.Render.GaussianSamples.getGaussianSamples;
import static org.academy.api.client.render.post.BloomEffect.BLOOM_TARGET;

public final class Render {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private Render() {
    }

    public static void init() {
        Buffers.init();
        TextureViews.init();
    }

    public static void resize() {
        BloomEffect.onResize();
        Buffers.getResourcePool().clear();
        if (Buffers.instance != null) {
            Buffers.getInstance().recreateSDC();
        }
    }

    public static void runBlitPass(
            GpuTextureView color, RenderPipeline pipeline, GpuBuffer fullscreenQuadVertexBuffer,
            List<TextureBinding> textures, List<UniformBinding> uniforms,
            boolean clear
    ) {
        runBlitPass(color, null, clear, false, pipeline, fullscreenQuadVertexBuffer, textures, uniforms);
    }

    /**
     * @param color                      输出喵
     * @param depth                      模板喵
     * @param pipeline                   管线喵
     * @param fullscreenQuadVertexBuffer 顶点缓冲区喵
     * @param textures                   Textures 喵
     * @param uniforms                   Uniforms 喵
     * @param clearColor                 是否在输出前清除颜色喵
     * @param clearDepth                 是否在输出前清除深度喵
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
                                clearColor ? Optional.of(new Vector4f(0)) : Optional.empty()
                        )
                        : commandEncoder.createRenderPass
                        (
                                () -> "Blit Pass to " + color + depth,
                                color,
                                clearColor ? Optional.of(new Vector4f(0)) : Optional.empty(),
                                depth,
                                clearDepth ? OptionalDouble.of(1) : OptionalDouble.empty()
                        )
        ) {
            IrisCompat.runWithBypass(() -> {
                renderPass.setPipeline(pipeline);

                for (var texture : textures) {
                    renderPass.bindTexture(texture.name(), texture.view(), texture.sampler());
                }
                for (var uniform : uniforms) {
                    renderPass.setUniform(uniform.name(), uniform.slice());
                }

                renderPass.setVertexBuffer(0, fullscreenQuadVertexBuffer.slice());
                var sequentialBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                renderPass.setIndexBuffer(sequentialBuffer.getBuffer(6), sequentialBuffer.type());
                renderPass.drawIndexed(6, 1, 0, 0, 0);
            });
        }
    }

    public static void close() {
        Buffers.close();
        TextureViews.close();
    }

    public record GaussianSamples(int sampleCount, Vector4f[] samples) {
        public static final int MAX_GAUSSIAN_SAMPLES = 12;
        private static final Int2ObjectMap<GaussianSamples> SAMPLES_CACHE = new Int2ObjectLinkedOpenHashMap<>();

        public static GaussianSamples getGaussianSamples(int radius) {
            return SAMPLES_CACHE.computeIfAbsent(radius, key -> {
                var samples = new Vector4f[MAX_GAUSSIAN_SAMPLES];
                var weights = new float[key + 1];
                var totalWeight = 0.0f;
                var sigma = key / 2.0f;

                for (var i = 0; i <= key; i++) {
                    weights[i] = (float) (Math.exp(-0.5 * (i * i) / (sigma * sigma)));
                    totalWeight += (i == 0 ? 1.0f : 2.0f) * weights[i];
                }

                for (var i = 0; i < weights.length; i++) {
                    weights[i] /= totalWeight;
                }

                var sampleCount = 0;
                samples[sampleCount++] = new Vector4f(0.0f, 0.0f, weights[0], 0.0f);

                for (var i = 1; i < key; i += 2) {
                    var weight1 = weights[i];
                    var weight2 = weights[i + 1];
                    var total = weight1 + weight2;
                    var offset = (i * weight1 + (i + 1.0f) * weight2) / total;
                    samples[sampleCount++] = new Vector4f(offset, offset, total, 0.0f);
                }

                for (var i = sampleCount; i < MAX_GAUSSIAN_SAMPLES; i++) {
                    samples[i] = new Vector4f();
                }

                return new GaussianSamples(sampleCount, samples);
            });
        }
    }

    public record BlurUniforms(Vector2f outSize, Vector2f blurDir, int sampleCount, Vector4f[] samples) {
        public static final int UBO_SIZE;

        static {
            var calculator = new Std140SizeCalculator().putVec2().putVec2().putInt();
            for (var i = 0; i < MAX_GAUSSIAN_SAMPLES; i++) {
                calculator.putVec4();
            }
            UBO_SIZE = calculator.get();
        }

        public static void writeBlurUniforms(Vector2f outSize, float dirX, float dirY, int radius) {
            try (var memoryStack = MemoryStack.stackPush()) {
                var samples = getGaussianSamples(radius);
                var builder = Std140Builder.onStack(memoryStack, UBO_SIZE);
                new Render.BlurUniforms(outSize, new Vector2f(dirX, dirY), samples.sampleCount(), samples.samples()).write(builder);
                var byteBuffer = builder.get();
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(getBlurUniformsBuffer().slice(), byteBuffer);
            }
        }

        public static GpuBuffer getBlurUniformsBuffer() {
            return Buffers.getInstance().getBlurUniformsBuffer();
        }

        public void write(Std140Builder builder) {
            builder.putVec2(outSize).putVec2(blurDir).putInt(sampleCount);
            for (var sample : samples) {
                builder.putVec4(sample);
            }
        }
    }

    public static final class Buffers {
        public static final int PROJECTION_UBO_SIZE = new Std140SizeCalculator().putMat4f().get();
        private static final CrossFrameResourcePool RESOURCE_POOL = new CrossFrameResourcePool(3);
        private static final ByteBufferBuilder BYTE_BUFFER_BUILDER = new ByteBufferBuilder(786432);
        @Nullable
        private static Buffers instance;

        /**
         * -1~1
         */
        private final GpuBuffer projectionUB;
        private final GpuBuffer fullScreenQuadVBNDC;
        private final GpuBuffer fullScreenQuadUvVBNDC;
        private final GpuBuffer fullScreenQuadUvColorVBNDC;
        private final GpuBuffer blurUniformsBuffer;
        /**
         * 0~scaled
         */
        private GpuBuffer fullScreenQuadUvVBSDC;
        private GpuBuffer fullScreenQuadUvColorVBSDC;
        @Nullable
        private Matrix4fc lastProjection;

        private Buffers() {
            fullScreenQuadVBNDC = createNDC();
            fullScreenQuadUvVBNDC = createUvNDC();
            fullScreenQuadUvColorVBNDC = createUvColorNDC();
            createSDC();
            projectionUB = createProjection();

            var device = RenderSystem.getDevice();
            var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
            blurUniformsBuffer = device.createBuffer(() -> "Blur UBO", uboUsage, Render.BlurUniforms.UBO_SIZE);
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

        public static ByteBufferBuilder getByteBufferBuilder() {
            return BYTE_BUFFER_BUILDER;
        }

        public static CrossFrameResourcePool getResourcePool() {
            return RESOURCE_POOL;
        }

        private void closeInternal() {
            fullScreenQuadVBNDC.close();
            fullScreenQuadUvVBNDC.close();
            fullScreenQuadUvColorVBNDC.close();
            fullScreenQuadUvVBSDC.close();
            fullScreenQuadUvColorVBSDC.close();
            projectionUB.close();
            blurUniformsBuffer.close();
        }

        public void recreateSDC() {
            fullScreenQuadUvVBSDC.close();
            fullScreenQuadUvColorVBSDC.close();
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
                        byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX
                );
                bufferBuilder.addVertex(0, 0, 0.0F).setUv(0.0F, 0.0F);
                bufferBuilder.addVertex(width, 0, 0.0F).setUv(1.0F, 0.0F);
                bufferBuilder.addVertex(width, height, 0.0F).setUv(1.0F, 1.0F);
                bufferBuilder.addVertex(0, height, 0.0F).setUv(0.0F, 1.0F);

                try (var meshData = bufferBuilder.buildOrThrow()) {
                    fullScreenQuadUvVBSDC = RenderSystem.getDevice().createBuffer(
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
                        byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR
                );
                var white = -1;
                bufferBuilder.addVertex(0, height, 0.0F).setUv(0.0F, 1.0F).setColor(white);
                bufferBuilder.addVertex(0, 0, 0.0F).setUv(0.0F, 0.0F).setColor(white);
                bufferBuilder.addVertex(width, 0, 0.0F).setUv(1.0F, 0.0F).setColor(white);
                bufferBuilder.addVertex(width, height, 0.0F).setUv(1.0F, 1.0F).setColor(white);

                try (var meshData = bufferBuilder.buildOrThrow()) {
                    fullScreenQuadUvColorVBSDC = RenderSystem.getDevice().createBuffer(
                            () -> "Fullscreen Quad Color SDC", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer()
                    );
                }
            }
        }

        private GpuBuffer createNDC() {
            try (
                    var byteBufferBuilder = ByteBufferBuilder.exactlySized(
                            DefaultVertexFormat.POSITION.getVertexSize() * 4
                    )
            ) {
                var bufferBuilder = new BufferBuilder(
                        byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION
                );
                bufferBuilder.addVertex(-1.0F, -1.0F, 0.0F);
                bufferBuilder.addVertex(1.0F, -1.0F, 0.0F);
                bufferBuilder.addVertex(1.0F, 1.0F, 0.0F);
                bufferBuilder.addVertex(-1.0F, 1.0F, 0.0F);

                try (var meshData = bufferBuilder.buildOrThrow()) {
                    return RenderSystem.getDevice().createBuffer(
                            () -> "Fullscreen Quad NDC", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer()
                    );
                }
            }
        }

        private GpuBuffer createUvNDC() {
            try (
                    var byteBufferBuilder = ByteBufferBuilder.exactlySized(
                            DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4
                    )
            ) {
                var bufferBuilder = new BufferBuilder(
                        byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX
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

        private GpuBuffer createUvColorNDC() {
            try (
                    var byteBufferBuilder = ByteBufferBuilder.exactlySized(
                            DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 4
                    )
            ) {
                var bufferBuilder = new BufferBuilder(
                        byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR
                );
                var white = -1;
                bufferBuilder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F).setColor(white);
                bufferBuilder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F).setColor(white);
                bufferBuilder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F).setColor(white);
                bufferBuilder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F).setColor(white);

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

        public GpuBuffer getProjectionUB(Matrix4fc projection) {
            if (projection.equals(lastProjection, 0)) return projectionUB;
            try (var memoryStack = MemoryStack.stackPush()) {
                var builder = Std140Builder.onStack(memoryStack, PROJECTION_UBO_SIZE);
                builder.putMat4f(projection);
                var byteBuffer = builder.get();
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(projectionUB.slice(), byteBuffer);
            }
            // new 是因为 org/joml/Matrix4f.java:13732
            lastProjection = new Matrix4f(projection);
            return projectionUB;
        }

        public GpuBuffer getFSQuadUvVBNDC() {
            return fullScreenQuadUvVBNDC;
        }

        public GpuBuffer getFSQuadVBNDC() {
            return fullScreenQuadVBNDC;
        }

        public GpuBuffer getFSQuadUvVBSDC() {
            return fullScreenQuadUvVBSDC;
        }

        public GpuBuffer getFSQuadUvColorVBSDC() {
            return fullScreenQuadUvColorVBSDC;
        }

        public GpuBuffer getFSQuadColorVBNDC() {
            return fullScreenQuadUvColorVBNDC;
        }

        public GpuBuffer getBlurUniformsBuffer() {
            return blurUniformsBuffer;
        }
    }

    public static final class TextureViews {
        @Nullable
        private static TextureViews instance;
        private final UiLightmap uiLightmap = new UiLightmap();

        private TextureViews() {
        }

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
    }

    @EventBusSubscriber
    public static final class RenderPipelines extends net.minecraft.client.renderer.RenderPipelines {
        public static final RenderPipeline.Snippet PROJECTION_SNIPPET = builder()
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .buildSnippet();

        public static final RenderPipeline IMGUI = builder()
                .withLocation(R.shaders.core.imgui)
                .withVertexShader(R.shaders.core.imgui)
                .withFragmentShader(R.shaders.core.imgui)
                .withVertexBinding(0, VertexFormat.builder(0)
                        .addAttribute("Position", GpuFormat.RG32_FLOAT)
                        .addAttribute("UV", GpuFormat.RG32_FLOAT)
                        .addAttribute("Color", GpuFormat.RGBA8_UNORM)
                        .build()
                )
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(
                        new ColorTargetState(
                                new BlendFunction(
                                        BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA,
                                        BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA
                                )
                        )
                )
                .withCull(false)
                .withPolygonMode(PolygonMode.FILL)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withSampler("Texture")
                                .build()
                )
                .build();

        public static final RenderPipeline MSDF_TEXT = builder()
                .withLocation(academy("pipeline/msdf_text"))
                .withVertexShader(R.shaders.core.msdf_text_instanced)
                .withFragmentShader(R.shaders.core.msdf_text)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withUniform("MsdfUniforms", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, VertexFormat.builder(0)
                        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                        .build()
                )
                .withVertexBinding(1, VertexFormat.builder(1)
                        .addAttribute("InstPos", GpuFormat.RGB32_FLOAT)
                        .addAttribute("InstSize", GpuFormat.RG32_FLOAT)
                        .addAttribute("InstUVStart", GpuFormat.RG32_FLOAT)
                        .addAttribute("InstUVEnd", GpuFormat.RG32_FLOAT)
                        .addAttribute("InstColor", GpuFormat.RGBA32_FLOAT)
                        .build()
                )
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build();

        public static final RenderPipeline.Snippet BLIT_SCREEN_SNIPPET = builder()
                .withVertexShader(R.shaders.core.screen_blit)
                .withFragmentShader(R.shaders.core.screen_blit)
                .withCull(false)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .buildSnippet();

        public static final RenderPipeline BLIT_SCREEN_WITH_BLEND = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen_with_blend"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .build();

        public static final RenderPipeline BLIT_SCREEN_PREMULTIPLIED_ALPHA = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen_premultiplied_alpha"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .build();

        public static final RenderPipeline BLIT_SCREEN_WITHOUT_BLEND = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen_without_blend"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .build();

        public static final RenderPipeline BLIT_SCREEN_WITHOUT_BLEND_INVERSE_CUTOUT = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen_without_blend_inverse_cutout"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withStencilTest(
                        new StencilTest(
                                new StencilPerFaceTest(
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        CompareOp.EQUAL
                                ),
                                0XFF,
                                0XFF,
                                0
                        )
                )
                .build();

        public static final RenderPipeline GAUSSIAN_BLUR = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/gaussian_blur"))
                .withFragmentShader(R.shaders.core.gaussian_blur)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .build();

        public static final RenderPipeline CUTOUT_GAUSSIAN_BLUR = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/cutout_gaussian_blur"))
                .withFragmentShader(R.shaders.core.gaussian_blur)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withStencilTest(
                        new StencilTest(
                                new StencilPerFaceTest(
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        CompareOp.EQUAL
                                ),
                                0XFF,
                                0XFF,
                                1
                        )
                )
                .build();

        public static final RenderPipeline BLOOM_BLEND = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/bloom_blend"))
                .withFragmentShader(R.shaders.core.bloom_blend)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withSampler("BlurTexture1")
                                .withSampler("BlurTexture2")
                                .withSampler("BlurTexture3")
                                .withUniform("BloomInfo", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_TRANGLES_ADDITIVE = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color_additive"))
                .withVertexShader(R.shaders.position_color)
                .withFragmentShader(R.shaders.position_color)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .build();

        public static final RenderPipeline MINE_DETECT_LINES = builder(LINES_SNIPPET)
                .withLocation(academy("pipeline/mine_detect_lines"))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build();

        public static final RenderPipeline IMAGE = builder()
                .withLocation(academy("pipeline/image"))
                .withVertexShader(R.shaders.core.image)
                .withFragmentShader(R.shaders.core.image)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .build();

        public static final RenderPipeline IMAGE_PREMULTIPLIED_ALPHA = builder()
                .withLocation(academy("pipeline/image_premultiplied_alpha"))
                .withVertexShader(R.shaders.position_tex_color)
                .withFragmentShader(R.shaders.position_tex_color)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .build();

        public static final RenderPipeline IMAGE_CIRCLE = builder()
                .withLocation(academy("pipeline/image_circle"))
                .withVertexShader(R.shaders.core.image)
                .withFragmentShader(R.shaders.core.image_circle)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .build();

        public static final RenderPipeline IMAGE_MONOCHROME = builder()
                .withLocation(academy("pipeline/image_monochrome"))
                .withVertexShader(R.shaders.core.image)
                .withFragmentShader(R.shaders.core.image_monochrome)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .build();

        public static final RenderPipeline SKILL_PROGRESS = builder()
                .withLocation(academy("pipeline/skill_progress"))
                .withVertexShader(R.shaders.core.image)
                .withFragmentShader(R.shaders.core.skill_progress)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withUniform("SkillProgress", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .build();

        public static final RenderPipeline IMAGE_STENCIL_PREMULTIPLIED_ALPHA = builder()
                .withLocation(academy("pipeline/image_stencil_premultiplied_alpha"))
                .withVertexShader(R.shaders.position_tex_color)
                .withFragmentShader(R.shaders.position_tex_color)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withStencilTest(
                        new StencilTest(
                                new StencilPerFaceTest(
                                        StencilOperation.KEEP,
                                        StencilOperation.KEEP,
                                        StencilOperation.REPLACE,
                                        CompareOp.ALWAYS_PASS
                                ),
                                0XFF,
                                0XFF,
                                1
                        )
                )
                .build();

        public static final RenderPipeline POS_COLOR = builder()
                .withLocation(academy("pipeline/pos_color"))
                .withVertexShader(R.shaders.position_color)
                .withFragmentShader(R.shaders.core.pos_color)
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .build();

        public static final RenderPipeline SDF_SHARP_MARGIN = builder()
                .withLocation(academy("pipeline/sdf_sharp_margin"))
                .withVertexShader(R.shaders.position_tex)
                .withFragmentShader(R.shaders.core.sdf_sharp_margin)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withUniform("SdfUniforms", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .build();

        public static final RenderPipeline GLOW_CIRCLE = builder()
                .withLocation(academy("pipeline/glow_circle"))
                .withVertexShader(R.shaders.position_tex)
                .withFragmentShader(R.shaders.core.glow_circle)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withCull(false)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .build();

        public static final RenderPipeline SDF_CIRCLE_GLOW = builder()
                .withLocation(academy("pipeline/sdf_circle_glow"))
                .withVertexShader(R.shaders.position_tex)
                .withFragmentShader(R.shaders.core.sdf_circle_glow)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withUniform("GlowUniforms", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .build();

        public static final RenderPipeline LEVEL_POS_TEX_COLOR = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_tex_color"))
                .withVertexShader(R.shaders.position_tex_color)
                .withFragmentShader(R.shaders.position_tex_color)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline LEVEL_POS_TEX_COLOR_NO_DEPTH_WRITE = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_tex_color_no_depth_write"))
                .withVertexShader(R.shaders.position_tex_color)
                .withFragmentShader(R.shaders.position_tex_color)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build();

        public static final RenderPipeline PLATINUM_COSMIC_WING = builder()
                .withLocation(academy("pipeline/platinum_cosmic_wing"))
                .withVertexShader(R.shaders.core.PLATINUM_COSMIC_WING)
                .withFragmentShader(R.shaders.core.PLATINUM_COSMIC_WING)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline PLATINUM_COSMIC_WING_NO_DEPTH_WRITE = builder()
                .withLocation(academy("pipeline/platinum_cosmic_wing_no_depth_write"))
                .withVertexShader(R.shaders.core.PLATINUM_COSMIC_WING)
                .withFragmentShader(R.shaders.core.PLATINUM_COSMIC_WING)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_QUADS = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color_quads"))
                .withVertexShader(R.shaders.position_color)
                .withFragmentShader(R.shaders.position_color)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_QUADS_NO_DEPTH_WRITE = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color_quads_no_depth_write"))
                .withVertexShader(R.shaders.POSITION_COLOR)
                .withFragmentShader(R.shaders.POSITION_COLOR)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_QUADS_ADDITIVE = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color_quads_additive"))
                .withVertexShader(R.shaders.POSITION_COLOR)
                .withFragmentShader(R.shaders.POSITION_COLOR)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_TRANGLES = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color_trangles"))
                .withVertexShader(R.shaders.position_color)
                .withFragmentShader(R.shaders.position_color)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline DISTORTION_RING = builder()
                .withLocation(academy("pipeline/distortion_ring"))
                .withVertexShader(R.shaders.core.distortion_ring)
                .withFragmentShader(R.shaders.core.distortion_ring)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, VertexFormat.builder(0)
                        .addAttribute("Position", DefaultVertexFormat.POSITION_FORMAT)
                        .addAttribute("UV0", DefaultVertexFormat.UV0_FORMAT)
                        .addAttribute("Normal", DefaultVertexFormat.NORMAL_FORMAT)
                        .build())
                .build();

        public static final RenderPipeline LEVEL_POS_TEX_COLOR_ADDITIVE_BLOOM = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_tex_color_additive_bloom"))
                .withVertexShader(R.shaders.position_tex_color)
                .withFragmentShader(R.shaders.core.particle_additive)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .build();

        public static final RenderPipeline SPATIAL_DISTORTION = builder()
                .withLocation(academy("pipeline/spatial_distortion"))
                .withVertexShader(R.shaders.position_tex)
                .withFragmentShader(R.shaders.core.spatial_distortion)
                .withBindGroupLayout(
                        BindGroupLayout.builder()
                                .withUniform("SpatialUniforms", UniformType.UNIFORM_BUFFER)
                                .build()
                )
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .build();

        private RenderPipelines() {
        }

        @SubscribeEvent
        public static void onRegisterRenderPipelinesEvent(RegisterRenderPipelinesEvent event) {
            for (var field : RenderPipelines.class.getDeclaredFields()) {
                var modifiers = field.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                    continue;
                }
                if (field.getType() != RenderPipeline.class) continue;
                try {
                    var pipeline = (RenderPipeline) field.get(null);
                    event.registerPipeline(pipeline);
                } catch (IllegalAccessException e) {
                    LOGGER.warn(e.getMessage());
                }
            }
        }
    }

    public abstract static class RenderTypes extends net.minecraft.client.renderer.rendertype.RenderTypes {
        public static final RenderType MINE_DETECT_LINES = create(
                "mine_detect_lines",
                RenderSetup.builder(RenderPipelines.MINE_DETECT_LINES)
                        .createRenderSetup()
        );

        public static final RenderType STORM_WING = create(
                "storm_wing",
                RenderSetup.builder(Render.RenderPipelines.LEVEL_POS_TEX_COLOR)
                        .withTexture(
                                "Sampler0", R.textures.ability.accelerator.skill.storm_wing.effect.tornado_ring,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType PLASMA_CLOUD = create(
                "plasma_cloud",
                RenderSetup.builder(Render.RenderPipelines.LEVEL_POS_TEX_COLOR)
                        .withTexture(
                                "Sampler0", R.textures.plasma_generation_cloud,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType BLACK_WING = create(
                "black_wing",
                RenderSetup.builder(Render.RenderPipelines.LEVEL_POS_TEX_COLOR)
                        .withTexture(
                                "Sampler0", R.textures.black_wing,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType WHITE_WING = create(
                "white_wing",
                RenderSetup.builder(Render.RenderPipelines.LEVEL_POS_TEX_COLOR)
                        .withTexture(
                                "Sampler0", R.textures.white_wing,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType PLATINUM_WING = create(
                "platinum_wing",
                RenderSetup.builder(Render.RenderPipelines.PLATINUM_COSMIC_WING)
                        .withTexture(
                                "Sampler0", R.textures.white_wing,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .withTexture(
                                "Sampler1", R.textures.platinum_wing_starfield,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType BLACK_WING_FIRST_PERSON = create(
                "black_wing_first_person",
                RenderSetup.builder(Render.RenderPipelines.LEVEL_POS_TEX_COLOR_NO_DEPTH_WRITE)
                        .withTexture(
                                "Sampler0", R.textures.black_wing,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType WHITE_WING_FIRST_PERSON = create(
                "white_wing_first_person",
                RenderSetup.builder(Render.RenderPipelines.LEVEL_POS_TEX_COLOR_NO_DEPTH_WRITE)
                        .withTexture(
                                "Sampler0", R.textures.white_wing,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType PLATINUM_WING_FIRST_PERSON = create(
                "platinum_wing_first_person",
                RenderSetup.builder(Render.RenderPipelines.PLATINUM_COSMIC_WING_NO_DEPTH_WRITE)
                        .withTexture(
                                "Sampler0", R.textures.white_wing,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .withTexture(
                                "Sampler1", R.textures.platinum_wing_starfield,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType PLATINUM_WING_BYPASS = create(
                "platinum_wing_bypass",
                RenderSetup.builder(Render.RenderPipelines.PLATINUM_COSMIC_WING_NO_DEPTH_WRITE)
                        .withTexture(
                                "Sampler0", R.textures.white_wing,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .withTexture(
                                "Sampler1", R.textures.platinum_wing_starfield,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType IRON_SAND_FIRST_PERSON = create(
                "iron_sand_first_person",
                RenderSetup.builder(Render.RenderPipelines.LEVEL_POS_TEX_COLOR_NO_DEPTH_WRITE)
                        .withTexture(
                                "Sampler0", R.textures.iron_sand_arsenal_effect,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType ARC = create(
                "arc",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_TEX_COLOR)
                        .withTexture(
                                "Sampler0", R.textures.ability.electromaster.skill.arc_generate.effect.line_segment,
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

        public static final RenderType POS_COLOR_QUADS_NO_DEPTH_WRITE = create(
                "pos_color_quads_no_depth_write",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_QUADS_NO_DEPTH_WRITE)
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType POS_COLOR_QUADS_ADDITIVE = create(
                "pos_color_quads_additive",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_QUADS_ADDITIVE)
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType POS_COLOR_QUADS_BLOOM_ADDITIVE = create(
                "pos_color_quads_bloom_additive",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_QUADS_ADDITIVE)
                        .setOutputTarget(BLOOM_TARGET)
                        .createRenderSetup()
        );

        public static final RenderType POS_COLOR_TRANGLES = create(
                "pos_color_trangles",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_TRANGLES)
                        .createRenderSetup()
        );

        public static final RenderType POS_COLOR_TRANGLES_BLOOM_ADDITIVE = create(
                "pos_color_trangles_bloom_additive",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_TRANGLES_ADDITIVE)
                        .setOutputTarget(BLOOM_TARGET)
                        .createRenderSetup()
        );

        // 记得使用对应的 BufferSource 喵

        /**
         * 同时输出到 Main 与 INPUT 喵
         */
        public static final RenderType POS_COLOR_QUADS_BLOOM = create(
                "pos_color_quads_bloom",
                RenderSetup.builder(RenderPipelines.LEVEL_POS_COLOR_QUADS)
                        //  .setOutputTarget(BLOOM_TARGET)
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

        public static final RenderType SPATIAL_DISTORTION = create(
                "spatial_distortion",
                RenderSetup.builder(RenderPipelines.SPATIAL_DISTORTION)
                        .sortOnUpload()
                        .createRenderSetup()
        );

        public static final RenderType DISTORTION_RING;
        public static final RenderType ABILITY_DEVELOPER = entityTranslucent(R.textures.model.ability_developer);
        public static final RenderType CAT_ENGINE = entityTranslucent(R.textures.item.cat_engine);
        public static final RenderType CLEANING_ROBOT = entitySolid(R.textures.model.cleaning_robot);

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
        }

        private RenderTypes() {
        }

    }
}
