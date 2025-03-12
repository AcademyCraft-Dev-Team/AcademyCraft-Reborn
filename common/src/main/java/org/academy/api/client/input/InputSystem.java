package org.academy.api.client.input;

import org.academy.AcademyCraftClientConfig;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Consumer;

public class InputSystem {
    public static final Map<String, KeyBinding> MOUSE_KEY_BINDINGS = new HashMap<>();
    public static final Map<String, KeyBinding> KEYBOARD_KEY_BINDING_MAP = new HashMap<>();
    public static final Map<String, Consumer<Integer>> scrollListeners = new HashMap<>();
    public static final Map<Integer, Integer> KEYBOARD_STATE = new HashMap<>();
    public static final Map<Integer, Integer> MOUSE_STATE = new HashMap<>();

    public static void handleKeyCallback(int key, int action, int modifiers) {
        KEYBOARD_STATE.put(key, action);
        processBindings(KEYBOARD_KEY_BINDING_MAP, KEYBOARD_STATE, key, modifiers);
    }

    public static void handleMouseButton(int button, int action, int modifiers) {
        MOUSE_STATE.put(button, action);
        processBindings(MOUSE_KEY_BINDINGS, MOUSE_STATE, button, modifiers);
    }

    private static void processBindings(Map<String, KeyBinding> bindings, Map<Integer, Integer> state, int input, int modifiers) {
        bindings.values().forEach(keyBinding -> {
            LinkedHashSet<Integer> requiredKeys = keyBinding.inputEvent.inputs, requiredModifiers = keyBinding.inputEvent.modifiers;
            int requiredAction = keyBinding.inputEvent.action;
            if (requiredKeys.isEmpty()) throw new IllegalStateException("Missing required keys");
            boolean modSuccess = requiredModifiers.isEmpty() || requiredModifiers.stream().allMatch(modifier -> (modifiers & modifier) != 0), keySuccess;
            if (requiredAction == GLFW.GLFW_RELEASE) {
                int lastKey = -1;
                for (Integer key : requiredKeys) lastKey = key;
                keySuccess = input == lastKey && requiredKeys.stream().allMatch(state::containsKey) && requiredKeys.stream().allMatch(requiredKey -> state.get(requiredKey) == requiredAction);
            } else {
                keySuccess = requiredKeys.stream().allMatch(state::containsKey) && requiredKeys.stream().allMatch(requiredKey -> state.get(requiredKey) == requiredAction);
            }
            if (modSuccess && keySuccess) keyBinding.runnable.run();
        });
    }

    public static void handleMouseScroll(long windowPointer, double xOffset, double yOffset) {
        if (yOffset != 0 && !scrollListeners.isEmpty()) {
            scrollListeners.values().forEach(listener -> listener.accept((int) yOffset));
        }
    }

    public static void registerKeyBinding(String keyName, AcademyCraftClientConfig.InputPair key, Runnable runnable) {
        if (keyName == null || key == null || runnable == null) {
            throw new IllegalArgumentException("Invalid key binding parameters");
        }

        switch (key.inputType()) {
            case MOUSE -> MOUSE_KEY_BINDINGS.put(keyName, new KeyBinding(key.inputEvent(), runnable));
            case KEYBOARD -> KEYBOARD_KEY_BINDING_MAP.put(keyName, new KeyBinding(key.inputEvent(), runnable));
            default -> throw new IllegalArgumentException("Unknown input type");
        }
    }

    /**
     * Describes a key event.
     *
     * @param inputs    A set of required keys, with at least one key present.
     * @param action    The expected action for the key (e.g., `GLFW_PRESS` for key press, `GLFW_RELEASE` for key release, or `GLFW_REPEAT` for key repeat, which is specific to keyboards).
     * @param modifiers A set of modifier keys (e.g., `GLFW_MOD_SHIFT` for Shift).
     */
    public record InputEvent(LinkedHashSet<Integer> inputs, int action, LinkedHashSet<Integer> modifiers) {
    }

    public record KeyBinding(InputEvent inputEvent, Runnable runnable) {
    }
}