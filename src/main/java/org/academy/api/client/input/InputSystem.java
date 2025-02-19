package org.academy.api.client.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.academy.AcademyCraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class InputSystem {
    public static final Map<List<Integer>, Runnable> KEY_PRESS_MAP = new ConcurrentHashMap<>();
    public static final Map<List<Integer>, Runnable> KEY_RELEASE_MAP = new ConcurrentHashMap<>();
    public static final Map<List<Integer>, Runnable> KEY_HOLD_MAP = new ConcurrentHashMap<>();

    public static final List<Consumer<Integer>> scrollListeners = new CopyOnWriteArrayList<>();
    public static double accumulatedScrollDelta = 0;
    public static long windowHandle = -1;
    public static GLFWScrollCallback previousScrollCallback = null;

    public static final Map<Integer, Boolean> keyStateMap = new ConcurrentHashMap<>();

    public static void init() {
        AcademyCraft.LOGGER.info("Initializing InputSystem");

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            windowHandle = Minecraft.getInstance().getWindow().getWindow();
            previousScrollCallback = GLFW.glfwSetScrollCallback(windowHandle, (w, x, y) -> {
                accumulatedScrollDelta += y;
                if (previousScrollCallback != null) previousScrollCallback.invoke(w, x, y);
            });
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            processKeyEvents();
            if (accumulatedScrollDelta != 0) {
                scrollListeners.forEach(listener -> listener.accept((int) accumulatedScrollDelta));
                accumulatedScrollDelta = 0;
            }
        });
    }

    private static void processKeyEvents() {
        Map<Integer, Boolean> currentStates = new HashMap<>();
        Set<Integer> allKeys = new HashSet<>();
        KEY_PRESS_MAP.keySet().forEach(allKeys::addAll);
        KEY_RELEASE_MAP.keySet().forEach(allKeys::addAll);
        KEY_HOLD_MAP.keySet().forEach(allKeys::addAll);

        for (Integer key : allKeys) {
            currentStates.put(key, GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_PRESS);
        }

        handleKeyEvent(KEY_PRESS_MAP, currentStates, (wasPressed, isPressed) -> !wasPressed && isPressed);
        handleKeyEvent(KEY_RELEASE_MAP, currentStates, (wasPressed, isPressed) -> wasPressed && !isPressed);
        handleKeyEvent(KEY_HOLD_MAP, currentStates, (wasPressed, isPressed) -> isPressed);

        keyStateMap.putAll(currentStates);
    }

    private static void handleKeyEvent(Map<List<Integer>, Runnable> keyMap, Map<Integer, Boolean> currentStates, BiPredicate<Boolean, Boolean> condition) {
        keyMap.forEach((keys, action) -> {
            if (keys.stream().allMatch(key ->
                    condition.test(keyStateMap.getOrDefault(key, false), currentStates.getOrDefault(key, false))
            )) {
                action.run();
            }
        });
    }
}