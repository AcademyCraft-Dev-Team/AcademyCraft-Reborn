package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.Dev;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLScancode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class InputSystem {
    public static final int ANY_ACTION = -1;
    public static final int ANY_MODIFIER = -1;
    public static final String CONFIG_KEY = "input_system_keybindings";

    public static final int SIDE_ANY = 0;
    public static final int SIDE_LEFT = 1;
    public static final int SIDE_RIGHT = 2;

    private static final Map<String, KeyBinding> KEY_BINDINGS = new LinkedHashMap<>();
    private static final Map<String, MaintainedBinding> MAINTAINED_BINDINGS = new HashMap<>();
    private static final Map<String, BooleanSupplier> EXCLUSIVE_BINDINGS = new LinkedHashMap<>();
    private static final Map<String, ActiveMaintainedBinding> ACTIVE_MAINTAINED_BINDINGS = new HashMap<>();
    private static final Map<String, ActivePairedBinding> ACTIVE_PAIRED_BINDINGS = new HashMap<>();
    private static final Set<SuppressedPairedRelease> SUPPRESSED_PAIRED_RELEASES = new HashSet<>();
    private static final Map<String, KeyCombination> DEFAULT_BINDINGS = new LinkedHashMap<>();
    private static final Map<String, Skill> EXPLICIT_TOGGLE_BINDINGS = new HashMap<>();
    private static final Map<String, Consumer<Integer>> SCROLL_LISTENERS = new HashMap<>();
    private static final Map<String, BiConsumer<Double, Double>> MOUSE_MOVE_HANDLERS = new HashMap<>();
    private static final Map<Integer, Integer> KEYBOARD_STATE = new HashMap<>();
    private static final Map<Integer, Integer> MOUSE_STATE = new HashMap<>();
    private static final Map<InputKey, Integer> PRESS_MODIFIER_SNAPSHOTS = new HashMap<>();
    private static final Set<InputKey> SUPPRESSED_RELEASES = new HashSet<>();
    public static int currentMouseButton = -1;
    public static int currentMouseAction = -1;
    public static int currentMouseModifier = -1;
    private static boolean imguiReleasedMouse;
    private static @Nullable Config config;
    private static @Nullable RebindSession rebindSession;
    private static long bindingRevision;

    private InputSystem() {
    }

    public static void addKeyBinding(String keyName, KeyCombination combo, Consumer<BindingContext> handler) {
        TemporalInputAccess.ownedWrite(keyName);
        rememberDefaultKeyBinding(keyName, combo);
        cancelMaintainedKeyBinding(keyName);
        cancelPairedBindingsFor(keyName);
        MAINTAINED_BINDINGS.remove(keyName);
        KEY_BINDINGS.put(keyName, new KeyBinding(combo, handler, true));
        bindingRevision++;
    }

    public static void addMaintainedKeyBinding(
            String keyName,
            KeyCombination combo,
            Consumer<BindingContext> onStart,
            Consumer<BindingContext> onStop,
            Consumer<BindingContext> onHeartbeat,
            BooleanSupplier canRemainActive
    ) {
        TemporalInputAccess.ownedWrite(keyName);
        var maintainedCombo = withAction(combo, ANY_ACTION);
        rememberDefaultKeyBinding(keyName, maintainedCombo);
        cancelMaintainedKeyBinding(keyName);
        MAINTAINED_BINDINGS.put(
                keyName,
                new MaintainedBinding(onStart, onStop, onHeartbeat, canRemainActive)
        );
        KEY_BINDINGS.put(
                keyName,
                new KeyBinding(maintainedCombo, context -> handleMaintainedInput(keyName, context), true)
        );
        bindingRevision++;
    }

    public static void addMaintainedKeyBinding(
            String keyName,
            KeyCombination combo,
            Consumer<BindingContext> onStart,
            Consumer<BindingContext> onStop
    ) {
        addMaintainedKeyBinding(keyName, combo, onStart, onStop, _ -> {
        }, () -> true);
    }

    public static void addMaintainedKeyBinding(
            String keyName,
            KeyCombination combo,
            Consumer<BindingContext> onStart,
            Consumer<BindingContext> onStop,
            BooleanSupplier canRemainActive
    ) {
        addMaintainedKeyBinding(keyName, combo, onStart, onStop, _ -> {
        }, canRemainActive);
    }

    public static void addExclusiveKeyBinding(
            String keyName, KeyCombination combo, Consumer<BindingContext> handler, BooleanSupplier active
    ) {
        addKeyBinding(keyName, withAction(combo, ANY_ACTION),
                context -> UiInputContext.run(() -> handler.accept(context)));
        EXCLUSIVE_BINDINGS.put(keyName, active);
    }

    public static void removeKeyBinding(String keyName) {
        TemporalInputAccess.ownedWrite(keyName);
        EXCLUSIVE_BINDINGS.remove(keyName);
        cancelMaintainedKeyBinding(keyName);
        cancelPairedBindingsFor(keyName);
        MAINTAINED_BINDINGS.remove(keyName);
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

    public static @Nullable KeyCombination getKeyBinding(String keyName) {
        var binding = KEY_BINDINGS.get(keyName);
        return binding == null ? null : binding.combo;
    }

    public static @Nullable KeyCombination getDefaultKeyBinding(String keyName) {
        return DEFAULT_BINDINGS.get(keyName);
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
        cancelMaintainedKeyBinding(keyName);
        cancelPairedBindingsFor(keyName);
        if (MAINTAINED_BINDINGS.containsKey(keyName) || EXCLUSIVE_BINDINGS.containsKey(keyName))
            combo = withAction(combo, ANY_ACTION);
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
        rebindSession = new RebindSession(keyName, onFinished);
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
        if (combo.unbound) return "None";
        var parts = new ArrayList<String>();
        if (combo.modifiers != ANY_MODIFIER) {
            addModifierName(parts, combo.modifiers, InputConstants.MOD_CONTROL, SDLKeycode.SDL_KMOD_LCTRL, "Ctrl");
            addModifierName(parts, combo.modifiers, InputConstants.MOD_SHIFT, SDLKeycode.SDL_KMOD_LSHIFT, "Shift");
            addModifierName(parts, combo.modifiers, InputConstants.MOD_ALT, SDLKeycode.SDL_KMOD_LALT, "Alt");
            addModifierName(parts, combo.modifiers, InputConstants.MOD_SUPER, SDLKeycode.SDL_KMOD_LGUI, "Super");
        }
        combo.keys.stream().sorted().map(key -> displayName(combo.type, key)).forEach(parts::add);
        return parts.isEmpty() ? "None" : String.join(" + ", parts);
    }

    private static void addModifierName(
            List<String> parts, int modifiers, int combined, int left, String base
    ) {
        var name = modifierName(modifiers, combined, left, base);
        if (name != null) parts.add(name);
    }

    private static void appendModifierName(
            StringBuilder builder, int modifiers, int combined, int left, String base
    ) {
        var name = modifierName(modifiers, combined, left, base);
        if (name != null) builder.append(name).append('+');
    }

    private static @Nullable String modifierName(int modifiers, int combined, int left, String base) {
        int held = modifiers & combined;
        if (held == 0) return null;
        if (held == combined) return base;
        if (held == left) return "L" + base;
        if (held == (combined ^ left)) return "R" + base;
        return base;
    }

    public static String formatBindingsForSkill(Skill skill) {
        return KEY_BINDINGS.entrySet().stream()
                .filter(entry -> isBindingForSkill(entry.getKey(), skill))
                .map(entry -> formatKeyCombination(entry.getValue().combo))
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    public static boolean isBindingForSkill(String keyName, Skill skill) {
        return isBindingForSkill(keyName, skill.getKey());
    }

    static boolean isBindingForSkill(String keyName, Identifier skillKey) {
        var skillName = skillKey.getPath();
        var namespacedSkillName = Util.makeDescriptionId("key", skillKey);
        return keyName.equals(skillName)
                || keyName.startsWith(skillName + "_")
                || keyName.startsWith(skillName + ".")
                || keyName.equals(namespacedSkillName)
                || keyName.startsWith(namespacedSkillName + ".");
    }

    public static void markToggleKeyBinding(String keyName, Skill skill) {
        EXPLICIT_TOGGLE_BINDINGS.put(keyName, skill);
    }

    public static boolean hasToggleBindingForSkill(Skill skill) {
        return KEY_BINDINGS.keySet().stream().anyMatch(keyName ->
                (isBindingForSkill(keyName, skill)
                        && (keyName.endsWith("_toggle") || keyName.endsWith(".toggle")))
                        || EXPLICIT_TOGGLE_BINDINGS.get(keyName) == skill
        );
    }

    public static boolean hasActiveBindingForSkill(Skill skill) {
        return KEY_BINDINGS.entrySet().stream().anyMatch(entry ->
                entry.getValue().enabled && isBindingForSkill(entry.getKey(), skill)
        );
    }

    public static boolean triggerPrimaryBindingForSkill(Skill skill, BindingContext context) {
        var skillName = skill.getKey().getPath();
        var primaryPriority = KEY_BINDINGS.entrySet().stream()
                .filter(entry -> entry.getValue().enabled && isBindingForSkill(entry.getKey(), skill))
                .mapToInt(entry -> primaryBindingPriority(entry.getKey(), skillName))
                .min()
                .orElse(Integer.MAX_VALUE);
        for (var entry : KEY_BINDINGS.entrySet()) {
            var binding = entry.getValue();
            if (!binding.enabled || !isBindingForSkill(entry.getKey(), skill)) continue;
            if (primaryBindingPriority(entry.getKey(), skillName) != primaryPriority) continue;
            var configuredAction = binding.combo.action;
            if (configuredAction != ANY_ACTION && configuredAction != context.action) continue;

            binding.handler.accept(context);
            return true;
        }
        return false;
    }

    static int primaryBindingPriority(String keyName, String skillName) {
        if (keyName.equals(skillName)) return 0;
        if (keyName.equals(skillName + "_use") || keyName.equals(skillName + ".use")
                || keyName.equals(skillName + "_cast") || keyName.equals(skillName + ".cast")
                || keyName.equals(skillName + "_toggle") || keyName.equals(skillName + ".toggle")
                || keyName.equals(skillName + "_run") || keyName.equals(skillName + ".run")) {
            return 0;
        }
        if (keyName.equals(skillName + "_start") || keyName.equals(skillName + ".start")
                || keyName.equals(skillName + "_charge") || keyName.equals(skillName + ".charge")
                || keyName.equals(skillName + "_end") || keyName.equals(skillName + ".end")
                || keyName.equals(skillName + "_stop") || keyName.equals(skillName + ".stop")
                || keyName.equals(skillName + "_release") || keyName.equals(skillName + ".release")) {
            return 1;
        }
        return 2;
    }

    /**
     * Tests whether an input belongs to the physical gesture of a binding while ignoring its
     * press/release phase. Maintained branch actions use this to yield to a rebound primary action
     * that occupies the same gesture.
     */
    public static boolean matchesKeyBindingGesture(String keyName, BindingContext context) {
        var binding = KEY_BINDINGS.get(keyName);
        if (binding == null || !binding.enabled) return false;
        var combo = binding.combo;
        if (combo.unbound || combo.type != context.type) return false;
        if (combo.modifiers != ANY_MODIFIER
                && !modifiersMatch(combo.modifiers, context.modifiers)) return false;
        return combo.keys.isEmpty() || combo.keys.contains(context.input);
    }

    /**
     * Replaces the KeyCombination of an existing binding, keeping its handler intact.
     */
    public static void updateKeyBinding(String keyName, KeyCombination combo) {
        setKeyBinding(keyName, combo);
    }

    public static void setKeyBindingEnabled(String keyName, boolean enabled) {
        var current = KEY_BINDINGS.get(keyName);
        if (current == null) return;
        if (!UiInputContext.isActive()
                && TemporalInputAccess.isExternalControl()) {
            enabled = TemporalInputAccess.externalWrite(keyName, enabled, current.enabled,
                    TemporalInputAccess.isLocalExternallyImmune());
        } else {
            TemporalInputAccess.ownedWrite(keyName);
        }
        setBindingEnabledUnchecked(keyName, enabled);
    }

    public static void refreshTemporalBindingRestrictions() {
        TemporalInputAccess.effectiveStates(
                TemporalInputAccess.isLocalExternallyImmune()
        ).forEach(InputSystem::setBindingEnabledUnchecked);
    }

    public static void clearTemporalBindingRestrictions() {
        TemporalInputAccess.clear().forEach(InputSystem::setBindingEnabledUnchecked);
    }

    private static void setBindingEnabledUnchecked(String keyName, boolean enabled) {
        var existing = KEY_BINDINGS.get(keyName);
        if (existing == null) {
            return;
        }
        if (existing.enabled != enabled) {
            if (!enabled) {
                cancelMaintainedKeyBinding(keyName);
                cancelPairedBindingsFor(keyName);
            }
            KEY_BINDINGS.put(keyName, new KeyBinding(existing.combo, existing.handler, enabled));
            bindingRevision++;
        }
    }

    public static boolean isKeyBindingEnabled(String keyName) {
        var binding = KEY_BINDINGS.get(keyName);
        return binding != null && binding.enabled;
    }

    public static boolean isDown(InputType type, int key) {
        return stateOf(type).getOrDefault(key, InputConstants.RELEASE) != InputConstants.RELEASE;
    }

    public static boolean isPhysicalDown(KeyMapping mapping) {
        var key = mapping.getKey();
        var type = key.getType() == InputConstants.Type.MOUSE
                ? InputType.MOUSE
                : InputType.KEYBOARD;
        return isDown(type, key.getValue());
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
        return new KeyCombination(type, keys, action, modifiers, availableWhenScreen, false);
    }

    public static KeyCombination unbound(KeyCombination template) {
        return new KeyCombination(
                template.type, Set.of(), template.action, template.modifiers, template.availableWhenScreen, true
        );
    }

    public static KeyCombination withAction(KeyCombination template, int action) {
        return new KeyCombination(
                template.type,
                template.keys,
                action,
                template.modifiers,
                template.availableWhenScreen,
                template.unbound
        );
    }

    public static KeyCombination withModifiers(KeyCombination template, int modifiers) {
        return new KeyCombination(
                template.type,
                template.keys,
                template.action,
                modifiers,
                template.availableWhenScreen,
                template.unbound
        );
    }

    public static int pinModifierSides(int modifiers, int side) {
        int masked = normalizeModifiers(modifiers);
        if (side != SIDE_LEFT && side != SIDE_RIGHT) return canonicalizeModifiers(masked);
        int pinned = 0;
        pinned |= pinModifierClass(masked, InputConstants.MOD_SHIFT, SDLKeycode.SDL_KMOD_LSHIFT, SDLKeycode.SDL_KMOD_RSHIFT, side);
        pinned |= pinModifierClass(masked, InputConstants.MOD_CONTROL, SDLKeycode.SDL_KMOD_LCTRL, SDLKeycode.SDL_KMOD_RCTRL, side);
        pinned |= pinModifierClass(masked, InputConstants.MOD_ALT, SDLKeycode.SDL_KMOD_LALT, SDLKeycode.SDL_KMOD_RALT, side);
        pinned |= pinModifierClass(masked, InputConstants.MOD_SUPER, SDLKeycode.SDL_KMOD_LGUI, SDLKeycode.SDL_KMOD_RGUI, side);
        return pinned;
    }

    private static int pinModifierClass(int modifiers, int combined, int left, int right, int side) {
        if ((modifiers & combined) == 0) return 0;
        return side == SIDE_LEFT ? left : right;
    }

    static boolean modifiersMatch(int comboModifiers, int eventModifiers) {
        if (comboModifiers == ANY_MODIFIER) return true;
        int combo = normalizeModifiers(comboModifiers);
        int event = normalizeModifiers(eventModifiers);
        return matchModifierClass(combo, event, InputConstants.MOD_SHIFT)
                && matchModifierClass(combo, event, InputConstants.MOD_CONTROL)
                && matchModifierClass(combo, event, InputConstants.MOD_ALT)
                && matchModifierClass(combo, event, InputConstants.MOD_SUPER);
    }

    private static boolean matchModifierClass(int combo, int event, int combined) {
        int configured = combo & combined;
        int pressed = event & combined;
        if (configured == combined) return pressed != 0;
        if (configured != 0) return (pressed & configured) == configured;
        return pressed == 0;
    }

    public static void tickMaintainedKeyBindings() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.gui.screen() != null || !minecraft.isWindowActive()) {
            cancelMaintainedKeyBindings();
            return;
        }
        for (var keyName : List.copyOf(ACTIVE_MAINTAINED_BINDINGS.keySet())) {
            var maintained = MAINTAINED_BINDINGS.get(keyName);
            if (maintained == null || !maintained.canRemainActive.getAsBoolean()) {
                cancelMaintainedKeyBinding(keyName);
                continue;
            }
            var active = ACTIVE_MAINTAINED_BINDINGS.get(keyName);
            if (active != null && ++active.ticksUntilHeartbeat >= 20) {
                active.ticksUntilHeartbeat = 0;
                maintained.onHeartbeat.accept(active.context);
            }
        }
    }

    public static void cancelMaintainedKeyBindings() {
        for (var keyName : List.copyOf(ACTIVE_MAINTAINED_BINDINGS.keySet())) {
            cancelMaintainedKeyBinding(keyName);
        }
        for (var keyName : List.copyOf(ACTIVE_PAIRED_BINDINGS.keySet())) {
            cancelPairedBinding(keyName, true);
        }
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
        if (Dev.HAS_IM_GUI
                && ImGuiUtilApi.INSTANCE.wantCaptureKeyboard()
                && key != SDLScancode.SDL_SCANCODE_ESCAPE) {
            ci.cancel();
            return;
        }
        KEYBOARD_STATE.put(key, action);

        if (handleRebindInput(InputType.KEYBOARD, key, action, event.modifiers(), ci)) return;

        var dispatchModifiers = modifiersForDispatch(InputType.KEYBOARD, key, action, event.modifiers());

        var inputEvent = new KeyInputEvent(key, event.keycode(), action, event.modifiers());
        NeoForge.EVENT_BUS.post(inputEvent);

        if (inputEvent.isCanceled()) {
            if (action == InputConstants.RELEASE) KeyMapping.set(InputConstants.getKey(event), false);
            ci.cancel();
            return;
        }

        dispatch(InputType.KEYBOARD, key, action, dispatchModifiers);
    }

    public static void handleMouseButton(int button, int action, int modifiers, CallbackInfo ci) {
        currentMouseButton = button;
        currentMouseAction = action;
        currentMouseModifier = modifiers;
        MOUSE_STATE.put(button, action);

        if (handleRebindInput(InputType.MOUSE, button, action, modifiers, ci)) return;

        var dispatchModifiers = modifiersForDispatch(InputType.MOUSE, button, action, modifiers);

        var event = new MouseButtonEvent(button, action, modifiers);
        NeoForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            if (action == InputConstants.RELEASE) {
                KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(button), false);
            }
            ci.cancel();
            return;
        }

        dispatch(InputType.MOUSE, button, action, dispatchModifiers);
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

    public static void updateImGuiMouseCapture(Minecraft minecraft) {
        if (!Dev.HAS_IM_GUI) return;
        var mouseHandler = minecraft.mouseHandler;
        var wantCapture = ImGuiUtilApi.INSTANCE.wantCaptureMouse();
        if (wantCapture) {
            if (mouseHandler.isMouseGrabbed()) {
                mouseHandler.releaseMouse();
                imguiReleasedMouse = true;
            }
        } else if (imguiReleasedMouse) {
            imguiReleasedMouse = false;
            if (minecraft.gui.screen() == null && minecraft.gameMode != null && !mouseHandler.isMouseGrabbed()) {
                mouseHandler.grabMouse();
            }
        }
    }

    static void dispatch(InputType eventType, int input, int action, int modifiers) {
        var context = new BindingContext(eventType, input, action, modifiers);
        for (var entry : EXCLUSIVE_BINDINGS.entrySet()) {
            var binding = KEY_BINDINGS.get(entry.getKey());
            if (binding != null && binding.enabled && entry.getValue().getAsBoolean()
                    && matches(binding.combo, eventType, input, action, modifiers)) {
                binding.handler.accept(context);
                return;
            }
        }
        for (var entry : KEY_BINDINGS.entrySet()) {
            var keyName = entry.getKey();
            var binding = entry.getValue();
            if (!binding.enabled || EXCLUSIVE_BINDINGS.containsKey(keyName)) continue;
            var combo = binding.combo;
            if (action == InputConstants.RELEASE
                    && consumeSuppressedPairedRelease(keyName, eventType, input)) {
                continue;
            }
            if (action == InputConstants.RELEASE
                    && dispatchActivePairedRelease(keyName, combo, context)) {
                continue;
            }
            if (action == InputConstants.RELEASE
                    && isReleaseForActiveMaintainedBinding(keyName, combo, eventType, input)) {
                binding.handler.accept(context);
                continue;
            }
            if (!matches(combo, eventType, input, action, modifiers)) continue;
            binding.handler.accept(context);
            if (action == InputConstants.PRESS) armPairedReleaseBindings(keyName, combo, context);
        }
    }

    private static void armPairedReleaseBindings(
            String startName, KeyCombination startCombo, BindingContext context
    ) {
        var startRoot = pairedStartRoot(startName);
        if (startRoot == null) return;
        for (var entry : KEY_BINDINGS.entrySet()) {
            var releaseName = entry.getKey();
            var releaseBinding = entry.getValue();
            if (!releaseBinding.enabled || !startRoot.equals(pairedReleaseRoot(releaseName))
                    || !sameGesture(startCombo, releaseBinding.combo)) continue;
            SUPPRESSED_PAIRED_RELEASES.removeIf(suppressed ->
                    suppressed.releaseName.equals(releaseName));
            ACTIVE_PAIRED_BINDINGS.putIfAbsent(
                    releaseName, new ActivePairedBinding(startName, context));
        }
    }

    private static boolean dispatchActivePairedRelease(
            String releaseName, KeyCombination combo, BindingContext context
    ) {
        var active = ACTIVE_PAIRED_BINDINGS.get(releaseName);
        if (active == null || combo.type != context.type
                || !(combo.keys.isEmpty() || combo.keys.contains(context.input)
                || active.context.input == context.input)) return false;
        ACTIVE_PAIRED_BINDINGS.remove(releaseName, active);
        var binding = KEY_BINDINGS.get(releaseName);
        if (binding != null) binding.handler.accept(context);
        return true;
    }

    private static void cancelPairedBindingsFor(String keyName) {
        for (var entry : List.copyOf(ACTIVE_PAIRED_BINDINGS.entrySet())) {
            if (entry.getKey().equals(keyName) || entry.getValue().startName.equals(keyName)) {
                cancelPairedBinding(entry.getKey(), true);
            }
        }
    }

    private static void cancelPairedBinding(String releaseName, boolean suppressPhysicalRelease) {
        var active = ACTIVE_PAIRED_BINDINGS.remove(releaseName);
        if (active == null) return;
        var binding = KEY_BINDINGS.get(releaseName);
        if (binding != null) binding.handler.accept(new BindingContext(
                active.context.type,
                active.context.input,
                InputConstants.RELEASE,
                active.context.modifiers
        ));
        if (suppressPhysicalRelease) SUPPRESSED_PAIRED_RELEASES.add(new SuppressedPairedRelease(
                releaseName, active.context.type, active.context.input));
    }

    private static boolean consumeSuppressedPairedRelease(String releaseName, InputType type, int input) {
        return SUPPRESSED_PAIRED_RELEASES.remove(new SuppressedPairedRelease(releaseName, type, input));
    }

    private static boolean sameGesture(KeyCombination first, KeyCombination second) {
        return !first.unbound && !second.unbound
                && first.type == second.type
                && first.keys.equals(second.keys)
                && canonicalizeModifiers(first.modifiers) == canonicalizeModifiers(second.modifiers)
                && first.availableWhenScreen == second.availableWhenScreen;
    }

    private static @Nullable String pairedStartRoot(String name) {
        return stripPairedSuffix(name, "_start", ".start", "_press", ".press", "_charge", ".charge");
    }

    private static @Nullable String pairedReleaseRoot(String name) {
        return stripPairedSuffix(name, "_stop", ".stop", "_end", ".end", "_release", ".release");
    }

    private static @Nullable String stripPairedSuffix(String name, String... suffixes) {
        for (var suffix : suffixes) {
            if (name.endsWith(suffix)) return name.substring(0, name.length() - suffix.length());
        }
        return null;
    }

    private static void handleMaintainedInput(String keyName, BindingContext context) {
        var maintained = MAINTAINED_BINDINGS.get(keyName);
        if (maintained == null) return;
        if (context.action == InputConstants.PRESS) {
            if (ACTIVE_MAINTAINED_BINDINGS.containsKey(keyName)
                    || !maintained.canRemainActive.getAsBoolean()) return;
            ACTIVE_MAINTAINED_BINDINGS.put(keyName, new ActiveMaintainedBinding(context));
            maintained.onStart.accept(context);
        } else if (context.action == InputConstants.RELEASE) {
            var active = ACTIVE_MAINTAINED_BINDINGS.remove(keyName);
            if (active != null) maintained.onStop.accept(context);
        }
    }

    private static boolean isReleaseForActiveMaintainedBinding(
            String keyName, KeyCombination combo, InputType type, int input
    ) {
        var active = ACTIVE_MAINTAINED_BINDINGS.get(keyName);
        if (active == null || combo.type != type) return false;
        return combo.keys.isEmpty() || combo.keys.contains(input) || active.context.input == input;
    }

    private static void cancelMaintainedKeyBinding(String keyName) {
        var active = ACTIVE_MAINTAINED_BINDINGS.remove(keyName);
        var maintained = MAINTAINED_BINDINGS.get(keyName);
        if (active == null || maintained == null) return;
        maintained.onStop.accept(new BindingContext(
                active.context.type,
                active.context.input,
                InputConstants.RELEASE,
                active.context.modifiers
        ));
    }

    static void dispatchMaintainedForTesting(String keyName, BindingContext context) {
        handleMaintainedInput(keyName, context);
    }

    static void dispatchPairedForTesting(String keyName, BindingContext context) {
        var binding = KEY_BINDINGS.get(keyName);
        if (binding == null || !binding.enabled) return;
        if (context.action == InputConstants.RELEASE
                && (consumeSuppressedPairedRelease(keyName, context.type, context.input)
                || dispatchActivePairedRelease(keyName, binding.combo, context))) return;
        binding.handler.accept(context);
        if (context.action == InputConstants.PRESS) {
            armPairedReleaseBindings(keyName, binding.combo, context);
        }
    }

    static int modifiersForDispatch(InputType type, int input, int action, int modifiers) {
        var inputKey = new InputKey(type, input);
        var normalized = normalizeModifiers(modifiers);
        if (action == InputConstants.PRESS) {
            PRESS_MODIFIER_SNAPSHOTS.put(inputKey, normalized);
            return normalized;
        }
        if (action == InputConstants.RELEASE) {
            var pressedModifiers = PRESS_MODIFIER_SNAPSHOTS.remove(inputKey);
            return pressedModifiers == null ? normalized : pressedModifiers;
        }
        return PRESS_MODIFIER_SNAPSHOTS.getOrDefault(inputKey, normalized);
    }

    static void clearModifierSnapshotsForTesting() {
        PRESS_MODIFIER_SNAPSHOTS.clear();
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
            var oldCombo = getKeyBinding(session.keyName);
            rebindSession = null;
            if (oldCombo != null) setKeyBinding(session.keyName, unbound(oldCombo));
            session.onFinished.run();
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

        var normalizedModifiers = canonicalizeModifiers(modifiers);
        var newCombo = combo(type, input, oldCombo.action, normalizedModifiers, oldCombo.availableWhenScreen);
        SUPPRESSED_RELEASES.add(inputKey);
        rebindSession = null;
        setKeyBinding(session.keyName, newCombo);
        session.onFinished.run();
        ci.cancel();
        return true;
    }

    private static boolean isModifierKey(int key) {
        return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT
                || key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
                || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT
                || key == InputConstants.KEY_LGUI || key == InputConstants.KEY_RGUI;
    }

    private static int normalizeModifiers(int modifiers) {
        return modifiers & (
                InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL | InputConstants.MOD_ALT | InputConstants.MOD_SUPER
        );
    }

    /**
     * Canonicalizes modifiers to the combined per-class masks, dropping side information.
     * Used for gesture comparisons and for storing side-agnostic ("either side") bindings.
     */
    public static int canonicalizeModifiers(int modifiers) {
        if (modifiers == ANY_MODIFIER) return ANY_MODIFIER;
        int masked = normalizeModifiers(modifiers);
        int canonical = 0;
        if ((masked & InputConstants.MOD_SHIFT) != 0) canonical |= InputConstants.MOD_SHIFT;
        if ((masked & InputConstants.MOD_CONTROL) != 0) canonical |= InputConstants.MOD_CONTROL;
        if ((masked & InputConstants.MOD_ALT) != 0) canonical |= InputConstants.MOD_ALT;
        if ((masked & InputConstants.MOD_SUPER) != 0) canonical |= InputConstants.MOD_SUPER;
        return canonical;
    }

    private static void releaseVanillaKey(InputType type, int input) {
        if (type == InputType.KEYBOARD) {
            KeyMapping.set(InputConstants.Type.KEYBOARD.getOrCreate(input), false);
        } else {
            KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(input), false);
        }
    }

    private static String displayName(InputType type, int key) {
        var inputKey = type == InputType.KEYBOARD
                ? InputConstants.Type.KEYBOARD.getOrCreate(key)
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
        if (combo.unbound) return false;
        if (combo.type != eventType) return false;
        if (!combo.availableWhenScreen && ClientUtil.hasScreen()) return false;
        if (combo.action != ANY_ACTION && combo.action != action) return false;
        if (combo.modifiers != ANY_MODIFIER
                && !modifiersMatch(combo.modifiers, modifiers)) return false;
        if (combo.keys.isEmpty()) return true;
        if (!combo.keys.contains(input)) return false;
        if (combo.action == ANY_ACTION || action == InputConstants.RELEASE) {
            return combo.keys.stream().allMatch(key ->
                    key == input || stateOf(eventType).getOrDefault(key, InputConstants.RELEASE) != InputConstants.RELEASE
            );
        }
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
            boolean availableWhenScreen,
            boolean unbound
    ) {
        private static String keyName(InputType type, int key) {
            return switch (type) {
                case MOUSE -> InputConstants.Type.MOUSE.getOrCreate(key).getDisplayName().getString();
                case KEYBOARD -> InputConstants.getKey(new KeyEvent(key, -1, 0))
                        .getDisplayName().getString();
            };
        }

        public String displayName() {
            if (unbound) {
                return "None";
            }
            if (keys.isEmpty()) {
                return "Any";
            }
            var builder = new StringBuilder();
            appendModifierName(builder, modifiers, InputConstants.MOD_SHIFT, SDLKeycode.SDL_KMOD_LSHIFT, "Shift");
            appendModifierName(builder, modifiers, InputConstants.MOD_CONTROL, SDLKeycode.SDL_KMOD_LCTRL, "Ctrl");
            appendModifierName(builder, modifiers, InputConstants.MOD_ALT, SDLKeycode.SDL_KMOD_LALT, "Alt");
            appendModifierName(builder, modifiers, InputConstants.MOD_SUPER, SDLKeycode.SDL_KMOD_LGUI, "Super");
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

    private record MaintainedBinding(
            Consumer<BindingContext> onStart,
            Consumer<BindingContext> onStop,
            Consumer<BindingContext> onHeartbeat,
            BooleanSupplier canRemainActive
    ) {
    }

    private static final class ActiveMaintainedBinding {
        private final BindingContext context;
        private int ticksUntilHeartbeat;

        private ActiveMaintainedBinding(BindingContext context) {
            this.context = context;
        }
    }

    private record ActivePairedBinding(String startName, BindingContext context) {
    }

    private record SuppressedPairedRelease(String releaseName, InputType type, int input) {
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
