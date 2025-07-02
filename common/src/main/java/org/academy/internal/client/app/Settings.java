package org.academy.internal.client.app;

import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.hud.DataTerminalHUD;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.KeyEvent;
import org.academy.api.client.input.MouseButtonEvent;
import org.academy.api.client.renderer.RenderTypes;
import org.academy.api.client.util.ClientUtil;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Settings implements DataTerminalHUD.App {
    public static final DataTerminalHUD.App INSTANCE = new Settings();

    private static PanelWidget keybindingControlPanel = null;
    private static PanelWidget activeKeybindingEntry = null;
    private static String activeKeyName = null;
    private static PanelWidget keySelectionButton = null;
    private static boolean isListeningForKey = false;
    private static PanelWidget keyboardBtn, mouseBtn;
    private static PanelWidget shiftModBtn, ctrlModBtn, altModBtn;
    private static PanelWidget keySelectionPanel = null;
    private static final Map<Integer, PanelWidget> keyboardButtonMap = new HashMap<>();
    private static final Map<Integer, PanelWidget> mouseButtonMap = new HashMap<>();

    private static final Runnable ON_CLICK = () -> {
        activeKeybindingEntry = null;
        activeKeyName = null;
        isListeningForKey = false;
        DataTerminalHUD.setAppArea(createSettingsPanel());
    };

    private static PanelWidget createSettingsPanel() {
        PanelWidget appArea = new PanelWidget(0, 0, 200, 225);

        BlendQuadWidget back = new BlendQuadWidget(0, 0, appArea.getWidth(), appArea.getHeight());
        back.drawLine = false;
        back.alpha = 0.25f;
        appArea.addChild("back", back);

        LayeredPanelWidget main = new LayeredPanelWidget(0, 0, appArea.getWidth(), appArea.getHeight());
        appArea.addChild("main", main);
        {
            SmoothScrollPanelWidget generalPanel = createGeneralPanel();
            main.addChild("panel_general", generalPanel);

            SmoothScrollPanelWidget keybindingsPanel = createKeybindingsPanel(appArea);
            main.addChild("panel_keybindings", keybindingsPanel);

            createTabBar(main, generalPanel, keybindingsPanel);
        }

        return appArea;
    }

    private static void createTabBar(AbstractContainerWidget parent, Widget generalPanel, Widget keybindingsPanel) {
        LayeredPanelWidget tabBar = new LayeredPanelWidget(5, 5, 190, 20);
        parent.addChild("tab_bar", tabBar);

        PanelWidget generalButton = createTabButton("General", 0, 90);
        PanelWidget keybindingsButton = createTabButton("Keybindings", 95, 95);

        Runnable showGeneral = () -> {
            generalPanel.setVisible(true);
            generalPanel.setEnabled(true);
            keybindingsPanel.setVisible(false);
            keybindingsPanel.setEnabled(false);
            generalButton.<BlendQuadWidget>getChildUnSafe("back").alpha = 0.6f;
            keybindingsButton.<BlendQuadWidget>getChildUnSafe("back").alpha = 0.3f;
        };

        Runnable showKeybindings = () -> {
            generalPanel.setVisible(false);
            generalPanel.setEnabled(false);
            keybindingsPanel.setVisible(true);
            keybindingsPanel.setEnabled(true);
            generalButton.<BlendQuadWidget>getChildUnSafe("back").alpha = 0.3f;
            keybindingsButton.<BlendQuadWidget>getChildUnSafe("back").alpha = 0.6f;
        };

        generalButton.<ImageButtonWidget>getChildUnSafe("button_logic").onPress = showGeneral;
        keybindingsButton.<ImageButtonWidget>getChildUnSafe("button_logic").onPress = showKeybindings;

        tabBar.addChild("btn_general", generalButton);
        tabBar.addChild("btn_keybindings", keybindingsButton);

        showGeneral.run();
    }

    private static PanelWidget createTabButton(String text, float x, float width) {
        PanelWidget panel = new PanelWidget(x, 0, width, 20);
        {
            panel.addChild("button_logic", new ImageButtonWidget(0, 0, width, 20, null, () -> {
            }));

            BlendQuadWidget back = new BlendQuadWidget(0, 0, width, 20);
            back.drawLine = false;
            panel.addChild("back", back);

            LayeredPanelWidget layered = new LayeredPanelWidget(0, 0, width, 20);
            layered.setEnabled(false);
            panel.addChild("layered", layered);
            {
                AutoScaleLabelWidget label = new AutoScaleLabelWidget(text, 0, 0, width, true);
                label.dropShadow = false;
                label.setY((20 - label.getHeight()) / 2f);
                layered.addChild("text", label);
            }
        }

        return panel;
    }

    private static SmoothScrollPanelWidget createGeneralPanel() {
        SmoothScrollPanelWidget panel = new SmoothScrollPanelWidget(5, 30, 190, 190);

        float currentY = 10f;

        panel.addChild("label_blur", new AutoScaleLabelWidget("Blur Radius", 0, currentY, 190, true));
        currentY += 20;

        Consumer<Float> blurRadiusUpdater = val -> {
            DataTerminalHUD.config.blurRadius = val;
            AcademyCraftClient.CLIENT_CONFIG.save();
        };
        panel.addChild("container_blur", createSliderWithLabel(5, currentY, 180, 8, DataTerminalHUD.config.blurRadius, 0.0f, 20.0f, blurRadiusUpdater));
        currentY += 20;

        panel.addChild("label_sensitivity", new AutoScaleLabelWidget("Mouse Sensitivity", 0, currentY, 190, true));
        currentY += 20;

        Consumer<Float> sensitivityUpdater = val -> {
            DataTerminalHUD.config.mouseSensitivity = val;
            AcademyCraftClient.CLIENT_CONFIG.save();
        };
        panel.addChild("container_sensitivity", createSliderWithLabel(5, currentY, 180, 8, DataTerminalHUD.config.mouseSensitivity, 0.1f, 2.0f, sensitivityUpdater));
        currentY += 20;

        panel.addChild("label_blur_toggle_title", new AutoScaleLabelWidget("Background Blur", 0, currentY, 190, true));
        currentY += 20;

        Supplier<Boolean> blurStateSupplier = () -> DataTerminalHUD.config.enableBlur;
        Runnable blurToggleAction = () -> {
            DataTerminalHUD.config.enableBlur = !DataTerminalHUD.config.enableBlur;
            AcademyCraftClient.CLIENT_CONFIG.save();
        };
        panel.addChild("blur_toggle_button", createToggleButton(5, currentY, 180, 20, blurStateSupplier, blurToggleAction));

        return panel;
    }

    private static LayeredPanelWidget createSliderWithLabel(float x, float y, float width, float height, float initialValue, float min, float max, Consumer<Float> onValueChanged) {
        LayeredPanelWidget container = new LayeredPanelWidget(x, y, width, height);
        SliderWidget slider = new SliderWidget(0, 0, width - 50, height, min, max, initialValue);
        container.addChild("slider", slider);

        AutoScaleLabelWidget valueLabel = new AutoScaleLabelWidget(String.format("%.2f", initialValue), slider.getWidth() + 5, 0, 45, true);
        valueLabel.dropShadow = false;
        valueLabel.scale = 0.75f;
        valueLabel.setY((height - valueLabel.getHeight() * valueLabel.scale) / 2f);
        container.addChild("label_value", valueLabel);

        slider.onValueChanged = val -> {
            onValueChanged.accept(val);
            valueLabel.setText(String.format("%.2f", val));
        };
        return container;
    }

    private static PanelWidget createToggleButton(float x, float y, float w, float h, Supplier<Boolean> stateSupplier, Runnable onPress) {
        PanelWidget buttonPanel = new PanelWidget(x, y, w, h);

        AutoScaleLabelWidget text = new AutoScaleLabelWidget(stateSupplier.get() ? "On" : "Off", 0, 0, w, true);
        Runnable toggleAction = () -> {
            onPress.run();
            text.setText(stateSupplier.get() ? "On" : "Off");
        };

        ImageButtonWidget logic = new ImageButtonWidget(0, 0, w, h, null, toggleAction);
        buttonPanel.addChild("button_logic", logic);

        BlendQuadWidget back = new BlendQuadWidget(0, 0, w, h);
        back.drawLine = false;
        back.alpha = 0.3f;
        buttonPanel.addChild("back", back);

        LayeredPanelWidget layered = new LayeredPanelWidget(0, 0, w, h);
        layered.setEnabled(false);
        buttonPanel.addChild("layered", layered);
        {
            text.dropShadow = false;
            text.setY((h - text.getHeight() * text.scale) / 2f);
            layered.addChild("text", text);
        }

        return buttonPanel;
    }

    private static SmoothScrollPanelWidget createKeybindingsPanel(AbstractContainerWidget parent) {
        SmoothScrollPanelWidget keybindingsPanel = new SmoothScrollPanelWidget(5, 30, 190, 190);
        keybindingsPanel.setVisible(false);
        keybindingsPanel.setEnabled(false);

        createKeySelectionPanel(parent);

        keybindingControlPanel = createKeybindingControlPanel();
        keybindingsPanel.addChild("control_panel", keybindingControlPanel);

        List<String> allKeyNames = new ArrayList<>(InputSystem.KEY_BINDINGS.keySet());
        Collections.sort(allKeyNames);

        for (String keyName : allKeyNames) {
            PanelWidget keybindingPanel = createKeybindingWidget(keyName, keyName);
            keybindingsPanel.addChild("keybinding_" + keyName, keybindingPanel);
        }
        updateKeybindingLayout(keybindingsPanel);
        return keybindingsPanel;
    }

    private static void setKeyButtonSelected(PanelWidget btn, boolean selected) {
        if (btn == null) return;
        BlendQuadWidget back = btn.getChildUnSafe("back");
        if (selected) {
            back.alpha = 0.8f;
            back.red = 0.7f;
            back.green = 0.9f;
        } else {
            back.alpha = 0.5f;
            back.red = 1.0f;
            back.green = 1.0f;
        }
        back.blue = 1.0f;
    }

    private static void updateKeySelectionHighlights() {
        keyboardButtonMap.values().forEach(btn -> setKeyButtonSelected(btn, false));
        mouseButtonMap.values().forEach(btn -> setKeyButtonSelected(btn, false));

        if (activeKeyName == null) return;
        InputSystem.InputPair binding = getKeyBindingFromInputSystem(activeKeyName);
        if (binding == null || binding.keyInfo.inputs.isEmpty() || binding.keyInfo.inputs.contains(-1)) return;

        Map<Integer, PanelWidget> targetMap = binding.inputType == InputSystem.InputType.KEYBOARD ? keyboardButtonMap : mouseButtonMap;

        for (int keyCode : binding.keyInfo.inputs) {
            setKeyButtonSelected(targetMap.get(keyCode), true);
        }
    }

    private static void setBackgroundControlsEnabled(boolean enabled) {
        if (keySelectionPanel == null || keySelectionPanel.getParent() == null) return;
        AbstractContainerWidget appArea = (AbstractContainerWidget) keySelectionPanel.getParent();
        for (Widget child : appArea.getChildren().values()) {
            if (child != keySelectionPanel) {
                child.setEnabled(enabled);
            }
        }
    }

    private static void createKeySelectionPanel(AbstractContainerWidget parent) {
        keySelectionPanel = new PanelWidget(0, 0, 180, 190);
        keySelectionPanel.setZ(keySelectionPanel.getZ() + 10);
        keySelectionPanel.setX((parent.getWidth() - keySelectionPanel.getWidth()) / 2f);
        keySelectionPanel.setY((parent.getHeight() - keySelectionPanel.getHeight()) / 2f);
        keySelectionPanel.setVisible(false);
        keySelectionPanel.setEnabled(false);
        parent.addChild("key_selection_panel", keySelectionPanel);
        {
            BlendQuadWidget back = new BlendQuadWidget(0, 0, keySelectionPanel.getWidth(), keySelectionPanel.getHeight());
            back.alpha = 0.4f;
            back.drawLine = false;
            keySelectionPanel.addChild("back", back);

            LayeredPanelWidget layered = new LayeredPanelWidget(0, 0, keySelectionPanel.getWidth(), keySelectionPanel.getHeight());
            layered.setEnabled(false);
            keySelectionPanel.addChild("key_selection_panel_layered", layered);
            {
                AutoScaleLabelWidget closeLabel = new AutoScaleLabelWidget("X", keySelectionPanel.getWidth() - 25, 10, 20, true);
                closeLabel.dropShadow = false;
                layered.addChild("text", closeLabel);

                AutoScaleLabelWidget title = new AutoScaleLabelWidget("Select Key", 5, 5, keySelectionPanel.getWidth() - 10);
                title.dropShadow = false;
                layered.addChild("title", title);
            }

            LayeredPanelWidget closeButtonPanel = new LayeredPanelWidget(keySelectionPanel.getWidth() - 25, 5, 20, 15);
            Runnable closeAction = () -> {
                setBackgroundControlsEnabled(true);
                keySelectionPanel.setVisible(false);
                keySelectionPanel.setEnabled(false);
            };
            ImageButtonWidget closeButtonLogic = new ImageButtonWidget(0, 0, 20, 15, null, closeAction);
            closeButtonPanel.addChild("button_logic", closeButtonLogic);


            BlendQuadWidget closeBack = new BlendQuadWidget(0, 0, 20, 15);
            closeBack.drawLine = false;
            closeBack.alpha = 0.5f;
            closeButtonPanel.addChild("back", closeBack);
            keySelectionPanel.addChild("close_btn", closeButtonPanel);

            SmoothScrollPanelWidget keysContainer = new SmoothScrollPanelWidget(5, 25, 170, 135);
            keySelectionPanel.addChild("keys_container", keysContainer);

            var scrollBar = new VerticalScrollBarWidget(keysContainer, keysContainer.getX() + keysContainer.getWidth() - 5, keysContainer.getY(), 5, keysContainer.getHeight());
            keySelectionPanel.addChild("scroll_bar", scrollBar);
            scrollBar.setThumbColor(0x50AAAAAA);
            scrollBar.setTrackColor(0x70202020);
            scrollBar.setZ(scrollBar.getZ() + 1);

            var listenBtnPanel = new LayeredPanelWidget(5, 165, 170, 20);
            Runnable listenAction = () -> {
                isListeningForKey = true;
                setBackgroundControlsEnabled(true);
                keySelectionPanel.setVisible(false);
                keySelectionPanel.setEnabled(false);
                keySelectionButton.<PanelWidget>getChildUnSafe("layered").<AutoScaleLabelWidget>getChildUnSafe("text").setText("> ... <");
            };
            ImageButtonWidget listenButtonLogic = new ImageButtonWidget(0, 0, 170, 20, null, listenAction);
            listenBtnPanel.addChild("button_logic", listenButtonLogic);
            BlendQuadWidget listenBtnBack = new BlendQuadWidget(0, 0, 170, 20);
            listenBtnBack.alpha = 0.5f;
            listenBtnBack.drawLine = false;
            listenBtnPanel.addChild("back", listenBtnBack);
            var listenLabel = new AutoScaleLabelWidget("Listen for Input", 0, 6, listenBtnPanel.getWidth(), true);
            listenLabel.dropShadow = false;
            listenLabel.setZ(listenLabel.getZ() + 1);
            listenBtnPanel.addChild("text", listenLabel);
            keySelectionPanel.addChild("listen_btn", listenBtnPanel);

            populateKeySelectionContainer(keysContainer);
        }
    }

    private static void addKey(PanelWidget container, Map<Integer, PanelWidget> buttonMap, InputSystem.InputType type,
                               String name, int code, float x, float y, float w, float h) {
        var keyBtn = createSmallKeyButton(name, x, y, w, h, () -> {
            handleRebind(type, new LinkedHashSet<>(Set.of(code)));
            setBackgroundControlsEnabled(true);
            keySelectionPanel.setVisible(false);
            keySelectionPanel.setEnabled(false);
        });
        container.addChild("btn_" + type.name().toLowerCase() + "_" + name.toLowerCase().replace(" ", "_"), keyBtn);
        if (code != -1) {
            buttonMap.put(code, keyBtn);
        }
    }

    private static void populateKeySelectionContainer(SmoothScrollPanelWidget container) {
        keyboardButtonMap.clear();
        mouseButtonMap.clear();

        final float yStart = 5f;
        float currentY = yStart;

        var keyboardLabel = new AutoScaleLabelWidget("Keyboard", 5, currentY, container.getWidth() - 10);
        keyboardLabel.dropShadow = false;
        keyboardLabel.setZ(keyboardLabel.getZ() + 1);
        container.addChild("keyboard_label", keyboardLabel);
        currentY += 15;

        var keyboardPanel = new LayeredPanelWidget(0, currentY, container.getWidth() - 10, 0);
        var keyboardBack = new BlendQuadWidget(0, 0, keyboardPanel.getWidth(), 0);
        keyboardBack.alpha = 0.2f;
        keyboardBack.drawLine = false;
        keyboardPanel.addChild("back", keyboardBack);
        container.addChild("keyboard_panel", keyboardPanel);

        final float KEY_HEIGHT = 15f;
        final float H_SPACING = 2f;
        final float V_SPACING = 3f;
        final float KEY_WIDTH_UNIT = (keyboardPanel.getWidth() - 14 * H_SPACING) / 15f;

        float rowY = 5f;
        float rowX;
        float keyWidth;

        // --- Keyboard Block 1: Main keys ---
        var mainKeysPanel = new LayeredPanelWidget(5, 5, keyboardPanel.getWidth() - 10, 0);
        keyboardPanel.addChild("main_keys_panel", mainKeysPanel);
        float mainKeysY = 0;

        // Row 2: Numbers
        rowX = 0f;
        keyWidth = KEY_WIDTH_UNIT; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "`", GLFW.GLFW_KEY_GRAVE_ACCENT, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        for (int i = 1; i <= 9; i++) { keyWidth = KEY_WIDTH_UNIT; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, String.valueOf(i), GLFW.GLFW_KEY_1 + i - 1, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING; }
        keyWidth = KEY_WIDTH_UNIT; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "0", GLFW.GLFW_KEY_0, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        keyWidth = KEY_WIDTH_UNIT; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "-", GLFW.GLFW_KEY_MINUS, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        keyWidth = KEY_WIDTH_UNIT; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "=", GLFW.GLFW_KEY_EQUAL, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        keyWidth = mainKeysPanel.getWidth() - rowX; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "BACK", GLFW.GLFW_KEY_BACKSPACE, rowX, mainKeysY, keyWidth, KEY_HEIGHT);
        mainKeysY += KEY_HEIGHT + V_SPACING;

        // Row 3: QWERTY
        rowX = 0f;
        keyWidth = KEY_WIDTH_UNIT * 1.5f; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "TAB", GLFW.GLFW_KEY_TAB, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        String[] row3keys = {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]"};
        int[] row3codes = {GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET};
        for(int i=0; i<row3keys.length; i++){ keyWidth = KEY_WIDTH_UNIT; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, row3keys[i], row3codes[i], rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING; }
        keyWidth = mainKeysPanel.getWidth() - rowX; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "\\", GLFW.GLFW_KEY_BACKSLASH, rowX, mainKeysY, keyWidth, KEY_HEIGHT);
        mainKeysY += KEY_HEIGHT + V_SPACING;

        // Row 4: ASDF
        rowX = 0f;
        keyWidth = KEY_WIDTH_UNIT * 1.75f; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "CAPS", GLFW.GLFW_KEY_CAPS_LOCK, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        String[] row4keys = {"A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'"};
        int[] row4codes = {GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE};
        for(int i=0; i<row4keys.length; i++){ keyWidth = KEY_WIDTH_UNIT; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, row4keys[i], row4codes[i], rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING; }
        keyWidth = mainKeysPanel.getWidth() - rowX; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "ENTER", GLFW.GLFW_KEY_ENTER, rowX, mainKeysY, keyWidth, KEY_HEIGHT);
        mainKeysY += KEY_HEIGHT + V_SPACING;

        // Row 5: ZXC
        rowX = 0f;
        keyWidth = KEY_WIDTH_UNIT * 2.25f; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "LSHIFT", GLFW.GLFW_KEY_LEFT_SHIFT, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        String[] row5keys = {"Z", "X", "C", "V", "B", "N", "M", ",", ".", "/"};
        int[] row5codes = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH};
        for(int i=0; i<row5keys.length; i++){ keyWidth = KEY_WIDTH_UNIT; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, row5keys[i], row5codes[i], rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING; }
        keyWidth = mainKeysPanel.getWidth() - rowX; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "RSHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT, rowX, mainKeysY, keyWidth, KEY_HEIGHT);
        mainKeysY += KEY_HEIGHT + V_SPACING;

        // Row 6: Modifiers
        rowX = 0f;
        keyWidth = KEY_WIDTH_UNIT * 1.5f; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "LCTRL", GLFW.GLFW_KEY_LEFT_CONTROL, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        keyWidth = KEY_WIDTH_UNIT * 1.25f; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "LALT", GLFW.GLFW_KEY_LEFT_ALT, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        keyWidth = keyboardPanel.getWidth() - (KEY_WIDTH_UNIT * 1.5f + KEY_WIDTH_UNIT * 1.25f + KEY_WIDTH_UNIT * 1.25f + KEY_WIDTH_UNIT * 1.5f) - H_SPACING*4;
        addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "SPACE", GLFW.GLFW_KEY_SPACE, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        keyWidth = KEY_WIDTH_UNIT * 1.25f; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "RALT", GLFW.GLFW_KEY_RIGHT_ALT, rowX, mainKeysY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        keyWidth = KEY_WIDTH_UNIT * 1.5f; addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "RCTRL", GLFW.GLFW_KEY_RIGHT_CONTROL, rowX, mainKeysY, keyWidth, KEY_HEIGHT);
        mainKeysY += KEY_HEIGHT + 5f;

        mainKeysPanel.setHeight(mainKeysY);

        // --- Keyboard Block 2: Side keys ---
        var sideKeysPanel = new LayeredPanelWidget(5, mainKeysY + 10, keyboardPanel.getWidth() - 10, 0);
        keyboardPanel.addChild("side_keys_panel", sideKeysPanel);
        float sideKeysY = 0;

        float arrowsStartX = sideKeysPanel.getWidth() - (3 * KEY_WIDTH_UNIT + 2 * H_SPACING);
        addKey(sideKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "UP", GLFW.GLFW_KEY_UP, arrowsStartX + KEY_WIDTH_UNIT + H_SPACING, sideKeysY - KEY_HEIGHT - V_SPACING, KEY_WIDTH_UNIT, KEY_HEIGHT);
        addKey(sideKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "LEFT", GLFW.GLFW_KEY_LEFT, arrowsStartX, sideKeysY, KEY_WIDTH_UNIT, KEY_HEIGHT);
        addKey(sideKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "DOWN", GLFW.GLFW_KEY_DOWN, arrowsStartX + KEY_WIDTH_UNIT + H_SPACING, sideKeysY, KEY_WIDTH_UNIT, KEY_HEIGHT);
        addKey(sideKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "RIGHT", GLFW.GLFW_KEY_RIGHT, arrowsStartX + 2 * (KEY_WIDTH_UNIT + H_SPACING), sideKeysY, KEY_WIDTH_UNIT, KEY_HEIGHT);
        sideKeysY += KEY_HEIGHT + 5f;

        sideKeysPanel.setHeight(sideKeysY);

        float keyboardContentHeight = mainKeysY + sideKeysY + 10;
        keyboardPanel.setHeight(keyboardContentHeight);
        keyboardBack.setHeight(keyboardContentHeight);
        currentY += keyboardContentHeight + 10;

        // --- Mouse Layout ---
        var mouseLabel = new AutoScaleLabelWidget("Mouse", 5, currentY, container.getWidth() - 10);
        mouseLabel.dropShadow = false;
        mouseLabel.setZ(mouseLabel.getZ() + 1);
        container.addChild("mouse_label", mouseLabel);
        currentY += 15;

        var mousePanel = new LayeredPanelWidget(5, currentY, container.getWidth() - 15, 0);
        var mouseBack = new BlendQuadWidget(0, 0, mousePanel.getWidth(), 0);
        mouseBack.alpha = 0.2f;
        mouseBack.drawLine = false;
        mousePanel.setZ(mousePanel.getZ() + 1);
        mousePanel.addChild("back", mouseBack);
        container.addChild("mouse_panel", mousePanel);

        rowY = 5f;
        rowX = 0f;
        keyWidth = (mousePanel.getWidth() - 2 * H_SPACING) / 3f;
        addKey(mousePanel, mouseButtonMap, InputSystem.InputType.MOUSE, "Left", GLFW.GLFW_MOUSE_BUTTON_LEFT, rowX, rowY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        addKey(mousePanel, mouseButtonMap, InputSystem.InputType.MOUSE, "Right", GLFW.GLFW_MOUSE_BUTTON_RIGHT, rowX, rowY, keyWidth, KEY_HEIGHT); rowX += keyWidth + H_SPACING;
        addKey(mousePanel, mouseButtonMap, InputSystem.InputType.MOUSE, "Middle", GLFW.GLFW_MOUSE_BUTTON_MIDDLE, rowX, rowY, keyWidth, KEY_HEIGHT);
        rowY += KEY_HEIGHT + V_SPACING;

        rowX = 0f;
        keyWidth = (mousePanel.getWidth() - 4 * H_SPACING) / 5f;
        for(int i = 4; i <= 8; i++) {
            addKey(mousePanel, mouseButtonMap, InputSystem.InputType.MOUSE, "M"+i, GLFW.GLFW_MOUSE_BUTTON_4 + i - 4, rowX, rowY, keyWidth, KEY_HEIGHT);
            rowX += keyWidth + H_SPACING;
        }
        rowY += KEY_HEIGHT + 5f;

        float mouseContentHeight = rowY;
        mousePanel.setHeight(mouseContentHeight);
        mouseBack.setHeight(mouseContentHeight);
    }

    private static PanelWidget createKeybindingControlPanel() {
        var controlPanel = new PanelWidget(5, 0, 180, 85);
        controlPanel.setVisible(false);
        controlPanel.setEnabled(false);

        BlendQuadWidget controlBack = new BlendQuadWidget(0, 0, 180, 85);
        controlBack.alpha = 0.2f;
        controlBack.drawLine = false;
        controlPanel.addChild("back", controlBack);

        var controlPanelLayered = new LayeredPanelWidget(0, 0, 180, 85);
        controlPanel.addChild("control_panel_layered", controlPanelLayered);
        {
            AutoScaleLabelWidget typeLabel = new AutoScaleLabelWidget("Type:", 5, 8, 40);
            typeLabel.dropShadow = false;
            controlPanelLayered.addChild("type_label", typeLabel);

            AutoScaleLabelWidget keyLabel = new AutoScaleLabelWidget("Key:", 5, 33, 40);
            keyLabel.dropShadow = false;
            controlPanelLayered.addChild("key_label", keyLabel);

            AutoScaleLabelWidget modLabel = new AutoScaleLabelWidget("Mods:", 5, 63, 40);
            modLabel.dropShadow = false;
            controlPanelLayered.addChild("mod_label", modLabel);
        }

        var inputTypePanel = new LayeredPanelWidget(50, 5, 125, 20);
        controlPanel.addChild("input_type", inputTypePanel);

        Runnable keyboardAction = () -> {
            updateModifierButtonState(keyboardBtn, true);
            updateModifierButtonState(mouseBtn, false);
            updateBindingFromControls(true);
        };
        keyboardBtn = new PanelWidget(0, 0, 60, 15);
        var keyboardLogic = new ImageButtonWidget(0, 0, 60, 15, null, keyboardAction);
        keyboardBtn.addChild("button_logic", keyboardLogic);

        var keyboardBtnBack = new BlendQuadWidget(0, 0, 60, 15);
        keyboardBtnBack.drawLine = false;
        keyboardBtnBack.alpha = 0.4f;
        keyboardBtn.addChild("back", keyboardBtnBack);

        var keyboardBtnLayered = new LayeredPanelWidget(0, 0, 60, 15);
        keyboardBtnLayered.setEnabled(false);
        keyboardBtn.addChild("keyboard_btn_layered", keyboardBtnLayered);
        {
            AutoScaleLabelWidget keyboardLabel = new AutoScaleLabelWidget("Keyboard", 0, 4, keyboardBtn.getWidth(), true);
            keyboardLabel.dropShadow = false;
            keyboardBtnLayered.addChild("label", keyboardLabel);
        }

        inputTypePanel.addChild("keyboard", keyboardBtn);

        Runnable mouseAction = () -> {
            updateModifierButtonState(keyboardBtn, false);
            updateModifierButtonState(mouseBtn, true);
            updateBindingFromControls(true);
        };
        mouseBtn = new PanelWidget(65, 0, 60, 15);
        var mouseLogic = new ImageButtonWidget(0, 0, 60, 15, null, mouseAction);
        mouseBtn.addChild("button_logic", mouseLogic);

        var mouseBtnBack = new BlendQuadWidget(0, 0, 60, 15);
        mouseBtnBack.drawLine = false;
        mouseBtnBack.alpha = 0.4f;
        mouseBtn.addChild("back", mouseBtnBack);

        var mouseBtnLayered = new LayeredPanelWidget(0, 0, 60, 15);
        mouseBtnLayered.setEnabled(false);
        mouseBtn.addChild("mouse_btn_layered", mouseBtnLayered);
        {
            var mouseLabel = new AutoScaleLabelWidget("Mouse", 0, 4, mouseBtn.getWidth(), true);
            mouseLabel.dropShadow = false;
            mouseBtnLayered.addChild("label", mouseLabel);
        }

        inputTypePanel.addChild("mouse", mouseBtn);

        keySelectionButton = new LayeredPanelWidget(50, 30, 125, 20);
        controlPanel.addChild("key_select", keySelectionButton);
        {
            Runnable keySelectAction = () -> {
                setBackgroundControlsEnabled(false);
                keySelectionPanel.setVisible(true);
                keySelectionPanel.setEnabled(true);
                updateKeySelectionHighlights();
            };
            var keySelectionLogic = new ImageButtonWidget(0, 0, 125, 20, null, keySelectAction);
            keySelectionButton.addChild("button_logic", keySelectionLogic);

            var keySelectionButtonBack = new BlendQuadWidget(0, 0, 125, 20);
            keySelectionButtonBack.alpha = 0.3f;
            keySelectionButtonBack.drawLine = false;
            keySelectionButton.addChild("back", keySelectionButtonBack);

            var keySelectionButtonLayered = new LayeredPanelWidget(0, 0, 125, 20);
            keySelectionButtonLayered.setEnabled(false);
            keySelectionButton.addChild("layered", keySelectionButtonLayered);
            {
                var keySelectionButtonLabel = new AutoScaleLabelWidget("Set Key", 0, 0, keySelectionButton.getWidth(), true);
                keySelectionButtonLabel.dropShadow = false;
                keySelectionButtonLabel.setY((keySelectionButton.getHeight() - keySelectionButtonLabel.getHeight()) / 2f);
                keySelectionButtonLayered.addChild("text", keySelectionButtonLabel);
            }
        }

        var modsPanel = new LayeredPanelWidget(50, 60, 125, 20);
        controlPanel.addChild("mods_panel", modsPanel);

        shiftModBtn = createModifierButton("Shift", 0, () -> {
            toggleModifierButton(shiftModBtn);
            updateBindingFromControls(false);
        });
        ctrlModBtn = createModifierButton("Ctrl", 45, () -> {
            toggleModifierButton(ctrlModBtn);
            updateBindingFromControls(false);
        });
        altModBtn = createModifierButton("Alt", 90, () -> {
            toggleModifierButton(altModBtn);
            updateBindingFromControls(false);
        });

        modsPanel.addChild("shift", shiftModBtn);
        modsPanel.addChild("ctrl", ctrlModBtn);
        modsPanel.addChild("alt", altModBtn);

        return controlPanel;
    }

    private static PanelWidget createModifierButton(String text, float x, Runnable onPress) {
        PanelWidget panel = new PanelWidget(x, 0, 35, 15);
        {
            ImageButtonWidget buttonLogic = new ImageButtonWidget(0, 0, 35, 15, null, onPress);
            panel.addChild("button_logic", buttonLogic);

            BlendQuadWidget back = new BlendQuadWidget(0, 0, 35, 15);
            back.drawLine = false;
            panel.addChild("back", back);

            LayeredPanelWidget layered = new LayeredPanelWidget(0, 0, 35, 15);
            layered.setEnabled(false);
            panel.addChild("layered", layered);
            {
                AutoScaleLabelWidget label = new AutoScaleLabelWidget(text, 0, 4, 35, true);
                label.dropShadow = false;
                layered.addChild("label", label);
            }
        }
        return panel;
    }

    private static void toggleKeybindingControls(PanelWidget entry, String keyName) {
        AbstractContainerWidget list = (AbstractContainerWidget) entry.getParent();
        if (list == null) return;

        boolean wasActive = activeKeybindingEntry == entry;

        if (isListeningForKey) {
            isListeningForKey = false;
            resetRebindStateVisuals();
        }

        if (wasActive) {
            activeKeybindingEntry = null;
            activeKeyName = null;
            keybindingControlPanel.setVisible(false);
            keybindingControlPanel.setEnabled(false);
        } else {
            activeKeybindingEntry = entry;
            activeKeyName = keyName;
            updateControlPanelContents();
            keybindingControlPanel.setVisible(true);
            keybindingControlPanel.setEnabled(true);
        }
        updateKeybindingLayout(list);
    }

    private static void updateKeybindingLayout(AbstractContainerWidget list) {
        float currentY = 10f;
        List<Widget> entries = new ArrayList<>();
        for (Map.Entry<String, Widget> entry : list.getChildren().entrySet()) {
            if (!entry.getKey().equals("control_panel")) {
                entries.add(entry.getValue());
            }
        }

        for (Widget child : entries) {
            child.setY(currentY);
            currentY += child.getHeight() + 5;

            if (child == activeKeybindingEntry) {
                keybindingControlPanel.setY(currentY);
                currentY += keybindingControlPanel.getHeight() + 5;
            }
        }
    }

    private static void updateControlPanelContents() {
        if (activeKeyName == null) return;
        InputSystem.InputPair binding = getKeyBindingFromInputSystem(activeKeyName);
        if (binding == null) return;

        isListeningForKey = false;

        boolean isKeyboard = binding.inputType == InputSystem.InputType.KEYBOARD;
        updateModifierButtonState(keyboardBtn, isKeyboard);
        updateModifierButtonState(mouseBtn, !isKeyboard);

        AutoScaleLabelWidget keyText = keySelectionButton.<PanelWidget>getChildUnSafe("layered").getChildUnSafe("text");
        keyText.setText(formatKey(binding));

        Set<Integer> mods = binding.keyInfo.modifiers;
        updateModifierButton(shiftModBtn, mods.contains(GLFW.GLFW_MOD_SHIFT));
        updateModifierButton(ctrlModBtn, mods.contains(GLFW.GLFW_MOD_CONTROL));
        updateModifierButton(altModBtn, mods.contains(GLFW.GLFW_MOD_ALT));
    }

    private static void updateModifierButtonState(PanelWidget button, boolean active) {
        BlendQuadWidget back = button.getChildUnSafe("back");
        back.alpha = active ? 0.6f : 0.3f;
        if (active) {
            back.red = 0.8f;
            back.green = 0.8f;
        } else {
            back.red = 1.0f;
            back.green = 1.0f;
        }
        back.blue = 1.0f;
    }

    private static void updateBindingFromControls(boolean keyShouldBeReset) {
        if (activeKeyName == null) return;
        InputSystem.KeyBinding binding = InputSystem.KEY_BINDINGS.get(activeKeyName);
        if (binding == null) return;

        binding.inputPair.inputType = keyboardBtn.<BlendQuadWidget>getChildUnSafe("back").alpha == 0.6f ? InputSystem.InputType.KEYBOARD : InputSystem.InputType.MOUSE;

        LinkedHashSet<Integer> newMods = new LinkedHashSet<>();
        if (shiftModBtn.<BlendQuadWidget>getChildUnSafe("back").alpha == 0.6f) newMods.add(GLFW.GLFW_MOD_SHIFT);
        if (ctrlModBtn.<BlendQuadWidget>getChildUnSafe("back").alpha == 0.6f) newMods.add(GLFW.GLFW_MOD_CONTROL);
        if (altModBtn.<BlendQuadWidget>getChildUnSafe("back").alpha == 0.6f) newMods.add(GLFW.GLFW_MOD_ALT);
        binding.inputPair.keyInfo.modifiers = newMods;

        if (keyShouldBeReset) {
            binding.inputPair.keyInfo.inputs.clear();
            binding.inputPair.keyInfo.inputs.add(-1);
        }

        AcademyCraftClient.CLIENT_CONFIG.save();
        updateDisplayForActiveBinding();
    }

    private static PanelWidget createKeybindingWidget(String displayName, String keyName) {
        PanelWidget panel = new PanelWidget(0, 0, 180, 20);

        HoverLabelWidget label = new HoverLabelWidget(displayName, 5, 5, 95);
        label.dropShadow = false;
        panel.addChild("label", label);

        PanelWidget buttonPanel = new PanelWidget(105, 0, 75, 20);
        ImageButtonWidget buttonLogic = new ImageButtonWidget(0, 0, 75, 20, null, () -> toggleKeybindingControls(panel, keyName));
        buttonPanel.addChild("button_logic", buttonLogic);
        panel.addChild("button", buttonPanel);

        BlendQuadWidget buttonBack = new BlendQuadWidget(0, 0, buttonPanel.getWidth(), buttonPanel.getHeight());
        buttonBack.alpha = 0.3f;
        buttonBack.drawLine = false;
        buttonPanel.addChild("back", buttonBack);

        InputSystem.InputPair binding = getKeyBindingFromInputSystem(keyName);

        LayeredPanelWidget layered = new LayeredPanelWidget(0, 0, 180, 20);
        layered.setEnabled(false);
        buttonPanel.addChild("layered", layered);
        {
            AutoScaleLabelWidget keyLabel = new AutoScaleLabelWidget(formatKeybinding(binding), 3, 0, buttonPanel.getWidth() - 6, true);
            keyLabel.dropShadow = false;
            keyLabel.setY((buttonPanel.getHeight() - keyLabel.getHeight()) / 2);
            layered.addChild("text", keyLabel);
        }

        return panel;
    }

    private static InputSystem.InputPair getKeyBindingFromInputSystem(String keyName) {
        InputSystem.KeyBinding kb = InputSystem.KEY_BINDINGS.get(keyName);
        return kb != null ? kb.inputPair : null;
    }

    private static String modifierBitToString(int mod) {
        if (mod == GLFW.GLFW_MOD_SHIFT) return "SHIFT";
        if (mod == GLFW.GLFW_MOD_CONTROL) return "CTRL";
        if (mod == GLFW.GLFW_MOD_ALT) return "ALT";
        return "MOD" + mod;
    }

    private static String formatKeybinding(InputSystem.InputPair pair) {
        if (pair == null) return "None";
        StringBuilder sb = new StringBuilder();
        pair.keyInfo.modifiers.forEach(mod -> sb.append(modifierBitToString(mod)).append("+"));
        sb.append(formatKey(pair));
        return sb.toString();
    }

    private static String formatKey(InputSystem.InputPair pair) {
        if (pair == null || pair.keyInfo == null || pair.keyInfo.inputs.isEmpty() || pair.keyInfo.inputs.contains(-1))
            return "NONE";
        int key = pair.keyInfo.inputs.iterator().next();
        String name;
        if (pair.inputType == InputSystem.InputType.KEYBOARD) {
            name = GLFW.glfwGetKeyName(key, GLFW.glfwGetKeyScancode(key));
            if (name == null) name = "Key" + key;
        } else {
            name = "Mouse" + key;
        }
        return name.toUpperCase();
    }

    private Settings() {
    }

    @Override
    public RenderType getIcon() {
        return RenderTypes.RENDER_TYPE_APP_SETTINGS;
    }

    @Override
    public String getName() {
        return "Settings";
    }

    @Override
    public Runnable onClick() {
        return ON_CLICK;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButtonEvent(MouseButtonEvent event) {
        if (!isListeningForKey) return;
        if (event.action == GLFW.GLFW_PRESS) return;
        event.setCanceled(true);

        LinkedHashSet<Integer> keys = new LinkedHashSet<>();
        keys.add(event.button);
        handleRebind(InputSystem.InputType.MOUSE, keys);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyEvent(KeyEvent event) {
        if (!isListeningForKey) return;
        if (event.action == GLFW.GLFW_PRESS) return;
        if (event.key == GLFW.GLFW_KEY_ESCAPE) {
            isListeningForKey = false;
            resetRebindStateVisuals();
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);
        LinkedHashSet<Integer> keys = new LinkedHashSet<>();
        keys.add(event.key);
        handleRebind(InputSystem.InputType.KEYBOARD, keys);
    }

    private static void handleRebind(InputSystem.InputType newType, LinkedHashSet<Integer> newKeys) {
        InputSystem.KeyBinding binding = InputSystem.KEY_BINDINGS.get(activeKeyName);
        if (binding == null) {
            resetRebindStateVisuals();
            return;
        }

        binding.inputPair.inputType = newType;
        binding.inputPair.keyInfo.inputs = newKeys;

        isListeningForKey = false;
        AcademyCraftClient.CLIENT_CONFIG.save();
        ClientUtil.playDownSound();
        updateDisplayForActiveBinding();
    }

    private static void resetRebindStateVisuals() {
        if (activeKeyName != null) {
            updateControlPanelContents();
        } else {
            if (keySelectionButton != null) {
                keySelectionButton.<PanelWidget>getChildUnSafe("layered").<AutoScaleLabelWidget>getChildUnSafe("text").setText("Set Key");
            }
        }
    }

    private static void updateDisplayForActiveBinding() {
        if (activeKeybindingEntry != null && activeKeyName != null) {
            InputSystem.InputPair binding = getKeyBindingFromInputSystem(activeKeyName);
            PanelWidget buttonPanel = activeKeybindingEntry.getChildUnSafe("button");
            AutoScaleLabelWidget text = buttonPanel.<PanelWidget>getChildUnSafe("layered").getChildUnSafe("text");
            text.setText(formatKeybinding(binding));
            updateControlPanelContents();
        }
    }

    private static void updateModifierButton(PanelWidget button, boolean active) {
        BlendQuadWidget back = button.getChildUnSafe("back");
        back.alpha = active ? 0.6f : 0.3f;
        if (active) {
            back.red = 0.8f;
            back.green = 0.8f;
        } else {
            back.red = 1.0f;
            back.green = 1.0f;
        }
        back.blue = 1.0f;
    }

    private static void toggleModifierButton(PanelWidget button) {
        BlendQuadWidget back = button.getChildUnSafe("back");
        boolean nowSelected = back.alpha != 0.6f;
        back.alpha = nowSelected ? 0.6f : 0.3f;
        if (nowSelected) {
            back.red = 0.8f;
            back.green = 0.8f;
        } else {
            back.red = 1.0f;
            back.green = 1.0f;
        }
        back.blue = 1.0f;
    }

    private static LayeredPanelWidget createSmallKeyButton(String text, float x, float y, float w, float h, Runnable onPress) {
        var panel = new LayeredPanelWidget(x, y, w, h);
        ImageButtonWidget buttonLogic = new ImageButtonWidget(0, 0, w, h, null, onPress);
        panel.addChild("button_logic", buttonLogic);

        BlendQuadWidget back = new BlendQuadWidget(0, 0, w, h);
        back.drawLine = false;
        back.alpha = 0.5f;
        panel.addChild("back", back);

        LayeredPanelWidget layered = new LayeredPanelWidget(0, 0, w, h);
        layered.setEnabled(false);
        panel.addChild("layered", layered);
        {
            AutoScaleLabelWidget label = new AutoScaleLabelWidget(text, 0, 0, w, true);
            label.dropShadow = false;
            label.scale = 0.75f;
            label.setY((h - label.getHeight() * label.scale) / 2f);
            layered.addChild("label", label);
        }
        return panel;
    }
}