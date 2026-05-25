package org.academy.api.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UiLightmap;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.PostEffect;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static com.mojang.blaze3d.pipeline.RenderPipeline.builder;
import static net.minecraft.client.renderer.rendertype.RenderType.create;
import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.Render.GaussianSamples.MAX_GAUSSIAN_SAMPLES;
import static org.academy.api.client.Render.GaussianSamples.getGaussianSamples;
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

        public void write(Std140Builder builder) {
            builder.putVec2(outSize).putVec2(blurDir).putInt(sampleCount);
            for (var sample : samples) {
                builder.putVec4(sample);
            }
        }

        public static void writeBlurUniforms(Vector2f outSize, float dirX, float dirY, int radius) {
            try (var memoryStack = MemoryStack.stackPush()) {
                var samples = getGaussianSamples(radius);
                var builder = Std140Builder.onStack(memoryStack, Render.BlurUniforms.UBO_SIZE);
                new Render.BlurUniforms(outSize, new Vector2f(dirX, dirY), samples.sampleCount(), samples.samples()).write(builder);
                var byteBuffer = builder.get();
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(getBlurUniformsBuffer().slice(), byteBuffer);
            }
        }

        public static GpuBuffer getBlurUniformsBuffer(){
            return Buffers.getInstance().getBlurUniformsBuffer();
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
        private final GpuBuffer projectionUB;
        private final GpuBuffer fullScreenQuadVBNDC;
        private final GpuBuffer fullScreenQuadUvVBNDC;
        private final GpuBuffer fullScreenQuadUvColorVBNDC;

        /**
         * 0~scaled
         */
        private GpuBuffer fullScreenQuadUvVBSDC;
        private GpuBuffer fullScreenQuadUvColorVBSDC;

        private final GpuBuffer blurUniformsBuffer;

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

        private void closeInternal() {
            fullScreenQuadUvVBNDC.close();
            fullScreenQuadUvVBSDC.close();
            fullScreenQuadUvColorVBSDC.close();
            projectionUB.close();
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
                        byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX
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
                        byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR
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
                        byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION
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

        private GpuBuffer createUvColorNDC() {
            try (
                    var byteBufferBuilder = ByteBufferBuilder.exactlySized(
                            DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 4
                    )
            ) {
                var bufferBuilder = new BufferBuilder(
                        byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR
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
        public static final RenderPipeline.Snippet PROJECTION_SNIPPET = builder()
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .buildSnippet();

        public static final RenderPipeline MSDF_TEXT = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/msdf_text"))
                .withVertexShader(Resource.Shaders.IMAGE)
                .withFragmentShader(Resource.Shaders.Fragment.MSDF_TEXT)
                .withSampler("Sampler0")
                .withUniform("MsdfUniforms", UniformType.UNIFORM_BUFFER)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline.Snippet BLIT_SCREEN_SNIPPET = builder()
                .withLocation(academy("pipeline/blit_screen"))
                .withVertexShader(Resource.Shaders.SCREEN_BLIT)
                .withFragmentShader(Resource.Shaders.SCREEN_BLIT)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                .buildSnippet();

        public static final RenderPipeline BLIT_SCREEN_WITH_BLEND = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen"))
                .withSampler("DiffuseSampler")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .build();

        public static final RenderPipeline BLIT_SCREEN_PREMULTIPLIED_ALPHA = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen"))
                .withSampler("DiffuseSampler")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .build();

        public static final RenderPipeline BLIT_SCREEN_WITHOUT_BLEND = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen"))
                .withSampler("DiffuseSampler")
                .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
                .build();

        public static final RenderPipeline BLIT_SCREEN_WITHOUT_BLEND_INVERSE_CUTOUT = builder(BLIT_SCREEN_SNIPPET)
                .withLocation(academy("pipeline/blit_screen"))
                .withSampler("DiffuseSampler")
                .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
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
                .withFragmentShader(Resource.Shaders.Fragment.GAUSSIAN_BLUR)
                .withSampler("DiffuseSampler")
                .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
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
                .withFragmentShader(Resource.Shaders.Fragment.BLOOM_BLEND)
                .withSampler("DiffuseSampler")
                .withSampler("BlurTexture1")
                .withSampler("BlurTexture2")
                .withSampler("BlurTexture3")
                .withUniform("BloomInfo", UniformType.UNIFORM_BUFFER)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_TRANGLES_ADDITIVE = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color_additive"))
                .withVertexShader(Resource.Shaders.POSITION_COLOR)
                .withFragmentShader(Resource.Shaders.POSITION_COLOR)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .build();

        public static final RenderPipeline IMAGE = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/image"))
                .withVertexShader(Resource.Shaders.IMAGE)
                .withFragmentShader(Resource.Shaders.IMAGE)
                .withSampler("Sampler0")
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline IMAGE_PREMULTIPLIED_ALPHA = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/image"))
                .withVertexShader(Resource.Shaders.POSITION_TEX_COLOR)
                .withFragmentShader(Resource.Shaders.POSITION_TEX_COLOR)
                .withSampler("Sampler0")
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline IMAGE_CIRCLE = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/image_circle"))
                .withVertexShader(Resource.Shaders.IMAGE)
                .withFragmentShader(Resource.Shaders.Fragment.IMAGE_CIRCLE)
                .withSampler("Sampler0")
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline IMAGE_STENCIL_PREMULTIPLIED_ALPHA = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/image"))
                .withVertexShader(Resource.Shaders.POSITION_TEX_COLOR)
                .withFragmentShader(Resource.Shaders.POSITION_TEX_COLOR)
                .withSampler("Sampler0")
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
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

        public static final RenderPipeline POS_COLOR = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/pos_color"))
                .withVertexShader(Resource.Shaders.POSITION_COLOR)
                .withFragmentShader(Resource.Shaders.Fragment.POS_COLOR)
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline SDF_SHARP_MARGIN = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/sdf_sharp_margin"))
                .withVertexShader(Resource.Shaders.POSITION_TEX)
                .withFragmentShader(Resource.Shaders.Fragment.SDF_SHARP_MARGIN)
                .withUniform("SdfUniforms", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline GLOW_CIRCLE = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/glow_circle"))
                .withVertexShader(Resource.Shaders.POSITION_TEX)
                .withFragmentShader(Resource.Shaders.Fragment.GLOW_CIRCLE)
                .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline SDF_CIRCLE_GLOW = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/sdf_circle_glow"))
                .withVertexShader(Resource.Shaders.POSITION_TEX)
                .withFragmentShader(Resource.Shaders.Fragment.SDF_CIRCLE_GLOW)
                .withUniform("GlowUniforms", UniformType.UNIFORM_BUFFER)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline LEVEL_POS_TEX_COLOR = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_tex_color"))
                .withVertexShader(Resource.Shaders.POSITION_TEX_COLOR)
                .withFragmentShader(Resource.Shaders.POSITION_TEX_COLOR)
                .withSampler("Sampler0")
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline LEVEL_POS_TEX_COLOR_HELLFLARE = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_tex_color_hellflare"))
                .withVertexShader(Resource.Shaders.POSITION_TEX_COLOR)
                .withFragmentShader(Resource.Shaders.Fragment.HELLFLARE_STEAM)
                .withSampler("Sampler0")
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline LEVEL_POS_TEX_COLOR_HELLFLARE_ADDITIVE = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_tex_color_hellflare_additive"))
                .withVertexShader(Resource.Shaders.POSITION_TEX_COLOR)
                .withFragmentShader(Resource.Shaders.Fragment.HELLFLARE_STEAM)
                .withSampler("Sampler0")
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_QUADS = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color"))
                .withVertexShader(Resource.Shaders.POSITION_COLOR)
                .withFragmentShader(Resource.Shaders.POSITION_COLOR)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline LEVEL_POS_COLOR_TRANGLES = builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(academy("pipeline/level_pos_color"))
                .withVertexShader(Resource.Shaders.POSITION_COLOR)
                .withFragmentShader(Resource.Shaders.POSITION_COLOR)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline DISTORTION_RING = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/distortion_ring"))
                .withVertexShader(Resource.Shaders.DISTORTION_RING)
                .withFragmentShader(Resource.Shaders.DISTORTION_RING)
                .withSampler("Sampler0")
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(
                        VertexFormat.builder()
                                .add("Position", VertexFormatElement.POSITION)
                                .add("UV0", VertexFormatElement.UV0)
                                .add("Normal", VertexFormatElement.NORMAL)
                                .padding(1)
                                .build(),
                        VertexFormat.Mode.QUADS
                )
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        public static final RenderPipeline DISTORTION_TUBE = builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(academy("pipeline/distortion_tube"))
                .withVertexShader(Resource.Shaders.DISTORTION_TUBE)
                .withFragmentShader(Resource.Shaders.DISTORTION_TUBE)
                .withSampler("Sampler0")
                .withCull(true)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(
                        VertexFormat.builder()
                                .add("Position", VertexFormatElement.POSITION)
                                .add("UV0", VertexFormatElement.UV0)
                                .add("Normal", VertexFormatElement.NORMAL)
                                .padding(1)
                                .build(),
                        VertexFormat.Mode.QUADS
                )
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build();

        private RenderPipelines() {
        }
    }

    public abstract static class RenderTypes extends net.minecraft.client.renderer.rendertype.RenderTypes {
        public static final RenderType STORM_WING = create(
                "storm_wing",
                RenderSetup.builder(Render.RenderPipelines.LEVEL_POS_TEX_COLOR)
                        .withTexture(
                                "Sampler0", Resource.Textures.STORM_WING,
                                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                        .sortOnUpload()
                        .createRenderSetup()
        );

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
        public static final RenderType DISTORTION_TUBE_TYPE;
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


            var tubeId = academy("render/distortion_tube");
            Minecraft.getInstance().getTextureManager().register(
                    tubeId,
                    new AbstractTexture() {
                        {
                            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                        }

                        @Override
                        public GpuTexture getTexture() {
                            var tex = PostEffect.MAIN_SCENE.getColorTexture();
                            return tex == null
                                    ? Minecraft.getInstance().getTextureManager().getTexture(MissingTextureAtlasSprite.getLocation()).getTexture()
                                    : tex;
                        }

                        @Override
                        public GpuTextureView getTextureView() {
                            var tex = PostEffect.MAIN_SCENE.getColorTextureView();
                            return tex == null
                                    ? Minecraft.getInstance().getTextureManager().getTexture(MissingTextureAtlasSprite.getLocation()).getTextureView()
                                    : tex;
                        }
                    }
            );
            DISTORTION_TUBE_TYPE = create(
                    "distortion_tube",
                    RenderSetup.builder(RenderPipelines.DISTORTION_TUBE)
                            .withTexture("Sampler0", tubeId)
                            .createRenderSetup()
            );

            PostEffect.addFixedBuffer(POS_COLOR_QUADS);
            PostEffect.addFixedBuffer(POS_COLOR_TRANGLES);
            PostEffect.addFixedBuffer(POS_COLOR_QUADS_BLOOM);
            PostEffect.addFixedBuffer(POS_COLOR_TRANGLES_BLOOM);
            BloomEffect.addFixedBuffer(POS_COLOR_QUADS_BLOOM_POST);
            BloomEffect.addFixedBuffer(POS_COLOR_TRANGLES_BLOOM_POST);
            BloomEffect.addFixedBuffer(POS_COLOR_TRANGLES_BLOOM_ADDITIVE);
        }

        private RenderTypes() {
        }

        public static RenderType getBloomTexture(Identifier identifier) {
            return create(
                    "bloom_texture",
                    RenderSetup.builder(RenderPipelines.LEVEL_POS_TEX_COLOR)
                            .withTexture(
                                    "Sampler0", identifier,
                                    () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                            )
                            .setOutputTarget(BLOOM_TARGET)
                            .createRenderSetup()
            );
        }

        public static RenderType getHellFlareSteam(Identifier identifier) {
            return create(
                    "hellflare_steam",
                    RenderSetup.builder(RenderPipelines.LEVEL_POS_TEX_COLOR_HELLFLARE_ADDITIVE)
                            .withTexture(
                                    "Sampler0", identifier,
                                    () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                            )
                            .createRenderSetup()
            );
        }

        public static RenderType getHellFlareSteamBloom(Identifier identifier) {
            return create(
                    "hellflare_steam_bloom",
                    RenderSetup.builder(RenderPipelines.LEVEL_POS_TEX_COLOR_HELLFLARE_ADDITIVE)
                            .withTexture(
                                    "Sampler0", identifier,
                                    () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                            )
                            .setOutputTarget(BLOOM_TARGET)
                            .createRenderSetup()
            );
        }
    }

    private Render() {
    }
}
