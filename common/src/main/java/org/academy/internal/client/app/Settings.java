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

    private static LayeredPanelWidget keybindingControlPanel = null;
    private static LayeredPanelWidget activeKeybindingEntry = null;
    private static String activeKeyName = null;
    private static LayeredPanelWidget keySelectionButton = null;
    private static boolean isListeningForKey = false;
    private static LayeredPanelWidget keyboardBtn, mouseBtn;
    private static LayeredPanelWidget shiftModBtn, ctrlModBtn, altModBtn;
    private static LayeredPanelWidget keySelectionPanel = null;
    private static final Map<Integer, LayeredPanelWidget> keyboardButtonMap = new HashMap<>();
    private static final Map<Integer, LayeredPanelWidget> mouseButtonMap = new HashMap<>();

    private static final Runnable ON_CLICK = () -> {
        activeKeybindingEntry = null;
        activeKeyName = null;
        isListeningForKey = false;
        DataTerminalHUD.setAppArea(createSettingsPanel());
    };

    private static LayeredPanelWidget createSettingsPanel() {
        LayeredPanelWidget appArea = new LayeredPanelWidget(0, 0, 200, 225);

        BlendQuadWidget back = new BlendQuadWidget(0, 0, appArea.getWidth(), appArea.getHeight());
        back.drawLine = false;
        back.alpha = 0.25f;
        appArea.addChild("back", back);

        SmoothScrollPanelWidget generalPanel = createGeneralPanel();
        appArea.addChild("panel_general", generalPanel);

        SmoothScrollPanelWidget keybindingsPanel = createKeybindingsPanel(appArea);
        appArea.addChild("panel_keybindings", keybindingsPanel);

        createTabBar(appArea, generalPanel, keybindingsPanel);

        return appArea;
    }

    private static void createTabBar(AbstractContainerWidget parent, Widget generalPanel, Widget keybindingsPanel) {
        LayeredPanelWidget tabBar = new LayeredPanelWidget(5, 5, 190, 20);
        tabBar.setZ(1);
        parent.addChild("tab_bar", tabBar);

        LayeredPanelWidget generalButton = createTabButton("General", 0, 90);
        LayeredPanelWidget keybindingsButton = createTabButton("Keybindings", 95, 95);

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

    private static LayeredPanelWidget createTabButton(String text, float x, float width) {
        LayeredPanelWidget panel = new LayeredPanelWidget(x, 0, width, 20);
        panel.addChild("button_logic", new ImageButtonWidget(0, 0, width, 20, null, () -> {
        }));

        BlendQuadWidget back = new BlendQuadWidget(0, 0, width, 20);
        back.drawLine = false;
        panel.addChild("back", back);

        AutoScaleLabelWidget label = new AutoScaleLabelWidget(text, 0, 0, width, true);
        label.dropShadow = false;
        label.setY((20 - label.getHeight()) / 2f);
        panel.addChild("text", label);

        return panel;
    }

    private static SmoothScrollPanelWidget createGeneralPanel() {
        SmoothScrollPanelWidget panel = new SmoothScrollPanelWidget(5, 30, 190, 190);
        panel.setZ(1);

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

    private static LayeredPanelWidget createToggleButton(float x, float y, float w, float h, Supplier<Boolean> stateSupplier, Runnable onPress) {
        LayeredPanelWidget buttonPanel = new LayeredPanelWidget(x, y, w, h);

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

        text.dropShadow = false;
        text.setY((h - text.getHeight() * text.scale) / 2f);
        buttonPanel.addChild("text", text);

        return buttonPanel;
    }

    private static SmoothScrollPanelWidget createKeybindingsPanel(AbstractContainerWidget parent) {
        SmoothScrollPanelWidget keybindingsPanel = new SmoothScrollPanelWidget(5, 30, 190, 190);
        keybindingsPanel.setVisible(false);
        keybindingsPanel.setEnabled(false);
        keybindingsPanel.setZ(1);

        createKeySelectionPanel(parent);

        keybindingControlPanel = createKeybindingControlPanel();
        keybindingsPanel.addChild("control_panel", keybindingControlPanel);

        List<String> allKeyNames = new ArrayList<>(InputSystem.KEY_BINDINGS.keySet());
        Collections.sort(allKeyNames);

        for (String keyName : allKeyNames) {
            LayeredPanelWidget keybindingPanel = createKeybindingWidget(keyName, keyName);
            keybindingsPanel.addChild("keybinding_" + keyName, keybindingPanel);
        }
        updateKeybindingLayout(keybindingsPanel);
        return keybindingsPanel;
    }

    private static void setKeyButtonSelected(LayeredPanelWidget btn, boolean selected) {
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

        Map<Integer, LayeredPanelWidget> targetMap = binding.inputType == InputSystem.InputType.KEYBOARD ? keyboardButtonMap : mouseButtonMap;

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
        keySelectionPanel = new LayeredPanelWidget(0, 0, 180, 190);
        keySelectionPanel.setX((parent.getWidth() - keySelectionPanel.getWidth()) / 2f);
        keySelectionPanel.setY((parent.getHeight() - keySelectionPanel.getHeight()) / 2f);
        keySelectionPanel.setVisible(false);
        keySelectionPanel.setEnabled(false);
        keySelectionPanel.setZ(10);
        parent.addChild("key_selection_panel", keySelectionPanel);

        BlendQuadWidget back = new BlendQuadWidget(0, 0, keySelectionPanel.getWidth(), keySelectionPanel.getHeight());
        back.alpha = 0.4f;
        back.drawLine = false;
        keySelectionPanel.addChild("back", back);

        LayeredPanelWidget closeButtonPanel = new LayeredPanelWidget(keySelectionPanel.getWidth() - 25, 5, 20, 15);
        Runnable closeAction = () -> {
            setBackgroundControlsEnabled(true);
            keySelectionPanel.setVisible(false);
            keySelectionPanel.setEnabled(false);
        };
        ImageButtonWidget closeButtonLogic = new ImageButtonWidget(0, 0, 20, 15, null, closeAction);
        closeButtonPanel.addChild("button_logic", closeButtonLogic);

        AutoScaleLabelWidget closeLabel = new AutoScaleLabelWidget("X", 0, 0, closeButtonPanel.getWidth(), true);
        closeLabel.dropShadow = false;
        closeLabel.setY((closeButtonPanel.getHeight() - closeLabel.getHeight()) / 2f);
        closeButtonPanel.addChild("text", closeLabel);
        BlendQuadWidget closeBack = new BlendQuadWidget(0, 0, 20, 15);
        closeBack.drawLine = false;
        closeBack.alpha = 0.5f;
        closeButtonPanel.addChild("back", closeBack);
        keySelectionPanel.addChild("close_btn", closeButtonPanel);
        closeButtonPanel.setZ(1);

        AutoScaleLabelWidget title = new AutoScaleLabelWidget("Select Key", 5, 5, keySelectionPanel.getWidth() - 10);
        title.dropShadow = false;
        keySelectionPanel.addChild("title", title);
        title.setZ(1);

        SmoothScrollPanelWidget keysContainer = new SmoothScrollPanelWidget(5, 25, 170, 135);
        keySelectionPanel.addChild("keys_container", keysContainer);
        keysContainer.setZ(1);

        VerticalScrollBarWidget scrollBar = new VerticalScrollBarWidget(keysContainer, keysContainer.getX() + keysContainer.getWidth() - 5, keysContainer.getY(), 5, keysContainer.getHeight());
        keySelectionPanel.addChild("scroll_bar", scrollBar);
        scrollBar.setThumbColor(0x50AAAAAA);
        scrollBar.setTrackColor(0x70202020);
        scrollBar.setZ(2);

        LayeredPanelWidget listenBtnPanel = new LayeredPanelWidget(5, 165, 170, 20);
        Runnable listenAction = () -> {
            isListeningForKey = true;
            setBackgroundControlsEnabled(true);
            keySelectionPanel.setVisible(false);
            keySelectionPanel.setEnabled(false);
            keySelectionButton.<AutoScaleLabelWidget>getChildUnSafe("text").setText("> ... <");
        };
        ImageButtonWidget listenButtonLogic = new ImageButtonWidget(0, 0, 170, 20, null, listenAction);
        listenBtnPanel.addChild("button_logic", listenButtonLogic);
        BlendQuadWidget listenBtnBack = new BlendQuadWidget(0, 0, 170, 20);
        listenBtnBack.alpha = 0.5f;
        listenBtnBack.drawLine = false;
        listenBtnPanel.addChild("back", listenBtnBack);
        AutoScaleLabelWidget listenLabel = new AutoScaleLabelWidget("Listen for Input", 0, 6, listenBtnPanel.getWidth(), true);
        listenLabel.dropShadow = false;
        listenBtnPanel.addChild("text", listenLabel);
        keySelectionPanel.addChild("listen_btn", listenBtnPanel);
        listenBtnPanel.setZ(1);

        populateKeySelectionContainer(keysContainer);
    }

    private static void populateKeySelectionContainer(SmoothScrollPanelWidget container) {
        keyboardButtonMap.clear();
        mouseButtonMap.clear();

        final float X_START = 5f;
        float yOffset = 5f;

        AutoScaleLabelWidget keyboardLabel = new AutoScaleLabelWidget("Keyboard", 5, yOffset, container.getWidth() - 10);
        keyboardLabel.dropShadow = false;
        container.addChild("keyboard_label", keyboardLabel);
        yOffset += 15;

        LayeredPanelWidget keyboardPanel = new LayeredPanelWidget(X_START, yOffset, container.getWidth() - 10, 0);
        BlendQuadWidget keyboardBack = new BlendQuadWidget(0, 0, keyboardPanel.getWidth(), 0);
        keyboardBack.alpha = 0.2f;
        keyboardBack.drawLine = false;
        keyboardPanel.addChild("back", keyboardBack);
        keyboardPanel.setZ(1);
        container.addChild("keyboard_panel", keyboardPanel);

        List<List<Key>> keyboardLayout = List.of(
                List.of(new Key("ESC", GLFW.GLFW_KEY_ESCAPE, 1.5f),
                        new Key("F1", GLFW.GLFW_KEY_F1, 1f), new Key("F2", GLFW.GLFW_KEY_F2, 1f), new Key("F3", GLFW.GLFW_KEY_F3, 1f), new Key("F4", GLFW.GLFW_KEY_F4, 1f),
                        new Key("", -1, 0.5f),
                        new Key("F5", GLFW.GLFW_KEY_F5, 1f), new Key("F6", GLFW.GLFW_KEY_F6, 1f), new Key("F7", GLFW.GLFW_KEY_F7, 1f), new Key("F8", GLFW.GLFW_KEY_F8, 1f),
                        new Key("", -1, 0.5f),
                        new Key("F9", GLFW.GLFW_KEY_F9, 1f), new Key("F10", GLFW.GLFW_KEY_F10, 1f), new Key("F11", GLFW.GLFW_KEY_F11, 1f), new Key("F12", GLFW.GLFW_KEY_F12, 1f)),
                List.of(new Key("`", GLFW.GLFW_KEY_GRAVE_ACCENT, 1f), new Key("1", GLFW.GLFW_KEY_1, 1f), new Key("2", GLFW.GLFW_KEY_2, 1f), new Key("3", GLFW.GLFW_KEY_3, 1f),
                        new Key("4", GLFW.GLFW_KEY_4, 1f), new Key("5", GLFW.GLFW_KEY_5, 1f), new Key("6", GLFW.GLFW_KEY_6, 1f), new Key("7", GLFW.GLFW_KEY_7, 1f),
                        new Key("8", GLFW.GLFW_KEY_8, 1f), new Key("9", GLFW.GLFW_KEY_9, 1f), new Key("0", GLFW.GLFW_KEY_0, 1f), new Key("-", GLFW.GLFW_KEY_MINUS, 1f),
                        new Key("=", GLFW.GLFW_KEY_EQUAL, 1f), new Key("BACK", GLFW.GLFW_KEY_BACKSPACE, 2f)),
                List.of(new Key("TAB", GLFW.GLFW_KEY_TAB, 1.5f), new Key("Q", GLFW.GLFW_KEY_Q, 1f), new Key("W", GLFW.GLFW_KEY_W, 1f), new Key("E", GLFW.GLFW_KEY_E, 1f),
                        new Key("R", GLFW.GLFW_KEY_R, 1f), new Key("T", GLFW.GLFW_KEY_T, 1f), new Key("Y", GLFW.GLFW_KEY_Y, 1f), new Key("U", GLFW.GLFW_KEY_U, 1f),
                        new Key("I", GLFW.GLFW_KEY_I, 1f), new Key("O", GLFW.GLFW_KEY_O, 1f), new Key("P", GLFW.GLFW_KEY_P, 1f), new Key("[", GLFW.GLFW_KEY_LEFT_BRACKET, 1f),
                        new Key("]", GLFW.GLFW_KEY_RIGHT_BRACKET, 1f), new Key("\\", GLFW.GLFW_KEY_BACKSLASH, 1.5f)),
                List.of(new Key("CAPS", GLFW.GLFW_KEY_CAPS_LOCK, 1.75f), new Key("A", GLFW.GLFW_KEY_A, 1f), new Key("S", GLFW.GLFW_KEY_S, 1f),
                        new Key("D", GLFW.GLFW_KEY_D, 1f), new Key("F", GLFW.GLFW_KEY_F, 1f), new Key("G", GLFW.GLFW_KEY_G, 1f), new Key("H", GLFW.GLFW_KEY_H, 1f),
                        new Key("J", GLFW.GLFW_KEY_J, 1f), new Key("K", GLFW.GLFW_KEY_K, 1f), new Key("L", GLFW.GLFW_KEY_L, 1f),
                        new Key(";", GLFW.GLFW_KEY_SEMICOLON, 1f), new Key("'", GLFW.GLFW_KEY_APOSTROPHE, 1f), new Key("ENTER", GLFW.GLFW_KEY_ENTER, 2.25f)),
                List.of(new Key("LSHIFT", GLFW.GLFW_KEY_LEFT_SHIFT, 2.25f), new Key("Z", GLFW.GLFW_KEY_Z, 1f), new Key("X", GLFW.GLFW_KEY_X, 1f),
                        new Key("C", GLFW.GLFW_KEY_C, 1f), new Key("V", GLFW.GLFW_KEY_V, 1f), new Key("B", GLFW.GLFW_KEY_B, 1f),
                        new Key("N", GLFW.GLFW_KEY_N, 1f), new Key("M", GLFW.GLFW_KEY_M, 1f), new Key(",", GLFW.GLFW_KEY_COMMA, 1f),
                        new Key(".", GLFW.GLFW_KEY_PERIOD, 1f), new Key("/", GLFW.GLFW_KEY_SLASH, 1f), new Key("RSHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT, 2.75f)),
                List.of(new Key("LCTRL", GLFW.GLFW_KEY_LEFT_CONTROL, 1.5f), new Key("LALT", GLFW.GLFW_KEY_LEFT_ALT, 1.5f),
                        new Key("SPACE", GLFW.GLFW_KEY_SPACE, 8.5f),
                        new Key("RALT", GLFW.GLFW_KEY_RIGHT_ALT, 1.5f), new Key("RCTRL", GLFW.GLFW_KEY_RIGHT_CONTROL, 1.5f)),
                List.of(new Key("", -1, 13.5f), new Key("UP", GLFW.GLFW_KEY_UP, 1f)),
                List.of(new Key("", -1, 12.5f), new Key("LEFT", GLFW.GLFW_KEY_LEFT, 1f), new Key("DOWN", GLFW.GLFW_KEY_DOWN, 1f), new Key("RIGHT", GLFW.GLFW_KEY_RIGHT, 1f))
        );

        float keyboardContentHeight = placeKeyRows(keyboardPanel, 5, keyboardLayout, InputSystem.InputType.KEYBOARD, keyboardButtonMap);
        keyboardPanel.setHeight(keyboardContentHeight);
        keyboardBack.setHeight(keyboardContentHeight);
        yOffset += keyboardContentHeight + 10;

        AutoScaleLabelWidget mouseLabel = new AutoScaleLabelWidget("Mouse", 5, yOffset, container.getWidth() - 10);
        mouseLabel.dropShadow = false;
        container.addChild("mouse_label", mouseLabel);
        yOffset += 15;

        LayeredPanelWidget mousePanel = new LayeredPanelWidget(X_START, yOffset, container.getWidth() - 10, 0);
        BlendQuadWidget mouseBack = new BlendQuadWidget(0, 0, mousePanel.getWidth(), 0);
        mouseBack.alpha = 0.2f;
        mouseBack.drawLine = false;
        mousePanel.addChild("back", mouseBack);
        mousePanel.setZ(1);
        container.addChild("mouse_panel", mousePanel);

        List<List<Key>> mouseLayout = List.of(
                List.of(
                        new Key("Left", GLFW.GLFW_MOUSE_BUTTON_LEFT, 2f),
                        new Key("Right", GLFW.GLFW_MOUSE_BUTTON_RIGHT, 2f),
                        new Key("Middle", GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 2f)
                ),
                List.of(
                        new Key("M4", GLFW.GLFW_MOUSE_BUTTON_4, 1.5f),
                        new Key("M5", GLFW.GLFW_MOUSE_BUTTON_5, 1.5f),
                        new Key("M6", GLFW.GLFW_MOUSE_BUTTON_6, 1.5f),
                        new Key("M7", GLFW.GLFW_MOUSE_BUTTON_7, 1.5f),
                        new Key("M8", GLFW.GLFW_MOUSE_BUTTON_8, 1.5f)
                )
        );

        float mouseContentHeight = placeKeyRows(mousePanel, 5, mouseLayout, InputSystem.InputType.MOUSE, mouseButtonMap);
        mousePanel.setHeight(mouseContentHeight);
        mouseBack.setHeight(mouseContentHeight);
    }

    private static float placeKeyRows(PanelWidget container, float y, List<List<Key>> layout, InputSystem.InputType type, Map<Integer, LayeredPanelWidget> buttonMap) {
        final float BASE_UNIT_WIDTH = 8.5f;
        final float BUTTON_HEIGHT = 15f;
        final float H_SPACING = 1.5f;
        final float V_SPACING = 3f;
        final float X_START = 0f;

        float maxWidth = 0;
        for (List<Key> row : layout) {
            float rowWidth = (float) row.stream().mapToDouble(k -> k.widthMultiplier * BASE_UNIT_WIDTH).sum() + Math.max(0, row.size() - 1) * H_SPACING;
            maxWidth = Math.max(maxWidth, rowWidth);
        }

        float availableWidth = container.getWidth();
        float scale = 1.0f;
        if (maxWidth > availableWidth) {
            scale = availableWidth / maxWidth;
        }

        final float f_BASE_UNIT_WIDTH = BASE_UNIT_WIDTH * scale;
        final float f_BUTTON_HEIGHT = BUTTON_HEIGHT * scale;
        final float f_H_SPACING = H_SPACING * scale;
        final float f_V_SPACING = V_SPACING * scale;

        float currentY = y;

        for (List<Key> row : layout) {
            float currentX = X_START;
            for (Key key : row) {
                float btnWidth = key.widthMultiplier * f_BASE_UNIT_WIDTH;

                if (key.code != -1) {
                    LayeredPanelWidget keyBtn = createSmallKeyButton(key.name, currentX, currentY, btnWidth, f_BUTTON_HEIGHT, () -> {
                        handleRebind(type, new LinkedHashSet<>(Set.of(key.code)));
                        setBackgroundControlsEnabled(true);
                        keySelectionPanel.setVisible(false);
                        keySelectionPanel.setEnabled(false);
                    });
                    keyBtn.<AutoScaleLabelWidget>getChildUnSafe("label").scale *= scale;
                    container.addChild("btn_" + type.name().toLowerCase() + "_" + key.name, keyBtn);
                    buttonMap.put(key.code, keyBtn);
                    keyBtn.setZ(1);
                }
                currentX += btnWidth + f_H_SPACING;
            }
            currentY += f_BUTTON_HEIGHT + f_V_SPACING;
        }

        return currentY - y;
    }

    private static LayeredPanelWidget createSmallKeyButton(String text, float x, float y, float w, float h, Runnable onPress) {
        LayeredPanelWidget panel = new LayeredPanelWidget(x, y, w, h);
        ImageButtonWidget buttonLogic = new ImageButtonWidget(0, 0, w, h, null, onPress);
        panel.addChild("button_logic", buttonLogic);

        BlendQuadWidget back = new BlendQuadWidget(0, 0, w, h);
        back.drawLine = false;
        back.alpha = 0.5f;
        panel.addChild("back", back);

        AutoScaleLabelWidget label = new AutoScaleLabelWidget(text, 0, 0, w, true);
        label.dropShadow = false;
        label.scale = 0.75f;
        label.setY((h - label.getHeight() * label.scale) / 2f);
        panel.addChild("label", label);
        return panel;
    }

    private static LayeredPanelWidget createKeybindingControlPanel() {
        LayeredPanelWidget controlPanel = new LayeredPanelWidget(5, 0, 180, 85);
        controlPanel.setVisible(false);
        controlPanel.setEnabled(false);

        BlendQuadWidget controlBack = new BlendQuadWidget(0, 0, 180, 85);
        controlBack.alpha = 0.2f;
        controlBack.drawLine = false;
        controlPanel.addChild("back", controlBack);

        AutoScaleLabelWidget typeLabel = new AutoScaleLabelWidget("Type:", 5, 8, 40);
        typeLabel.dropShadow = false;
        controlPanel.addChild("type_label", typeLabel);

        LayeredPanelWidget inputTypePanel = new LayeredPanelWidget(50, 5, 125, 20);
        controlPanel.addChild("input_type", inputTypePanel);
        inputTypePanel.setZ(1);

        Runnable keyboardAction = () -> {
            updateModifierButtonState(keyboardBtn, true);
            updateModifierButtonState(mouseBtn, false);
            updateBindingFromControls(true);
        };
        keyboardBtn = new LayeredPanelWidget(0, 0, 60, 15);
        ImageButtonWidget keyboardLogic = new ImageButtonWidget(0, 0, 60, 15, null, keyboardAction);
        keyboardBtn.addChild("button_logic", keyboardLogic);

        AutoScaleLabelWidget keyboardLabel = new AutoScaleLabelWidget("Keyboard", 0, 4, keyboardBtn.getWidth(), true);
        keyboardLabel.dropShadow = false;
        keyboardBtn.addChild("label", keyboardLabel);
        BlendQuadWidget keyboardBtnBack = new BlendQuadWidget(0, 0, 60, 15);
        keyboardBtnBack.drawLine = false;
        keyboardBtnBack.alpha = 0.4f;
        keyboardBtn.addChild("back", keyboardBtnBack);
        inputTypePanel.addChild("keyboard", keyboardBtn);

        Runnable mouseAction = () -> {
            updateModifierButtonState(keyboardBtn, false);
            updateModifierButtonState(mouseBtn, true);
            updateBindingFromControls(true);
        };
        mouseBtn = new LayeredPanelWidget(65, 0, 60, 15);
        ImageButtonWidget mouseLogic = new ImageButtonWidget(0, 0, 60, 15, null, mouseAction);
        mouseBtn.addChild("button_logic", mouseLogic);

        AutoScaleLabelWidget mouseLabel = new AutoScaleLabelWidget("Mouse", 0, 4, mouseBtn.getWidth(), true);
        mouseLabel.dropShadow = false;
        mouseBtn.addChild("label", mouseLabel);
        BlendQuadWidget mouseBtnBack = new BlendQuadWidget(0, 0, 60, 15);
        mouseBtnBack.drawLine = false;
        mouseBtnBack.alpha = 0.4f;
        mouseBtn.addChild("back", mouseBtnBack);
        inputTypePanel.addChild("mouse", mouseBtn);

        AutoScaleLabelWidget keyLabel = new AutoScaleLabelWidget("Key:", 5, 33, 40);
        keyLabel.dropShadow = false;
        controlPanel.addChild("key_label", keyLabel);
        keySelectionButton = new LayeredPanelWidget(50, 30, 125, 20);
        Runnable keySelectAction = () -> {
            setBackgroundControlsEnabled(false);
            keySelectionPanel.setVisible(true);
            keySelectionPanel.setEnabled(true);
            updateKeySelectionHighlights();
        };
        ImageButtonWidget keySelectionLogic = new ImageButtonWidget(0, 0, 125, 20, null, keySelectAction);
        keySelectionButton.addChild("button_logic", keySelectionLogic);

        BlendQuadWidget keySelectionButtonBack = new BlendQuadWidget(0, 0, 125, 20);
        keySelectionButtonBack.alpha = 0.3f;
        keySelectionButtonBack.drawLine = false;
        keySelectionButton.addChild("back", keySelectionButtonBack);
        keySelectionButtonBack.setZ(1);
        AutoScaleLabelWidget keySelectionButtonLabel = new AutoScaleLabelWidget("Set Key", 0, 0, keySelectionButton.getWidth(), true);
        keySelectionButtonLabel.dropShadow = false;
        keySelectionButtonLabel.setY((keySelectionButton.getHeight() - keySelectionButtonLabel.getHeight()) / 2f);
        keySelectionButton.addChild("text", keySelectionButtonLabel);
        controlPanel.addChild("key_select", keySelectionButton);

        AutoScaleLabelWidget modLabel = new AutoScaleLabelWidget("Mods:", 5, 63, 40);
        modLabel.dropShadow = false;
        controlPanel.addChild("mod_label", modLabel);
        LayeredPanelWidget modsPanel = new LayeredPanelWidget(50, 60, 125, 20);
        controlPanel.addChild("mods_panel", modsPanel);
        modsPanel.setZ(1);

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

    private static LayeredPanelWidget createModifierButton(String text, float x, Runnable onPress) {
        LayeredPanelWidget panel = new LayeredPanelWidget(x, 0, 35, 15);
        ImageButtonWidget buttonLogic = new ImageButtonWidget(0, 0, 35, 15, null, onPress);
        panel.addChild("button_logic", buttonLogic);

        BlendQuadWidget back = new BlendQuadWidget(0, 0, 35, 15);
        back.drawLine = false;
        panel.addChild("back", back);
        AutoScaleLabelWidget label = new AutoScaleLabelWidget(text, 0, 4, 35, true);
        label.dropShadow = false;
        panel.addChild("label", label);
        return panel;
    }

    private static void toggleKeybindingControls(LayeredPanelWidget entry, String keyName) {
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

        AutoScaleLabelWidget keyText = keySelectionButton.getChildUnSafe("text");
        keyText.setText(formatKey(binding));

        Set<Integer> mods = binding.keyInfo.modifiers;
        updateModifierButton(shiftModBtn, mods.contains(GLFW.GLFW_MOD_SHIFT));
        updateModifierButton(ctrlModBtn, mods.contains(GLFW.GLFW_MOD_CONTROL));
        updateModifierButton(altModBtn, mods.contains(GLFW.GLFW_MOD_ALT));
    }

    private static void updateModifierButtonState(LayeredPanelWidget button, boolean active) {
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

    private static LayeredPanelWidget createKeybindingWidget(String displayName, String keyName) {
        LayeredPanelWidget panel = new LayeredPanelWidget(0, 0, 180, 20);

        HoverLabelWidget label = new HoverLabelWidget(displayName, 5, 5, 95);
        label.dropShadow = false;
        panel.addChild("label", label);

        LayeredPanelWidget buttonPanel = new LayeredPanelWidget(105, 0, 75, 20);
        ImageButtonWidget buttonLogic = new ImageButtonWidget(0, 0, 75, 20, null, () -> toggleKeybindingControls(panel, keyName));
        buttonPanel.addChild("button_logic", buttonLogic);
        panel.addChild("button", buttonPanel);

        BlendQuadWidget buttonBack = new BlendQuadWidget(0, 0, buttonPanel.getWidth(), buttonPanel.getHeight());
        buttonBack.alpha = 0.3f;
        buttonBack.drawLine = false;
        buttonPanel.addChild("back", buttonBack);

        InputSystem.InputPair binding = getKeyBindingFromInputSystem(keyName);
        AutoScaleLabelWidget keyLabel = new AutoScaleLabelWidget(formatKeybinding(binding), 3, 0, buttonPanel.getWidth() - 6, true);
        keyLabel.dropShadow = false;
        keyLabel.setY((buttonPanel.getHeight() - keyLabel.getHeight()) / 2);
        buttonPanel.addChild("text", keyLabel);

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
                keySelectionButton.<AutoScaleLabelWidget>getChildUnSafe("text").setText("Set Key");
            }
        }
    }

    private static void updateDisplayForActiveBinding() {
        if (activeKeybindingEntry != null && activeKeyName != null) {
            InputSystem.InputPair binding = getKeyBindingFromInputSystem(activeKeyName);
            LayeredPanelWidget buttonPanel = activeKeybindingEntry.getChildUnSafe("button");
            AutoScaleLabelWidget text = buttonPanel.getChildUnSafe("text");
            text.setText(formatKeybinding(binding));
            updateControlPanelContents();
        }
    }

    private static void updateModifierButton(LayeredPanelWidget button, boolean active) {
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

    private static void toggleModifierButton(LayeredPanelWidget button) {
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

    record Key(String name, int code, float widthMultiplier) {
    }
}