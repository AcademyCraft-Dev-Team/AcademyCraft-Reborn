package org.academy.api.client.gui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.academy.api.client.gui.command.SubmittedCommand;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.widget.WidgetContainer;
import org.academy.api.client.render.UniformPayload;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.thread.RenderThread;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.UncheckedUtil;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class UiContext {
    private final AtomicReference<@Nullable List<SubmittedCommand>> commandList = new AtomicReference<>();

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private final Map<Class<? extends DynamicUniformStorage.DynamicUniform>, DynamicUniformStorage<?>> dynamicUniformStorages = new HashMap<>();
    private final CommandExecutor commandExecutor = new CommandExecutor();

    @Nullable
    private CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer;
    @Nullable
    private GpuBuffer dynamicTransformsUbo;

    private final float layered;

    public UiContext() {
        this(3000);
    }

    public UiContext(float layered) {
        this.layered = layered;
        ClientUtil.getRenderEventLoop().execute(this::initOnRenderThread);
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
        var context = new RenderContext();
        generateCommands(context, rootWidget, mouseX, mouseY, partialTick);
        commandList.set(context.getCommands());
    }

    @RenderThread
    public void upload(RenderTarget target, boolean clear) {
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

        var depthEpsilon = calculateDepthEpsilon(depthTexture) * layered;

        var meshesToDraw = BatchProcessor.process(commands, depthEpsilon, this::uploadPayload);

        var effectiveScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        var window = Minecraft.getInstance().getWindow();
        var guiScaledWidth = window.getWidth() / effectiveScale;
        var guiScaledHeight = window.getHeight() / effectiveScale;
        var projectionBufferSlice = projectionMatrixBuffer.getBuffer(guiScaledWidth, guiScaledHeight);
        commandExecutor.execute(
                meshesToDraw, colorTextureView, depthTextureView,
                projectionBufferSlice, dynamicTransformsUbo, effectiveScale
        );
    }

    private <T extends DynamicUniformStorage.DynamicUniform> GpuBufferSlice uploadPayload(UniformPayload<T> payload) {
        return getOrCreateUbo(payload.type(), payload.size()).writeUniform(payload.data());
    }

    @MainThread
    private <T extends DynamicUniformStorage.DynamicUniform> DynamicUniformStorage<T> getOrCreateUbo(Class<T> uboClass, int size) {
        return UncheckedUtil.uncheckedCast(dynamicUniformStorages.computeIfAbsent(
                uboClass,
                _ -> new DynamicUniformStorage<>(uboClass.getSimpleName() + "_UBO", size, 2)
        ));
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

    @RenderThread
    private void initOnRenderThread() {
        /*
         * Map: z [0, layered] -> NDC [1, -1] :: Depth [1, 0]
         * Eq : ndc = z * 2 / (zNear - zFar) + (zNear + zFar) / (zNear - zFar)
         * z=0 => ndc= 1 => (zNear + zFar) / (zNear - zFar) = 1 => zFar = 0
         * z=layered => ndc=-1 => layered * 2 / zNear + 1 = -1 => zNear = -layered
         */
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

    public void close() {
        if (closing.get() || closed.get()) return;
        closing.set(true);
        ClientUtil.getRenderEventLoop().execute(this::closeOnRenderThread);
    }

    public void closeOnRenderThread() {
        if (projectionMatrixBuffer != null) projectionMatrixBuffer.close();
        if (dynamicTransformsUbo != null) dynamicTransformsUbo.close();

        commandExecutor.close();
        for (var ubo : dynamicUniformStorages.values()) ubo.close();
        dynamicUniformStorages.clear();
        closed.set(true);
        closing.set(false);
    }
}