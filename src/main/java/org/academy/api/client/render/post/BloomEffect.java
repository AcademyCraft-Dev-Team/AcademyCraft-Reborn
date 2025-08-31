package org.academy.api.client.render.post;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.Render;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;
import java.util.SequencedMap;

public final class BloomEffect {
    private static final RenderTarget INPUT, OUTPUT, SWAP2A, SWAP4A, SWAP8A, SWAP2B, SWAP4B, SWAP8B;
    private static final ByteBufferBuilder BYTE_BUFFER_BUILDER = new ByteBufferBuilder(786432);
    private static final SequencedMap<RenderType, ByteBufferBuilder> FIXED_BUFFERS = new Object2ObjectLinkedOpenHashMap<>();
    public static final MultiBufferSource.BufferSource BUFFER_SOURCE = MultiBufferSource.immediateWithBuffers(FIXED_BUFFERS, BYTE_BUFFER_BUILDER);
    private static final GpuBuffer blurUniformsBuffer;
    private static final GpuBuffer bloomUniformsBuffer;
    private static final GpuBuffer fullscreenQuadVertexBuffer;

    public static final RenderStateShard.OutputStateShard BLOOM_TARGET = new RenderStateShard.OutputStateShard(
            "bloom_target",
            BloomEffect::getInput
    );

    static {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        var width = mainRenderTarget.width;
        var height = mainRenderTarget.height;

        INPUT = new TextureTarget(null, width, height, true) {
            @Override
            public void createBuffers(int width, int height) {
                RenderSystem.assertOnRenderThread();
                var gpudevice = RenderSystem.getDevice();
                var i = gpudevice.getMaxTextureSize();
                if (width > 0 && width <= i && height > 0 && height <= i) {
                    this.viewWidth = width;
                    this.viewHeight = height;
                    this.width = width;
                    this.height = height;

                    colorTexture = gpudevice.createTexture(() -> label + " / Color", 15, TextureFormat.RGBA8, width, height, 1, 1);
                    colorTextureView = gpudevice.createTextureView(colorTexture);
                    colorTexture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
                    setFilterMode(FilterMode.LINEAR);
                } else {
                    throw new IllegalArgumentException("Window " + width + "x" + height + " size out of bounds (max. size: " + i + ")");
                }
            }

            @Override
            public @Nullable GpuTextureView getDepthTextureView() {
                return Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
            }

            @Override
            public @Nullable GpuTexture getDepthTexture() {
                return Minecraft.getInstance().getMainRenderTarget().getDepthTexture();
            }
        };
        OUTPUT = getRenderTarget(width, height);

        SWAP2A = getRenderTarget(width / 2, height / 2);
        SWAP4A = getRenderTarget(width / 4, height / 4);
        SWAP8A = getRenderTarget(width / 8, height / 8);
        SWAP2B = getRenderTarget(width / 2, height / 2);
        SWAP4B = getRenderTarget(width / 4, height / 4);
        SWAP8B = getRenderTarget(width / 8, height / 8);

        var device = RenderSystem.getDevice();
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        blurUniformsBuffer = device.createBuffer(() -> "Bloom Blur UBO", uboUsage, BlurUniforms.UBO_SIZE);
        bloomUniformsBuffer = device.createBuffer(() -> "Bloom Blend UBO", uboUsage, BloomUniforms.UBO_SIZE);

        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4)) {
            var bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferBuilder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
            bufferBuilder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
            bufferBuilder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
            bufferBuilder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);

            try (var meshData = bufferBuilder.buildOrThrow()) {
                fullscreenQuadVertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Bloom Fullscreen Quad", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
            }
        }
    }

    public static void close() {
        INPUT.destroyBuffers();
        OUTPUT.destroyBuffers();
        SWAP2A.destroyBuffers();
        SWAP4A.destroyBuffers();
        SWAP8A.destroyBuffers();
        SWAP2B.destroyBuffers();
        SWAP4B.destroyBuffers();
        SWAP8B.destroyBuffers();
        blurUniformsBuffer.close();
        bloomUniformsBuffer.close();
        fullscreenQuadVertexBuffer.close();
        BYTE_BUFFER_BUILDER.close();
    }

    public static void resize(int width, int height) {
        INPUT.resize(width, height);
        resize(OUTPUT, width, height);
        resize(SWAP2A, width / 2, height / 2);
        resize(SWAP4A, width / 4, height / 4);
        resize(SWAP8A, width / 8, height / 8);
        resize(SWAP2B, width / 2, height / 2);
        resize(SWAP4B, width / 4, height / 4);
        resize(SWAP8B, width / 8, height / 8);
    }

    private static void resize(RenderTarget renderTarget, int width, int height) {
        renderTarget.resize(width, height);
        renderTarget.setFilterMode(FilterMode.LINEAR);
    }

    public static void addFixedBuffer(RenderType type) {
        FIXED_BUFFERS.put(type, new ByteBufferBuilder(type.bufferSize()));
    }

    private static RenderTarget getRenderTarget(int width, int height) {
        var renderTarget = new TextureTarget(null, width, height, false);
        renderTarget.setFilterMode(FilterMode.LINEAR);
        return renderTarget;
    }

    public static RenderTarget getInput() {
        return INPUT;
    }

    private static void writeBlurUniforms(Vector2f outSize, float dirX, float dirY, int radius) {
        try (var memoryStack = org.lwjgl.system.MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(memoryStack, BlurUniforms.UBO_SIZE);
            new BlurUniforms(outSize, new Vector2f(dirX, dirY), radius).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(blurUniformsBuffer.slice(), byteBuffer);
        }
    }

    private static void writeBloomUniforms(float radius, float intensity) {
        try (var memoryStack = org.lwjgl.system.MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(memoryStack, BloomUniforms.UBO_SIZE);
            new BloomUniforms(radius, intensity).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(bloomUniformsBuffer.slice(), byteBuffer);
        }
    }

    private static void runBlitPass(RenderTarget target, com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
                                    Map<String, GpuTextureView> samplers, Map<String, GpuBufferSlice> uniforms, boolean clear) {
        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        var clearColor = clear ? OptionalInt.of(0) : OptionalInt.empty();

        try (var renderPass = commandEncoder.createRenderPass(() -> "Blit Pass to " + target.toString(), target.getColorTextureView(), clearColor)) {
            renderPass.setPipeline(pipeline);
            samplers.forEach(renderPass::bindSampler);
            uniforms.forEach(renderPass::setUniform);

            renderPass.setVertexBuffer(0, fullscreenQuadVertexBuffer);
            var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            renderPass.setIndexBuffer(sequentialBuffer.getBuffer(6), sequentialBuffer.type());
            renderPass.drawIndexed(0, 0, 6, 1);
        }
    }

    public static void process() {
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(INPUT.getColorTexture(), 0);
        BUFFER_SOURCE.endBatch();

        var mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
        var blurUboSlice = blurUniformsBuffer.slice();

        runBlitPass(mainRenderTarget, Render.RenderPipelines.BLIT_SCREEN, Map.of("DiffuseSampler", INPUT.getColorTextureView()), Collections.emptyMap(), false);

        writeBlurUniforms(new Vector2f(SWAP2A.width, SWAP2A.height), 1.0f, 0.0f, 4);
        runBlitPass(SWAP2A, Render.RenderPipelines.GAUSSIAN_BLUR, Map.of("DiffuseSampler", INPUT.getColorTextureView()), Map.of("BlurInfo", blurUboSlice), true);

        writeBlurUniforms(new Vector2f(SWAP2B.width, SWAP2B.height), 0.0f, 1.0f, 4);
        runBlitPass(SWAP2B, Render.RenderPipelines.GAUSSIAN_BLUR, Map.of("DiffuseSampler", SWAP2A.getColorTextureView()), Map.of("BlurInfo", blurUboSlice), true);

        writeBlurUniforms(new Vector2f(SWAP4A.width, SWAP4A.height), 1.0f, 0.0f, 6);
        runBlitPass(SWAP4A, Render.RenderPipelines.GAUSSIAN_BLUR, Map.of("DiffuseSampler", SWAP2B.getColorTextureView()), Map.of("BlurInfo", blurUboSlice), true);

        writeBlurUniforms(new Vector2f(SWAP4B.width, SWAP4B.height), 0.0f, 1.0f, 6);
        runBlitPass(SWAP4B, Render.RenderPipelines.GAUSSIAN_BLUR, Map.of("DiffuseSampler", SWAP4A.getColorTextureView()), Map.of("BlurInfo", blurUboSlice), true);

        writeBlurUniforms(new Vector2f(SWAP8A.width, SWAP8A.height), 1.0f, 0.0f, 8);
        runBlitPass(SWAP8A, Render.RenderPipelines.GAUSSIAN_BLUR, Map.of("DiffuseSampler", SWAP4B.getColorTextureView()), Map.of("BlurInfo", blurUboSlice), true);

        writeBlurUniforms(new Vector2f(SWAP8B.width, SWAP8B.height), 0.0f, 1.0f, 8);
        runBlitPass(SWAP8B, Render.RenderPipelines.GAUSSIAN_BLUR, Map.of("DiffuseSampler", SWAP8A.getColorTextureView()), Map.of("BlurInfo", blurUboSlice), true);

        writeBloomUniforms(1.0f, 1.0f);
        var blendSamplers = Map.of(
                "DiffuseSampler", mainRenderTarget.getColorTextureView(),
                "BlurTexture1", SWAP2B.getColorTextureView(),
                "BlurTexture2", SWAP4B.getColorTextureView(),
                "BlurTexture3", SWAP8B.getColorTextureView()
        );
        runBlitPass(mainRenderTarget, Render.RenderPipelines.BLOOM_BLEND, blendSamplers, Map.of("BloomInfo", bloomUniformsBuffer.slice()), false);
    }

    public record BlurUniforms(Vector2f outSize, Vector2f blurDir, int radius) {
        public static final int UBO_SIZE = new Std140SizeCalculator().putVec2().putVec2().putInt().get();

        public void write(Std140Builder builder) {
            builder.putVec2(outSize).putVec2(blurDir).putInt(radius);
        }
    }

    public record BloomUniforms(float radius, float intensity) {
        public static final int UBO_SIZE = new Std140SizeCalculator().putFloat().putFloat().get();

        public void write(Std140Builder builder) {
            builder.putFloat(radius).putFloat(intensity);
        }
    }
}