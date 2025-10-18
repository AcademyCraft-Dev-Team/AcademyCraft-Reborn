package org.academy.api.client.render.post;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.Render;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;
import java.util.SequencedMap;

public final class PostEffect {
    public static final RenderTarget MAIN_SCENE;
    public static final ByteBufferBuilder BYTE_BUFFER_BUILDER = new ByteBufferBuilder(786432);
    public static final SequencedMap<RenderType, ByteBufferBuilder> FIXED_BUFFERS = new Object2ObjectLinkedOpenHashMap<>();
    public static final MultiBufferSource.BufferSource BUFFER_SOURCE_PRE = MultiBufferSource.immediateWithBuffers(FIXED_BUFFERS, BYTE_BUFFER_BUILDER);
    public static final MultiBufferSource.BufferSource BUFFER_SOURCE_POST = MultiBufferSource.immediateWithBuffers(FIXED_BUFFERS, BYTE_BUFFER_BUILDER);
    private static final GpuBuffer fullscreenQuadVertexBuffer;

    static {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        var width = mainRenderTarget.width;
        var height = mainRenderTarget.height;
        MAIN_SCENE = new TextureTarget(null, width, height, true);
        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4)) {
            var bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferBuilder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
            bufferBuilder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
            bufferBuilder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
            bufferBuilder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);

            try (var meshData = bufferBuilder.buildOrThrow()) {
                fullscreenQuadVertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Fullscreen Quad", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
            }
        }
    }

    public static void close() {
        BYTE_BUFFER_BUILDER.close();
        fullscreenQuadVertexBuffer.close();
    }

    public static void addFixedBuffer(RenderType type) {
        FIXED_BUFFERS.put(type, new ByteBufferBuilder(type.bufferSize()));
    }

    public static void resize(int width, int height) {
        MAIN_SCENE.resize(width, height);
    }

    public static void pre() {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(MAIN_SCENE.getColorTexture(), 0, MAIN_SCENE.getDepthTexture(), 1);
        runBlitPass(MAIN_SCENE, Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND, Map.of("DiffuseSampler", mainRenderTarget.getColorTextureView()), Collections.emptyMap(), false);
        BUFFER_SOURCE_PRE.endBatch();
    }

    public static void post() {
        BUFFER_SOURCE_POST.endBatch();
    }

    public static void runBlitPass(RenderTarget target, RenderPipeline pipeline,
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

    /**
     * 我也不知道怎么命名好喵
     * <br>
     * 用途为创建一个 BufferSource 用于后处理的某个阶段喵
     * <br>
     * 可以参考 BloomEffect 喵
     * <br>
     * 共享 BYTE_BUFFER_BUILDER 喵
     */
    public static MultiBufferSource.BufferSource createPostEffectPassBuffer(SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers) {
        return MultiBufferSource.immediateWithBuffers(fixedBuffers, BYTE_BUFFER_BUILDER);
    }

    private PostEffect() {
    }
}