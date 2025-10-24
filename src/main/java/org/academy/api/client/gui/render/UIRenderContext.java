package org.academy.api.client.gui.framework.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.academy.api.client.gui.framework.Widget;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class UIRenderContext {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int DEFAULT_BUFFER_CAPACITY = 786432;

    private final ByteBufferBuilder sharedByteBufferBuilder;
    private final Map<VertexFormat, MappableRingBuffer> vertexBuffers;
    private final BatchProcessor batchProcessor;
    private final CommandExecutor commandExecutor;

    private final CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer;
    private final Map<Class<? extends DynamicUniformStorage.DynamicUniform>, DynamicUniformStorage<?>> dynamicUniformStorages;
    private final GpuBuffer dynamicTransformsUbo;

    private ScaleMode scaleMode = ScaleMode.WINDOW;
    private float customScale = 1.0f;

    private boolean closed = false;

    public enum ScaleMode {
        WINDOW,
        CUSTOM
    }
    public UIRenderContext() {
        this(3000);
    }

    public UIRenderContext(float layered) {
        sharedByteBufferBuilder = new ByteBufferBuilder(DEFAULT_BUFFER_CAPACITY);
        vertexBuffers = new HashMap<>();
        batchProcessor = new BatchProcessor(sharedByteBufferBuilder);
        commandExecutor = new CommandExecutor(vertexBuffers);
        projectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer("AC_GUI", -layered, 0.0F, true);
        dynamicUniformStorages = new HashMap<>();
        var device = RenderSystem.getDevice();
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;

        try (var memoryStack = MemoryStack.stackPush()) {
            var size = new Std140SizeCalculator().putMat4f().putVec4().putVec3().putMat4f().putFloat().get();
            var builder = Std140Builder.onStack(memoryStack, size);
            var identityMatrix = new Matrix4f();
            builder.putMat4f(identityMatrix);
            builder.putVec4(1.0f, 1.0f, 1.0f, 1.0f);
            builder.putVec3(0.0f, 0.0f, 0.0f);
            builder.putMat4f(identityMatrix);
            builder.putFloat(1.0f);
            var byteBuffer = builder.get();
            dynamicTransformsUbo = device.createBuffer(() -> "AC_GUI_DynamicTransforms_UBO", uboUsage, byteBuffer);
        }
    }

    public void setScaleMode(ScaleMode mode) {
        scaleMode = mode;
    }

    public void setCustomScale(float scale) {
        if (scale <= 0)
            throw new IllegalArgumentException("Scale must be positive.");
        customScale = scale;
    }

    public float getEffectiveScale() {
        if (scaleMode == ScaleMode.WINDOW)
            return (float) Minecraft.getInstance().getWindow().getGuiScale();
        return customScale;
    }

    private float calculateDepthEpsilon(RenderTarget target) {
        var depthTexture = target.getDepthTexture();
        if (depthTexture == null) {
            return 0;
        }

        var format = depthTexture.getFormat();
        if (!format.hasDepthAspect()) {
            return 0;
        }
        var depthBits = switch (format) {
            case DEPTH32, DEPTH32_STENCIL8 -> 32;
            case DEPTH24_STENCIL8 -> 24;
            default -> 0;
        };

        if (depthBits == 0) {
            return 0;
        }

        return 1f / ((1L << depthBits) - 1);
    }

    public void renderFrame(Widget rootWidget, RenderTarget target, double mouseX, double mouseY, float partialTick) {
        if (isClosed()) {
            LOGGER.warn("UIRenderContext has been closed!");
            return;
        }

        prepareForNewFrame();

        var context = new WidgetRenderContext(this::getOrCreateUbo);
        context.pose().pushPose();
        {
            context.pose().translate(rootWidget.getX(), rootWidget.getY(), rootWidget.getZ());
            context.pose().translate(rootWidget.getTranslationX(), rootWidget.getTranslationY(), 0);

            rootWidget.render(context, mouseX, mouseY, partialTick);
        }
        context.pose().popPose();
        var submittedCommands = context.getCommands();

        if (submittedCommands.isEmpty())
            return;

        var mutableCommands = new ArrayList<>(submittedCommands);
        var depthEpsilon = calculateDepthEpsilon(target);
        var meshesToDraw = batchProcessor.process(mutableCommands, depthEpsilon);

        var effectiveScale = getEffectiveScale();
        var window = Minecraft.getInstance().getWindow();
        var guiScaledWidth = window.getWidth() / effectiveScale;
        var guiScaledHeight = window.getHeight() / effectiveScale;
        var projectionBufferSlice = projectionMatrixBuffer.getBuffer(guiScaledWidth, guiScaledHeight);

        commandExecutor.execute(meshesToDraw, target, projectionBufferSlice, dynamicTransformsUbo, effectiveScale);
    }

    private void prepareForNewFrame() {
        sharedByteBufferBuilder.discard();
        for (var buffer : vertexBuffers.values()) buffer.rotate();
        for (var ubo : dynamicUniformStorages.values()) ubo.endFrame();
    }

    @SuppressWarnings("unchecked")
    private <T extends DynamicUniformStorage.DynamicUniform> DynamicUniformStorage<T> getOrCreateUbo(Class<T> uboClass, int size) {
        return (DynamicUniformStorage<T>) dynamicUniformStorages.computeIfAbsent(
                uboClass,
                k -> new DynamicUniformStorage<>(uboClass.getSimpleName() + "_UBO", size, 2)
        );
    }

    public void close() {
        closed = true;

        sharedByteBufferBuilder.close();
        for (var buffer : vertexBuffers.values()) buffer.close();
        vertexBuffers.clear();

        projectionMatrixBuffer.close();
        for (var ubo : dynamicUniformStorages.values()) ubo.close();
        dynamicUniformStorages.clear();
        dynamicTransformsUbo.close();
    }

    public boolean isClosed() {
        return closed;
    }
}