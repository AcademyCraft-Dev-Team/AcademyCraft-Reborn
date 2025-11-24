package org.academy.api.client.hud.terminal;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import org.academy.api.client.gui.render.UIContext;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import org.jspecify.annotations.Nullable;

public final class Renderer implements AutoCloseable {
    private final Config config;
    private final UIManager uiManager;
    private final UIContext internalUIContext;

    @Nullable
    private RenderTarget uiRenderTarget;
    @Nullable
    private GpuBuffer uiRenderQuadVertexBuffer;
    @Nullable
    private GpuBuffer terminalTransformUbo;
    @Nullable
    private GpuBuffer maskVertexBuffer;
    @Nullable
    private GpuBuffer maskIndexBuffer;
    private int maskIndexCount = 0;

    public Renderer(Config config, UIManager uiManager) {
        this.config = config;
        this.uiManager = uiManager;
        internalUIContext = new UIContext();

        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        uiRenderTarget = new TextureTarget(null, mainRenderTarget.width, mainRenderTarget.height, true);

        var device = RenderSystem.getDevice();
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        terminalTransformUbo = device.createBuffer(() -> "Terminal Transform UBO", uboUsage, TransformUniforms.UBO_SIZE);

        var window = mc.getWindow();
        createGuiQuadVertexBuffer(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public void render(double mouseX, double mouseY, float partialTick) {
        if (uiRenderTarget == null) return;

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        commandEncoder.clearColorAndDepthTextures(uiRenderTarget.getColorTexture(), 0, uiRenderTarget.getDepthTexture(), 1);

    }

    private void updateTransformUBO(Matrix4f projectionMatrix) {
        try (var memoryStack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(memoryStack, TransformUniforms.UBO_SIZE);
            new TransformUniforms(projectionMatrix).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(terminalTransformUbo.slice(), byteBuffer);
        }
    }

    public void resize(int width, int height) {
        var window = Minecraft.getInstance().getWindow();
        createGuiQuadVertexBuffer(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        if (uiRenderTarget != null) {
            uiRenderTarget.resize(width, height);
        }
    }

    private void createGuiQuadVertexBuffer(float width, float height) {
        if (uiRenderQuadVertexBuffer != null) {
            uiRenderQuadVertexBuffer.close();
        }

        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 4)) {
            var bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            var white = -1;
            bufferBuilder.addVertex(0.0F, 0.0F, 0.0F).setUv(0.0F, 1.0F).setColor(white);
            bufferBuilder.addVertex(0.0F, height, 0.0F).setUv(0.0F, 0.0F).setColor(white);
            bufferBuilder.addVertex(width, height, 0.0F).setUv(1.0F, 0.0F).setColor(white);
            bufferBuilder.addVertex(width, 0.0F, 0.0F).setUv(1.0F, 1.0F).setColor(white);

            try (var meshData = bufferBuilder.buildOrThrow()) {
                uiRenderQuadVertexBuffer = RenderSystem.getDevice().createBuffer(() -> "DataTerminal GUI Quad", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
            }
        }
    }

    @Override
    public void close() {
        if (uiRenderTarget != null) {
            uiRenderTarget.destroyBuffers();
            uiRenderTarget = null;
        }
        if (uiRenderQuadVertexBuffer != null) {
            uiRenderQuadVertexBuffer.close();
            uiRenderQuadVertexBuffer = null;
        }
        if (terminalTransformUbo != null) {
            terminalTransformUbo.close();
            terminalTransformUbo = null;
        }
        if (maskVertexBuffer != null) {
            maskVertexBuffer.close();
            maskVertexBuffer = null;
        }
        maskIndexBuffer = null;
        internalUIContext.close();
    }

    private static class TransformUniforms {
        public static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().get();
        private final Matrix4f mvp;

        public TransformUniforms(Matrix4f mvp) {
            this.mvp = mvp;
        }

        public void write(Std140Builder builder) {
            builder.putMat4f(mvp);
        }
    }
}