package org.academy.internal.client.hud;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.input.*;
import org.academy.api.client.renderer.hud.HUDManager;
import org.academy.api.client.renderer.hud.HUDRenderer;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

public class DataTerminalHUD implements HUDRenderer {
    public static final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    public static boolean active = false;
    public static double xpos;
    public static double ypos;
    public static final String KEY_NAME = "data_terminal_hud";
    public static final DataTerminalHUD INSTANCE = new DataTerminalHUD();
    public static InputSystem.InputPair keyBinding;
    public static final float WIDTH = 150;
    public static final float HEIGHT = 187.5f;

    @Override
    public void render(GuiGraphics guiGraphics, float partialTick) {
        if (!active) return;

        RenderSystem.backupProjectionMatrix();

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        float winW = window.getWidth(), winH = window.getHeight();
        float guiW = window.getGuiScaledWidth(), guiH = window.getGuiScaledHeight();
        float aspect = winW / winH;
        float fov = (float) mc.options.fov().get();
        float fovY = 2f * (float) Math.atan(Math.tan(Math.toRadians(fov) / 2f) / aspect);

        RenderSystem.setProjectionMatrix(new Matrix4f().perspective(fovY, aspect, 0.1f, 1000f), VertexSorting.DISTANCE_TO_ORIGIN);

        PoseStack pose = RenderSystem.getModelViewStack();
        pose.pushPose();
        pose.setIdentity();

        float z = -2.8f;
        float scale = (2f * Math.abs(z) * (float) Math.tan(fovY / 2f)) / guiH;
        pose.translate(0, 0, z * 1.125f);
        pose.scale(scale, -scale, scale);

        float panelX = guiW / 2 - WIDTH * 1.25f;
        float panelY = -HEIGHT * 0.5f;
        float centerX = panelX + WIDTH / 2f;
        float centerY = panelY + HEIGHT / 2f;
        float dx = (float) (xpos - centerX);
        float dy = (float) (ypos - centerY);

        pose.translate(panelX, panelY, 0);
        pose.translate(WIDTH / 2f, HEIGHT / 2f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(dx * 0.075f - 8));
        pose.mulPose(Axis.XP.rotationDegrees(-dy * 0.075f));
        pose.translate(-WIDTH / 2f, -HEIGHT / 2f, 0);

        RenderSystem.applyModelViewMatrix();

        guiGraphics.pose().pushPose();
        rootContainer.render(guiGraphics, xpos, ypos, partialTick);
        guiGraphics.flush();
        guiGraphics.pose().popPose();

        pose.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    public static void initGui(int width, int height) {
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);
        rootContainer.clearChildren();
        BlendQuadWidget back = new BlendQuadWidget(0, 0, WIDTH, HEIGHT);
        back.drawLine  = false;
        back.alpha = 0.25f;
        rootContainer.addChild("back", back);
        CursorWidget cursorWidget = new CursorWidget(8, 8);
        cursorWidget.setZ(0.01f);
        rootContainer.addChild("cursor", cursorWidget);
        ImageButtonWidget buttonWidget = new ImageButtonWidget(10, 10, 32, 16, TextureResources.RenderTypes.RENDER_TYPE_BUTTON, null);
        buttonWidget.setZ(0.02f);
        rootContainer.addChild("button", buttonWidget);
    }

    public static void toggle() {
        MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;
        active = !active;
        if (active) {
            mouseHandler.releaseMouse();
            Window window = Minecraft.getInstance().getWindow();
            GLFW.glfwSetInputMode(window.getWindow(), GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
            initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        } else {
            mouseHandler.grabMouse();
        }
    }

    public static void init() {
        HUDManager.registerHUDRenderer(INSTANCE);
        AcademyCraft.EVENT_BUS.register(DataTerminalHUD.class);
        keyBinding = AcademyCraftClient.CLIENT_CONFIG.getKey(KEY_NAME,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_RIGHT_ALT)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        );
        InputSystem.addKeyBinding(KEY_NAME, keyBinding, DataTerminalHUD::toggle);
    }

    @SubscribeEvent
    public static void onMouseButton(MouseButtonEvent event) {
        if (active) {
            if (event.button == GLFW_MOUSE_BUTTON_1) {
                rootContainer.mouseClicked(xpos, ypos, event.action);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseMove(MouseMoveEvent event) {
        MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;

        if (active) {
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

            mouseHandler.xpos = newX * guiScale;
            mouseHandler.ypos = newY * guiScale;

            GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), mouseHandler.xpos, mouseHandler.ypos);

            rootContainer.mouseMoved(xpos, ypos);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(MouseScrollEvent event) {
        if (active) {
            rootContainer.mouseScrolled(xpos, ypos, event.yOffset);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(KeyEvent event) {
        if (active) {
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

    private DataTerminalHUD() {
    }
}