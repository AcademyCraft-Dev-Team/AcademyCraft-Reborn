package org.academy.api.client.hud;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.input.*;
import org.academy.api.client.renderer.RenderTypes;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.vanilla.ChangeScreenEvent;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.api.common.config.IConfigAction;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.*;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;

public final class DataTerminalHUD implements HUDRenderer {
    private static final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    private static boolean active = false;
    private static double xpos;
    private static double ypos;
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
    private static final PostChain postChain;
    private static final List<App> APP_LIST = new ArrayList<>();

    static {
        try {
            postChain = new PostChain(Minecraft.getInstance().getTextureManager(), Minecraft.getInstance().getResourceManager(), Minecraft.getInstance().getMainRenderTarget(), new ResourceLocation("shaders/post/blur_mask.json"));
            postChain.resize(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void updateParametersFromConfig() {
        INFO_BAR_HEIGHT = config.infoBarHeight;
        float PADDING_GENERAL = config.paddingGeneral;
        float PADDING_SMALL = config.paddingSmall;
        APP_ICON_SIZE = config.appIconSize;
        float APP_TEXT_HEIGHT = config.appTextHeight;
        float ICON_TEXT_SPACING = config.iconTextSpacing;
        APP_WIDGET_HEIGHT = APP_ICON_SIZE + ICON_TEXT_SPACING + APP_TEXT_HEIGHT;
        APP_WIDGET_WIDTH = config.appWidgetWidth;
        APP_HORIZONTAL_SPACING = PADDING_SMALL;
        APP_VERTICAL_SPACING = PADDING_SMALL;
        APP_AREA_PADDING = PADDING_SMALL;
        APP_COLS = config.appCols;
        int VISIBLE_APP_ROWS = config.visibleAppRows;
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
    public void render(GuiGraphics guiGraphics, float partialTick) {
        if (active) {
            for (PostPass pass : postChain.passes) {
                pass.getEffect().getUniform("Radius").set(config.blurRadius);
            }
            guiGraphics.pose().pushPose();
            RenderSystem.backupProjectionMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            Minecraft mc = Minecraft.getInstance();
            Window window = mc.getWindow();
            float winW = window.getWidth(), winH = window.getHeight();
            float guiW = window.getGuiScaledWidth(), guiH = window.getGuiScaledHeight();
            float aspect = winW / winH;
            float fov = 80;
            float fovY = 2f * (float) Math.atan(Math.tan(Math.toRadians(fov) / 2f) / aspect);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective(fovY, aspect, 1, 1000), VertexSorting.DISTANCE_TO_ORIGIN);
            PoseStack pose = RenderSystem.getModelViewStack();
            pose.pushPose();
            pose.setIdentity();
            float z = -2.5125f;
            float scale = (2f * Math.abs(z) * (float) Math.tan(fovY / 2f)) / guiH;
            pose.translate(0, 0, z);
            pose.scale(scale, -scale, scale);
            float centerX = guiW - WIDTH / 2f;
            float centerY = guiH / 2f;
            float dx = (float) (xpos - centerX);
            float dy = (float) (ypos - centerY);
            pose.rotateAround(Axis.YP.rotationDegrees(dx * 0.035f - 5), guiW / 2 - WIDTH * 1.25f + WIDTH / 2, 0, 0);
            pose.rotateAround(Axis.XP.rotationDegrees(-dy * 0.035f + 2), 0, 0, 0);
            pose.translate(-guiW / 2, -guiH / 2, 0);
            guiGraphics.pose().scale(1, 1, 0.035f);
            RenderSystem.applyModelViewMatrix();
            guiGraphics.pose().pushPose();
            {
                guiGraphics.bufferSource().endBatch(RenderType.gui());
                RenderTarget maskInput = postChain.getTempTarget("maskInput");
                maskInput.clear(false);
                maskInput.bindWrite(false);
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(guiW - WIDTH * 1.25f, (guiH - HEIGHT) / 2, 0);
                RenderUtil.fill(guiGraphics.pose().last().pose(), 0, 0, WIDTH, HEIGHT, 0XFFFFFFFF, guiGraphics.bufferSource());
                guiGraphics.pose().popPose();
                if (rootContainer.getChildren().containsKey("area_app")) {
                    guiGraphics.pose().pushPose();
                    Widget widget = rootContainer.getChildren().get("area_app");
                    guiGraphics.pose().translate(widget.getAbsoluteX(), widget.getAbsoluteY(), 0);
                    RenderUtil.fill(guiGraphics.pose().last().pose(), 0, 0, widget.getWidth(), widget.getHeight(), 0XFFFFFFFF, guiGraphics.bufferSource());
                    guiGraphics.pose().popPose();
                }
                guiGraphics.bufferSource().endBatch(RenderType.gui());
                postChain.process(partialTick);
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            }
            rootContainer.render(guiGraphics, xpos, ypos, partialTick);
            guiGraphics.pose().popPose();
            guiGraphics.flush();
            pose.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            guiGraphics.pose().popPose();
        }
    }

    public static void initGui(int width, int height) {
        updateParametersFromConfig();
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);
        rootContainer.clearChildren();
        PanelWidget main = new PanelWidget(width - WIDTH * 1.25f, (height - HEIGHT) / 2, WIDTH, HEIGHT);
        rootContainer.addChild("main", main);
        {
            BlendQuadWidget back = new BlendQuadWidget(0, 0, WIDTH, HEIGHT);
            back.drawLine = false;
            back.alpha = 0.25f;
            main.addChild("back", back);
            PanelWidget root = new PanelWidget(0, 0, WIDTH, HEIGHT);
            DynamicGeometricBackgroundWidget dynamicGeometricBack = new DynamicGeometricBackgroundWidget(
                    0, 0, WIDTH, HEIGHT,
                    48, 1, 64,
                    0x20FFFFFF, true, 32);
            main.addChild("back_dynamic", dynamicGeometricBack);
            main.addChild("root", root);
            {
                PanelWidget infoBar = new PanelWidget(0, 0, WIDTH, INFO_BAR_HEIGHT);
                root.addChild("info_bar", infoBar);
                {
                    ImageWidget icon = new ImageWidget(ICON_X_Y_PADDING_INFO_BAR, ICON_X_Y_PADDING_INFO_BAR, ICON_SIZE_INFO_BAR, ICON_SIZE_INFO_BAR,
                            RenderTypes.RENDER_TYPE_ICON_DATA_TERMINAL);
                    infoBar.addChild("icon", icon);

                    LocalPlayer player = Minecraft.getInstance().player;
                    String playerName = "N/A";
                    if (player != null) {
                        playerName = player.getGameProfile().getName();
                    }
                    Font font = Minecraft.getInstance().font;
                    playerNameLabel = new LabelWidget(playerName, WIDTH - font.width(playerName) - INFO_BAR_PLAYER_NAME_PADDING_X, PLAYER_NAME_LABEL_Y_INFO_BAR);
                    playerNameLabel.dropShadow = false;
                    infoBar.addChild("player_name", playerNameLabel);

                    FillWidget spiltLine = new FillWidget(SPLIT_LINE_X_PADDING_INFO_BAR, INFO_BAR_HEIGHT, WIDTH - SPLIT_LINE_WIDTH_REDUCTION_INFO_BAR, 1, 0x30FFFFFF);
                    infoBar.addChild("spilt_line", spiltLine);
                }

                SmoothScrollPanelWidget appArea = new SmoothScrollPanelWidget(APP_AREA_X, APP_AREA_Y, APP_AREA_WIDTH, APP_AREA_HEIGHT);
                root.addChild("area_app_list", appArea);
                {
                    List<App> apps = new ArrayList<>(APP_LIST);
                    int totalAppsToCreate = apps.size();

                    for (int i = 0; i < totalAppsToCreate; i++) {
                        App app = apps.get(i);
                        int row = i / APP_COLS;
                        int col = i % APP_COLS;
                        float currentAppX = APP_AREA_PADDING + col * (APP_WIDGET_WIDTH + APP_HORIZONTAL_SPACING);
                        float currentAppY = APP_AREA_PADDING + row * (APP_WIDGET_HEIGHT + APP_VERTICAL_SPACING);

                        PanelWidget appButton = getAppWidget(app);
                        appButton.setX(currentAppX);
                        appButton.setY(currentAppY);
                        appArea.addChild("app_" + app.getName(), appButton);
                    }
                }
                VerticalScrollBarWidget appAreaBar = new VerticalScrollBarWidget(appArea,
                        appArea.getX() + appArea.getWidth(), appArea.getY(), APP_AREA_BAR_WIDTH, appArea.getHeight());
                appAreaBar.setThumbColor(0x50AAAAAA);
                appAreaBar.setTrackColor(0x70202020);
                appAreaBar.showBackground = false;
                root.addChild("bar_area_app_list", appAreaBar);
            }
        }
        PanelWidget appArea = new PanelWidget(0, 0, 0, 0);
        rootContainer.addChild("area_app", appArea);

        CursorWidget cursorWidget = new CursorWidget(CURSOR_WIDGET_SIZE, CURSOR_WIDGET_SIZE);
        cursorWidget.setEnabled(false);
        cursorWidget.setZ(99);
        rootContainer.addChild("cursor", cursorWidget);
    }

    public static void toggle() {
        ClientUtil.playDownSound();
        if (Minecraft.getInstance().screen != null) {
            active = false;
        } else {
            active = !active;
        }
        Window window = Minecraft.getInstance().getWindow();
        if (active) {
            if (AbilitySystemClient.isActiveHUD()) {
                AbilitySystemClient.setActiveHUD(false);
            }
            initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
    }

    public static void init() {
        HUDManager.registerHUDRenderer(INSTANCE);
        AcademyCraft.EVENT_BUS.register(DataTerminalHUD.class);
        AcademyCraftConfig.registerConfigActions(CONFIG_KEY_DATA_TERMINAL, Config.Action.INSTANCE);
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

    private static PanelWidget getAppWidget(App app) {
        PanelWidget appPanel = new PanelWidget(0, 0, APP_WIDGET_WIDTH, APP_WIDGET_HEIGHT);

        AppWidget appIconWidget = new AppWidget(
                (APP_WIDGET_WIDTH - APP_ICON_SIZE) / 2,
                0,
                app.getIcon(), app.onClick());
        appPanel.addChild("app_icon", appIconWidget);

        AutoScaleLabelWidget nameLabel = new AutoScaleLabelWidget(app.getName(), APP_NAME_LABEL_OFFSET_X, APP_ICON_SIZE, APP_WIDGET_WIDTH, true);
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
        MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;

        if (active && Minecraft.getInstance().screen == null) {
            Window window = Minecraft.getInstance().getWindow();

            float screenWidthGui = window.getGuiScaledWidth();
            float screenHeightGui = window.getGuiScaledHeight();

            float guiWidthScale = (float) window.getGuiScaledWidth() / screenWidthGui;
            float guiHeightScale = (float) window.getGuiScaledHeight() / screenHeightGui;

            double rawMouseX = event.xpos * guiWidthScale;
            double rawMouseY = event.ypos * guiHeightScale;

            double mouseGuiX = rawMouseX;
            double mouseGuiY = rawMouseY;

            Widget mainPanel = rootContainer.getChildren().get("main");
            if (mainPanel != null) {
                float minX = mainPanel.getAbsoluteX();
                float minY = mainPanel.getAbsoluteY();
                float maxX = minX + mainPanel.getWidth();
                float maxY = minY + mainPanel.getHeight();

                Widget appAreaPanel = rootContainer.getChildren().get("area_app");
                if (appAreaPanel != null && appAreaPanel.getWidth() > 0 && appAreaPanel.getHeight() > 0) {
                    minX = Math.min(minX, appAreaPanel.getAbsoluteX());
                    minY = Math.min(minY, appAreaPanel.getAbsoluteY());
                    maxX = Math.max(maxX, appAreaPanel.getAbsoluteX() + appAreaPanel.getWidth());
                    maxY = Math.max(maxY, appAreaPanel.getAbsoluteY() + appAreaPanel.getHeight());
                }

                mouseGuiX = MathUtil.clamp(rawMouseX, minX, maxX);
                mouseGuiY = MathUtil.clamp(rawMouseY, minY, maxY);
            }

            xpos = mouseGuiX;
            ypos = mouseGuiY;

            rootContainer.mouseMoved(xpos, ypos);

            if (InputSystem.currentMouseAction == GLFW_PRESS || InputSystem.currentMouseAction == GLFW_REPEAT) {
                double f = (xpos - mouseHandler.xpos * guiWidthScale);
                double g = (ypos - mouseHandler.ypos * guiHeightScale);
                rootContainer.mouseDragged(xpos, ypos, InputSystem.currentMouseButton, f, g);
            }

            mouseHandler.xpos = xpos / guiWidthScale;
            mouseHandler.ypos = ypos / guiHeightScale;

            GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), mouseHandler.xpos, mouseHandler.ypos);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(MouseScrollEvent event) {
        if (active && Minecraft.getInstance().screen == null) {
            double yOffset = event.yOffset;
            Options options = Minecraft.getInstance().options;
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
            Options options = Minecraft.getInstance().options;
            KeyMapping keyLeft = options.keyLeft;
            KeyMapping keyRight = options.keyRight;
            KeyMapping keyUp = options.keyUp;
            KeyMapping keyDown = options.keyDown;
            KeyMapping keyJump = options.keyJump;
            KeyMapping keyShift = options.keyShift;
            KeyMapping keySprint = options.keySprint;
            KeyMapping[] keyHotbarSlots = options.keyHotbarSlots;

            boolean keyHotbar = false;

            for (KeyMapping keyHotbarSlot : keyHotbarSlots) {
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
        postChain.resize(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight());
        initGui(event.width, event.height);
    }

    @SubscribeEvent
    public static void onScreenChange(ChangeScreenEvent.Post event) {
        if (active) toggle();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent event) {
        if (active) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (playerNameLabel != null && player != null) {
                String name = player.getGameProfile().getName();
                Font font = Minecraft.getInstance().font;
                playerNameLabel.setX(WIDTH - font.width(name) - INFO_BAR_PLAYER_NAME_PADDING_X);
                playerNameLabel.value = name;
            }
        }
    }

    public static class Config extends KeyBindingConfig {
        @SerializedName("blurRadius")
        public float blurRadius = 10.0f;

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

        public static final class Action implements IConfigAction<Config> {
            public static final IConfigAction<Config> INSTANCE = new Action();

            private Action() {
            }

            @Override
            public @NotNull DataTerminalHUD.Config deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                return gson.fromJson(jsonElement, Config.class);
            }

            @Override
            public @NotNull JsonElement serialize(@NotNull DataTerminalHUD.Config configInstance, @NotNull Gson gson) {
                return gson.toJsonTree(configInstance);
            }

            @Override
            public @NotNull DataTerminalHUD.Config getDefaultConfig() {
                return new Config();
            }

            @Override
            public @NotNull Class<Config> getConfigClass() {
                return Config.class;
            }
        }
    }

    public interface
    App {
        RenderType getIcon();

        String getName();

        Runnable onClick();
    }

    public static void setAppArea(Widget widget) {
        if (rootContainer.getChildren().containsKey("main")) {
            Widget main = rootContainer.getChildren().get("main");
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
        public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
            widthScale = MathUtil.lerpStartEndFactor(widthScale, targetScale,
                    ClientUtil.animationFactor(1));
            heightScale = widthScale;
            RenderType oringinRenderType = renderType;
            renderType = RenderTypes.RENDER_TYPE_APP_BACK;
            super.render(graphics, mouseX, mouseY, partialTick);
            renderType = oringinRenderType;
            graphics.pose().translate(APP_WIDGET_ICON_TRANSLATE_X, APP_WIDGET_ICON_TRANSLATE_Y, 1);
            width = APP_ICON_SIZE * 0.8F;
            height = APP_ICON_SIZE * 0.8F;
            super.render(graphics, mouseX, mouseY, partialTick);
            width = APP_ICON_SIZE;
            height = APP_ICON_SIZE;
        }

        @Override
        public void mouseMoved(double mouseX, double mouseY) {
            targetScale = isHovered() ? 1.25f : 1;
            if (isHovered() != this.previousHoveredState && isHovered()) {
                ClientUtil.playDownSound();
            }
            super.mouseMoved(mouseX, mouseY);
        }
    }

    private DataTerminalHUD() {
    }
}