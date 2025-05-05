package org.academy.api.client.input;

import org.academy.AcademyCraft;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class InputSystem {
    public static final Map<String, KeyBinding> MOUSE_KEY_BINDINGS = new HashMap<>();
    public static final Map<String, KeyBinding> KEYBOARD_KEY_BINDING_MAP = new HashMap<>();
    public static final Map<String, Consumer<Integer>> scrollListeners = new HashMap<>();
    public static final Map<Integer, Integer> KEYBOARD_STATE = new HashMap<>();
    public static final Map<Integer, Integer> MOUSE_STATE = new HashMap<>();
    public static final Map<String, BiConsumer<Double, Double>> MOUSE_MOVE_HANDLERS = new HashMap<>();
    public static int currentMouseButton = -1;
    public static int currentMouseAction = -1;
    public static int currentMouseModifier = -1;
    public static int currentKeyCode = -1;
    public static int currentKeyAction = -1;

    public static void handleMouseMove(double xpos, double ypos) {
        MouseMoveEvent event = new MouseMoveEvent(xpos, ypos);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        xpos = event.xpos;
        ypos = event.ypos;
        for (BiConsumer<Double, Double> consumer : MOUSE_MOVE_HANDLERS.values()) {
            consumer.accept(xpos, ypos);
        }
    }

    public static void handleKey(int key, int action, int modifiers) {
        currentKeyCode = key;
        currentKeyAction = action;
        KeyEvent event = new KeyEvent(key, action, modifiers);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        key = event.key;
        action = event.action;
        modifiers = event.modifiers;
        KEYBOARD_STATE.put(key, action);
        processBindings(KEYBOARD_KEY_BINDING_MAP, KEYBOARD_STATE, key, modifiers);
    }

    public static void handleMouseButton(int button, int action, int modifiers) {
        MouseButtonEvent event = new MouseButtonEvent(button, action, modifiers);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        button = event.button;
        action = event.action;
        modifiers = event.modifiers;
        currentMouseButton = button;
        currentMouseAction = action;
        currentMouseModifier = modifiers;
        MOUSE_STATE.put(button, action);
        processBindings(MOUSE_KEY_BINDINGS, MOUSE_STATE, button, modifiers);
    }

    private static void processBindings(Map<String, KeyBinding> bindings, Map<Integer, Integer> state, int input, int modifiers) {
        bindings.values().forEach(keyBinding -> {
            LinkedHashSet<Integer> requiredKeys = keyBinding.keyInfo.inputs, requiredModifiers = keyBinding.keyInfo.modifiers;
            int requiredAction = keyBinding.keyInfo.action;
            if (requiredKeys.isEmpty()) throw new IllegalStateException("Missing required keys");

            int requiredMask = requiredModifiers.stream().reduce(0, (a, b) -> a | b);
            boolean modSuccess = requiredModifiers.isEmpty() || modifiers == requiredMask;

            boolean keySuccess;
            if (requiredAction == GLFW.GLFW_RELEASE) {
                int lastKey = -1;
                for (Integer key : requiredKeys) lastKey = key;
                keySuccess = input == lastKey
                        && requiredKeys.stream().allMatch(state::containsKey)
                        && requiredKeys.stream().allMatch(requiredKey -> state.get(requiredKey) == requiredAction);
            } else {
                keySuccess = requiredKeys.stream().allMatch(state::containsKey)
                        && requiredKeys.stream().allMatch(requiredKey -> state.get(requiredKey) == requiredAction);
            }

            if (modSuccess && keySuccess)
                keyBinding.runnable.run();
        });
    }

    public static void handleMouseScroll(long windowPointer, double xOffset, double yOffset) {
        if (yOffset != 0 && !scrollListeners.isEmpty()) {
            scrollListeners.values().forEach(listener -> listener.accept((int) yOffset));
        }
    }

    public static void addKeyBinding(@NotNull String keyName, @NotNull InputSystem.InputPair key, @NotNull Runnable runnable) {
        switch (key.inputType()) {
            case MOUSE -> MOUSE_KEY_BINDINGS.put(keyName, new KeyBinding(key.keyInfo(), runnable));
            case KEYBOARD -> KEYBOARD_KEY_BINDING_MAP.put(keyName, new KeyBinding(key.keyInfo(), runnable));
            default -> throw new IllegalArgumentException("Unknown input type");
        }
    }

    /**
     * Under normal circumstances, use this.
     *
     * @param keyName KeyName
     */
    public static void removeKeyBinding(@NotNull String keyName) {
        MOUSE_KEY_BINDINGS.keySet().removeIf(keyName::equals);
        KEYBOARD_KEY_BINDING_MAP.keySet().removeIf(keyName::equals);
    }

    public static void removeKeyBinding(@NotNull String keyName, @NotNull InputSystem.InputType inputType) {
        switch (inputType) {
            case MOUSE -> MOUSE_KEY_BINDINGS.remove(keyName);
            case KEYBOARD -> KEYBOARD_KEY_BINDING_MAP.remove(keyName);
        }
    }

    public static void removeKeyBinding(@NotNull String keyName, @NotNull InputSystem.InputPair key) {
        switch (key.inputType()) {
            case MOUSE -> MOUSE_KEY_BINDINGS.remove(keyName);
            case KEYBOARD -> KEYBOARD_KEY_BINDING_MAP.remove(keyName);
        }
    }

    public enum InputType {
        MOUSE,
        KEYBOARD
    }

    /**
     * Describes a key event.
     *
     * @param inputs    A set of required keys, with at least one key present.
     * @param action    The expected action for the key (e.g., `GLFW_PRESS` for key press, `GLFW_RELEASE` for key release, or `GLFW_REPEAT` for key repeat, which is specific to keyboards).
     * @param modifiers A set of modifier keys (e.g., `GLFW_MOD_SHIFT` for Shift).
     */
    public record KeyInfo(LinkedHashSet<Integer> inputs, int action, LinkedHashSet<Integer> modifiers) {
    }

    public record KeyBinding(KeyInfo keyInfo, Runnable runnable) {
    }

    public record InputPair(InputType inputType, KeyInfo keyInfo) {
    }
}