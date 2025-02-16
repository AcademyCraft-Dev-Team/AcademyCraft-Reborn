package org.academy.api.client.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class InputSystem {
    private static final Map<List<Integer>, Runnable> KEY_PRESS_MAP = new ConcurrentHashMap<>();
    private static final Map<List<Integer>, Runnable> KEY_RELEASE_MAP = new ConcurrentHashMap<>();
    private static final List<Consumer<Integer>> scrollListeners = new CopyOnWriteArrayList<>();
    private static double accumulatedScrollDelta = 0;
    private static long windowHandle = -1;
    private static GLFWScrollCallback previousScrollCallback = null;

    private static final Map<Integer, Boolean> keyStateMap = new ConcurrentHashMap<>();

    public static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            windowHandle = Minecraft.getInstance().getWindow().getWindow();
            previousScrollCallback = GLFW.glfwSetScrollCallback(windowHandle, (w, x, y) -> {
                accumulatedScrollDelta += y;
                if (previousScrollCallback != null)
                    previousScrollCallback.invoke(w, x, y);
            });
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            // Handle key press actions
            KEY_PRESS_MAP.forEach((keys, action) -> {
                if (keys.stream().allMatch(key -> GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_PRESS)) {
                    action.run();
                }
            });

            // Handle key release actions
            KEY_RELEASE_MAP.forEach((keys, action) -> {
                if (keys.stream().allMatch(key -> {
                    boolean wasPressed = keyStateMap.getOrDefault(key, false);
                    boolean isReleased = GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_RELEASE;
                    keyStateMap.put(key, !isReleased);
                    return wasPressed && isReleased;
                })) {
                    action.run();
                }
            });

            // Handle scroll listeners
            if (accumulatedScrollDelta != 0) {
                scrollListeners.forEach(listener -> listener.accept((int) accumulatedScrollDelta));
                accumulatedScrollDelta = 0;
            }
        });
    }

    // Will run when key is pressed every tick.
    public static void addKeyPress(List<Integer> keyCodes, Runnable action) {
        KEY_PRESS_MAP.put(keyCodes, action);
    }

    public static void removeKeyPress(List<Integer> keyCodes) {
        KEY_PRESS_MAP.remove(keyCodes);
    }

    // Only run when key up.
    public static void addKeyRelease(List<Integer> keyCodes, Runnable action) {
        KEY_RELEASE_MAP.put(keyCodes, action);
    }

    public static void removeKeyRelease(List<Integer> keyCodes) {
        KEY_RELEASE_MAP.remove(keyCodes);
    }

    public static void addScroll(Consumer<Integer> listener) {
        scrollListeners.add(listener);
    }

    public static void removeScroll(Consumer<Integer> listener) {
        scrollListeners.remove(listener);
    }
}