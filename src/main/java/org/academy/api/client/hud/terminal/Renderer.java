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
import org.academy.api.client.Render;
import org.academy.api.client.gui.framework.UIRenderContext;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.gui.widget.CursorWidget;
import org.academy.api.client.render.post.BlurEffect;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class Renderer implements AutoCloseable {
    private final Config config;
    private final UIManager uiManager;
    private final UIRenderContext internalUIRenderContext;

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
        internalUIRenderContext = new UIRenderContext();

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

        internalUIRenderContext.renderFrame(uiManager.getRootContainer(), uiRenderTarget, mouseX, mouseY, partialTick);
        renderUIWith3DEffect(mouseX, mouseY);
    }

    private void renderUIWith3DEffect(double mouseX, double mouseY) {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        var guiWidth = (float) window.getGuiScaledWidth();
        var guiHeight = (float) window.getGuiScaledHeight();
        var aspectRatio = (float) window.getWidth() / (float) window.getHeight();
        float fov = 80;
        var fovY = 2f * (float) Math.atan(Math.tan(Math.toRadians(fov) / 2f) / aspectRatio);

        var projectionMatrix = new Matrix4f().perspective(fovY, aspectRatio, 1.0F, 1000.0F);
        var viewMatrix = new Matrix4f().identity();

        {
            var z = -2.5125F;
            var scale = 2.0F * Math.abs(z) * (float) Math.tan(fovY / 2.0F) / guiHeight;

            viewMatrix.translate(0.0F, 0.0F, z);
            viewMatrix.scale(scale, -scale, scale);
            var centerX = guiWidth / 2.0F;
            var centerY = guiHeight / 2.0F;
            var dx = (float) (mouseX - centerX);
            var dy = (float) (mouseY - centerY);
            viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 1.0F, 0.0F), dx * 0.01F));
            viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(1.0F, 0.0F, 0.0F), -dy * 0.01F));
            viewMatrix.translate(-centerX, -centerY, 0.0F);
        }

        updateTransformUBO(projectionMatrix);

        var dynamicTransformsSlice = RenderSystem.getDynamicUniforms()
                .writeTransform(viewMatrix, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);

        if (config.enableBlur) {
            updateAndUploadMaskGeometry();
            var mainTarget = mc.getMainRenderTarget();
            if (maskIndexCount > 0 && maskVertexBuffer != null && maskIndexBuffer != null) {
                var uniforms = Map.of(
                        "Projection", terminalTransformUbo.slice(),
                        "DynamicTransforms", dynamicTransformsSlice
                );
                BlurEffect.apply(renderPass -> {
                    renderPass.setPipeline(Render.RenderPipelines.MASK_BRUSH);
                    uniforms.forEach(renderPass::setUniform);
                    renderPass.setVertexBuffer(0, maskVertexBuffer);
                    var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                    renderPass.setIndexBuffer(maskIndexBuffer, sequentialBuffer.type());
                    renderPass.drawIndexed(0, 0, maskIndexCount, 1);
                }, mainTarget, mainTarget);
            }
        }

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        var mainRenderTarget = mc.getMainRenderTarget();
        var samplers = Map.of("Sampler0", uiRenderTarget.getColorTextureView());
        var uniforms = Map.of("Projection", terminalTransformUbo.slice());

        try (var renderPass = commandEncoder.createRenderPass(() -> "DataTerminal 3D Composite", mainRenderTarget.getColorTextureView(), OptionalInt.empty(), mainRenderTarget.getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(Render.RenderPipelines.IMAGE);
            samplers.forEach(renderPass::bindSampler);
            uniforms.forEach(renderPass::setUniform);
            renderPass.setUniform("DynamicTransforms", dynamicTransformsSlice);
            renderPass.setVertexBuffer(0, uiRenderQuadVertexBuffer);
            var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            renderPass.setIndexBuffer(sequentialBuffer.getBuffer(6), sequentialBuffer.type());
            renderPass.drawIndexed(0, 0, 6, 1);
        }

        commandEncoder.clearDepthTexture(mainRenderTarget.getDepthTexture(), 1);
    }

    private void updateAndUploadMaskGeometry() {
        var widgetsToMask = new ArrayList<Widget>();
        for (var widget : uiManager.getRootContainer().getChildren().values()) {
            if (!(widget instanceof CursorWidget)) {
                widgetsToMask.add(widget);
            }
        }

        maskIndexCount = 0;

        var vertexCount = widgetsToMask.size() * 4;
        var vertexSize = DefaultVertexFormat.POSITION_COLOR.getVertexSize();

        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(vertexCount * vertexSize)) {
            var bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            var whiteColor = -1;

            for (var widget : widgetsToMask) {
                var x0 = widget.getAbsoluteX();
                var y0 = widget.getAbsoluteY();
                var x1 = x0 + widget.getWidth();
                var y1 = y0 + widget.getHeight();
                var z = 0.0f;

                bufferBuilder.addVertex(x0, y0, z).setColor(whiteColor);
                bufferBuilder.addVertex(x0, y1, z).setColor(whiteColor);
                bufferBuilder.addVertex(x1, y1, z).setColor(whiteColor);
                bufferBuilder.addVertex(x1, y0, z).setColor(whiteColor);
            }

            try (var meshData = bufferBuilder.buildOrThrow()) {
                var device = RenderSystem.getDevice();
                var vertexData = meshData.vertexBuffer();
                var requiredSize = vertexData.remaining();

                if (maskVertexBuffer == null || maskVertexBuffer.size() < requiredSize) {
                    if (maskVertexBuffer != null) {
                        maskVertexBuffer.close();
                    }
                    var usage = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST;
                    maskVertexBuffer = device.createBuffer(() -> "DataTerminal Mask VB", usage, requiredSize);
                }

                device.createCommandEncoder().writeToBuffer(maskVertexBuffer.slice(0, requiredSize), vertexData);
                maskIndexCount = meshData.drawState().indexCount();
                var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                maskIndexBuffer = sequentialBuffer.getBuffer(maskIndexCount);
            }
        }
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
        internalUIRenderContext.close();
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