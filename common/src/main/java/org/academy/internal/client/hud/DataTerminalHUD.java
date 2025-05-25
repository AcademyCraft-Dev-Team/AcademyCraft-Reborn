package org.academy.internal.client.hud;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
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
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.api.client.config.IClientConfigActions;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.input.*;
import org.academy.api.client.renderer.hud.HUDManager;
import org.academy.api.client.renderer.hud.HUDRenderer;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.vanilla.ChangeScreenEvent;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

public class DataTerminalHUD implements HUDRenderer {
    public static final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    public static boolean active = false;
    public static double xpos;
    public static double ypos;
    public static final String CONFIG_KEY_DATA_TERMINAL = "data_terminal_hud_config";
    public static final String KEY_NAME_TOGGLE_HUD = "toggle_data_terminal_hud_action";
    public static final DataTerminalHUD INSTANCE = new DataTerminalHUD();
    public static InputSystem.InputPair keyBinding;

    private static final float APP_ICON_SIZE = 48;
    private static final float APP_TEXT_HEIGHT = 8;
    private static final float ICON_TEXT_SPACING = 2;
    private static final float APP_WIDGET_HEIGHT = APP_ICON_SIZE + ICON_TEXT_SPACING + APP_TEXT_HEIGHT;
    private static final float APP_WIDGET_WIDTH = 52;

    private static final float APP_HORIZONTAL_SPACING = 4;
    private static final float APP_VERTICAL_SPACING = 4;
    private static final float APP_AREA_PADDING = 4;
    private static final int APP_COLS = 3;
    private static final int VISIBLE_APP_ROWS = 3;

    private static final float APP_AREA_ACTUAL_WIDTH = (APP_WIDGET_WIDTH * APP_COLS) + (APP_HORIZONTAL_SPACING * (APP_COLS - 1)) + (APP_AREA_PADDING * 2);
    private static final float APP_AREA_VISIBLE_HEIGHT = (APP_WIDGET_HEIGHT * VISIBLE_APP_ROWS) + (APP_VERTICAL_SPACING * (VISIBLE_APP_ROWS - 1)) + (APP_AREA_PADDING * 2);
    private static final float APP_AREA_X = 8;
    private static final float APP_AREA_Y = 32 + 4;
    private static final float APP_AREA_BAR_WIDTH = 4;

    public static final float WIDTH = APP_AREA_X + APP_AREA_ACTUAL_WIDTH + APP_AREA_BAR_WIDTH + 8;
    public static final float HEIGHT = APP_AREA_Y + APP_AREA_VISIBLE_HEIGHT + 8;
    public static LabelWidget playerNameLabel;

    @Override
    public void render(GuiGraphics guiGraphics, float partialTick) {
        if (!active) return;
        guiGraphics.pose().pushPose();

        RenderSystem.backupProjectionMatrix();

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

        float z = -1.985f;
        float scale = (2f * Math.abs(z) * (float) Math.tan(fovY / 2f)) / guiH;
        pose.translate(0, 0, z);
        pose.scale(scale, -scale, scale);
        pose.translate(0, 0, -WIDTH * 0.5f);

        float panelX = guiW / 2 - WIDTH * 1.25f;
        float panelY = -HEIGHT * 0.5f;
        float centerX = panelX + WIDTH / 2f;
        float centerY = panelY + HEIGHT / 2f;
        float dx = (float) (xpos - centerX);
        float dy = (float) (ypos - centerY);

        pose.translate(panelX, panelY, 0);
        pose.translate(WIDTH / 2f, HEIGHT / 2f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(dx * 0.075f - 24));
        pose.mulPose(Axis.XP.rotationDegrees(-dy * 0.075f + 5));
        pose.translate(-WIDTH / 2f, -HEIGHT / 2f, 0);

        guiGraphics.pose().scale(1, 1, 0.01f);
        RenderSystem.applyModelViewMatrix();

        guiGraphics.pose().pushPose();
        rootContainer.render(guiGraphics, xpos, ypos, partialTick);
        guiGraphics.flush();
        guiGraphics.pose().popPose();

        pose.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();

        guiGraphics.pose().popPose();
    }

    public static void initGui(int width, int height) {
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);
        rootContainer.clearChildren();
        BlendQuadWidget back = new BlendQuadWidget(0, 0, WIDTH, HEIGHT);
        back.drawLine = false;
        back.alpha = 0.25f;
        rootContainer.addChild("back", back);
        PanelWidget root = new PanelWidget(0, 0, WIDTH, HEIGHT);
        root.setZ(root.getZ() + 1);
        rootContainer.addChild("root", root);
        {
            PanelWidget infoBar = new PanelWidget(0, 0, WIDTH, 32);
            root.addChild("info_bar", infoBar);
            {
                LocalPlayer player = Minecraft.getInstance().player;
                String playerName = "N/A";
                if (player != null) {
                    playerName = player.getGameProfile().getName();
                }
                Font font = Minecraft.getInstance().font;
                playerNameLabel = new LabelWidget(playerName, WIDTH - font.width(playerName) - 4, 5);
                playerNameLabel.dropShadow = false;
                infoBar.addChild("player_name", playerNameLabel);

                FillWidget spiltLine = new FillWidget(8, 32, WIDTH - 16, 1, 0x30FFFFFF);
                infoBar.addChild("spilt_line", spiltLine);
            }

            SmoothScrollPanelWidget appArea = new SmoothScrollPanelWidget(APP_AREA_X, APP_AREA_Y, APP_AREA_ACTUAL_WIDTH, APP_AREA_VISIBLE_HEIGHT);
            root.addChild("area_app", appArea);
            {
                int totalAppsToCreate = 14;

                for (int i = 0; i < totalAppsToCreate; i++) {
                    int row = i / APP_COLS;
                    int col = i % APP_COLS;
                    float currentAppX = APP_AREA_PADDING + col * (APP_WIDGET_WIDTH + APP_HORIZONTAL_SPACING);
                    float currentAppY = APP_AREA_PADDING + row * (APP_WIDGET_HEIGHT + APP_VERTICAL_SPACING);

                    int finalI = i;
                    PanelWidget appButton = getAppWidget("App " + i, () -> {
                        AcademyCraft.LOGGER.info("App {} clicked", finalI);
                    });
                    appButton.setX(currentAppX);
                    appButton.setY(currentAppY);
                    appArea.addChild("app_" + i, appButton);
                }
            }
            VerticalScrollBarWidget appAreaBar = new VerticalScrollBarWidget(appArea,
                    appArea.getX() + appArea.getWidth(), appArea.getY(), APP_AREA_BAR_WIDTH, appArea.getHeight());
            appAreaBar.showBackground = false;
            root.addChild("bar_area_app", appAreaBar);
        }

        CursorWidget cursorWidget = new CursorWidget(8, 8);
        cursorWidget.setEnabled(false);
        cursorWidget.setZ(99);
        rootContainer.addChild("cursor", cursorWidget);
    }

    public static void toggle() {
        MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;
        if (Minecraft.getInstance().screen != null) {
            active = false;
        } else {
            active = !active;
        }
        Window window = Minecraft.getInstance().getWindow();
        if (active) {
            if (mouseHandler.isMouseGrabbed()) mouseHandler.releaseMouse();
            GLFW.glfwSetInputMode(window.getWindow(), GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
            double guiScale = window.getGuiScale();
            GLFW.glfwSetCursorPos(window.getWindow(), (window.getGuiScaledWidth() - WIDTH * 1.25 / 2) * guiScale, window.getGuiScaledHeight() * 0.5 * guiScale);
            initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        } else {
            if (!mouseHandler.isMouseGrabbed()) {
                if (Minecraft.getInstance().screen != null) {
                    GLFW.glfwSetInputMode(window.getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                }
                mouseHandler.grabMouse();
            }
        }
    }

    public static void init() {
        HUDManager.registerHUDRenderer(INSTANCE);
        AcademyCraft.EVENT_BUS.register(DataTerminalHUD.class);
        AcademyCraftClientConfig.registerConfigActions(CONFIG_KEY_DATA_TERMINAL, new DataTerminalHUDConfigData());
        DataTerminalHUDConfigData configData = AcademyCraftClient.CLIENT_CONFIG.getConfig(CONFIG_KEY_DATA_TERMINAL, DataTerminalHUDConfigData.class);
        if (configData == null) {
            configData = new DataTerminalHUDConfigData();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(CONFIG_KEY_DATA_TERMINAL, configData);
        }

        keyBinding = configData.getKeyBinding(KEY_NAME_TOGGLE_HUD,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_RIGHT_ALT)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        );
        InputSystem.addKeyBinding(KEY_NAME_TOGGLE_HUD, keyBinding, DataTerminalHUD::toggle);
    }

    public static PanelWidget getAppWidget(String appName, Runnable onPress) {
        PanelWidget appPanel = new PanelWidget(0, 0, APP_WIDGET_WIDTH, APP_WIDGET_HEIGHT);

        ImageButtonWidget appIconButton = new ImageButtonWidget(
                (APP_WIDGET_WIDTH - APP_ICON_SIZE) / 2,
                0,
                APP_ICON_SIZE,
                APP_ICON_SIZE,
                TextureResources.RenderTypes.RENDER_TYPE_BLEND_QUAD,
                onPress
        );
        appIconButton.hoverEffect = true;
        appIconButton.red = 0.3f + (float) Math.random() * 0.7f;
        appIconButton.green = 0.3f + (float) Math.random() * 0.7f;
        appIconButton.blue = 0.3f + (float) Math.random() * 0.7f;
        appPanel.addChild("icon_button", appIconButton);

        Font font = Minecraft.getInstance().font;
        LabelWidget nameLabel = new LabelWidget(appName, 0, APP_ICON_SIZE + ICON_TEXT_SPACING);
        nameLabel.dropShadow = false;

        float textWidthScaled = font.width(appName) * nameLabel.scale;
        nameLabel.setX((APP_WIDGET_WIDTH - textWidthScaled) / 2);
        appPanel.addChild("name_label", nameLabel);

        return appPanel;
    }

    @SubscribeEvent
    public static void onMouseButton(MouseButtonEvent event) {
        if (active && Minecraft.getInstance().screen == null) {
            InputSystem.currentMouseButton = event.button;
            InputSystem.currentMouseAction = event.action;
            InputSystem.currentMouseModifier = event.modifiers;
            if (event.action == GLFW_PRESS) {
                rootContainer.mouseClicked(xpos, ypos, event.button);
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
            float guiScale = (float) window.getGuiScale();

            float screenWidthGui = window.getGuiScaledWidth();
            float screenHeightGui = window.getGuiScaledHeight();
            float hudScreenX = screenWidthGui - 1.25f * WIDTH;
            float hudScreenY = (screenHeightGui - HEIGHT) / 2f;

            double mouseGuiX = event.xpos / guiScale;
            double mouseGuiY = event.ypos / guiScale;

            double relX = mouseGuiX - hudScreenX;
            double relY = mouseGuiY - hudScreenY;

            xpos = Math.max(0, Math.min(WIDTH, relX));
            ypos = Math.max(0, Math.min(HEIGHT, relY));

            double newX = hudScreenX + DataTerminalHUD.xpos;
            double newY = hudScreenY + DataTerminalHUD.ypos;

            rootContainer.mouseMoved(xpos, ypos);

            if (InputSystem.currentMouseAction == GLFW_PRESS || InputSystem.currentMouseAction == GLFW_REPEAT) {
                double f = (xpos - mouseHandler.xpos) * window.getGuiScaledWidth() / window.getScreenWidth();
                double g = (ypos - mouseHandler.ypos) * window.getGuiScaledHeight() / window.getScreenHeight();
                rootContainer.mouseDragged(xpos, ypos, InputSystem.currentMouseButton, f, g);
            }

            mouseHandler.xpos = newX * guiScale;
            mouseHandler.ypos = newY * guiScale;

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

            if (!(keyLeft.matches(key, scanCode)
                    || keyRight.matches(key, scanCode)
                    || keyUp.matches(key, scanCode)
                    || keyDown.matches(key, scanCode)
                    || keyJump.matches(key, scanCode))) {
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
        initGui(event.width, event.height);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent event) {
        if (active) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (playerNameLabel != null && player != null) {
                String name = player.getGameProfile().getName();
                Font font = Minecraft.getInstance().font;
                playerNameLabel.setX(WIDTH - font.width(name) - 4);
                playerNameLabel.value = name;
            }
        }
    }

    @SubscribeEvent
    public static void onScreenChange(ChangeScreenEvent.Post event) {
        if (active && event.currentScreen instanceof PauseScreen)
            toggle();
    }

    private DataTerminalHUD() {
    }

    public static class DataTerminalHUDConfigData implements IClientConfigActions<DataTerminalHUDConfigData> {
        @SerializedName("keyBindings")
        private final Map<String, InputSystem.InputPair> keyBindings = new HashMap<>();

        public InputSystem.InputPair getKeyBinding(String name, InputSystem.InputPair defaultConfig) {
            if (!keyBindings.containsKey(name)) {
                setKeyBinding(name, defaultConfig);
            }
            return keyBindings.get(name);
        }

        public void setKeyBinding(String name, InputSystem.InputPair keyBinding) {
            this.keyBindings.put(name, keyBinding);
        }

        @Override
        public @NotNull DataTerminalHUDConfigData deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
            return gson.fromJson(jsonElement, DataTerminalHUDConfigData.class);
        }

        @Override
        public @NotNull JsonElement serialize(@NotNull DataTerminalHUDConfigData configInstance, @NotNull Gson gson) {
            return gson.toJsonTree(configInstance);
        }

        @Override
        public @NotNull DataTerminalHUDConfigData getDefaultConfig() {
            return new DataTerminalHUDConfigData();
        }

        @Override
        public @NotNull Class<DataTerminalHUDConfigData> getConfigClass() {
            return DataTerminalHUDConfigData.class;
        }
    }
}