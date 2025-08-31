package org.academy.api.client.hud;

import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.Render;
import org.academy.api.client.Resource;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.ScrollEvent;
import org.academy.api.client.gui.framework.*;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.input.*;
import org.academy.api.client.render.post.BlurEffect;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.*;

import static org.academy.api.client.gui.framework.Orientation.HORIZONTAL;

public final class DataTerminalHUD implements IAnimationScreen, HUDRenderer {
    public static final DataTerminalHUD INSTANCE = new DataTerminalHUD();
    public static final String CONFIG_KEY_DATA_TERMINAL = "data_terminal_hud_config";
    public static final String KEY_NAME_TOGGLE = "data_terminal_hud_config_toggle";

    private static final UIRenderContext internalUIRenderContext = new UIRenderContext();
    private static final AbstractContainerWidget rootContainer = new PanelWidget(0.0F, 0.0F, 0.0F, 0.0F);
    private static final List<App> APP_LIST = new ArrayList<>();

    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();

    @Nullable
    private static RenderTarget uiRenderTarget;
    @Nullable
    private static GpuBuffer uiRenderQuadVertexBuffer;
    @Nullable
    private static GpuBuffer terminalTransformUbo;
    @Nullable
    private static GpuBuffer maskVertexBuffer;
    @Nullable
    private static GpuBuffer maskIndexBuffer;
    private static VertexFormat.IndexType maskIndexType = VertexFormat.IndexType.SHORT;
    private static int maskIndexCount = 0;
    private static boolean maskDirty = true;

    private static boolean active = false;
    private static double xpos;
    private static double ypos;
    private static double lastRawMouseX;
    private static double lastRawMouseY;
    private static boolean isFirstMove = true;
    public static DataTerminalConfig config;

    static {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        uiRenderTarget = new TextureTarget(null, mainRenderTarget.width, mainRenderTarget.height, true);
        uiRenderTarget.setFilterMode(FilterMode.LINEAR);
    }

    private DataTerminalHUD() {
    }

    public static void init() {
        var mc = Minecraft.getInstance();
        var device = RenderSystem.getDevice();
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        terminalTransformUbo = device.createBuffer(() -> "Terminal Transform UBO", uboUsage, TransformUniforms.UBO_SIZE);

        var window = mc.getWindow();
        createGuiQuadVertexBuffer(window.getGuiScaledWidth(), window.getGuiScaledHeight());

        HUDManager.registerHUDRenderer(INSTANCE);
        NeoForge.EVENT_BUS.register(DataTerminalHUD.class);

        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY_DATA_TERMINAL, DataTerminalConfig.Action.INSTANCE);
        config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY_DATA_TERMINAL);

        var toggleKeys = new LinkedHashSet<Integer>();
        toggleKeys.add(GLFW.GLFW_KEY_RIGHT_ALT);
        var defaultKey = new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(toggleKeys, 0, new LinkedHashSet<>()));
        InputSystem.addKeyBinding(KEY_NAME_TOGGLE, config.getKeyBinding(KEY_NAME_TOGGLE, defaultKey), DataTerminalHUD::toggle);

        initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public static void close() {
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
        if (maskIndexBuffer != null) {
            maskIndexBuffer = null;
        }
    }

    private static void initGui(int width, int height) {
        rootContainer.setWidth((float) width);
        rootContainer.setHeight((float) height);
        rootContainer.clearChildren();

        var infoBar = buildInfoBar();
        rootContainer.addChild(infoBar.getName(), infoBar);

        var appDock = buildAppDock();
        rootContainer.addChild(appDock.getName(), appDock);

        rootContainer.addChild("app_window", new PanelWidget(0.0F, 0.0F, 0.0F, 0.0F));

        var cursorWidget = new CursorWidget(config.layout.cursorWidgetSize);
        cursorWidget.setName("cursor");
        rootContainer.addChild(cursorWidget.getName(), cursorWidget);

        maskDirty = true;
    }

    private static Widget buildInfoBar() {
        var layout = config.layout;
        var barWidth = 150.0F * layout.scale;
        var barHeight = 20.0F * layout.scale;
        var padding = 3.0F * layout.scale;
        var screenWidth = rootContainer.getWidth();
        var screenHeight = rootContainer.getHeight();
        var infoBarX = screenWidth - barWidth - screenWidth / 10.0F;
        var infoBarY = screenHeight / 10.0F;

        var infoBar = new PanelWidget(infoBarX, infoBarY, barWidth, barHeight);
        infoBar.setName("info_bar");
        infoBar.setAlpha(0.0F);

        var back = new FillWidget(0.0F, 0.0F, barWidth, barHeight, 1073741824);
        infoBar.addChild("back", back);

        var iconSize = barHeight - padding * 2.0F;
        var icon = new ImageWidget(padding, padding, iconSize, iconSize, Resource.Textures.ICON_DATA_TERMINAL);
        infoBar.addChild("icon", icon);

        var player = Minecraft.getInstance().player;
        var playerName = player != null ? player.getGameProfile().getName() : "N/A";
        var playerNameLabel = new LabelWidget(Component.literal(playerName), padding, padding, barWidth - padding * 2.0F, iconSize);
        playerNameLabel.setAlignment(LabelWidget.Alignment.RIGHT);
        playerNameLabel.setVerticalAlignment(LabelWidget.VerticalAlignment.MIDDLE);
        playerNameLabel.setDropShadow(false);
        infoBar.addChild("player_name", playerNameLabel);

        return infoBar;
    }

    private static Widget buildAppDock() {
        var layout = config.layout;
        var dockWidth = 220.0F * layout.scale;
        var dockHeight = 40.0F * layout.scale;
        var dockX = (rootContainer.getWidth() - dockWidth) / 2.0F;
        var dockY = rootContainer.getHeight() - 2.0F * dockHeight;

        var dockPanel = new PanelWidget(dockX, dockY, dockWidth, dockHeight);
        dockPanel.setName("app_dock");
        dockPanel.setAlpha(0.0F);

        var back = new FillWidget(0.0F, 0.0F, dockWidth, dockHeight, 1073741824);
        dockPanel.addChild("back", back);

        var scrollPanel = new ScrollPanelWidget(5.0F * layout.scale, 0.0F, dockWidth - 10.0F * layout.scale, dockHeight);
        scrollPanel.setScrollSpeed(12.0F);
        dockPanel.addChild("scroll_panel", scrollPanel);

        var linearLayout = new LinearLayoutContainer(0.0F, 0.0F, 0.0F, scrollPanel.getHeight(), HORIZONTAL);
        linearLayout.setSpacing(5.0F * layout.scale);
        scrollPanel.addChild("layout", linearLayout);

        for (var app : APP_LIST) {
            var appWidget = new AppWidget(app);
            linearLayout.addChild("app_" + app.getName(), appWidget);
        }

        linearLayout.doLayout();
        return dockPanel;
    }

    @Override
    public void render(double mouseX, double mouseY, float partialTick) {
        if (!active)
            return;

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        commandEncoder.clearColorAndDepthTextures(uiRenderTarget.getColorTexture(), 0, uiRenderTarget.getDepthTexture(), 1);

        internalUIRenderContext.renderFrame(rootContainer, uiRenderTarget, xpos, ypos, partialTick);
        renderUIWith3DEffect();
    }

    private void renderUIWith3DEffect() {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        var guiWidth = (float) window.getGuiScaledWidth();
        var guiHeight = (float) window.getGuiScaledHeight();
        var aspectRatio = (float) window.getWidth() / (float) window.getHeight();
        float fov = 80;
        float fovY = 2f * (float) Math.atan(Math.tan(Math.toRadians(fov) / 2f) / aspectRatio);

        var projectionMatrix = new Matrix4f().perspective(fovY, aspectRatio, 1.0F, 1000.0F);
        var viewMatrix = new Matrix4f().identity();

        {
            var z = -2.5125F;
            var scale = 2.0F * Math.abs(z) * (float) Math.tan(fovY / 2.0F) / guiHeight;

            viewMatrix.translate(0.0F, 0.0F, z);
            viewMatrix.scale(scale, -scale, scale);
            var centerX = guiWidth / 2.0F;
            var centerY = guiHeight / 2.0F;
            var dx = (float) (xpos - (double) centerX);
            var dy = (float) (ypos - (double) centerY);
            viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 1.0F, 0.0F), dx * 0.01F));
            viewMatrix.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(1.0F, 0.0F, 0.0F), -dy * 0.01F));
            viewMatrix.translate(-centerX, -centerY, 0.0F);
        }

        updateTransformUBO(projectionMatrix);

        var dynamicTransformsSlice = RenderSystem.getDynamicUniforms()
                .writeTransform(viewMatrix, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);

        if (config.enableBlur) {
            updateAndUploadMaskGeometry();

            if (maskIndexCount > 0) {
                var uniforms = Map.of(
                        "Projection", terminalTransformUbo.slice(),
                        "DynamicTransforms", dynamicTransformsSlice
                );
                BlurEffect.apply(renderPass -> {
                    renderPass.setPipeline(Render.RenderPipelines.POS_COLOR);
                    uniforms.forEach(renderPass::setUniform);
                    renderPass.setVertexBuffer(0, maskVertexBuffer);
                    var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                    renderPass.setIndexBuffer(maskIndexBuffer, sequentialBuffer.type());
                    renderPass.drawIndexed(0, 0, maskIndexCount, 1);
                });
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
    }

    private void updateAndUploadMaskGeometry() {
        var widgetsToMask = new ArrayList<Widget>();
        collectMaskWidgets(rootContainer, widgetsToMask);

        maskIndexCount = 0;
        if (widgetsToMask.isEmpty()) {
            maskDirty = false;
            return;
        }

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
                maskIndexType = meshData.drawState().indexType();
                var sequentialBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                maskIndexBuffer = sequentialBuffer.getBuffer(maskIndexCount);
            }
        }
        maskDirty = false;
    }

    private void collectMaskWidgets(Widget widget, List<Widget> collector) {
        if (!widget.isVisible()) {
            return;
        }

        if (widget instanceof FillWidget || (widget instanceof ImageWidget && !"cursor".equals(widget.getName()))) {
            collector.add(widget);
        }

        if (widget instanceof WidgetContainer container) {
            for (var child : container.getChildren().values()) {
                collectMaskWidgets(child, collector);
            }
        }
    }

    private static void updateTransformUBO(Matrix4f projectionMatrix) {
        try (var memoryStack = org.lwjgl.system.MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(memoryStack, TransformUniforms.UBO_SIZE);
            new TransformUniforms(projectionMatrix).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(terminalTransformUbo.slice(), byteBuffer);
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (active) {
            rootContainer.tick();
            var infoBar = (AbstractContainerWidget) rootContainer.getChildUnSafe("info_bar");
            var nameLabel = (LabelWidget) infoBar.getChildUnSafe("player_name");
            var player = Minecraft.getInstance().player;
            if (player != null) {
                nameLabel.setText(player.getGameProfile().getName());
            }
        }
    }

    public static void toggle() {
        ClientUtil.playDownSound();
        if (Minecraft.getInstance().screen != null) {
            active = false;
        } else {
            active = !active;
        }

        if (active) {
            if (AbilitySystemClient.isActiveHUD()) {
                AbilitySystemClient.setActiveHUD(false);
            }

            isFirstMove = true;
            var window = Minecraft.getInstance().getWindow();
            var mouseHandler = Minecraft.getInstance().mouseHandler;
            xpos = mouseHandler.xpos();
            ypos = mouseHandler.ypos();
            initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
            INSTANCE.playEntranceAnimation(rootContainer.getChildUnSafe("info_bar"), AnimationDirection.FROM_RIGHT);
            INSTANCE.playEntranceAnimation(rootContainer.getChildUnSafe("app_dock"), AnimationDirection.FROM_BOTTOM);
        }
    }

    private void playEntranceAnimation(Widget widget, AnimationDirection direction) {
        var finalX = widget.getX();
        var finalY = widget.getY();
        var offset = 20.0F;

        switch (direction) {
            case FROM_RIGHT -> widget.setX(finalX + offset);
            case FROM_BOTTOM -> widget.setY(finalY + offset);
        }

        playAnimation(ObjectAnimator.ofFloat(widget::setAlpha, 0.0F, 1.0F).setDuration(300L));
        var posAnimator = direction == AnimationDirection.FROM_RIGHT ? ObjectAnimator.ofFloat(widget::setX, widget.getX(), finalX) : ObjectAnimator.ofFloat(widget::setY, widget.getY(), finalY);
        posAnimator.setDuration(300L).setInterpolator(EasingFunctions.EASE_OUT_CUBIC);
        playAnimation(posAnimator);
    }

    public static void registerApp(App app) {
        APP_LIST.add(app);
    }

    public static void openAppInWindow(App app) {
        var contentWidget = app.getContainer();
        var title = app.getName();
        rootContainer.addChild("app_window", new PanelWidget(0.0F, 0.0F, 0.0F, 0.0F));
        app.onClick().run();

        var titleBarSize = 16.0F;
        var padding = 2.0F;
        var windowWidth = contentWidget.getWidth() + padding * 2.0F;
        var windowHeight = contentWidget.getHeight() + titleBarSize + padding * 2.0F;

        var windowFrame = new PanelWidget((rootContainer.getWidth() - windowWidth) / 2.0F, (rootContainer.getHeight() - windowHeight) / 2.0F, windowWidth, windowHeight);
        windowFrame.setName("app_window");
        rootContainer.addChild(windowFrame.getName(), windowFrame);

        var back = new FillWidget(0.0F, 0.0F, windowWidth, windowHeight, -2147483648);
        windowFrame.addChild("back", back);

        var titleBar = new PanelWidget(0.0F, 0.0F, windowWidth, titleBarSize);
        titleBar.setName("title_bar");
        windowFrame.addChild(titleBar.getName(), titleBar);

        var titleLabel = new LabelWidget(Component.literal(title), padding, 0.0F, windowWidth - padding * 2.0F - titleBarSize, titleBarSize);
        titleLabel.setVerticalAlignment(LabelWidget.VerticalAlignment.MIDDLE);
        titleBar.addChild("title_label", titleLabel);

        var closeButton = new ImageButtonWidget(windowWidth - titleBarSize, 0.0F, titleBarSize, titleBarSize, Resource.Textures.ICON_RANDOM, () -> {
            app.onClose().run();
            rootContainer.addChild("app_window", new PanelWidget(0.0F, 0.0F, 0.0F, 0.0F));
            maskDirty = true;
        });
        closeButton.setDefaultHoverEffect(true);
        titleBar.addChild("close_button", closeButton);

        contentWidget.setX(padding);
        contentWidget.setY(titleBarSize + padding);
        windowFrame.addChild("content", contentWidget);

        windowFrame.setAlpha(0.0F);
        windowFrame.setY(windowFrame.getY() + 20.0F);
        INSTANCE.playAnimation(ObjectAnimator.ofFloat(windowFrame::setAlpha, 0.0F, 1.0F).setDuration(200L));
        INSTANCE.playAnimation(ObjectAnimator.ofFloat(windowFrame::setY, windowFrame.getY(), (rootContainer.getHeight() - windowHeight) / 2.0F).setDuration(200L).setInterpolator(EasingFunctions.EASE_OUT_CUBIC));

        maskDirty = true;
    }

    @SubscribeEvent
    public static void onMouseButton(MouseButtonEvent event) {
        if (active && Minecraft.getInstance().screen == null) {
            InputSystem.currentMouseButton = event.button;
            InputSystem.currentMouseAction = event.action;
            InputSystem.currentMouseModifier = event.modifiers;
            var inputEvent = event.action == 1 ? MouseEvent.createPressEvent(xpos, ypos, event.button) : MouseEvent.createReleaseEvent(xpos, ypos, event.button);
            rootContainer.dispatchEvent(inputEvent);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseMove(MouseMoveEvent event) {
        if (active && Minecraft.getInstance().screen == null) {
            var mouseHandler = Minecraft.getInstance().mouseHandler;
            if (isFirstMove) {
                lastRawMouseX = event.xpos;
                lastRawMouseY = event.ypos;
                isFirstMove = false;
            }

            var deltaRawX = event.xpos - lastRawMouseX;
            var deltaRawY = event.ypos - lastRawMouseY;
            var guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            var deltaGuiX = deltaRawX / guiScale;
            var deltaGuiY = deltaRawY / guiScale;
            xpos += deltaGuiX * (double) config.mouseSensitivity;
            ypos += deltaGuiY * (double) config.mouseSensitivity;
            var window = Minecraft.getInstance().getWindow();
            xpos = MathUtil.clamp(xpos, 0.0, window.getGuiScaledWidth());
            ypos = MathUtil.clamp(ypos, 0.0, window.getGuiScaledHeight());
            rootContainer.dispatchEvent(MouseEvent.createMoveEvent(xpos, ypos));
            if (InputSystem.currentMouseAction == 1 || InputSystem.currentMouseAction == 2) {
                rootContainer.dispatchEvent(MouseEvent.createDragEvent(xpos, ypos, InputSystem.currentMouseButton, deltaGuiX, deltaGuiY));
            }

            lastRawMouseX = event.xpos;
            lastRawMouseY = event.ypos;
            mouseHandler.xpos = event.xpos;
            mouseHandler.ypos = event.ypos;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(MouseScrollEvent event) {
        if (active && Minecraft.getInstance().screen == null) {
            var options = Minecraft.getInstance().options;
            var d0 = (options.discreteMouseScroll().get() ? Math.signum(event.yOffset) : event.yOffset) * options.mouseWheelSensitivity().get();
            rootContainer.dispatchEvent(new ScrollEvent(xpos, ypos, d0));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(KeyInputEvent event) {
        if (active && Minecraft.getInstance().screen == null) {
            var options = Minecraft.getInstance().options;
            var key = event.key;
            var scanCode = event.scanCode;
            var isMovementKey = options.keyUp.matches(key, scanCode) || options.keyDown.matches(key, scanCode) || options.keyLeft.matches(key, scanCode) || options.keyRight.matches(key, scanCode) || options.keyJump.matches(key, scanCode) || options.keyShift.matches(key, scanCode) || options.keySprint.matches(key, scanCode);
            var isHotbarKey = false;
            var var7 = options.keyHotbarSlots;

            for (var hotbarKey : var7) {
                if (hotbarKey.matches(key, scanCode)) {
                    isHotbarKey = true;
                    break;
                }
            }

            if (!isMovementKey && !isHotbarKey) {
                var keyEvent = event.action == 0 ? new KeyEvent(EventType.KEY_RELEASED, event.key, event.scanCode, event.modifiers) : new KeyEvent(EventType.KEY_PRESSED, event.key, event.scanCode, event.modifiers);
                rootContainer.dispatchEvent(keyEvent);
                if (event.action == 0 && !keyEvent.isConsumed()) {
                    toggle();
                }
                event.setCanceled(true);
            }
        }
    }

    public static void resize(int width, int height) {
        var window = Minecraft.getInstance().getWindow();
        initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        createGuiQuadVertexBuffer(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        if (uiRenderTarget != null) {
            uiRenderTarget.resize(width, height);
        }
    }

    @SubscribeEvent
    public static void onScreenChange(ScreenEvent.Opening event) {
        if (active) {
            toggle();
        }
    }

    @Override
    public @NotNull List<Animator> getScreenAnimations() {
        return screenAnimations;
    }

    @Override
    public @NotNull Map<Widget, List<Animator>> getTrackedAnimations() {
        return trackedAnimations;
    }

    private static void createGuiQuadVertexBuffer(float width, float height) {
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

    private enum AnimationDirection {
        FROM_RIGHT,
        FROM_BOTTOM
    }

    public interface App {
        ResourceLocation getIcon();

        String getName();

        Runnable onClick();

        Runnable onClose();

        AbstractContainerWidget getContainer();
    }

    public static class DataTerminalConfig extends KeyBindingConfig {
        @SerializedName("layout")
        public LayoutConfig layout = new LayoutConfig();
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
            public static final TypeHandler<DataTerminalConfig> INSTANCE = new Action();

            private Action() {
            }

            @Override
            public @NotNull DataTerminalConfig getDefault() {
                return new DataTerminalConfig();
            }

            @Override
            public @NotNull Class<DataTerminalConfig> getTypeClass() {
                return DataTerminalConfig.class;
            }
        }
    }

    private static class AppWidget extends PanelWidget {
        private static final float BASE_ICON_SIZE = 22.0F;
        private static final float HOVER_ICON_SIZE = 26.0F;
        private static final float PANEL_SIZE = 30.0F;
        private static final float LABEL_HEIGHT = 10.0F;
        private final ImageWidget background;
        private final ImageButtonWidget iconButton;
        private final HoverLabelWidget label;
        private float currentSize = 22.0F;
        private boolean wasHovered = false;
        private final App app;

        public AppWidget(App app) {
            super(0.0F, 0.0F, 30.0F, 40.0F);
            this.app = app;

            background = new ImageWidget(0.0F, 0.0F, 0.0F, 0.0F, Resource.Textures.APP_BACK);
            addChild("background", background);

            iconButton = new ImageButtonWidget(0.0F, 0.0F, 0.0F, 0.0F, app.getIcon(), () -> openAppInWindow(app));
            addChild("icon_button", iconButton);

            label = new HoverLabelWidget(app.getName(), 0.0F, 30.0F, 30.0F);
            label.setBaseScale(0.5F);
            addChild("label", label);
        }

        @Override
        public void setHovered(boolean hovered) {
            if (isHovered() != hovered) {
                super.setHovered(hovered);
                INSTANCE.cancelAnimations(this);
                var targetSize = hovered ? 26.0F : 22.0F;
                var sizeAnimator = ObjectAnimator.ofFloat(this::setCurrentSize, currentSize, targetSize).setDuration(200L).setInterpolator(EasingFunctions.EASE_OUT_SINE);
                INSTANCE.playTrackedAnimation(this, sizeAnimator);
            }
        }

        private void setCurrentSize(float size) {
            currentSize = size;
            var offset = (30.0F - size) / 2.0F;
            background.setX(offset);
            background.setY(offset);
            background.setWidth(size);
            background.setHeight(size);
            var iconInset = size * 0.1F;
            var iconSize = size - iconInset * 2.0F;
            var iconOffset = offset + iconInset;
            iconButton.setX(iconOffset);
            iconButton.setY(iconOffset);
            iconButton.setWidth(iconSize);
            iconButton.setHeight(iconSize);
        }

        @Override
        public void render(@NotNull WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
            var isCurrentlyHovered = iconButton.isMouseOver(mouseX, mouseY);
            if (isCurrentlyHovered && !wasHovered) {
                ClientUtil.playDownSound();
            }
            wasHovered = isCurrentlyHovered;
            label.setHovered(isCurrentlyHovered);
            iconButton.setHovered(isCurrentlyHovered);
            super.render(context, mouseX, mouseY, partialTick);
        }
    }

    private static class TransformUniforms {
        public static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().get();
        private Matrix4f mvp;

        public TransformUniforms(Matrix4f mvp) {
            this.mvp = mvp;
        }

        public Matrix4f getMvp() {
            return this.mvp;
        }

        public void setMvp(Matrix4f mvp) {
            this.mvp = mvp;
        }

        public void write(Std140Builder builder) {
            builder.putMat4f(this.mvp);
        }
    }
}