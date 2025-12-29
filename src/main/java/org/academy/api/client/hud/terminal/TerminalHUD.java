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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.Render;
import org.academy.api.client.Resource;
import org.academy.api.client.app.App;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.animation.StateListAnimator;
import org.academy.api.client.gui.animation.ValueAnimator;
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
import org.academy.api.client.render.UniformPayload;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class TerminalHUD {
    public static final int COLOR = 0x40000000;
    public static final float MAIN_WIDTH = 150;
    public static final float UNFOLDED_MAIN_WIDTH = 384;
    public static final float MAIN_HEIGHT = 200;
    public static final String CONFIG_KEY = "terminal_hud_config";
    public static final String KEY_NAME_TOGGLE = "terminal_hud_config_toggle";
    private static volatile boolean active = false;

    @Nullable
    private static TerminalHUD INSTANCE;

    private static final List<App> APPS = new ArrayList<>();

    private final TerminalConfig config;
    private final Context context = new Context();
    private final UiContext uiContext;

    /**
     * 0.0f : 面向鼠标喵
     * <br>
     * 1.0f : 平行于屏幕喵
     */
    private float viewStateProgress = 0.0f;
    private volatile double xpos, ypos;
    private double startXpos, startYpos;

    private TerminalHUD() {
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, TerminalConfig.Action.INSTANCE);
        config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY);

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
                () -> {
                    if (INSTANCE != null) INSTANCE.toggleActive();
                }
        );

        uiContext = createUiContext();
    }

    private UiContext createUiContext() {
        return new UiContext() {
            @Override
            public void generateCommands(
                    RenderContext context, WidgetContainer rootWidget, double mouseX, double mouseY, float partialTick
            ) {
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
                        1,
                        List.of(),
                        List.of(new UniformPayload<>(
                                "GlowUniforms", SDFData.class, sdfData, SDFData.UBO_SIZE)
                        )
                ) {};
                context.submit(glowCommand);
            }
        };
    }

    public static TerminalHUD getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("TerminalHUD has not been initialized.");
        return INSTANCE;
    }

    public static void addApp(App app) {
        APPS.add(app);
    }

    public static boolean isActive() {
        return active;
    }

    public static void initMain() {
        INSTANCE = new TerminalHUD();
        NeoForge.EVENT_BUS.register(INSTANCE);
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

    @MainThread
    public void toggleActive() {
        active = !active;

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
            context.get().requestLayout();
        } else GLFW.glfwSetCursorPos(w.handle(), startXpos, startYpos);
    }

    @MainThread
    public void perform(double mouseX, double mouseY, float deltaPartialTick) {
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick);
    }

    public void render(
            int width, int height,
            GpuTextureView color,
            GpuTextureView depth,
            AtomicBoolean drew
    ) {
        if (!isActive()) return;

        var desc = new RenderTargetDescriptor(
                width, height,
                true, 0
        );
        var terminalTarget = Render.Buffers.getResourcePool().acquire(desc);

        try {
            uiContext.upload(terminalTarget, false);

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
                renderPass.setPipeline(Render.RenderPipelines.IMAGE_STENCIL_PREMULTIPLIED_ALPHA);
                renderPass.bindTexture(
                        "Sampler0",
                        terminalView,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                );
                renderPass.setUniform("Projection", projectionUBSlice);
                renderPass.setUniform("DynamicTransforms", dynamicTransformsSlice);

                renderPass.setVertexBuffer(0, Render.Buffers.getInstance().getFSQuadColorVBSDC());
                var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                renderPass.setIndexBuffer(sequentialBuffer.getBuffer(6), sequentialBuffer.type());
                renderPass.drawIndexed(0, 0, 6, 1);
            }
            drew.set(true);
        } finally {
            Render.Buffers.getResourcePool().release(desc, terminalTarget);
        }
    }

    private Matrix4f calculateViewMatrix(Window window, float fovY) {
        var guiWidth = window.getGuiScaledWidth();
        var guiHeight = window.getGuiScaledHeight();
        var viewMatrix = new Matrix4f().identity();

        var z = -2.5125F;
        var scale = (float) (2 * Math.abs(z) * Math.tan(fovY / 2) / guiHeight);

        viewMatrix.translate(0.0F, 0.0F, z);
        viewMatrix.scale(scale, scale, scale);

        var currentWidth = context.main.getWidth();
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
        viewMatrix.rotate(
                new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 1.0F, 0.0F), rotateY)
        );
        viewMatrix.rotate(
                new Quaternionf().fromAxisAngleDeg(new Vector3f(1.0F, 0.0F, 0.0F), rotateX)
        );
        viewMatrix.translate(-centerX, 0, 0.0F);

        viewMatrix.translate(-(guiWidth / 2.0F), -(guiHeight / 2.0F), 0.0F);

        return viewMatrix;
    }

    public void closeApp() {
        context.closeApp();
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        context.get().tick();
    }

    @SubscribeEvent
    public void onMouseMove(MouseMoveEvent event) {
        if (isActive() && ClientUtil.hasNoScreen()) {
            var guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            var deltaGuiX = event.xpos / guiScale;
            var deltaGuiY = event.ypos / guiScale;
            var window = Minecraft.getInstance().getWindow();
            xpos = Mth.clamp(deltaGuiX, 0.0, window.getGuiScaledWidth());
            ypos = Mth.clamp(deltaGuiY, 0.0, window.getGuiScaledHeight());
            context.get().dispatchEvent(MouseEvent.createMoveEvent(xpos, ypos));
            if (InputSystem.currentMouseAction == 1 || InputSystem.currentMouseAction == 2) {
                context.get().dispatchEvent(
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
    public void onMouseButton(MouseButtonEvent event) {
        if (isActive() && Minecraft.getInstance().screen == null) {
            InputSystem.currentMouseButton = event.button;
            InputSystem.currentMouseAction = event.action;
            InputSystem.currentMouseModifier = event.modifiers;
            var inputEvent =
                    event.action == 1
                            ? MouseEvent.createPressEvent(xpos, ypos, event.button)
                            : MouseEvent.createReleaseEvent(xpos, ypos, event.button);
            context.get().dispatchEvent(inputEvent);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseScroll(MouseScrollEvent event) {
        if (isActive() && ClientUtil.hasNoScreen()) {
            var options = Minecraft.getInstance().options;
            var d0 = (options.discreteMouseScroll().get()
                    ? Math.signum(event.yOffset)
                    : event.yOffset
                    ) * options.mouseWheelSensitivity().get();
            context.get().dispatchEvent(new ScrollEvent(xpos, ypos, d0));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onKey(KeyInputEvent event) {
        if (isActive()
                && ClientUtil.hasNoScreen()
                && !ClientUtil.isControlKey(event.key, event.scanCode, event.modifiers)
        ) {
            var keyEvent = new KeyEvent(
                    event.action == InputConstants.RELEASE ? EventType.KEY_RELEASED : EventType.KEY_PRESSED,
                    event.key, event.scanCode, event.modifiers
            );
            context.get().dispatchEvent(keyEvent);
            if (event.action == InputConstants.RELEASE && !keyEvent.isConsumed()) toggleActive();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onScreenChange(ScreenEvent.Opening event) {
        if (isActive()) toggleActive();
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

    private class Context implements WidgetContext {
        private final FrameLayoutWidget main = new FrameLayoutWidget();
        private final LinearLayoutWidget content = new LinearLayoutWidget();
        private final FrameLayoutWidget appContainer = new FrameLayoutWidget();
        private final FrameLayoutWidget root = createRoot();

        @Override
        public WidgetContainer get() {
            return root;
        }

        private FrameLayoutWidget createRoot() {
            var root = new FrameLayoutWidget();
            {
                main.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .gravity(Gravity.CENTER_RIGHT)
                                .margin(0, 0, 32, 0)
                                .size(MAIN_WIDTH, MAIN_HEIGHT)
                );
                root.addChild("main", main);
                {
                    var background = new FillWidget(COLOR);
                    main.addChild("back", background);
                    content.setOrientation(Orientation.VERTICAL);
                    content.setSpacing(2);
                    main.addChild("content", content);
                    {
                        var logo = new ImageWidget(Resource.Textures.ICON_TERMINAL);
                        logo.setLayoutParams(
                                new LinearLayoutWidget.LayoutParams()
                                        .size(16, 16)
                                        .gravity(Gravity.START)
                                        .margin(2, 2, 0, 0)
                        );
                        content.addChild("icon", logo);

                        var splitLine = new FillWidget(0xFFFFFFFF);
                        splitLine.setLayoutParams(
                                new LinearLayoutWidget.LayoutParams()
                                        .height(1)
                                        .widthMode(SizeMode.MATCH_PARENT)
                                        .padding(2, 0)
                        );
                        content.addChild("split_line", splitLine);

                        var apps = new ScrollPanelWidget();
                        apps.setLayoutParams(
                                new LinearLayoutWidget.LayoutParams()
                                        .weight(1)
                                        .widthMode(SizeMode.MATCH_PARENT)
                                        .gravity(Gravity.CENTER)
                                        .padding(4, 4, 4, 2)
                        );
                        content.addChild("apps", apps);
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
                                    for (var app : APPS) {
                                        rowOne.addChild(app.name(), createApp(app));
                                    }
                                }
                            }
                        }
                    }
                    appContainer.setLayoutParams(
                            new FrameLayoutWidget.LayoutParams()
                                    .sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
                    );
                    appContainer.setEnabled(false);
                    appContainer.setAlpha(0);
                    main.addChild("app_container", appContainer);
                }
            }
            return root;
        }

        private void openApp(App app) {
            content.setEnabled(false);
            content.cancelAnimations();
            content.startAnimation(
                    ObjectAnimator.ofFloat(content::setAlpha, content.getAlpha(), 0)
                            .setDuration(100)
            );

            appContainer.setEnabled(true);
            appContainer.clearChildren();
            appContainer.addChild("current_app", app.createContext().get());
            appContainer.startAnimation(
                    ObjectAnimator.ofFloat(
                                    appContainer::setAlpha, appContainer.getAlpha(), 1
                            )
                            .setDuration(100)
                            .setStartDelay(200)
            );

            animateMainWidthTransition(1);
        }

        public void closeApp() {
            content.setEnabled(true);
            content.cancelAnimations();
            content.startAnimation(
                    ObjectAnimator.ofFloat(content::setAlpha, content.getAlpha(), 1)
                            .setStartDelay(200)
                            .setDuration(100)
            );

            appContainer.setEnabled(false);
            appContainer.startAnimation(
                    ObjectAnimator.ofFloat(
                                    appContainer::setAlpha, appContainer.getAlpha(), 0
                            )
                            .setDuration(100)
            );

            animateMainWidthTransition(0);
        }

        private void animateMainWidthTransition(float target) {
            var viewStateAnimator = ValueAnimator.ofFloat(viewStateProgress, target);
            viewStateAnimator.setDuration(400);
            viewStateAnimator.setInterpolator(EasingFunctions.EASE_OUT_CUBIC);
            viewStateAnimator.addUpdateListener(anim -> {
                viewStateProgress = anim.getAnimatedValue();
                main.setWidth(Mth.lerp(
                        viewStateProgress, MAIN_WIDTH, UNFOLDED_MAIN_WIDTH
                ));
            });
            main.cancelAnimations();
            main.startAnimation(viewStateAnimator);
        }

        private LinearLayoutWidget createApp(App app) {
            var icon = app.icon();
            var name = app.name();

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
                iconArea.setOnClickListener(_ -> openApp(app));
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

                    var progressState = new AtomicReference<>(0f);
                    Consumer<Float> updateState = progress -> {
                        progressState.set(progress);
                        iconArea.setScale(1.0f + 0.2f * progress);
                        back.setBrightness(0.8f + 0.2f * progress);
                        iconWidget.setBrightness(0.9f + 0.1f * progress);
                    };

                    var animator = new StateListAnimator();
                    animator.addState(Widget.State.HOVERED,
                            ObjectAnimator.ofFloat(progressState::get, updateState, 1.0f)
                                    .setDuration(100)
                                    .setInterpolator(EasingFunctions.EASE_OUT_SINE)
                    );
                    animator.addState(Widget.State.NONE,
                            ObjectAnimator.ofFloat(progressState::get, updateState, 0.0f)
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
    }
}