package org.academy.api.client.gui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.academy.api.client.gui.command.SubmittedCommand;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.widget.WidgetContainer;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.thread.RenderThread;
import org.academy.api.common.util.UncheckedUtil;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 看情况 close 喵, 像 ScreenDispatcher 这种就没必要 close 了喵
 */
public class UIContext {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AtomicReference<List<SubmittedCommand>> commandList = new AtomicReference<>();

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private final Map<VertexFormat, MappableRingBuffer> vertexBuffers = new HashMap<>();
    private final Map<Class<? extends DynamicUniformStorage.DynamicUniform>, DynamicUniformStorage<?>> dynamicUniformStorages = new HashMap<>();
    private final CommandExecutor commandExecutor = new CommandExecutor(vertexBuffers);

    @Nullable
    private CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer;
    @Nullable
    private GpuBuffer dynamicTransformsUbo;

    public UIContext() {
        this(3000);
    }

    public UIContext(float layered) {
        Minecraft.getInstance().execute(() -> initOnRenderThread(layered));
    }

    private void initOnRenderThread(float layered) {
        projectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer("gui", -layered, 0.0F, true);
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
            dynamicTransformsUbo = device.createBuffer(() -> "UI DynamicTransforms UBO", uboUsage, byteBuffer);
        }
    }

    private float calculateDepthEpsilon(GpuTexture depthTexture) {
        var format = depthTexture.getFormat();

        if (!format.hasDepthAspect()) return 0;

        var depthBits = switch (format) {
            case DEPTH32, DEPTH32_STENCIL8 -> 32;
            case DEPTH24_STENCIL8 -> 24;
            default -> 0;
        };

        if (depthBits == 0) return 0;

        return 1f / ((1L << depthBits) - 1);
    }

    @MainThread
    public void perform(WidgetContainer rootWidget, double mouseX, double mouseY, float partialTick) {
        if (closed.get() || closing.get()) return;

        var window = Minecraft.getInstance().getWindow();

        if (rootWidget.isLayoutDirty()) {
            var width = window.getGuiScaledWidth();
            var height = window.getGuiScaledHeight();

            var widthSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, width);
            var heightSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, height);
            rootWidget.measure(widthSpec, heightSpec);
            rootWidget.layout(0, 0, width, height);
        }
        var context = new RenderContext(this::getOrCreateUbo);
        generateCommands(context, rootWidget, mouseX, mouseY, partialTick);
        commandList.set(context.getCommands());
    }

    public void generateCommands(
            RenderContext context, WidgetContainer rootWidget, double mouseX, double mouseY, float partialTick
    ) {
        context.pose().pushPose();
        {
            context.pose().translate(rootWidget.getX(), rootWidget.getY(), rootWidget.getZ());
            context.pose().translate(rootWidget.getTranslationX(), rootWidget.getTranslationY(), 0);

            rootWidget.render(context);
        }
        context.pose().popPose();
    }

    @RenderThread
    public void upload(RenderTarget target, boolean clear, boolean stencilTest) {
        for (var buffer : vertexBuffers.values()) buffer.rotate();
        for (var ubo : dynamicUniformStorages.values()) ubo.endFrame();

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        var colorTexture = target.getColorTexture();
        var depthTextureView = target.getDepthTextureView();
        if (depthTextureView == null) return;
        var depthTexture = depthTextureView.texture();
        var colorTextureView = target.getColorTextureView();

        if (colorTexture == null || colorTextureView == null) return;

        if (clear) commandEncoder.clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1);

        if (projectionMatrixBuffer == null || dynamicTransformsUbo == null) return;

        var commands = commandList.getAndSet(null);

        if (commands == null || commands.isEmpty()) return;

        var depthEpsilon = calculateDepthEpsilon(depthTexture);
        var meshesToDraw = BatchProcessor.process(commands, depthEpsilon);

        var effectiveScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        var window = Minecraft.getInstance().getWindow();
        var guiScaledWidth = window.getWidth() / effectiveScale;
        var guiScaledHeight = window.getHeight() / effectiveScale;
        var projectionBufferSlice = projectionMatrixBuffer.getBuffer(guiScaledWidth, guiScaledHeight);
        commandExecutor.execute(
                meshesToDraw, colorTextureView, depthTextureView,
                projectionBufferSlice, dynamicTransformsUbo, effectiveScale, stencilTest
        );
    }

    private <T extends DynamicUniformStorage.DynamicUniform> DynamicUniformStorage<T> getOrCreateUbo(Class<T> uboClass, int size) {
        return UncheckedUtil.uncheckedCast(dynamicUniformStorages.computeIfAbsent(
                uboClass,
                k -> new DynamicUniformStorage<>(uboClass.getSimpleName() + "_UBO", size, 2)
        ));
    }

    public void close() {
        if (closing.get() || closed.get()) return;
        closing.set(true);
        Minecraft.getInstance().execute(this::closeOnRenderThread);
    }

    public void closeOnRenderThread() {
        if (projectionMatrixBuffer == null || dynamicTransformsUbo == null) return;

        for (var buffer : vertexBuffers.values()) buffer.close();
        vertexBuffers.clear();

        projectionMatrixBuffer.close();
        for (var ubo : dynamicUniformStorages.values()) ubo.close();
        dynamicUniformStorages.clear();
        dynamicTransformsUbo.close();
        closed.set(true);
        closing.set(false);
    }
}