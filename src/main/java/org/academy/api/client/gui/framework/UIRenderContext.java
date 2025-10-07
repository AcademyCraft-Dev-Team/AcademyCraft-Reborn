package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class UIRenderContext {
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

    public enum ScaleMode {
        WINDOW,
        CUSTOM
    }

    public UIRenderContext() {
        this.sharedByteBufferBuilder = new ByteBufferBuilder(DEFAULT_BUFFER_CAPACITY);
        this.vertexBuffers = new HashMap<>();
        this.batchProcessor = new BatchProcessor(this.sharedByteBufferBuilder);
        this.commandExecutor = new CommandExecutor(this.vertexBuffers);
        this.projectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer("AC_GUI", -3000.0F, 0.0F, true);
        this.dynamicUniformStorages = new HashMap<>();
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
            this.dynamicTransformsUbo = device.createBuffer(() -> "AC_GUI_DynamicTransforms_UBO", uboUsage, byteBuffer);
        }
    }

    public void setScaleMode(ScaleMode mode) {
        this.scaleMode = mode;
    }

    public void setCustomScale(float scale) {
        if (scale <= 0)
            throw new IllegalArgumentException("Scale must be positive.");
        this.customScale = scale;
    }

    public float getEffectiveScale() {
        if (this.scaleMode == ScaleMode.WINDOW)
            return (float) Minecraft.getInstance().getWindow().getGuiScale();
        return this.customScale;
    }

    public void renderFrame(Widget rootWidget, RenderTarget target, double mouseX, double mouseY, float partialTick) {
        this.prepareForNewFrame();

        var context = new WidgetRenderContext(this::getOrCreateUbo);
        rootWidget.render(context, mouseX, mouseY, partialTick);
        var submittedCommands = context.getCommands();

        if (submittedCommands.isEmpty())
            return;

        var mutableCommands = new ArrayList<>(submittedCommands);
        var meshesToDraw = this.batchProcessor.process(mutableCommands);

        var effectiveScale = this.getEffectiveScale();
        var window = Minecraft.getInstance().getWindow();
        var guiScaledWidth = window.getWidth() / effectiveScale;
        var guiScaledHeight = window.getHeight() / effectiveScale;
        var projectionBufferSlice = this.projectionMatrixBuffer.getBuffer(guiScaledWidth, guiScaledHeight);

        this.commandExecutor.execute(meshesToDraw, target, projectionBufferSlice, this.dynamicTransformsUbo, effectiveScale);
    }

    private void prepareForNewFrame() {
        this.sharedByteBufferBuilder.discard();
        for (var buffer : this.vertexBuffers.values())
            buffer.rotate();
        for (var ubo : this.dynamicUniformStorages.values())
            ubo.endFrame();
    }

    @SuppressWarnings("unchecked")
    private <T extends DynamicUniformStorage.DynamicUniform> DynamicUniformStorage<T> getOrCreateUbo(Class<T> uboClass, int size) {
        return (DynamicUniformStorage<T>) this.dynamicUniformStorages.computeIfAbsent(
                uboClass,
                k -> new DynamicUniformStorage<>(uboClass.getSimpleName() + "_UBO", size, 2)
        );
    }

    public void close() {
        this.sharedByteBufferBuilder.close();
        for (var buffer : this.vertexBuffers.values())
            buffer.close();
        this.vertexBuffers.clear();

        this.projectionMatrixBuffer.close();
        for (var ubo : this.dynamicUniformStorages.values())
            ubo.close();
        this.dynamicUniformStorages.clear();
        this.dynamicTransformsUbo.close();
    }
}