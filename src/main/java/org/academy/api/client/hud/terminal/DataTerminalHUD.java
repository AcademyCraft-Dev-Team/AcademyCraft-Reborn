package org.academy.api.client.hud.terminal;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.Render;
import org.academy.api.client.Resource;
import org.academy.api.client.app.App;
import org.academy.api.client.gui.animation.*;
import org.academy.api.client.gui.command.PosTexRectDrawCommand;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.ScrollEvent;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.render.UiContext;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.input.*;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.app.MusicApp;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

@EventBusSubscriber(Dist.CLIENT)
public final class DataTerminalHUD {
    public static final int COLOR = 0x40000000;
    public static final float MAIN_WIDTH = 150;
    public static final float MAIN_HEIGHT = 200;
    public static final String CONFIG_KEY_DATA_TERMINAL = "data_terminal_hud_config";
    public static final String KEY_NAME_TOGGLE = "data_terminal_hud_config_toggle";
    @Nullable
    private static DataTerminalConfig config;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FrameLayoutWidget ROOT = new FrameLayoutWidget();
    @Nullable
    private static UiContext uiContext;
    /**
     * Main 和 Render 线程都需要用喵
     */
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static volatile double xpos, ypos;
    private static double startXpos, startYpos;
    @Nullable
    private static LinearLayoutWidget contentList;
    @Nullable
    private static FrameLayoutWidget appContainer;
    @Nullable
    private static FrameLayoutWidget main;

    // 视图切换状态动画相关
    private static float viewStateProgress = 0.0f; // 1.0f:平行于屏幕

    @MainThread
    public static void toggleActive() {
        // 保险喵?
        boolean prev;
        do prev = ACTIVE.get();
        while (!ACTIVE.compareAndSet(prev, !prev));

        var mc = Minecraft.getInstance();
        var w = mc.getWindow();
        closeApp();
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
        } else GLFW.glfwSetCursorPos(w.handle(), startXpos, startYpos);
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
        uiContext = new UiContext() {
            @Override
            public void generateCommands(RenderContext context, WidgetContainer rootWidget, double mouseX, double mouseY, float partialTick) {
                super.generateCommands(context, rootWidget, mouseX, mouseY, partialTick);

                context.pose().pushPose();
                context.pose().translate(xpos, ypos, 1000);

                var sdfData = new SDFData(new Vector4f(0, 0, 0, 0.75f), 0.5f, 0.5f);

                context.pose().pushPose();
                {
                    context.pose().translate(-2, -2, 0);
                    submitGlowCommand(context, 4, sdfData);
                }
                context.pose().popPose();

                context.pose().pushPose();
                {
                    context.pose().translate(-1.5f, -1.5f, 0.1f);
                    sdfData = new SDFData(new Vector4f(1, 1, 1, 1), 0.5f, 0.25f);
                    submitGlowCommand(context, 3, sdfData);
                }
                context.pose().popPose();
            }

            private void submitGlowCommand(RenderContext context, float size, SDFData sdfData) {
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
                    public List<TextureBinding> getTextures() {
                        return List.of();
                    }

                    @Override
                    public List<UniformBinding> getUniforms() {
                        var uboStorage = context.getDynamicUbo(SDFData.class, SDFData.UBO_SIZE);
                        var uboSlice = uboStorage.writeUniform(sdfData);
                        return List.of(new UniformBinding("GlowUniforms", uboSlice));
                    }
                };
                context.submit(glowCommand);
            }
        };
    }

    private static void init() {
        ROOT.setName("root");
        ROOT.clearChildren();

        main = new FrameLayoutWidget();
        main.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER_RIGHT)
                        .margin(0, 0, 32, 0)
                        .size(MAIN_WIDTH, MAIN_HEIGHT)
        );
        ROOT.addChild("main", main);
        {
            var background = new FillWidget(COLOR);
            main.addChild("back", background);
            contentList = new LinearLayoutWidget();
            contentList.setOrientation(Orientation.VERTICAL);
            contentList.setSpacing(2);
            main.addChild("content", contentList);
            {
                var logo = new ImageWidget(Resource.Textures.ICON_DATA_TERMINAL);
                logo.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .size(16, 16)
                                .gravity(Gravity.START)
                                .margin(2, 2, 0, 0)
                );
                contentList.addChild("icon", logo);

                var splitLine = new FillWidget(0xFFFFFFFF);
                splitLine.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .height(1)
                                .widthMode(SizeMode.MATCH_PARENT)
                                .padding(2, 0)
                );
                contentList.addChild("splitLine", splitLine);

                var apps = new ScrollPanelWidget();
                apps.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .weight(1)
                                .widthMode(SizeMode.MATCH_PARENT)
                                .gravity(Gravity.CENTER)
                                .padding(4, 4, 4, 2)
                );
                contentList.addChild("apps", apps);
                {
                    var appRows = new LinearLayoutWidget();
                    appRows.setOrientation(Orientation.VERTICAL);
                    appRows.setSpacing(4);
                    appRows.setLayoutParams(
                            new WidgetContainer.LayoutParams()
                                    .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    );
                    apps.addChild("app_rows", appRows);
                    {
                        var rowOne = new LinearLayoutWidget();
                        rowOne.setOrientation(Orientation.HORIZONTAL);
                        rowOne.setLayoutParams(
                                new LinearLayoutWidget.LayoutParams()
                                        .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                        );
                        appRows.addChild("row_one", rowOne);
                        {
                            rowOne.addChild("settings", createApp(
                                    Resource.Textures.ICON_SETTINGS,
                                    "Settings",
                                    DataTerminalHUD::closeApp
                            ));

                            rowOne.addChild("music", createApp(
                                    Resource.Textures.ICON_MUSIC_PLAYER,
                                    "Music",
                                    () -> openApp(MusicApp.INSTANCE)
                            ));
                        }
                    }
                }
            }
            appContainer = new FrameLayoutWidget();
            appContainer.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT));
            appContainer.setVisibility(Widget.Visibility.INVISIBLE);
            main.addChild("app_container", appContainer);
        }
    }

    public static void resize() {
        init();
    }

    @MainThread
    public static void perform(double mouseX, double mouseY, float deltaPartialTick) {
        if (uiContext != null) uiContext.perform(ROOT, mouseX, mouseY, deltaPartialTick);
        else hasNotBeenInitialized();
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

            var commandEncoder = RenderSystem.getDevice().createCommandEncoder();

            try (var renderPass = commandEncoder.createRenderPass(
                            () -> "Blit Pass to " + color + depth,
                            color, OptionalInt.empty(), depth, OptionalDouble.empty()
            )) {
                renderPass.setPipeline(Render.RenderPipelines.IMAGE_STENCIL);
                renderPass.bindTexture(
                        "Sampler0",
                        terminalView,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                );
                renderPass.setUniform("Projection", projectionUBSlice);
                renderPass.setUniform("DynamicTransforms", dynamicTransformsSlice);

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

        var currentWidth = main == null ? MAIN_WIDTH : main.getWidth();
        var widgetCenterX = guiWidth - 32 - (currentWidth / 2.0f);
        var screenCenterX = guiWidth / 2.0f;
        var shiftX = viewStateProgress * (screenCenterX - widgetCenterX);

        viewMatrix.translate(shiftX, 0, 0);

        var dx = (float) (xpos - guiWidth - 32 - MAIN_WIDTH / 2f);
        var dy = (float) (ypos - guiHeight / 2.0F);

        var rotateY = Mth.lerp(viewStateProgress, dx * 0.05F - 5, 0f);
        var rotateX = Mth.lerp(viewStateProgress, -dy * 0.05F - 1, 0f);
        var centerX = (guiWidth / 2.0F) - 32 - MAIN_WIDTH / 2f;

        viewMatrix.translate(centerX, 0, 0.0F);
        viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 1.0F, 0.0F), rotateY));
        viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(1.0F, 0.0F, 0.0F), rotateX));
        viewMatrix.translate(-centerX, 0, 0.0F);

        viewMatrix.translate(-(guiWidth / 2.0F), -(guiHeight / 2.0F), 0.0F);

        return viewMatrix;
    }

    private static GpuBufferSlice createDynamicTransformsSlice(Matrix4f viewMatrix) {
        return RenderSystem.getDynamicUniforms()
                .writeTransform(
                        viewMatrix,
                        new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                        new Vector3f(),
                        new Matrix4f()
                );
    }

    private static GpuBufferSlice createProjectionUboSlice(float fovY, float aspectRatio) {
        var projectionMatrix = new Matrix4f().perspective(fovY, aspectRatio, 1.0F, 1000.0F);
        return Render.Buffers.getInstance().getProjectionUB(projectionMatrix).slice();
    }

    @SuppressWarnings("ConstantConditions")
    private static void openApp(App app) {
        var viewStateAnimator = ValueAnimator.ofFloat(viewStateProgress, 1.0f);
        viewStateAnimator.setDuration(400);
        viewStateAnimator.setInterpolator(EasingFunctions.EASE_OUT_CUBIC);
        viewStateAnimator.addUpdateListener(anim -> {
            viewStateProgress = anim.getAnimatedValue();

            if (main != null) {
                float screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                var targetWidth = screenWidth * 0.8f;
                main.setWidth(Mth.lerp(viewStateProgress, MAIN_WIDTH, targetWidth));
            }
        });
        viewStateAnimator.start();

        if (contentList == null || appContainer == null) return;
        contentList.setVisibility(Widget.Visibility.INVISIBLE);
        appContainer.clearChildren();
        appContainer.addChild("current_app", app.content());
        appContainer.setVisibility(Widget.Visibility.VISIBLE);
    }

    public static void closeApp() {
        var viewStateAnimator = ValueAnimator.ofFloat(viewStateProgress, 0.0f);
        viewStateAnimator.setDuration(400);
        viewStateAnimator.setInterpolator(EasingFunctions.EASE_OUT_CUBIC);
        viewStateAnimator.addUpdateListener(anim -> {
            viewStateProgress = anim.getAnimatedValue();
            if (main != null) {
                float screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                var targetWidth = screenWidth * 0.8f;
                main.setWidth(Mth.lerp(viewStateProgress, MAIN_WIDTH, targetWidth));
            }
        });
        viewStateAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (appContainer != null) {
                    appContainer.setVisibility(Widget.Visibility.INVISIBLE);
                    appContainer.clearChildren();
                }
                if (contentList != null) {
                    contentList.setVisibility(Widget.Visibility.VISIBLE);
                }
            }
        });
        viewStateAnimator.start();
    }

    public static LinearLayoutWidget createApp(Identifier icon, String name, Runnable onClick) {
        var size = 48;
        var layout = new LinearLayoutWidget();
        layout.setSpacing(1);
        layout.setOrientation(Orientation.VERTICAL);
        layout.setLayoutParams(
                new LinearLayoutWidget.LayoutParams()
                        .width(size)
                        .heightMode(SizeMode.WRAP_CONTENT)
        );
        {
            var iconArea = new ButtonWidget();
            iconArea.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .size(size, size)
            );
            iconArea.setOnClickListener(_ -> onClick.run());
            layout.addChild("icon_area", iconArea);
            {
                var back = new ImageWidget(Resource.Textures.APP_BACK);
                back.setColor(0.8f, 0.8f, 0.8f);
                iconArea.addChild("back", back);

                var iconWidget = new ImageWidget(icon);
                iconWidget.setColor(0.9f, 0.9f, 0.9f);
                iconWidget.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .size(size / 2f, size / 2f)
                                .gravity(Gravity.CENTER)
                );
                iconArea.addChild("icon", iconWidget);

                var progressState = new float[]{0.0f};

                Consumer<Float> updateState = progress -> {
                    progressState[0] = progress;
                    iconArea.setScale(1.0f + 0.2f * progress);
                    back.setBrightness(0.8f + 0.2f * progress);
                    iconWidget.setBrightness(0.9f + 0.1f * progress);
                };

                Supplier<Float> getProgress = () -> progressState[0];

                var animator = new StateListAnimator();
                animator.addState(Widget.State.HOVERED,
                        ObjectAnimator.ofFloat(getProgress, updateState, 1.0f)
                                .setDuration(100)
                                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
                );
                animator.addState(Widget.State.NONE,
                        ObjectAnimator.ofFloat(getProgress, updateState, 0.0f)
                                .setDuration(100)
                                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
                );
                iconArea.setStateListAnimator(animator);
            }
            var nameWidget = new LabelWidget(name);
            nameWidget.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .gravity(Gravity.CENTER)
                            .margin(2, 0)
            );
            layout.addChild("name", nameWidget);
        }
        return layout;
    }

    public static float getViewStateProgress() {
        return viewStateProgress;
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
                ROOT.dispatchEvent(
                        MouseEvent.createDragEvent(
                                xpos, ypos, InputSystem.currentMouseButton, deltaGuiX, deltaGuiY
                        )
                );
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
            var inputEvent =
                    event.action == 1
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
            var d0 = (options.discreteMouseScroll().get()
                    ? Math.signum(event.yOffset)
                    : event.yOffset
                    ) * options.mouseWheelSensitivity().get();
            ROOT.dispatchEvent(new ScrollEvent(xpos, ypos, d0));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(KeyInputEvent event) {
        if (isActive()
                && Minecraft.getInstance().screen == null
                && !ignoreKey(event.key, event.scanCode, event.modifiers)
        ) {
            var keyEvent = new KeyEvent(
                    event.action == InputConstants.RELEASE ? EventType.KEY_RELEASED : EventType.KEY_PRESSED,
                    event.key, event.scanCode, event.modifiers
            );
            ROOT.dispatchEvent(keyEvent);
            if (event.action == InputConstants.RELEASE && !keyEvent.isConsumed()) toggleActive();
            event.setCanceled(true);
        }
    }

    private static boolean ignoreKey(
            @InputConstants.Value int key, int scancode, @InputWithModifiers.Modifiers int modifiers
    ) {
        var options = Minecraft.getInstance().options;
        var event = new net.minecraft.client.input.KeyEvent(key, scancode, modifiers);
        var isHotbarKey = false;
        for (var hotbarKey : options.keyHotbarSlots) {
            if (hotbarKey.matches(event)) {
                isHotbarKey = true;
                break;
            }
        }
        var isMovementKey
                = options.keyUp.matches(event)
                || options.keyDown.matches(event)
                || options.keyLeft.matches(event)
                || options.keyRight.matches(event)
                || options.keyJump.matches(event)
                || options.keyShift.matches(event)
                || options.keySprint.matches(event);
        return isHotbarKey || isMovementKey;
    }

    @SubscribeEvent
    public static void onScreenChange(ScreenEvent.Opening event) {
        if (isActive()) toggleActive();
    }

    private static void hasNotBeenInitialized() {
        LOGGER.warn("DataTerminalHUD has not been initialized.");
    }

    public record SDFData(Vector4f color, float radius,
                          float softness) implements DynamicUniformStorage.DynamicUniform {
        public static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putFloat().putFloat().get();

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(color)
                    .putFloat(radius)
                    .putFloat(softness);
        }
    }

    private DataTerminalHUD() {
    }
}