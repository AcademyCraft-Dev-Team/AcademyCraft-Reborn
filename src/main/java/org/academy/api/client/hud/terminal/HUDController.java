package org.academy.api.client.hud.terminal;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.ScrollEvent;
import org.academy.api.client.hud.HUDManager;
import org.academy.api.client.hud.HUDRenderer;
import org.academy.api.client.input.*;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;

public final class HUDController implements HUDRenderer {
    public static final HUDController INSTANCE = new HUDController();

    public static final String CONFIG_KEY = "data_terminal_hud_config";
    public static final String KEY_NAME_TOGGLE = "data_terminal_hud_config_toggle";

    private boolean active = false;

    @Nullable
    private Config config;
    @Nullable
    private UIManager uiManager;
    @Nullable
    private Renderer renderer;

    private double xpos;
    private double ypos;
    private double lastRawMouseX;
    private double lastRawMouseY;
    private boolean isFirstMove = true;
    private int currentMouseButton;
    private int currentMouseAction;

    private HUDController() {
    }

    public void init() {
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, Config.Action.INSTANCE);
        config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY);

        uiManager = new UIManager(config);
        renderer = new Renderer(config, uiManager);

        HUDManager.registerHUDRenderer(this);
        NeoForge.EVENT_BUS.register(this);

        var toggleKeys = new LinkedHashSet<Integer>();
        toggleKeys.add(GLFW.GLFW_KEY_RIGHT_ALT);
        var defaultKey = new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(toggleKeys, 0, new LinkedHashSet<>()));
        InputSystem.addKeyBinding(KEY_NAME_TOGGLE, config.getKeyBinding(KEY_NAME_TOGGLE, defaultKey), INSTANCE::toggle);
    }

    public void close() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (uiManager != null) {
            uiManager.close();
            uiManager = null;
        }
    }

    private void toggle() {
        ClientUtil.playDownSound();
        if (Minecraft.getInstance().screen != null) {
            setActive(false);
        } else {
            setActive(!active);
        }
    }

    private void setActive(boolean active) {
        if (this.active == active || uiManager == null) return;

        this.active = active;
        if (active) {
            if (AbilitySystemClient.isActiveHUD()) {
                AbilitySystemClient.setActiveHUD(false);
            }
            onActivation();
            var window = Minecraft.getInstance().getWindow();
            uiManager.initGui(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        } else {
            var window = Minecraft.getInstance().getWindow();
            var mouseHandler = Minecraft.getInstance().mouseHandler;
            GLFW.glfwSetCursorPos(window.handle(), mouseHandler.xpos(), mouseHandler.ypos());
        }
    }

    private void onActivation() {
        isFirstMove = true;
        var window = Minecraft.getInstance().getWindow();
        var x = window.getWidth() / 2d;
        var y = window.getHeight() / 2d;
        xpos = x / window.getGuiScale();
        ypos = y / window.getGuiScale();
        GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().handle(), x, y);
    }

    public boolean isActive() {
        return active;
    }

    public void resize(int width, int height) {
        if (uiManager != null) {
            uiManager.resize();
        }
        if (renderer != null) {
            renderer.resize(width, height);
        }
    }

    @Override
    public void render(double mouseX, double mouseY, float partialTick) {
        if (!active || renderer == null) return;

        renderer.render(xpos, ypos, partialTick);
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        if (isActive() && uiManager != null) {
            uiManager.tick();
        }
    }

    @SubscribeEvent
    public void onScreenChange(ScreenEvent.Opening event) {
        if (isActive()) {
            setActive(false);
        }
    }

    @SubscribeEvent
    public void onMouseButton(MouseButtonEvent event) {
        if (isActive() && Minecraft.getInstance().screen == null && uiManager != null) {
            currentMouseButton = event.button;
            currentMouseAction = event.action;
            var inputEvent = event.action == 1
                    ? MouseEvent.createPressEvent(xpos, ypos, event.button)
                    : MouseEvent.createReleaseEvent(xpos, ypos, event.button);
            uiManager.getRootContainer().dispatchEvent(inputEvent);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseMove(MouseMoveEvent event) {
        if (isActive() && Minecraft.getInstance().screen == null && uiManager != null && config != null) {
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
            xpos += deltaGuiX * config.mouseSensitivity;
            ypos += deltaGuiY * config.mouseSensitivity;
            var window = Minecraft.getInstance().getWindow();
            xpos = MathUtil.clamp(xpos, 0.0, window.getGuiScaledWidth());
            ypos = MathUtil.clamp(ypos, 0.0, window.getGuiScaledHeight());
            uiManager.getRootContainer().dispatchEvent(MouseEvent.createMoveEvent(xpos, ypos));

            if (currentMouseAction == 1 || currentMouseAction == 2) {
                uiManager.getRootContainer().dispatchEvent(MouseEvent.createDragEvent(xpos, ypos, currentMouseButton, deltaGuiX, deltaGuiY));
            }

            lastRawMouseX = event.xpos;
            lastRawMouseY = event.ypos;
            mouseHandler.xpos = event.xpos;
            mouseHandler.ypos = event.ypos;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseScroll(MouseScrollEvent event) {
        if (isActive() && Minecraft.getInstance().screen == null && uiManager != null) {
            var options = Minecraft.getInstance().options;
            var scrollAmount = (options.discreteMouseScroll().get() ? Math.signum(event.yOffset) : event.yOffset) * options.mouseWheelSensitivity().get();
            uiManager.getRootContainer().dispatchEvent(new ScrollEvent(xpos, ypos, scrollAmount));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onKey(KeyInputEvent event) {
        if (isActive() && Minecraft.getInstance().screen == null && uiManager != null) {
            var options = Minecraft.getInstance().options;
            var key = event.key;
            var scanCode = event.scanCode;
            var mcKeyEvent = new net.minecraft.client.input.KeyEvent(key, scanCode, event.modifiers);

            var isMovementKey = options.keyUp.matches(mcKeyEvent) || options.keyDown.matches(mcKeyEvent)
                    || options.keyLeft.matches(mcKeyEvent) || options.keyRight.matches(mcKeyEvent)
                    || options.keyJump.matches(mcKeyEvent) || options.keyShift.matches(mcKeyEvent)
                    || options.keySprint.matches(mcKeyEvent);

            var isHotbarKey = false;
            for (var hotbarKey : options.keyHotbarSlots) {
                if (hotbarKey.matches(mcKeyEvent)) {
                    isHotbarKey = true;
                    break;
                }
            }

            if (!isMovementKey && !isHotbarKey) {
                var keyEvent = event.action == 0
                        ? new KeyEvent(EventType.KEY_RELEASED, event.key, event.scanCode, event.modifiers)
                        : new KeyEvent(EventType.KEY_PRESSED, event.key, event.scanCode, event.modifiers);

                uiManager.getRootContainer().dispatchEvent(keyEvent);

                if (event.action == 0 && !keyEvent.isConsumed()) {
                    toggle();
                }
                event.setCanceled(true);
            }
        }
    }
}