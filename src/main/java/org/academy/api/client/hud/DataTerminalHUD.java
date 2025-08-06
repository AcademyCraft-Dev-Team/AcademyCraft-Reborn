package org.academy.api.client.hud;

import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.gui.animation.AnimationManager;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.ScrollEvent;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.IAnimationScreen;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.input.*;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.RenderTypes;
import org.academy.api.client.render.post.BlurEffect;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;

public final class DataTerminalHUD implements HUDRenderer, IAnimationScreen {

    public static final DataTerminalHUD INSTANCE = new DataTerminalHUD();
    public static final RenderTarget UI;
    public static final String CONFIG_KEY_DATA_TERMINAL = "data_terminal_hud_config";
    public static final String KEY_NAME_TOGGLE = CONFIG_KEY_DATA_TERMINAL + "_toggle";

    private static final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    private static final List<App> APP_LIST = new ArrayList<>();

    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();

    private static boolean active = false;
    private static double xpos, ypos;
    private static double lastRawMouseX, lastRawMouseY;
    private static boolean isFirstMove = true;
    public static Config config;

    private enum AnimationDirection {
        FROM_RIGHT, FROM_BOTTOM
    }

    static {
        rootContainer.setName("root");

        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        UI = new TextureTarget(mainRenderTarget.width, mainRenderTarget.height, true, Minecraft.ON_OSX);
        UI.setClearColor(0, 0, 0, 0);
        UI.enableStencil();
    }

    private static void initGui(int width, int height) {
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);
        rootContainer.clearChildren();

        var infoBar = buildInfoBar();
        rootContainer.addChild(infoBar.getName(), infoBar);

        var appDock = buildAppDock();
        rootContainer.addChild(appDock.getName(), appDock);

        rootContainer.addChild("app_window", new PanelWidget(0, 0, 0, 0));

        var cursorWidget = new CursorWidget(config.layout.cursorWidgetSize);
        cursorWidget.setName("cursor");
        rootContainer.addChild(cursorWidget.getName(), cursorWidget);
    }

    private static Widget buildInfoBar() {
        var layout = config.layout;
        float barWidth = 150f * layout.scale;
        float barHeight = 20f * layout.scale;
        float padding = 3f * layout.scale;

        float screenWidth = rootContainer.getWidth();
        float screenHeight = rootContainer.getHeight();
        float infoBarX = screenWidth - barWidth - (screenWidth / 10f);
        float infoBarY = screenHeight / 10f;

        var infoBar = new PanelWidget(infoBarX, infoBarY, barWidth, barHeight);
        infoBar.setName("info_bar");
        infoBar.setAlpha(0f);

        var back = new FillWidget(0, 0, barWidth, barHeight, 0x40000000);
        infoBar.addChild("back", back);

        var iconSize = barHeight - padding * 2;
        var icon = new ImageWidget(padding, padding, iconSize, iconSize, RenderTypes.ICON_DATA_TERMINAL);
        infoBar.addChild("icon", icon);

        var player = Minecraft.getInstance().player;
        String playerName = player != null ? player.getGameProfile().getName() : "N/A";
        var playerNameLabel = new LabelWidget(Component.literal(playerName),
                padding, padding, barWidth - padding * 2, iconSize);
        playerNameLabel.setAlignment(LabelWidget.Alignment.RIGHT);
        playerNameLabel.setVerticalAlignment(LabelWidget.VerticalAlignment.MIDDLE);
        playerNameLabel.setDropShadow(false);
        infoBar.addChild("player_name", playerNameLabel);

        return infoBar;
    }

    private static Widget buildAppDock() {
        var layout = config.layout;
        float dockWidth = 220f * layout.scale;
        float dockHeight = 40f * layout.scale;
        float dockX = (rootContainer.getWidth() - dockWidth) / 2f;
        float dockY = rootContainer.getHeight() - 2 * dockHeight;

        var dockPanel = new PanelWidget(dockX, dockY, dockWidth, dockHeight);
        dockPanel.setName("app_dock");
        dockPanel.setAlpha(0f);

        var back = new FillWidget(0, 0, dockWidth, dockHeight, 0x40000000);
        dockPanel.addChild("back", back);

        var scrollPanel = new ScrollPanelWidget(5f * layout.scale, 0, dockWidth - 10f * layout.scale, dockHeight);
        scrollPanel.setScrollSpeed(12f);
        dockPanel.addChild("scroll_panel", scrollPanel);

        var linearLayout = new LinearLayoutContainer(0, 0, 0, scrollPanel.getHeight(), Orientation.HORIZONTAL);
        linearLayout.setSpacing(5f * layout.scale);
        scrollPanel.addChild("layout", linearLayout);

        for (App app : APP_LIST) {
            var appWidget = new AppWidget(app);
            linearLayout.addChild("app_" + app.getName(), appWidget);
        }

        linearLayout.doLayout();

        return dockPanel;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        if (!active) return;

        bufferSource.endBatch();
        stack.pushPose();

        renderUIWith3DEffect(stack, bufferSource, partialTick);

        stack.popPose();
    }

    private static void renderUIWith3DEffect(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        RenderSystem.backupProjectionMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        float guiW = window.getGuiScaledWidth(), guiH = window.getGuiScaledHeight();

        MatrixStack orthoStack = new MatrixStack();
        UI.clear(Minecraft.ON_OSX);
        UI.bindWrite(false);

        orthoStack.pushPose();
        rootContainer.render(orthoStack, bufferSource, xpos, ypos, partialTick);
        orthoStack.popPose();

        bufferSource.endBatch();
        mc.getMainRenderTarget().bindWrite(false);

        float aspect = (float) window.getWidth() / window.getHeight();
        float fov = 80;
        float fovY = 2f * (float) Math.atan(Math.tan(Math.toRadians(fov) / 2f) / aspect);
        RenderSystem.setProjectionMatrix(new Matrix4f().perspective(fovY, aspect, 1, 1000), VertexSorting.DISTANCE_TO_ORIGIN);

        var pose = RenderSystem.getModelViewStack();
        pose.pushMatrix();
        pose.identity();

        float z = -2.5125f;
        float scale = (2f * Math.abs(z) * (float) Math.tan(fovY / 2f)) / guiH;
        stack.translate(0, 0, z);
        stack.scale(scale, -scale, scale);

        float centerX = guiW / 2f;
        float centerY = guiH / 2f;

        float dx = (float) (xpos - centerX);
        float dy = (float) (ypos - centerY);
        stack.mulPose(Axis.YP.rotationDegrees(dx * 0.01f));
        stack.mulPose(Axis.XP.rotationDegrees(-dy * 0.01f));

        stack.translate(-centerX, -centerY, 0);

        RenderSystem.applyModelViewMatrix();
        stack.pushPose();

        if (config.enableBlur) {
            renderBlurMask(stack, bufferSource);
        }
        renderUIToScreen(stack, bufferSource);

        stack.popPose();
        pose.popMatrix();

        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        UI.clear(Minecraft.ON_OSX);
        mc.getMainRenderTarget().bindWrite(false);
    }

    private static void renderBlurMask(MatrixStack stack, MultiBufferSource.BufferSource bufferSource) {
        var blurMaskRenderType = RenderType.gui();
        BlurEffect.setBlurRadius(config.blurRadius);
        BlurEffect.start(bufferSource, blurMaskRenderType);

        for (var child : rootContainer.getChildren().values()) {
            if (child.isVisible() && !(child instanceof CursorWidget)) {
                stack.pushPose();
                stack.translate(child.getX(), child.getY(), 0);
                RenderUtil.fill(stack, bufferSource, 0, 0, child.getWidth(), child.getHeight(), 0xFFFFFFFF);
                stack.popPose();
            }
        }

        BlurEffect.stop(bufferSource, blurMaskRenderType);
    }

    private static void renderUIToScreen(MatrixStack stack, MultiBufferSource.BufferSource bufferSource) {
        var renderType = RenderType.create("data_terminal_ui", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 1024, RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_TEX_SHADER).setCullState(RenderStateShard.NO_CULL).setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setTextureState(new RenderStateShard.EmptyTextureStateShard(() -> RenderSystem.setShaderTexture(0, UI.getColorTextureId()), () -> {
        })).createCompositeState(false));
        RenderUtil.blitWithRenderType(stack, bufferSource, renderType, 0, 0, rootContainer.getWidth(), rootContainer.getHeight(), 0, 1, 1, 0, 1, 1, 1, 1);
        bufferSource.endBatch(renderType);
    }

    public static void init() {
        HUDManager.registerHUDRenderer(INSTANCE);
        NeoForge.EVENT_BUS.register(DataTerminalHUD.class);
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY_DATA_TERMINAL, Config.Action.INSTANCE);
        config = AcademyCraftClient.CLIENT_CONFIG.getConfig(CONFIG_KEY_DATA_TERMINAL);
        if (config == null) {
            config = new Config();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(CONFIG_KEY_DATA_TERMINAL, config);
        }
        InputSystem.addKeyBinding(KEY_NAME_TOGGLE, config.getKeyBinding(KEY_NAME_TOGGLE, new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_RIGHT_ALT)), GLFW.GLFW_RELEASE, new LinkedHashSet<>()))), DataTerminalHUD::toggle);
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
            var m = Minecraft.getInstance().mouseHandler;
            xpos = m.xpos();
            ypos = m.ypos();
            initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());

            playEntranceAnimation(rootContainer.getChildUnSafe("info_bar"), AnimationDirection.FROM_RIGHT);
            playEntranceAnimation(rootContainer.getChildUnSafe("app_dock"), AnimationDirection.FROM_BOTTOM);
        }
    }

    private static void playEntranceAnimation(@NotNull Widget widget, @NotNull AnimationDirection direction) {
        float finalX = widget.getX();
        float finalY = widget.getY();
        float offset = 20f;

        switch (direction) {
            case FROM_RIGHT -> widget.setX(finalX + offset);
            case FROM_BOTTOM -> widget.setY(finalY + offset);
        }

        INSTANCE.playAnimation(ObjectAnimator.ofFloat(widget::setAlpha, 0f, 1f).setDuration(300));

        var posAnimator = (direction == AnimationDirection.FROM_RIGHT)
                ? ObjectAnimator.ofFloat(widget::setX, widget.getX(), finalX)
                : ObjectAnimator.ofFloat(widget::setY, widget.getY(), finalY);

        posAnimator.setDuration(300).setInterpolator(EasingFunctions.EASE_OUT_CUBIC);
        INSTANCE.playAnimation(posAnimator);
    }

    public static void registerApp(App app) {
        APP_LIST.add(app);
    }

    public static void openAppInWindow(App app) {
        var contentWidget = app.getContainer();
        var title = app.getName();
        rootContainer.addChild("app_window", new PanelWidget(0, 0, 0, 0));
        app.onClick().run();

        float TITLE_BAR_SIZE = 16f;
        float PADDING = 2f;

        var windowWidth = contentWidget.getWidth() + PADDING * 2;
        var windowHeight = contentWidget.getHeight() + TITLE_BAR_SIZE + PADDING * 2;

        var windowFrame = new PanelWidget(
                (rootContainer.getWidth() - windowWidth) / 2f,
                (rootContainer.getHeight() - windowHeight) / 2f,
                windowWidth, windowHeight
        );
        windowFrame.setName("app_window");
        rootContainer.addChild(windowFrame.getName(), windowFrame);

        var back = new FillWidget(0, 0, windowWidth, windowHeight, 0x80000000);
        windowFrame.addChild("back", back);

        var titleBar = new PanelWidget(0, 0, windowWidth, TITLE_BAR_SIZE);
        titleBar.setName("title_bar");
        windowFrame.addChild(titleBar.getName(), titleBar);

        var titleLabel = new LabelWidget(Component.literal(title), PADDING, 0, windowWidth - PADDING * 2 - TITLE_BAR_SIZE, TITLE_BAR_SIZE);
        titleLabel.setVerticalAlignment(LabelWidget.VerticalAlignment.MIDDLE);
        titleBar.addChild("title_label", titleLabel);

        var closeButton = new ImageButtonWidget(windowWidth - TITLE_BAR_SIZE, 0, TITLE_BAR_SIZE, TITLE_BAR_SIZE, RenderTypes.ICON_RANDOM,
                () ->
                {
                    app.onClose().run();
                    rootContainer.addChild("app_window", new PanelWidget(0, 0, 0, 0));
                }
        );
        closeButton.setDefaultHoverEffect(true);
        titleBar.addChild("close_button", closeButton);

        contentWidget.setX(PADDING);
        contentWidget.setY(TITLE_BAR_SIZE + PADDING);
        windowFrame.addChild("content", contentWidget);

        windowFrame.setAlpha(0f);
        windowFrame.setY(windowFrame.getY() + 20f);
        INSTANCE.playAnimation(ObjectAnimator.ofFloat(windowFrame::setAlpha, 0f, 1f).setDuration(200));
        INSTANCE.playAnimation(ObjectAnimator.ofFloat(windowFrame::setY, windowFrame.getY(), (rootContainer.getHeight() - windowHeight) / 2f).setDuration(200).setInterpolator(EasingFunctions.EASE_OUT_CUBIC));
    }

    @SubscribeEvent
    public static void onMouseButton(MouseButtonEvent event) {
        if (!active || Minecraft.getInstance().screen != null) return;
        InputSystem.currentMouseButton = event.button;
        InputSystem.currentMouseAction = event.action;
        InputSystem.currentMouseModifier = event.modifiers;
        var inputEvent = (event.action == GLFW_PRESS)
                ? MouseEvent.createPressEvent(xpos, ypos, event.button)
                : MouseEvent.createReleaseEvent(xpos, ypos, event.button);
        rootContainer.dispatchEvent(inputEvent);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseMove(MouseMoveEvent event) {
        if (!active || Minecraft.getInstance().screen != null) return;

        var mouseHandler = Minecraft.getInstance().mouseHandler;
        if (isFirstMove) {
            lastRawMouseX = event.xpos;
            lastRawMouseY = event.ypos;
            isFirstMove = false;
        }

        double deltaRawX = event.xpos - lastRawMouseX;
        double deltaRawY = event.ypos - lastRawMouseY;

        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        double deltaGuiX = deltaRawX / guiScale;
        double deltaGuiY = deltaRawY / guiScale;

        xpos += deltaGuiX * config.mouseSensitivity;
        ypos += deltaGuiY * config.mouseSensitivity;

        var window = Minecraft.getInstance().getWindow();
        xpos = MathUtil.clamp(xpos, 0, window.getGuiScaledWidth());
        ypos = MathUtil.clamp(ypos, 0, window.getGuiScaledHeight());

        rootContainer.dispatchEvent(MouseEvent.createMoveEvent(xpos, ypos));

        if (InputSystem.currentMouseAction == GLFW_PRESS || InputSystem.currentMouseAction == GLFW_REPEAT) {
            rootContainer.dispatchEvent(MouseEvent.createDragEvent(xpos, ypos, InputSystem.currentMouseButton, deltaGuiX, deltaGuiY));
        }

        lastRawMouseX = event.xpos;
        lastRawMouseY = event.ypos;
        mouseHandler.xpos = event.xpos;
        mouseHandler.ypos = event.ypos;

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseScroll(MouseScrollEvent event) {
        if (!active || Minecraft.getInstance().screen != null) return;
        var options = Minecraft.getInstance().options;
        var d0 = (options.discreteMouseScroll().get() ? Math.signum(event.yOffset) : event.yOffset) * options.mouseWheelSensitivity().get();
        rootContainer.dispatchEvent(new ScrollEvent(xpos, ypos, d0));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKey(KeyInputEvent event) {
        if (!active || Minecraft.getInstance().screen != null) {
            return;
        }

        var options = Minecraft.getInstance().options;
        int key = event.key, scanCode = event.scanCode;
        boolean isMovementKey = options.keyUp.matches(key, scanCode) || options.keyDown.matches(key, scanCode) || options.keyLeft.matches(key, scanCode) || options.keyRight.matches(key, scanCode) || options.keyJump.matches(key, scanCode) || options.keyShift.matches(key, scanCode) || options.keySprint.matches(key, scanCode);
        boolean isHotbarKey = false;
        for (var hotbarKey : options.keyHotbarSlots) {
            if (hotbarKey.matches(key, scanCode)) {
                isHotbarKey = true;
                break;
            }
        }

        if (!isMovementKey && !isHotbarKey) {
            var keyEvent = (event.action == GLFW.GLFW_RELEASE)
                    ? new KeyEvent(EventType.KEY_RELEASED, event.key, event.scanCode, event.modifiers)
                    : new KeyEvent(EventType.KEY_PRESSED, event.key, event.scanCode, event.modifiers);
            rootContainer.dispatchEvent(keyEvent);
            if (event.action == GLFW.GLFW_RELEASE && !keyEvent.isConsumed()) {
                toggle();
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onResizeDisplay(ResizeDisplayEvent event) {
        var window = Minecraft.getInstance().getWindow();
        initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void onScreenChange(ScreenEvent.Opening event) {
        if (active) toggle();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (active) {
            AnimationManager.getInstance().onFrameUpdate();
            rootContainer.tick();

            var infoBar = rootContainer.<AbstractContainerWidget>getChildUnSafe("info_bar");
            var nameLabel = infoBar.<LabelWidget>getChildUnSafe("player_name");
            var player = Minecraft.getInstance().player;
            if (player != null) {
                nameLabel.setText(player.getGameProfile().getName());
            }
        }
    }

    @Override
    public List<Animator> getScreenAnimations() {
        return this.screenAnimations;
    }

    @Override
    public Map<Widget, List<Animator>> getTrackedAnimations() {
        return this.trackedAnimations;
    }

    public static class Config extends KeyBindingConfig {
        @SerializedName("layout")
        public LayoutConfig layout = new LayoutConfig();
        @SerializedName("blurRadius")
        public float blurRadius = 10.0f;
        @SerializedName("enableBlur")
        public boolean enableBlur = true;
        @SerializedName("mouseSensitivity")
        public float mouseSensitivity = 1.0f;

        public static class LayoutConfig {
            @SerializedName("scale")
            public float scale = 0.9f;
            @SerializedName("cursorWidgetSize")
            public float cursorWidgetSize = 4f;
        }

        public static final class Action implements TypeHandler<Config> {
            public static final TypeHandler<Config> INSTANCE = new Action();

            private Action() {
            }

            @Override
            public @NotNull DataTerminalHUD.Config getDefault() {
                return new Config();
            }

            @Override
            public @NotNull Class<Config> getTypeClass() {
                return Config.class;
            }
        }
    }

    public interface App {
        @NotNull
        RenderType getIcon();

        @NotNull
        String getName();

        @NotNull
        Runnable onClick();

        @NotNull
        Runnable onClose();

        @NotNull
        AbstractContainerWidget getContainer();
    }

    private static class AppWidget extends PanelWidget {
        private static final float BASE_ICON_SIZE = 22f;
        private static final float HOVER_ICON_SIZE = 26f;
        private static final float PANEL_SIZE = HOVER_ICON_SIZE + 4f;
        private static final float LABEL_HEIGHT = 10f;

        private float currentSize = BASE_ICON_SIZE;
        private boolean wasHovered = false;

        public AppWidget(App app) {
            super(0, 0, PANEL_SIZE, PANEL_SIZE + LABEL_HEIGHT);

            var background = new ImageWidget(0, 0, 0, 0, RenderTypes.APP_BACK);
            this.addChild("background", background);

            var iconButton = new ImageButtonWidget(0, 0, 0, 0, app.getIcon(), () -> openAppInWindow(app));
            this.addChild("icon_button", iconButton);

            var label = new HoverLabelWidget(app.getName(), 0, PANEL_SIZE, PANEL_SIZE);
            label.setBaseScale(0.5f);
            this.addChild("label", label);
        }

        @Override
        public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
            var iconButton = this.<ImageButtonWidget>getChildUnSafe("icon_button");
            var background = this.<ImageWidget>getChildUnSafe("background");
            var label = this.<HoverLabelWidget>getChildUnSafe("label");

            boolean isCurrentlyHovered = iconButton.isHovered();
            if (isCurrentlyHovered && !this.wasHovered) {
                ClientUtil.playDownSound();
            }
            this.wasHovered = isCurrentlyHovered;

            label.setHovered(isCurrentlyHovered);

            float targetSize = isCurrentlyHovered ? HOVER_ICON_SIZE : BASE_ICON_SIZE;
            if (Math.abs(this.currentSize - targetSize) > 0.01f) {
                this.currentSize = MathUtil.lerpStartEndFactor(this.currentSize, targetSize, ClientUtil.animationFactor(3f));
            } else {
                this.currentSize = targetSize;
            }

            float offset = (PANEL_SIZE - this.currentSize) / 2f;
            background.setX(offset);
            background.setY(offset);
            background.setWidth(this.currentSize);
            background.setHeight(this.currentSize);

            float iconInset = this.currentSize * 0.1f;
            float iconSize = this.currentSize - (iconInset * 2);
            float iconOffset = offset + iconInset;

            iconButton.setX(iconOffset);
            iconButton.setY(iconOffset);
            iconButton.setWidth(iconSize);
            iconButton.setHeight(iconSize);

            super.render(stack, bufferSource, mouseX, mouseY, partialTick);
        }
    }

    private DataTerminalHUD() {
    }
}