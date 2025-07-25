package org.academy.api.client.input;

import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class InputSystem {
    public static final Map<String, KeyBinding> KEY_BINDINGS = new HashMap<>();
    public static final Map<String, Consumer<Integer>> scrollListeners = new HashMap<>();
    public static final Map<Integer, Integer> KEYBOARD_STATE = new HashMap<>();
    public static final Map<Integer, Integer> MOUSE_STATE = new HashMap<>();
    public static final Map<String, BiConsumer<Double, Double>> MOUSE_MOVE_HANDLERS = new HashMap<>();
    public static int currentMouseButton = -1;
    public static int currentMouseAction = -1;
    public static int currentMouseModifier = -1;
    public static int currentKeyCode = -1;
    public static int currentKeyAction = -1;

    public static void handleMouseMove(double xpos, double ypos, CallbackInfo ci) {
        var event = new MouseMoveEvent(xpos, ypos);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
            return;
        }
        xpos = event.xpos;
        ypos = event.ypos;
        for (var consumer : MOUSE_MOVE_HANDLERS.values()) {
            consumer.accept(xpos, ypos);
        }
    }

    public static void handleKey(int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        currentKeyCode = key;
        currentKeyAction = action;
        KEYBOARD_STATE.put(key, action);

        var event = new KeyEvent(key, scanCode, action, modifiers);
        NeoForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            if (action == GLFW.GLFW_RELEASE) {
                net.minecraft.client.KeyMapping.set(com.mojang.blaze3d.platform.InputConstants.getKey(key, scanCode), false);
            }
            ci.cancel();
            return;
        }

        processBindings(InputType.KEYBOARD, KEYBOARD_STATE, event.key, event.modifiers);
    }

    public static void handleMouseButton(int button, int action, int modifiers, CallbackInfo ci) {
        currentMouseButton = button;
        currentMouseAction = action;
        currentMouseModifier = modifiers;
        MOUSE_STATE.put(button, action);

        var event = new MouseButtonEvent(button, action, modifiers);
        NeoForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            if (action == GLFW.GLFW_RELEASE) {
                net.minecraft.client.KeyMapping.set(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(button), false);
            }
            ci.cancel();
            return;
        }

        processBindings(InputType.MOUSE, MOUSE_STATE, event.button, event.modifiers);
    }

    private static void processBindings(InputType eventType, Map<Integer, Integer> state, int input, int modifiers) {
        KEY_BINDINGS.values().forEach(binding -> {
            if (binding.inputPair.inputType != eventType) {
                return;
            }

            var keyInfo = binding.inputPair.keyInfo;
            var requiredKeys = keyInfo.inputs;
            var requiredModifiers = keyInfo.modifiers;
            var requiredAction = keyInfo.action;

            if (requiredKeys.isEmpty()) return;

            var requiredMask = requiredModifiers.stream().reduce(0, (a, b) -> a | b);
            var modSuccess = keyInfo.modifiers.isEmpty() || modifiers == requiredMask;

            boolean keySuccess;
            if (requiredAction == GLFW.GLFW_RELEASE) {
                var lastKey = -1;
                for (var key : requiredKeys) lastKey = key;
                keySuccess = input == lastKey && requiredKeys.stream().allMatch(state::containsKey)
                        && requiredKeys.stream().allMatch(requiredKey -> state.get(requiredKey) == requiredAction);
            } else {
                keySuccess = requiredKeys.stream().allMatch(state::containsKey)
                        && requiredKeys.stream().allMatch(requiredKey -> state.get(requiredKey) == requiredAction);
            }

            if (modSuccess && keySuccess)
                binding.runnable.run();
        });
    }

    public static void handleMouseScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        var event = new MouseScrollEvent(windowPointer, xOffset, yOffset);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
            return;
        }
        windowPointer = event.windowPointer;
        xOffset = event.xOffset;
        yOffset = event.yOffset;
        if (yOffset != 0 && !scrollListeners.isEmpty()) {
            var finalYOffset = yOffset;
            scrollListeners.values().forEach(listener -> listener.accept((int) finalYOffset));
        }
    }

    public static void addKeyBinding(@NotNull String keyName, @NotNull InputSystem.InputPair pair, @NotNull Runnable runnable) {
        var binding = new KeyBinding(pair, runnable);
        KEY_BINDINGS.put(keyName, binding);
    }

    public static void removeKeyBinding(@NotNull String keyName) {
        KEY_BINDINGS.remove(keyName);
    }

    public enum InputType {
        MOUSE,
        KEYBOARD
    }

    public static final class KeyInfo {
        public LinkedHashSet<Integer> inputs;
        public int action;
        public LinkedHashSet<Integer> modifiers;

        public KeyInfo(LinkedHashSet<Integer> newInputs, int newAction, LinkedHashSet<Integer> newModifiers) {
            inputs = newInputs;
            action = newAction;
            modifiers = newModifiers;
        }
    }

    public static class KeyBinding {
        public InputPair inputPair;
        public Runnable runnable;

        public KeyBinding(InputPair newInputPair, Runnable newRunnable) {
            inputPair = newInputPair;
            runnable = newRunnable;
        }
    }

    public static class InputPair {
        public InputType inputType;
        public KeyInfo keyInfo;

        public InputPair(InputType newInputType, KeyInfo newKeyInfo) {
            inputType = newInputType;
            keyInfo = newKeyInfo;
        }
    }
}