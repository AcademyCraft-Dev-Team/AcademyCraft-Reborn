package org.academy.api.client.hud;

import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;

public final class DataTerminalHUD implements HUDRenderer {
    private static final AbstractContainerWidget rootContainer = new LayeredPanelWidget(0, 0, 0, 0);
    private static boolean active = false;
    private static double xpos, ypos;
    private static double lastRawMouseX, lastRawMouseY;
    private static boolean isFirstMove = true;
    public static final String CONFIG_KEY_DATA_TERMINAL = "data_terminal_hud_config";
    public static final String KEY_NAME_TOGGLE = CONFIG_KEY_DATA_TERMINAL + "_toggle";
    public static final DataTerminalHUD INSTANCE = new DataTerminalHUD();
    public static Config config;

    private static float INFO_BAR_HEIGHT;
    private static float APP_ICON_SIZE;
    private static float APP_WIDGET_HEIGHT;
    private static float APP_WIDGET_WIDTH;
    private static float APP_HORIZONTAL_SPACING;
    private static float APP_VERTICAL_SPACING;
    private static float APP_AREA_PADDING;
    private static int APP_COLS;
    private static float APP_AREA_WIDTH;
    private static float APP_AREA_HEIGHT;
    private static float APP_AREA_X;
    private static float APP_AREA_Y;
    private static float APP_AREA_BAR_WIDTH;
    private static float WIDTH;
    private static float HEIGHT;
    private static float ICON_X_Y_PADDING_INFO_BAR;
    private static float ICON_SIZE_INFO_BAR;
    private static float INFO_BAR_PLAYER_NAME_PADDING_X;
    private static float PLAYER_NAME_LABEL_Y_INFO_BAR;
    private static float SPLIT_LINE_X_PADDING_INFO_BAR;
    private static float SPLIT_LINE_WIDTH_REDUCTION_INFO_BAR;
    private static float CURSOR_WIDGET_SIZE;
    private static float APP_WIDGET_ICON_TRANSLATE_X;
    private static float APP_WIDGET_ICON_TRANSLATE_Y;
    private static float APP_NAME_LABEL_OFFSET_X;

    private static LabelWidget playerNameLabel;
    private static final List<App> APP_LIST = new ArrayList<>();

    private static void updateParametersFromConfig() {
        INFO_BAR_HEIGHT = config.infoBarHeight;
        var PADDING_GENERAL = config.paddingGeneral;
        var PADDING_SMALL = config.paddingSmall;
        APP_ICON_SIZE = config.appIconSize;
        var APP_TEXT_HEIGHT = config.appTextHeight;
        var ICON_TEXT_SPACING = config.iconTextSpacing;
        APP_WIDGET_HEIGHT = APP_ICON_SIZE + ICON_TEXT_SPACING + APP_TEXT_HEIGHT;
        APP_WIDGET_WIDTH = config.appWidgetWidth;
        APP_HORIZONTAL_SPACING = PADDING_SMALL;
        APP_VERTICAL_SPACING = PADDING_SMALL;
        APP_AREA_PADDING = PADDING_SMALL;
        APP_COLS = config.appCols;
        var VISIBLE_APP_ROWS = config.visibleAppRows;
        APP_AREA_WIDTH = (APP_WIDGET_WIDTH * APP_COLS) + (APP_HORIZONTAL_SPACING * (APP_COLS - 1)) + (APP_AREA_PADDING * 2);
        APP_AREA_HEIGHT = (APP_WIDGET_HEIGHT * VISIBLE_APP_ROWS) + (APP_VERTICAL_SPACING * (VISIBLE_APP_ROWS - 1)) + (APP_AREA_PADDING * 2);
        APP_AREA_X = PADDING_GENERAL;
        APP_AREA_Y = INFO_BAR_HEIGHT + PADDING_SMALL;
        APP_AREA_BAR_WIDTH = PADDING_SMALL;
        WIDTH = APP_AREA_X + APP_AREA_WIDTH + APP_AREA_BAR_WIDTH + PADDING_GENERAL;
        HEIGHT = APP_AREA_Y + APP_AREA_HEIGHT + PADDING_GENERAL;
        ICON_X_Y_PADDING_INFO_BAR = 3f;
        ICON_SIZE_INFO_BAR = 14f;
        INFO_BAR_PLAYER_NAME_PADDING_X = 3f;
        PLAYER_NAME_LABEL_Y_INFO_BAR = 4f;
        SPLIT_LINE_X_PADDING_INFO_BAR = PADDING_GENERAL;
        SPLIT_LINE_WIDTH_REDUCTION_INFO_BAR = PADDING_GENERAL * 2;
        CURSOR_WIDGET_SIZE = 5f;
        APP_WIDGET_ICON_TRANSLATE_X = 3f;
        APP_WIDGET_ICON_TRANSLATE_Y = 6f;
        APP_NAME_LABEL_OFFSET_X = -2f;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        if (active) {
            bufferSource.endBatch();
            stack.pushPose();
            RenderSystem.backupProjectionMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            var mc = Minecraft.getInstance();
            var window = mc.getWindow();
            float winW = window.getWidth(), winH = window.getHeight();
            float guiW = window.getGuiScaledWidth(), guiH = window.getGuiScaledHeight();
            float aspect = winW / winH;
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
            float centerX = guiW - WIDTH / 2f;
            float centerY = guiH / 2f;
            float dx = (float) (xpos - centerX);
            float dy = (float) (ypos - centerY);
            stack.lastMatrix().rotateAround(Axis.YP.rotationDegrees(dx * 0.035f - 5), guiW / 2 - WIDTH * 1.25f + WIDTH / 2, 0, 0);
            stack.lastMatrix().rotateAround(Axis.XP.rotationDegrees(-dy * 0.035f + 2), 0, 0, 0);
            stack.translate(-guiW / 2, -guiH / 2, 0);
            stack.scale(1, 1, 0.01f);
            RenderSystem.applyModelViewMatrix();
            stack.pushPose();
            {
                if (config.enableBlur) {
                    var blurMaskRenderType = RenderType.gui();
                    BlurEffect.setBlurRadius(config.blurRadius);
                    BlurEffect.start(bufferSource, blurMaskRenderType);
                    stack.pushPose();
                    stack.translate(guiW - WIDTH * 1.25f, (guiH - HEIGHT) / 2, 0);
                    RenderUtil.fill(stack, bufferSource, 0, 0, WIDTH, HEIGHT, 0XFFFFFFFF);
                    stack.popPose();
                    if (rootContainer.getChildren().containsKey("area_app")) {
                        stack.pushPose();
                        var widget = rootContainer.getChildren().get("area_app");
                        stack.translate(widget.getAbsoluteX(), widget.getAbsoluteY(), 0);
                        RenderUtil.fill(stack, bufferSource, 0, 0, widget.getWidth(), widget.getHeight(), 0XFFFFFFFF);
                        stack.popPose();
                    }
                    BlurEffect.stop(bufferSource, blurMaskRenderType);
                }
            }
            rootContainer.render(stack, bufferSource, xpos, ypos, partialTick);
            stack.popPose();
            pose.popMatrix();

            RenderSystem.disableDepthTest();
            bufferSource.endBatch();
            RenderSystem.enableDepthTest();

            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    public static void initGui(int width, int height) {
        updateParametersFromConfig();
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);
        rootContainer.clearChildren();
        var main = new LayeredPanelWidget(width - WIDTH * 1.25f, (height - HEIGHT) / 2, WIDTH, HEIGHT);
        rootContainer.addChild("main", main);
        {
            var back = new FillWidget(0, 0, WIDTH, HEIGHT, 0xFF000000);
            back.setAlpha(0.25f);
            main.addChild("back", back);
            var root = new LayeredPanelWidget(0, 0, WIDTH, HEIGHT);
            var dynamicGeometricBack = new DynamicGeometricBackgroundWidget(
                    0, 0, WIDTH, HEIGHT,
                    48, 1, 64,
                    0x20FFFFFF, true, 32);
            main.addChild("back_dynamic", dynamicGeometricBack);
            main.addChild("root", root);
            {
                var infoBar = new LayeredPanelWidget(0, 0, WIDTH, INFO_BAR_HEIGHT);
                root.addChild("info_bar", infoBar);
                {
                    var icon = new ImageWidget(ICON_X_Y_PADDING_INFO_BAR, ICON_X_Y_PADDING_INFO_BAR, ICON_SIZE_INFO_BAR, ICON_SIZE_INFO_BAR,
                            RenderTypes.ICON_DATA_TERMINAL);
                    infoBar.addChild("icon", icon);

                    var player = Minecraft.getInstance().player;
                    String playerName = "N/A";
                    if (player != null) {
                        playerName = player.getGameProfile().getName();
                    }
                    var font = Minecraft.getInstance().font;
                    playerNameLabel = new LabelWidget(playerName, WIDTH - font.width(playerName) - INFO_BAR_PLAYER_NAME_PADDING_X, PLAYER_NAME_LABEL_Y_INFO_BAR);
                    playerNameLabel.dropShadow = false;
                    infoBar.addChild("player_name", playerNameLabel);

                    var spiltLine = new FillWidget(SPLIT_LINE_X_PADDING_INFO_BAR, INFO_BAR_HEIGHT, WIDTH - SPLIT_LINE_WIDTH_REDUCTION_INFO_BAR, 1, 0x30FFFFFF);
                    infoBar.addChild("spilt_line", spiltLine);
                }

                var appArea = new ScrollPanelWidget(APP_AREA_X, APP_AREA_Y, APP_AREA_WIDTH, APP_AREA_HEIGHT);
                root.addChild("area_app_list", appArea);
                {
                    var apps = new ArrayList<>(APP_LIST);
                    int totalAppsToCreate = apps.size();

                    for (int i = 0; i < totalAppsToCreate; i++) {
                        var app = apps.get(i);
                        int row = i / APP_COLS;
                        int col = i % APP_COLS;
                        float currentAppX = APP_AREA_PADDING + col * (APP_WIDGET_WIDTH + APP_HORIZONTAL_SPACING);
                        float currentAppY = APP_AREA_PADDING + row * (APP_WIDGET_HEIGHT + APP_VERTICAL_SPACING);

                        var appButton = getAppWidget(app);
                        appButton.setX(currentAppX);
                        appButton.setY(currentAppY);
                        appArea.addChild("app_" + app.getName(), appButton);
                    }
                }
                var appAreaBar = new ScrollBarWidget(appArea,
                        appArea.getX() + appArea.getWidth(), appArea.getY(), APP_AREA_BAR_WIDTH, appArea.getHeight(), Orientation.VERTICAL);
                appAreaBar.setThumbColor(0x20AAAAAA);
                appAreaBar.setTrackColor(0x10202020);
                root.addChild("bar_area_app_list", appAreaBar);
            }
        }
        var appArea = new LayeredPanelWidget(0, 0, 0, 0);
        rootContainer.addChild("area_app", appArea);

        var cursorWidget = new CursorWidget(CURSOR_WIDGET_SIZE, CURSOR_WIDGET_SIZE);
        cursorWidget.setEnabled(false);
        rootContainer.addChild("cursor", cursorWidget);
        cursorWidget.setZ(99);
    }

    public static void toggle() {
        ClientUtil.playDownSound();
        if (Minecraft.getInstance().screen != null) {
            active = false;
        } else {
            active = !active;
        }
        var window = Minecraft.getInstance().getWindow();
        if (active) {
            if (AbilitySystemClient.isActiveHUD()) {
                AbilitySystemClient.setActiveHUD(false);
            }
            isFirstMove = true;
            var m = Minecraft.getInstance().mouseHandler;
            xpos = m.xpos() * (double) window.getGuiScaledWidth() / (double) window.getScreenWidth();
            ypos = m.ypos() * (double) window.getGuiScaledHeight() / (double) window.getScreenHeight();
            initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
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
        updateParametersFromConfig();

        InputSystem.addKeyBinding(KEY_NAME_TOGGLE, config.getKeyBinding(KEY_NAME_TOGGLE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_RIGHT_ALT)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), DataTerminalHUD::toggle);
    }

    private static LayeredPanelWidget getAppWidget(App app) {
        var appPanel = new LayeredPanelWidget(0, 0, APP_WIDGET_WIDTH, APP_WIDGET_HEIGHT);

        var appIconWidget = new AppWidget(
                (APP_WIDGET_WIDTH - APP_ICON_SIZE) / 2,
                0,
                app.getIcon(), app.onClick());
        appPanel.addChild("app_icon", appIconWidget);

        var nameLabel = new AutoScaleLabelWidget(app.getName(), APP_NAME_LABEL_OFFSET_X, APP_ICON_SIZE, APP_WIDGET_WIDTH, true);
        nameLabel.scale = 0.85f;
        nameLabel.dropShadow = false;

        appPanel.addChild("name_label", nameLabel);

        return appPanel;
    }

    public static void registerApp(App app) {
        APP_LIST.add(app);
    }

    @SubscribeEvent
    public static void onMouseButton(MouseButtonEvent event) {
        if (active && Minecraft.getInstance().screen == null) {
            InputSystem.currentMouseButton = event.button;
            InputSystem.currentMouseAction = event.action;
            InputSystem.currentMouseModifier = event.modifiers;
            if (event.action == GLFW_PRESS) {
                rootContainer.mousePressed(xpos, ypos, event.button);
            } else {
                rootContainer.mouseReleased(xpos, ypos, event.button);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseMove(MouseMoveEvent event) {
        var mouseHandler = Minecraft.getInstance().mouseHandler;

        if (active && Minecraft.getInstance().screen == null) {
            var window = Minecraft.getInstance().getWindow();
            if (isFirstMove) {
                lastRawMouseX = event.xpos;
                lastRawMouseY = event.ypos;
                isFirstMove = false;
            }

            double deltaRawX = event.xpos - lastRawMouseX;
            double deltaRawY = event.ypos - lastRawMouseY;

            double guiScale = window.getGuiScale();
            double deltaGuiX = deltaRawX * config.mouseSensitivity / guiScale;
            double deltaGuiY = deltaRawY * config.mouseSensitivity / guiScale;

            xpos += deltaGuiX;
            ypos += deltaGuiY;

            var mainPanel = rootContainer.getChildren().get("main");
            if (mainPanel != null) {
                float minX = mainPanel.getAbsoluteX();
                float minY = mainPanel.getAbsoluteY();
                float maxX = minX + mainPanel.getWidth();
                float maxY = minY + mainPanel.getHeight();

                var appAreaPanel = rootContainer.getChildren().get("area_app");
                if (appAreaPanel != null && appAreaPanel.getWidth() > 0 && appAreaPanel.getHeight() > 0) {
                    minX = Math.min(minX, appAreaPanel.getAbsoluteX());
                    minY = Math.min(minY, appAreaPanel.getAbsoluteY());
                    maxX = Math.max(maxX, appAreaPanel.getAbsoluteX() + appAreaPanel.getWidth());
                    maxY = Math.max(maxY, appAreaPanel.getAbsoluteY() + appAreaPanel.getHeight());
                }

                xpos = MathUtil.clamp(xpos, minX, maxX);
                ypos = MathUtil.clamp(ypos, minY, maxY);
            }

            rootContainer.mouseMoved(xpos, ypos);

            if (InputSystem.currentMouseAction == GLFW_PRESS || InputSystem.currentMouseAction == GLFW_REPEAT) {
                rootContainer.mouseDragged(xpos, ypos, InputSystem.currentMouseButton, deltaGuiX, deltaGuiY);
            }

            double newRawMouseX = xpos * guiScale;
            double newRawMouseY = ypos * guiScale;

            lastRawMouseX = newRawMouseX;
            lastRawMouseY = newRawMouseY;

            mouseHandler.xpos = newRawMouseX;
            mouseHandler.ypos = newRawMouseY;

            GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), newRawMouseX, newRawMouseY);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(MouseScrollEvent event) {
        if (active && Minecraft.getInstance().screen == null) {
            double yOffset = event.yOffset;
            var options = Minecraft.getInstance().options;
            boolean discreteMouseScroll = options.discreteMouseScroll().get();
            double mouseWheelSensitivity = options.mouseWheelSensitivity().get();
            double d0 = (discreteMouseScroll ? Math.signum(yOffset) : yOffset) * mouseWheelSensitivity;
            rootContainer.mouseScrolled(xpos, ypos, d0);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(KeyEvent event) {
        if (active && Minecraft.getInstance().screen == null) {
            int key = event.key;
            int scanCode = event.scanCode;
            var options = Minecraft.getInstance().options;
            var keyLeft = options.keyLeft;
            var keyRight = options.keyRight;
            var keyUp = options.keyUp;
            var keyDown = options.keyDown;
            var keyJump = options.keyJump;
            var keyShift = options.keyShift;
            var keySprint = options.keySprint;
            var keyHotbarSlots = options.keyHotbarSlots;

            boolean keyHotbar = false;

            for (var keyHotbarSlot : keyHotbarSlots) {
                if (!keyHotbar) {
                    keyHotbar = keyHotbarSlot.matches(key, scanCode);
                }
            }

            if (!(
                    keyLeft.matches(key, scanCode)
                            || keyRight.matches(key, scanCode)
                            || keyUp.matches(key, scanCode)
                            || keyDown.matches(key, scanCode)
                            || keyShift.matches(key, scanCode)
                            || keySprint.matches(key, scanCode)
                            || keyJump.matches(key, scanCode)
                            || keyHotbar
            )
            ) {
                if (event.action == GLFW.GLFW_RELEASE) {
                    toggle();
                }
                event.setCanceled(true);
            }
        }
        if (event.action == GLFW.GLFW_RELEASE) {
            rootContainer.keyPressed(event.key, event.scanCode, event.modifiers);
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
            var player = Minecraft.getInstance().player;
            if (playerNameLabel != null && player != null) {
                String name = player.getGameProfile().getName();
                var font = Minecraft.getInstance().font;
                playerNameLabel.setX(WIDTH - font.width(name) - INFO_BAR_PLAYER_NAME_PADDING_X);
                playerNameLabel.value = name;
            }
        }
    }

    public static class Config extends KeyBindingConfig {
        @SerializedName("blurRadius")
        public float blurRadius = 10.0f;
        @SerializedName("enableBlur")
        public boolean enableBlur = true;
        @SerializedName("mouseSensitivity")
        public float mouseSensitivity = 1.0f;

        @SerializedName("infoBarHeight")
        public float infoBarHeight = 27f;
        @SerializedName("paddingGeneral")
        public float paddingGeneral = 7f;
        @SerializedName("paddingSmall")
        public float paddingSmall = 3f;
        @SerializedName("appIconSize")
        public float appIconSize = 40f;
        @SerializedName("appTextHeight")
        public float appTextHeight = 7f;
        @SerializedName("iconTextSpacing")
        public float iconTextSpacing = 2f;
        @SerializedName("appWidgetWidth")
        public float appWidgetWidth = 44f;
        @SerializedName("appCols")
        public int appCols = 3;
        @SerializedName("visibleAppRows")
        public int visibleAppRows = 3;

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
        RenderType getIcon();

        String getName();

        Runnable onClick();
    }

    public static void setAppArea(Widget widget) {
        if (rootContainer.getChildren().containsKey("main")) {
            var main = rootContainer.getChildren().get("main");
            widget.setX(main.getX() - widget.getWidth());
            widget.setY(main.getY());
            rootContainer.addChild("area_app", widget);
        }
    }

    public static final class AppWidget extends ImageButtonWidget {
        public float targetScale = 1;

        public AppWidget(float x, float y, RenderType renderType, Runnable onPress) {
            super(x, y, APP_ICON_SIZE, APP_ICON_SIZE, renderType, onPress);
            defaultHoverEffect = true;
        }

        @SuppressWarnings("SuspiciousNameCombination")
        @Override
        public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
            widthScale = MathUtil.lerpStartEndFactor(widthScale, targetScale,
                    ClientUtil.animationFactor(1));
            heightScale = widthScale;
            var oringinRenderType = renderType;
            renderType = RenderTypes.APP_BACK;
            super.render(stack, bufferSource, mouseX, mouseY, partialTick);
            renderType = oringinRenderType;
            stack.translate(APP_WIDGET_ICON_TRANSLATE_X, APP_WIDGET_ICON_TRANSLATE_Y, 1);
            width = APP_ICON_SIZE * 0.8F;
            height = APP_ICON_SIZE * 0.8F;
            super.render(stack, bufferSource, mouseX, mouseY, partialTick);
            width = APP_ICON_SIZE;
            height = APP_ICON_SIZE;
        }

        @Override
        public void mouseMoved(double mouseX, double mouseY) {
            targetScale = isHovered() ? 1.25f : 1;
            if (isHovered() != previousHoveredState && isHovered()) {
                ClientUtil.playDownSound();
            }
            super.mouseMoved(mouseX, mouseY);
        }
    }

    private DataTerminalHUD() {
    }
}