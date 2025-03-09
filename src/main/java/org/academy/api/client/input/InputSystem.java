package org.academy.api.client.input;

import org.academy.AcademyCraft;
import org.academy.AcademyCraftClientConfig;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class InputSystem {
    public static final Map<String, KeyBinding> MOUSE_KEY_BINDINGS = new HashMap<>();
    public static final Map<String, KeyBinding> KEYBOARD_KEY_BINDING_MAP = new HashMap<>();

    public static final Map<String, Consumer<Integer>> scrollListeners = new HashMap<>();

    public static final Map<Integer, Integer> KEY_STATE = new HashMap<>();

    public static void handleKeyCallback(int key, int action, int modifiers) {
        KEY_STATE.put(key, action);
        KEYBOARD_KEY_BINDING_MAP.values().forEach(keyBinding -> {
            Set<Integer> requiredKeys = keyBinding.inputEvent.inputs;
            Set<Integer> requiredModifiers = keyBinding.inputEvent.modifiers;
            int requiredAction = keyBinding.inputEvent.action;
            boolean modSuccess = false;
            boolean keySuccess = false;
            if (requiredModifiers.isEmpty()) {
                modSuccess = true;
            } else {
                if (requiredModifiers.stream().allMatch(modifier -> (modifiers & modifier) != 0)) modSuccess = true;
            }
            if (requiredKeys.isEmpty()) {
                throw new RuntimeException("Missing required keys");
            } else if (requiredKeys.stream().allMatch(KEY_STATE::containsKey)) {
                if ((requiredKeys.stream().allMatch(requiredKey -> KEY_STATE.get(requiredKey) == requiredAction))) {
                    keySuccess = true;
                }
            }
            if (modSuccess) {
                requiredKeys.forEach(KEY_STATE::remove);
                if (keySuccess) {
                    keyBinding.runnable.run();
                }
            }
        });
    }

    public static void handleMouseButton(int button, int action, int modifiers) {
        AcademyCraft.LOGGER.info("Handling Mouse button " + button + " action " + action + " modifiers " + modifiers);
        KEY_STATE.put(button, action);
        MOUSE_KEY_BINDINGS.values().forEach(keyBinding -> {
            Set<Integer> requiredKeys = keyBinding.inputEvent.inputs;
            Set<Integer> requiredModifiers = keyBinding.inputEvent.modifiers;
            int requiredAction = keyBinding.inputEvent.action;
            boolean modSuccess = false;
            boolean keySuccess = false;
            if (requiredModifiers.isEmpty()) {
                modSuccess = true;
            } else {
                if (requiredModifiers.stream().allMatch(modifier -> (modifiers & modifier) != 0)) modSuccess = true;
            }
            if (requiredKeys.isEmpty()) {
                throw new RuntimeException("Missing required keys");
            } else if (requiredKeys.stream().allMatch(KEY_STATE::containsKey)) {
                if ((requiredKeys.stream().allMatch(requiredKey -> KEY_STATE.get(requiredKey) == requiredAction))) {
                    keySuccess = true;
                }
            }
            if (modSuccess) {
                requiredKeys.forEach(KEY_STATE::remove);
                if (keySuccess) {
                    keyBinding.runnable.run();
                }
            }
        });
    }

    public static void handleMouseScroll(long windowPointer, double xOffset, double yOffset) {
        if (yOffset != 0) {
            scrollListeners.values().forEach(listener -> listener.accept((int) yOffset));
        }
    }

    public static void registerKeyBinding(String keyName, AcademyCraftClientConfig.InputPair key, Runnable runnable) {
        switch (key.inputType()) {
            case MOUSE:
                InputSystem.MOUSE_KEY_BINDINGS.put(keyName, new InputSystem.KeyBinding(key.inputEvent(), runnable));
                break;
            case KEYBOARD:
                InputSystem.KEYBOARD_KEY_BINDING_MAP.put(keyName, new InputSystem.KeyBinding(key.inputEvent(), runnable));
                break;
        }
    }

    public record InputEvent(Set<Integer> inputs, int action, Set<Integer> modifiers) {
    }

    public record KeyBinding(InputEvent inputEvent, Runnable runnable) {
    }
}