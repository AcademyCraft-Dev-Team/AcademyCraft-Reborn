package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class InputSystem {
    public static final int ANY_ACTION = -1;
    public static final int ANY_MODIFIER = -1;
    public static final String CONFIG_KEY = "input_system_keybindings";

    private static final Map<String, KeyBinding> KEY_BINDINGS = new LinkedHashMap<>();
    private static final Map<String, KeyCombination> DEFAULT_BINDINGS = new LinkedHashMap<>();
    private static final Map<String, Skill> EXPLICIT_TOGGLE_BINDINGS = new HashMap<>();
    private static final Map<String, Consumer<Integer>> SCROLL_LISTENERS = new HashMap<>();
    private static final Map<String, BiConsumer<Double, Double>> MOUSE_MOVE_HANDLERS = new HashMap<>();
    private static final Map<Integer, Integer> KEYBOARD_STATE = new HashMap<>();
    private static final Map<Integer, Integer> MOUSE_STATE = new HashMap<>();
    private static final Set<InputKey> SUPPRESSED_RELEASES = new HashSet<>();
    private static Config config;
    private static RebindSession rebindSession;
    private static long bindingRevision;
    public static int currentMouseButton = -1;
    public static int currentMouseAction = -1;
    public static int currentMouseModifier = -1;

    private InputSystem() {
    }

    public static void addKeyBinding(String keyName, KeyCombination combo, Consumer<BindingContext> handler) {
        rememberDefaultKeyBinding(keyName, combo);
        KEY_BINDINGS.put(keyName, new KeyBinding(combo, handler, true));
        bindingRevision++;
    }

    public static void removeKeyBinding(String keyName) {
        KEY_BINDINGS.remove(keyName);
        bindingRevision++;
    }

    public static void rememberDefaultKeyBinding(String keyName, KeyCombination combo) {
        DEFAULT_BINDINGS.putIfAbsent(keyName, combo);
    }

    public static List<BindingInfo> getKeyBindings() {
        return KEY_BINDINGS.entrySet().stream()
                .map(entry -> new BindingInfo(entry.getKey(), entry.getValue().combo))
                .toList();
    }

    public static KeyCombination getKeyBinding(String keyName) {
        var binding = KEY_BINDINGS.get(keyName);
        return binding == null ? null : binding.combo;
    }

    public static boolean matchesKeyBinding(
            String keyName, InputType eventType, int input, int action, int modifiers
    ) {
        var binding = KEY_BINDINGS.get(keyName);
        return binding != null
                && binding.enabled
                && matches(binding.combo, eventType, input, action, modifiers);
    }

    public static void setKeyBinding(String keyName, KeyCombination combo) {
        var binding = KEY_BINDINGS.get(keyName);
        if (binding == null) return;
        KEY_BINDINGS.put(keyName, new KeyBinding(combo, binding.handler, binding.enabled));
        bindingRevision++;
    }

    public static void resetKeyBinding(String keyName) {
        var defaultCombo = DEFAULT_BINDINGS.get(keyName);
        if (defaultCombo != null) setKeyBinding(keyName, defaultCombo);
    }

    public static long getBindingRevision() {
        return bindingRevision;
    }

    public static boolean isRebinding(String keyName) {
        return rebindSession != null && rebindSession.keyName.equals(keyName);
    }

    public static void beginRebind(String keyName, Runnable onFinished) {
        if (!KEY_BINDINGS.containsKey(keyName)) return;
        rebindSession = new RebindSession(keyName, onFinished == null ? () -> {
        } : onFinished);
        bindingRevision++;
    }

    public static void cancelRebind() {
        if (rebindSession == null) return;
        var callback = rebindSession.onFinished;
        rebindSession = null;
        bindingRevision++;
        callback.run();
    }

    public static String formatKeyBinding(String keyName) {
        var combo = getKeyBinding(keyName);
        return combo == null ? "" : formatKeyCombination(combo);
    }

    public static String formatKeyCombination(KeyCombination combo) {
        var parts = new ArrayList<String>();
        if (combo.modifiers != ANY_MODIFIER) {
            if ((combo.modifiers & GLFW.GLFW_MOD_CONTROL) != 0) parts.add("Ctrl");
            if ((combo.modifiers & GLFW.GLFW_MOD_SHIFT) != 0) parts.add("Shift");
            if ((combo.modifiers & GLFW.GLFW_MOD_ALT) != 0) parts.add("Alt");
            if ((combo.modifiers & GLFW.GLFW_MOD_SUPER) != 0) parts.add("Super");
        }
        combo.keys.stream().sorted().map(key -> displayName(combo.type, key)).forEach(parts::add);
        return parts.isEmpty() ? "None" : String.join(" + ", parts);
    }

    public static String formatBindingsForSkill(Skill skill) {
        return KEY_BINDINGS.entrySet().stream()
                .filter(entry -> isBindingForSkill(entry.getKey(), skill))
                .map(entry -> formatKeyCombination(entry.getValue().combo))
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    public static boolean isBindingForSkill(String keyName, Skill skill) {
        var skillName = skill.getKey().getPath();
        return keyName.equals(skillName)
                || keyName.startsWith(skillName + "_")
                || keyName.startsWith(skillName + ".");
    }

    public static void markToggleKeyBinding(String keyName, Skill skill) {
        EXPLICIT_TOGGLE_BINDINGS.put(keyName, skill);
    }

    public static boolean hasToggleBindingForSkill(Skill skill) {
        return KEY_BINDINGS.keySet().stream().anyMatch(keyName ->
                (isBindingForSkill(keyName, skill) && keyName.endsWith("_toggle"))
                        || EXPLICIT_TOGGLE_BINDINGS.get(keyName) == skill
        );
    }

    /**
     * Replaces the KeyCombination of an existing binding, keeping its handler intact.
     */
    public static void updateKeyBinding(String keyName, KeyCombination combo) {
        setKeyBinding(keyName, combo);
    }

    /**
     * Enables or disables an existing binding without discarding its handler.
     */
    public static void setKeyBindingEnabled(String keyName, boolean enabled) {
        var existing = KEY_BINDINGS.get(keyName);
        if (existing == null) {
            return;
        }
        if (existing.enabled != enabled) {
            KEY_BINDINGS.put(keyName, new KeyBinding(existing.combo, existing.handler, enabled));
            bindingRevision++;
        }
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

        if (handleRebindInput(InputType.KEYBOARD, key, action, event.modifiers(), ci)) return;

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

        if (handleRebindInput(InputType.MOUSE, button, action, modifiers, ci)) return;

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
            if (!binding.enabled) continue;
            var combo = binding.combo;
            if (!matches(combo, eventType, input, action, modifiers)) continue;
            binding.handler.accept(new BindingContext(eventType, input, action, modifiers));
        }
    }

    private static boolean handleRebindInput(
            InputType type, int input, int action, int modifiers, CallbackInfo ci
    ) {
        var inputKey = new InputKey(type, input);
        if (action == InputConstants.RELEASE && SUPPRESSED_RELEASES.remove(inputKey)) {
            releaseVanillaKey(type, input);
            ci.cancel();
            return true;
        }

        var session = rebindSession;
        if (session == null || action != InputConstants.PRESS) return false;

        if (type == InputType.KEYBOARD && input == InputConstants.KEY_ESCAPE) {
            SUPPRESSED_RELEASES.add(inputKey);
            cancelRebind();
            ci.cancel();
            return true;
        }
        if (type == InputType.KEYBOARD && isModifierKey(input)) {
            SUPPRESSED_RELEASES.add(inputKey);
            ci.cancel();
            return true;
        }

        var oldCombo = getKeyBinding(session.keyName);
        if (oldCombo == null) {
            cancelRebind();
            ci.cancel();
            return true;
        }

        var normalizedModifiers = normalizeModifiers(modifiers);
        var newCombo = combo(type, input, oldCombo.action, normalizedModifiers, oldCombo.availableWhenScreen);
        SUPPRESSED_RELEASES.add(inputKey);
        rebindSession = null;
        setKeyBinding(session.keyName, newCombo);
        session.onFinished.run();
        ci.cancel();
        return true;
    }

    private static boolean isModifierKey(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private static int normalizeModifiers(int modifiers) {
        return modifiers & (
                GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER
        );
    }

    private static void releaseVanillaKey(InputType type, int input) {
        if (type == InputType.KEYBOARD) {
            KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(input), false);
        } else {
            KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(input), false);
        }
    }

    private static String displayName(InputType type, int key) {
        var inputKey = type == InputType.KEYBOARD
                ? InputConstants.Type.KEYSYM.getOrCreate(key)
                : InputConstants.Type.MOUSE.getOrCreate(key);
        return inputKey.getDisplayName().getString();
    }

    private static Config config() {
        if (config == null) {
            AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, Config.Action.INSTANCE);
            config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY);
        }
        return config;
    }

    private static void markConfigDirty(boolean saveNow) {
        AcademyCraftClient.Config.INSTANCE.setConfig(CONFIG_KEY, config());
        if (saveNow) AcademyCraftClient.Config.INSTANCE.save();
    }

    private static boolean matches(KeyCombination combo, InputType eventType, int input, int action, int modifiers) {
        if (combo.type != eventType) return false;
        if (!combo.availableWhenScreen && ClientUtil.hasScreen()) return false;
        if (combo.action != ANY_ACTION && combo.action != action) return false;
        if (combo.modifiers != ANY_MODIFIER
                && normalizeModifiers(combo.modifiers) != normalizeModifiers(modifiers)) return false;
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
        private static String keyName(InputType type, int key) {
            return switch (type) {
                case MOUSE -> InputConstants.Type.MOUSE.getOrCreate(key).getDisplayName().getString();
                case KEYBOARD -> InputConstants.getKey(new KeyEvent(key, -1, 0))
                        .getDisplayName().getString();
            };
        }

        public String displayName() {
            if (keys.isEmpty()) {
                return "Any";
            }
            var builder = new StringBuilder();
            if ((modifiers & InputConstants.MOD_SHIFT) != 0) builder.append("Shift+");
            if ((modifiers & InputConstants.MOD_CONTROL) != 0) builder.append("Ctrl+");
            if ((modifiers & InputConstants.MOD_ALT) != 0) builder.append("Alt+");
            var keyCodes = keys.stream().sorted().toList();
            for (var i = 0; i < keyCodes.size(); i++) {
                if (i > 0) builder.append('+');
                builder.append(keyName(type, keyCodes.get(i)));
            }
            return builder.toString();
        }
    }

    public record BindingContext(InputType type, int input, int action, int modifiers) {
    }

    public record BindingInfo(String name, KeyCombination combo) {
    }

    private record KeyBinding(KeyCombination combo, Consumer<BindingContext> handler, boolean enabled) {
    }

    private record InputKey(InputType type, int key) {
    }

    private record RebindSession(String keyName, Runnable onFinished) {
    }

    public static final class Config extends KeyBindingConfig {
        public static final class Action implements TypeHandler<Config> {
            public static final TypeHandler<Config> INSTANCE = new Action();

            private Action() {
            }

            @Override
            public Config getDefault() {
                return new Config();
            }

            @Override
            public Class<Config> getTypeClass() {
                return Config.class;
            }
        }
    }
}
