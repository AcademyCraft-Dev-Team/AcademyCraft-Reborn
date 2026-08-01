package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.util.ClientUtil;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class InputSystem {
    public static final int ANY_ACTION = -1;
    public static final int ANY_MODIFIER = -1;

    private static final Map<String, KeyBinding> KEY_BINDINGS = new HashMap<>();
    private static final Map<String, Consumer<Integer>> SCROLL_LISTENERS = new HashMap<>();
    private static final Map<String, BiConsumer<Double, Double>> MOUSE_MOVE_HANDLERS = new HashMap<>();
    private static final Map<Integer, Integer> KEYBOARD_STATE = new HashMap<>();
    private static final Map<Integer, Integer> MOUSE_STATE = new HashMap<>();
    public static int currentMouseButton = -1;
    public static int currentMouseAction = -1;
    public static int currentMouseModifier = -1;

    private InputSystem() {
    }

    public static void addKeyBinding(String keyName, KeyCombination combo, Consumer<BindingContext> handler) {
        KEY_BINDINGS.put(keyName, new KeyBinding(combo, handler));
    }

    public static void removeKeyBinding(String keyName) {
        KEY_BINDINGS.remove(keyName);
    }

    public static boolean isDown(InputType type, int key) {
        return stateOf(type).getOrDefault(key, InputConstants.RELEASE) != InputConstants.RELEASE;
    }

    public static int actionOf(InputType type, int key) {
        return stateOf(type).getOrDefault(key, InputConstants.RELEASE);
    }

    public static void addScrollListener(String name, Consumer<Integer> listener) {
        SCROLL_LISTENERS.put(name, listener);
    }

    public static void removeScrollListener(String name) {
        SCROLL_LISTENERS.remove(name);
    }

    public static void addMouseMoveHandler(String name, BiConsumer<Double, Double> handler) {
        MOUSE_MOVE_HANDLERS.put(name, handler);
    }

    public static void removeMouseMoveHandler(String name) {
        MOUSE_MOVE_HANDLERS.remove(name);
    }

    public static KeyCombination combo(InputType type, int key, int action) {
        return combo(type, Set.of(key), action, ANY_MODIFIER, false);
    }

    public static KeyCombination combo(InputType type, int key, int action, int modifiers) {
        return combo(type, Set.of(key), action, modifiers, false);
    }

    public static KeyCombination combo(InputType type, int key, int action, int modifiers, boolean availableWhenScreen) {
        return combo(type, Set.of(key), action, modifiers, availableWhenScreen);
    }

    public static KeyCombination anyKey(InputType type, int action, int modifiers) {
        return combo(type, Set.of(), action, modifiers, false);
    }

    public static KeyCombination anyKey(InputType type, int action, int modifiers, boolean availableWhenScreen) {
        return combo(type, Set.of(), action, modifiers, availableWhenScreen);
    }

    public static KeyCombination combo(InputType type, Set<Integer> keys, int action, int modifiers, boolean availableWhenScreen) {
        return new KeyCombination(type, keys, action, modifiers, availableWhenScreen);
    }

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

    public static void handleKey(@KeyEvent.Action int action, KeyEvent event, CallbackInfo ci) {
        var key = event.key();
        KEYBOARD_STATE.put(key, action);

        var inputEvent = new KeyInputEvent(key, event.scancode(), action, event.modifiers());
        NeoForge.EVENT_BUS.post(inputEvent);

        if (inputEvent.isCanceled()) {
            if (action == InputConstants.RELEASE) KeyMapping.set(InputConstants.getKey(event), false);
            ci.cancel();
            return;
        }

        dispatch(InputType.KEYBOARD, key, action, event.modifiers());
    }

    public static void handleMouseButton(int button, int action, int modifiers, CallbackInfo ci) {
        currentMouseButton = button;
        currentMouseAction = action;
        currentMouseModifier = modifiers;
        MOUSE_STATE.put(button, action);

        var event = new MouseButtonEvent(button, action, modifiers);
        NeoForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            if (action == InputConstants.RELEASE) {
                KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(button), false);
            }
            ci.cancel();
            return;
        }

        dispatch(InputType.MOUSE, button, action, modifiers);
    }

    public static void handleMouseScroll(double xOffset, double yOffset, CallbackInfo ci) {
        var event = new MouseScrollEvent(xOffset, yOffset);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
            return;
        }
        yOffset = event.yOffset;
        if (yOffset != 0 && !SCROLL_LISTENERS.isEmpty()) {
            var finalYOffset = yOffset;
            SCROLL_LISTENERS.values().forEach(listener -> listener.accept((int) finalYOffset));
        }
    }

    private static void dispatch(InputType eventType, int input, int action, int modifiers) {
        for (var binding : KEY_BINDINGS.values()) {
            var combo = binding.combo;
            if (!matches(combo, eventType, input, action, modifiers)) continue;
            binding.handler.accept(new BindingContext(eventType, input, action, modifiers));
        }
    }

    private static boolean matches(KeyCombination combo, InputType eventType, int input, int action, int modifiers) {
        if (combo.type != eventType) return false;
        if (!combo.availableWhenScreen && ClientUtil.hasScreen()) return false;
        if (combo.action != ANY_ACTION && combo.action != action) return false;
        if (combo.modifiers != ANY_MODIFIER && combo.modifiers != modifiers) return false;
        if (combo.keys.isEmpty()) return true;
        if (!combo.keys.contains(input)) return false;
        return combo.keys.stream().allMatch(key -> stateOf(eventType).getOrDefault(key, InputConstants.RELEASE) == combo.action);
    }

    private static Map<Integer, Integer> stateOf(InputType type) {
        return type == InputType.KEYBOARD ? KEYBOARD_STATE : MOUSE_STATE;
    }

    public enum InputType {
        MOUSE,
        KEYBOARD
    }

    public record KeyCombination(
            InputType type,
            Set<Integer> keys,
            int action,
            int modifiers,
            boolean availableWhenScreen
    ) {
        public KeyCombination {
            keys = keys == null ? Set.of() : keys;
        }
    }

    public record BindingContext(InputType type, int input, int action, int modifiers) {
    }

    private record KeyBinding(KeyCombination combo, Consumer<BindingContext> handler) {
    }
}
