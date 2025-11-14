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
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.Render;
import org.academy.api.client.Resource;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.gui.command.PosTexRectDrawCommand;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.ScrollEvent;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.UIContext;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.input.*;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.hud.apps.SettingsApp;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(Dist.CLIENT)
public final class DataTerminalHUD {
    public static final int COLOR = 0x40000000;
    public static final String CONFIG_KEY_DATA_TERMINAL = "data_terminal_hud_config";
    public static final String KEY_NAME_TOGGLE = "data_terminal_hud_config_toggle";
    @Nullable
    private static DataTerminalConfig config;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FrameLayoutWidget ROOT = new FrameLayoutWidget();
    @Nullable
    private static UIContext uiContext;
    /**
     * Main 和 Render 线程都需要用喵
     */
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static volatile double xpos, ypos;
    private static double startXpos, startYpos;

    @MainThread
    public static void toggleActive() {
        // 保险喵?
        boolean prev;
        do {
            prev = ACTIVE.get();
        } while (!ACTIVE.compareAndSet(prev, !prev));

        var mc = Minecraft.getInstance();
        var w = mc.getWindow();
        if (isActive()) {
            ClientUtil.playDownSound();
            var m = mc.mouseHandler;
            var width = w.getGuiScaledWidth();
            var height = w.getGuiScaledHeight();
            xpos = width / 2.0;
            ypos = height / 2.0;
            GLFW.glfwSetCursorPos(w.handle(), w.getWidth() / 2.0, w.getHeight() / 2.0);
            startXpos = m.xpos;
            startYpos = m.ypos;
            init();
        } else {
            GLFW.glfwSetCursorPos(w.handle(), startXpos, startYpos);
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
        uiContext = new UIContext() {
            @Override
            public void generateCommands(RenderContext context, WidgetContainer rootWidget, double mouseX, double mouseY, float partialTick) {
                super.generateCommands(context, rootWidget, mouseX, mouseY, partialTick);
                var size = 4;

                var renderX = xpos - size / 2f;
                var renderY = ypos - size / 2f;

                context.pose().pushPose();
                context.pose().translate(renderX, renderY, 0);

                submitGlowCommand(context, new Vector4f(0.0f, 0.0f, 0.0f, 0.8f), size);
                context.pose().translate(0, 0, 0.1f);
                submitGlowCommand(context, new Vector4f(1.0f, 1.0f, 1.0f, 0.8f), size);

                context.pose().popPose();
            }

            private void submitGlowCommand(RenderContext context, Vector4f color, float size) {
                var sdfData = new CursorWidget.SDFData(color, 0.25f, 0.75f);
                var glowCommand = new PosTexRectDrawCommand(
                        Render.RenderPipelines.SDF_CIRCLE_GLOW,
                        size,
                        size,
                        0,
                        0,
                        1,
                        1
                ) {
                    @Override
                    public Map<String, GpuTextureView> getSamplers() {
                        return Collections.emptyMap();
                    }

                    @Override
                    public Map<String, GpuBufferSlice> getUniforms() {
                        var uboStorage = context.getDynamicUbo(CursorWidget.SDFData.class, CursorWidget.SDFData.UBO_SIZE);
                        var uboSlice = uboStorage.writeUniform(sdfData);
                        return Map.of("GlowUniforms", uboSlice);
                    }
                };
                context.submit(glowCommand);
            }
        };
    }

    private static void init() {
        ROOT.setName("root");
        ROOT.clearChildren();

        var infoBar = new FrameLayoutWidget();
        infoBar.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .gravity(Gravity.TOP_RIGHT)
                        .margin(0, 12, 12, 0)
                        .sizeMode(SizeMode.WRAP_CONTENT)
        );
        ROOT.addChild("info_bar", infoBar);
        {
            var back = new FillWidget(COLOR);
            back.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
            );
            infoBar.addChild("back", back);

            var info = new LinearLayoutWidget();
            info.setSpacing(5);
            info.setOrientation(Orientation.HORIZONTAL);
            info.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.WRAP_CONTENT)
            );
            infoBar.addChild("info", info);
            {
                var icon = new ImageWidget(Resource.Textures.ICON_DATA_TERMINAL);
                icon.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .gravity(Gravity.CENTER_VERTICAL)
                                .size(16, 16)
                                .margin(4, 2)
                );
                info.addChild("icon", icon);

                var player = Minecraft.getInstance().player;
                var playerName = player == null ? "None" : player.getPlainTextName();
                var name = new LabelWidget(playerName);
                name.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .gravity(Gravity.CENTER_VERTICAL)
                                .margin(4, 2)
                );
                info.addChild("name", name);
            }
        }
        var dockBar = new FrameLayoutWidget();
        dockBar.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER_BOTTOM)
                        .margin(0, 0, 0, 24)
                        .widthMode(SizeMode.WRAP_CONTENT)
                        .height(24)
        );
        ROOT.addChild("dock_bar", dockBar);
        {
            var back = new FillWidget(COLOR);
            back.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
            );
            dockBar.addChild("back", back);

            var appArea = new LinearLayoutWidget();
            appArea.setOrientation(Orientation.HORIZONTAL);
            appArea.setSpacing(4);
            appArea.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
            );
            dockBar.addChild("app_area", appArea);
            {
                var settings = new FrameLayoutWidget();
                settings.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                );
                appArea.addChild("settings", settings);
                {
                    var icon = new ImageWidget(Resource.Textures.ICON_SETTINGS);
                    icon.setLayoutParams(
                            new FrameLayoutWidget.LayoutParams()
                                    .size(16, 16)
                                    .gravity(Gravity.CENTER)
                                    .margin(0, 0, 0, 4)
                    );
                    settings.addChild("icon", icon);

                    var name = new LabelWidget("Settings");
                    name.setLayoutParams(
                            new FrameLayoutWidget.LayoutParams()
                                    .gravity(Gravity.CENTER_BOTTOM)
                                    .width(16)
                                    .margin(2, 0, 2, 2)
                    );
                    settings.addChild("name", name);
                }
            }
        }

        var window = SettingsApp.create();
        window.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .size(280, 160)
                        .margin(0, 0, 0, 24)
        );
        ROOT.addChild("window", window);
    }

    public static void resize() {
        init();
    }

    @MainThread
    public static void perform(double mouseX, double mouseY, float deltaPartialTick) {
        if (uiContext != null) {
            uiContext.perform(ROOT, mouseX, mouseY, deltaPartialTick);
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
        if (!isActive() || uiContext == null) return;

        var desc = new RenderTargetDescriptor(
                width, height,
                true, 0
        );
        var terminalTarget = Render.Buffers.getResourcePool().acquire(desc);

        try {
            uiContext.upload(terminalTarget, false, false);

            var terminalView = terminalTarget.getColorTextureView();
            if (terminalView == null) return;

            var mc = Minecraft.getInstance();
            var window = mc.getWindow();

            var aspectRatio = (float) window.getWidth() / window.getHeight();
            var fovY = (float) MathUtil.calculateVerticalFov(80.0, aspectRatio);

            var viewMatrix = calculateViewMatrix(window, fovY);
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
                renderPass.setPipeline(Render.RenderPipelines.IMAGE_STENCIL);
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

    private static Matrix4f calculateViewMatrix(Window window, float fovY) {
        var guiWidth = window.getGuiScaledWidth();
        var guiHeight = window.getGuiScaledHeight();
        var viewMatrix = new Matrix4f().identity();

        var z = -2.5125F;
        var scale = (float) (2 * Math.abs(z) * Math.tan(fovY / 2) / guiHeight);

        viewMatrix.translate(0.0F, 0.0F, z);
        viewMatrix.scale(scale, scale, scale);

        var dx = (float) (xpos - guiWidth / 2.0F);
        var dy = (float) (ypos - guiHeight / 2.0F);

        viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 1.0F, 0.0F), dx * 0.01F));
        viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(1.0F, 0.0F, 0.0F), -dy * 0.01F));
        viewMatrix.translate(-(guiWidth / 2.0F), -(guiHeight / 2.0F), 0.0F);

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
    public static void onMouseMove(MouseMoveEvent event) {
        if (isActive() && Minecraft.getInstance().screen == null) {
            var guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            var deltaGuiX = event.xpos / guiScale;
            var deltaGuiY = event.ypos / guiScale;
            var window = Minecraft.getInstance().getWindow();
            xpos = Mth.clamp(deltaGuiX, 0.0, window.getGuiScaledWidth());
            ypos = Mth.clamp(deltaGuiY, 0.0, window.getGuiScaledHeight());
            ROOT.dispatchEvent(MouseEvent.createMoveEvent(xpos, ypos));
            if (InputSystem.currentMouseAction == 1 || InputSystem.currentMouseAction == 2) {
                ROOT.dispatchEvent(MouseEvent.createDragEvent(xpos, ypos, InputSystem.currentMouseButton, deltaGuiX, deltaGuiY));
            }

            GLFW.glfwSetCursorPos(
                    Minecraft.getInstance().getWindow().handle(),
                    Mth.clamp(event.xpos, 0.0, window.getWidth()),
                    Mth.clamp(event.ypos, 0.0, window.getHeight())
            );

            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseButton(MouseButtonEvent event) {
        if (isActive() && Minecraft.getInstance().screen == null) {
            InputSystem.currentMouseButton = event.button;
            InputSystem.currentMouseAction = event.action;
            InputSystem.currentMouseModifier = event.modifiers;
            var inputEvent = event.action == 1
                    ? MouseEvent.createPressEvent(xpos, ypos, event.button)
                    : MouseEvent.createReleaseEvent(xpos, ypos, event.button);
            ROOT.dispatchEvent(inputEvent);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(MouseScrollEvent event) {
        if (isActive() && Minecraft.getInstance().screen == null) {
            var options = Minecraft.getInstance().options;
            var d0 = (options.discreteMouseScroll().get() ? Math.signum(event.yOffset) : event.yOffset) * options.mouseWheelSensitivity().get();
            ROOT.dispatchEvent(new ScrollEvent(xpos, ypos, d0));
            event.setCanceled(true);
        }
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