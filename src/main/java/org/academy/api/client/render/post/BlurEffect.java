package org.academy.api.client.render.post;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import org.academy.api.client.Render;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryStack;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;

public final class BlurEffect {
    private static float blurRadius = 20.0F;
    @Nullable
    private static RenderTarget maskInputRenderTarget;
    @Nullable
    private static RenderTarget swapTarget;
    @Nullable
    private static GpuBuffer blurUniformsBuffer;
    @Nullable
    private static GpuBuffer fullscreenQuadVertexBuffer;

    public static void init() {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        var width = mainRenderTarget.width;
        var height = mainRenderTarget.height;
        maskInputRenderTarget = createRenderTarget(width, height);
        swapTarget = createRenderTarget(width, height);

        var device = RenderSystem.getDevice();
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        blurUniformsBuffer = device.createBuffer(() -> "Blur UBO", uboUsage, BlurUniforms.UBO_SIZE);
        createFullscreenQuad();
    }

    public static void resize(int width, int height) {
        if (maskInputRenderTarget != null)
            resizeRenderTarget(maskInputRenderTarget, width, height);
        if (swapTarget != null)
            resizeRenderTarget(swapTarget, width, height);
    }

    public static void close() {
        if (maskInputRenderTarget != null)
            maskInputRenderTarget.destroyBuffers();
        if (swapTarget != null)
            swapTarget.destroyBuffers();
        if (blurUniformsBuffer != null)
            blurUniformsBuffer.close();
        if (fullscreenQuadVertexBuffer != null)
            fullscreenQuadVertexBuffer.close();
    }

    public static void apply(Consumer<RenderPass> maskDrawer, RenderTarget samplerTarget, RenderTarget outputTarget) {
        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        {
            try (
                    var renderPass = commandEncoder.createRenderPass(
                            () -> "Blur Mask Pass",
                            maskInputRenderTarget.getColorTextureView(),
                            OptionalInt.of(0)
                    )
            ) {
                maskDrawer.accept(renderPass);
            }
        }

        var blurUboSlice = blurUniformsBuffer.slice();

        {
            writeBlurUniforms(new Vector2f((float) swapTarget.width, (float) swapTarget.height), 1.0F, 0.0F);
            var samplers = Map.of("DiffuseSampler", samplerTarget.getColorTextureView(), "MaskSampler", maskInputRenderTarget.getColorTextureView());
            var uniforms = Map.of("BlurInfo", blurUboSlice);
            runBlitPass(swapTarget, Render.RenderPipelines.MASKED_BLUR_SHADER, samplers, uniforms, true);
        }

        {
            writeBlurUniforms(new Vector2f((float) samplerTarget.width, (float) samplerTarget.height), 0.0F, 1.0F);
            var samplers = Map.of("DiffuseSampler", swapTarget.getColorTextureView(), "MaskSampler", maskInputRenderTarget.getColorTextureView());
            var uniforms = Map.of("BlurInfo", blurUboSlice);
            runBlitPass(outputTarget, Render.RenderPipelines.MASKED_BLUR_SHADER, samplers, uniforms, false);
        }
    }

    private static void createFullscreenQuad() {
        if (fullscreenQuadVertexBuffer != null) {
            fullscreenQuadVertexBuffer.close();
        }

        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4)) {
            var bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferBuilder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
            bufferBuilder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
            bufferBuilder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
            bufferBuilder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);

            try (var meshData = bufferBuilder.buildOrThrow()) {
                fullscreenQuadVertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Blur Fullscreen Quad", 32, meshData.vertexBuffer());
            }
        }
    }

    private static void resizeRenderTarget(RenderTarget renderTarget, int width, int height) {
        renderTarget.resize(width, height);
        renderTarget.setFilterMode(FilterMode.LINEAR);
    }

    private static RenderTarget createRenderTarget(int width, int height) {
        var renderTarget = new TextureTarget(null, width, height, false);
        renderTarget.setFilterMode(FilterMode.LINEAR);
        return renderTarget;
    }

    private static void writeBlurUniforms(Vector2f outSize, float dirX, float dirY) {
        try (var memoryStack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(memoryStack, BlurUniforms.UBO_SIZE);
            new BlurUniforms(outSize, new Vector2f(dirX, dirY), blurRadius).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(blurUniformsBuffer.slice(), byteBuffer);
        }
    }

    private static void runBlitPass(RenderTarget target, RenderPipeline pipeline, Map<String, GpuTextureView> samplers, Map<String, GpuBufferSlice> uniforms, boolean clearColor) {
        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        if (clearColor) {
            commandEncoder.clearColorTexture(target.getColorTexture(), 0);
        }

        try (
                var renderPass = commandEncoder.createRenderPass(
                        () -> "Blit Pass to " + target, target.getColorTextureView(), OptionalInt.empty()
                )
        ) {
            renderPass.setPipeline(pipeline);
            samplers.forEach(renderPass::bindSampler);
            uniforms.forEach(renderPass::setUniform);
            renderPass.setVertexBuffer(0, fullscreenQuadVertexBuffer);
            var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            renderPass.setIndexBuffer(sequentialBuffer.getBuffer(6), sequentialBuffer.type());
            renderPass.drawIndexed(0, 0, 6, 1);
        }
    }

    public static float getBlurRadius() {
        return blurRadius;
    }

    public static void setBlurRadius(float blurRadius) {
        BlurEffect.blurRadius = blurRadius;
    }

    private BlurEffect() {
    }

    public static class BlurUniforms {
        public static final int UBO_SIZE = new Std140SizeCalculator().putVec2().putVec2().putFloat().get();

        private Vector2f outSize;
        private Vector2f blurDir;
        private float radius;

        public BlurUniforms(Vector2f outSize, Vector2f blurDir, float radius) {
            this.outSize = outSize;
            this.blurDir = blurDir;
            this.radius = radius;
        }

        public void write(Std140Builder builder) {
            builder.putVec2(this.outSize).putVec2(this.blurDir).putFloat(this.radius);
        }

        public Vector2f getOutSize() {
            return this.outSize;
        }

        public void setOutSize(Vector2f outSize) {
            this.outSize = outSize;
        }

        public Vector2f getBlurDir() {
            return this.blurDir;
        }

        public void setBlurDir(Vector2f blurDir) {
            this.blurDir = blurDir;
        }

        public float getRadius() {
            return this.radius;
        }

        public void setRadius(float radius) {
            this.radius = radius;
        }
    }
}