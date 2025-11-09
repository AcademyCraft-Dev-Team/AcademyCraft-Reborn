package org.academy.internal.client.hud;

import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.Render;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.UIRenderContext;
import org.academy.api.client.gui.widget.FillWidget;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.KeyInputEvent;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(Dist.CLIENT)
public final class DataTerminalHUD {
    public static final String CONFIG_KEY_DATA_TERMINAL = "data_terminal_hud_config";
    public static final String KEY_NAME_TOGGLE = "data_terminal_hud_config_toggle";
    @Nullable
    private static DataTerminalConfig config;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FrameLayoutWidget ROOT = new FrameLayoutWidget();
    @Nullable
    private static UIRenderContext uiRenderContext;
    /**
     * Main 和 Render 线程都需要用喵
     */
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    @MainThread
    public static void toggleActive() {
        // 保险喵?
        boolean prev;
        do {
            prev = ACTIVE.get();
        } while (!ACTIVE.compareAndSet(prev, !prev));

        var mc = Minecraft.getInstance();
        var mh = mc.mouseHandler;
        if (isActive()) {
            ClientUtil.playDownSound();
            var w = mc.getWindow();
            init(w.getGuiScaledWidth(), w.getGuiScaledHeight());
            mh.releaseMouse();
        } else {
            mh.grabMouse();
        }
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static void initMain() {
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY_DATA_TERMINAL, DataTerminalConfig.Action.INSTANCE);
        config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY_DATA_TERMINAL);

        var toggleKeys = new LinkedHashSet<Integer>();
        toggleKeys.add(GLFW.GLFW_KEY_RIGHT_ALT);
        var defaultKey = new InputSystem.InputPair(
                InputSystem.InputType.KEYBOARD,
                new InputSystem.KeyInfo(
                        toggleKeys, 0, new LinkedHashSet<>()
                )
        );
        InputSystem.addKeyBinding(
                KEY_NAME_TOGGLE,
                config.getKeyBinding(KEY_NAME_TOGGLE, defaultKey),
                DataTerminalHUD::toggleActive
        );
    }

    public static void initRender() {
        uiRenderContext = new UIRenderContext();
    }

    private static void init(int width, int height) {
        ROOT.setName("root");
        ROOT.clearChildren();

        var dockBar = new FrameLayoutWidget();
        dockBar.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER_BOTTOM)
                        .margin(0, 0, 0, 32)
                        .sizeMode(SizeMode.WRAP_CONTENT)
        );
        {
            var back = new FillWidget(1073741824);
            back.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .size(100, 24)
            );
            dockBar.addChild("back", back);
        }

        ROOT.addChild("dock_bar", dockBar);
    }

    public static void resize(int width, int height) {
        init(width, height);
    }

    @MainThread
    public static void perform(double mouseX, double mouseY, float deltaPartialTick) {
        if (uiRenderContext != null) {
            uiRenderContext.perform(ROOT, mouseX, mouseY, deltaPartialTick);
        } else {
            hasNotBeenInitialized();
        }
    }

    public static void render(
            int width, int height,
            GpuTextureView color,
            GpuTextureView depth,
            AtomicBoolean drew
    ) {
        if (!isActive() || uiRenderContext == null) return;

        var desc = new RenderTargetDescriptor(
                width, height,
                true, 0
        );
        var terminalTarget = Render.Buffers.getResourcePool().acquire(desc);

        try {
            uiRenderContext.upload(terminalTarget, false, false);

            var terminalView = terminalTarget.getColorTextureView();
            if (terminalView == null) return;

            var mc = Minecraft.getInstance();
            var window = mc.getWindow();
            var mouseHandler = mc.mouseHandler;

            var aspectRatio = (float) window.getWidth() / window.getHeight();
            var fovY = (float) MathUtil.calculateVerticalFov(80.0, aspectRatio);

            var viewMatrix = calculateViewMatrix(window, mouseHandler, fovY);
            var dynamicTransformsSlice = createDynamicTransformsSlice(viewMatrix);
            var projectionUBSlice = createProjectionUboSlice(fovY, aspectRatio);

            var samplers = Map.of("Sampler0", terminalView);
            var uniforms = Map.of(
                    "Projection", projectionUBSlice,
                    "DynamicTransforms", dynamicTransformsSlice
            );

            var commandEncoder = RenderSystem.getDevice().createCommandEncoder();

            try (
                    var renderPass = commandEncoder.createRenderPass(
                            () -> "Blit Pass to " + color + depth,
                            color, OptionalInt.empty(), depth, OptionalDouble.empty()
                    )
            ) {
                renderPass.setPipeline(Render.RenderPipelines.IMAGE_NO_DEPTH_STENCIL);
                samplers.forEach(renderPass::bindSampler);
                uniforms.forEach(renderPass::setUniform);

                renderPass.setVertexBuffer(0, Render.Buffers.getInstance().getFullScreenQuadColorVBSDC());
                var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                renderPass.setIndexBuffer(sequentialBuffer.getBuffer(6), sequentialBuffer.type());
                renderPass.drawIndexed(0, 0, 6, 1);
            }
            drew.set(true);
        } finally {
            Render.Buffers.getResourcePool().release(desc, terminalTarget);
        }
    }

    private static Matrix4f calculateViewMatrix(Window window, MouseHandler mouseHandler, float fovY) {
        var guiWidth = window.getGuiScaledWidth();
        var guiHeight = window.getGuiScaledHeight();
        var viewMatrix = new Matrix4f().identity();

        var z = -2.5125F;
        var scale = (float) (2 * Math.abs(z) * Math.tan(fovY / 2) / guiHeight);

        viewMatrix.translate(0.0F, 0.0F, z);
        viewMatrix.scale(scale, scale, scale);

        var centerX = guiWidth / 2.0F;
        var centerY = guiHeight / 2.0F;
        var dx = (float) (mouseHandler.xpos() - centerX);
        var dy = (float) (mouseHandler.ypos() - centerY);

        viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 1.0F, 0.0F), dx * 0.01F));
        viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(1.0F, 0.0F, 0.0F), -dy * 0.01F));
        viewMatrix.translate(-centerX, -centerY, 0.0F);

        return viewMatrix;
    }

    private static GpuBufferSlice createDynamicTransformsSlice(Matrix4f viewMatrix) {
        return RenderSystem.getDynamicUniforms()
                .writeTransform(
                        viewMatrix,
                        new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                        new Vector3f(),
                        new Matrix4f(),
                        0.0F
                );
    }

    private static GpuBufferSlice createProjectionUboSlice(float fovY, float aspectRatio) {
        var projectionMatrix = new Matrix4f().perspective(fovY, aspectRatio, 1.0F, 1000.0F);
        return Render.Buffers.getInstance().getProjectionUB(projectionMatrix).slice();
    }

    @SubscribeEvent
    public static void onKey(KeyInputEvent event) {
        if (isActive() && Minecraft.getInstance().screen == null) {
            var options = Minecraft.getInstance().options;
            var key = event.key;
            var scanCode = event.scanCode;
            var mcKeyEvent = new net.minecraft.client.input.KeyEvent(key, scanCode, event.modifiers);
            var isMovementKey
                    = options.keyUp.matches(mcKeyEvent)
                    || options.keyDown.matches(mcKeyEvent)
                    || options.keyLeft.matches(mcKeyEvent)
                    || options.keyRight.matches(mcKeyEvent)
                    || options.keyJump.matches(mcKeyEvent)
                    || options.keyShift.matches(mcKeyEvent)
                    || options.keySprint.matches(mcKeyEvent);
            var isHotbarKey = false;

            for (var hotbarKey : options.keyHotbarSlots) {
                if (hotbarKey.matches(mcKeyEvent)) {
                    isHotbarKey = true;
                    break;
                }
            }

            if (!isMovementKey && !isHotbarKey) {
                var keyEvent
                        = event.action == 0
                        ? new KeyEvent(EventType.KEY_RELEASED, event.key, event.scanCode, event.modifiers)
                        : new KeyEvent(EventType.KEY_PRESSED, event.key, event.scanCode, event.modifiers);
                ROOT.dispatchEvent(keyEvent);
                if (event.action == 0 && !keyEvent.isConsumed()) toggleActive();
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenChange(ScreenEvent.Opening event) {
        if (isActive()) {
            toggleActive();
        }
    }

    private static void hasNotBeenInitialized() {
        LOGGER.warn("DataTerminalHUD has not been initialized.");
    }

    public static class DataTerminalConfig extends KeyBindingConfig {
        @SerializedName("layout")
        public DataTerminalConfig.LayoutConfig layout = new DataTerminalConfig.LayoutConfig();
        @SerializedName("blurRadius")
        public float blurRadius = 10.0F;
        @SerializedName("enableBlur")
        public boolean enableBlur = true;
        @SerializedName("mouseSensitivity")
        public float mouseSensitivity = 1.0F;

        public static class LayoutConfig {
            @SerializedName("scale")
            public float scale = 0.9F;
            @SerializedName("cursorWidgetSize")
            public float cursorWidgetSize = 4.0F;
        }

        public static final class Action implements TypeHandler<DataTerminalConfig> {
            public static final TypeHandler<DataTerminalConfig> INSTANCE = new DataTerminalConfig.Action();

            private Action() {
            }

            @Override
            public DataTerminalConfig getDefault() {
                return new DataTerminalConfig();
            }

            @Override
            public Class<DataTerminalConfig> getTypeClass() {
                return DataTerminalConfig.class;
            }
        }
    }

    private DataTerminalHUD() {
    }
}