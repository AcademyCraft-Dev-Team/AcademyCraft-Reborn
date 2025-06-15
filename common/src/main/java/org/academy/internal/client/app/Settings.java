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

public final class Settings implements DataTerminalHUD.App {
    public static final DataTerminalHUD.App INSTANCE = new Settings();

    private static PanelWidget keybindingControlPanel = null;
    private static PanelWidget activeKeybindingEntry = null;
    private static String activeKeyName = null;
    private static PanelButtonWidget keySelectionButton = null;
    private static boolean isListeningForKey = false;
    private static PanelButtonWidget keyboardBtn, mouseBtn;
    private static PanelButtonWidget shiftModBtn, ctrlModBtn, altModBtn;
    private static PanelWidget keySelectionPanel = null;

    private static final Runnable ON_CLICK = () -> {
        activeKeybindingEntry = null;
        activeKeyName = null;
        isListeningForKey = false;
        DataTerminalHUD.setAppArea(createSettingsPanel());
    };

    private static void setBackgroundControlsEnabled(boolean enabled) {
        if (keySelectionPanel == null || keySelectionPanel.getParent() == null) return;
        AbstractContainerWidget appArea = (AbstractContainerWidget) keySelectionPanel.getParent();
        for (Widget child : appArea.getChildren().values()) {
            if (child != keySelectionPanel) {
                child.setEnabled(enabled);
            }
        }
    }

    private static PanelWidget createSettingsPanel() {
        PanelWidget appArea = new PanelWidget(0, 0, 200, 225);

        BlendQuadWidget back = new BlendQuadWidget(0, 0, appArea.getWidth(), appArea.getHeight());
        back.drawLine = false;
        back.alpha = 0.25f;
        appArea.addChild("back", back);

        PanelWidget tabBar = new PanelWidget(5, 5, 190, 20);
        appArea.addChild("tab_bar", tabBar);
        tabBar.setZ(tabBar.getZ() + 1);

        SmoothScrollPanelWidget generalPanel = new SmoothScrollPanelWidget(5, 30, 190, 190);
        appArea.addChild("panel_general", generalPanel);
        generalPanel.setZ(generalPanel.getZ() + 1);
        {
            float currentY = 10f;
            LabelWidget blurLabel = new LabelWidget("Blur Radius", 5, currentY);
            generalPanel.addChild("label_blur", blurLabel);
            currentY += 15;

            PanelWidget blurSliderContainer = new PanelWidget(5, currentY, 150, 8);
            generalPanel.addChild("container_blur", blurSliderContainer);

            SliderWidget blurSlider = new SliderWidget(0, 0, 100, 8, 0.0f, 20.0f, DataTerminalHUD.config.blurRadius);
            blurSliderContainer.addChild("slider_blur", blurSlider);

            LabelWidget blurValueLabel = new LabelWidget(String.format("%.2f", DataTerminalHUD.config.blurRadius), 105, 0);
            blurValueLabel.scale = 0.75f;
            blurValueLabel.setY((blurSlider.getHeight() - blurValueLabel.getHeight() * blurValueLabel.scale) / 2);
            blurSliderContainer.addChild("label_blur_value", blurValueLabel);

            blurSlider.onValueChanged = (val) -> {
                DataTerminalHUD.config.blurRadius = val;
                AcademyCraftClient.CLIENT_CONFIG.save();
                blurValueLabel.value = String.format("%.2f", val);
            };
        }

        SmoothScrollPanelWidget keybindingsPanel = new SmoothScrollPanelWidget(5, 30, 190, 190);
        keybindingsPanel.setVisible(false);
        keybindingsPanel.setEnabled(false);
        appArea.addChild("panel_keybindings", keybindingsPanel);
        keybindingsPanel.setZ(keybindingsPanel.getZ() + 1);
        {
            List<String> allKeyNames = new ArrayList<>(InputSystem.KEY_BINDINGS.keySet());
            Collections.sort(allKeyNames);

            for (String keyName : allKeyNames) {
                PanelWidget keybindingPanel = createKeybindingWidget(keyName, keyName);
                keybindingsPanel.addChild("keybinding_" + keyName, keybindingPanel);
            }
            createKeybindingControlPanel(keybindingsPanel);
            updateKeybindingLayout(keybindingsPanel);
        }

        createKeySelectionPanel(appArea);

        PanelButtonWidget generalButton = new PanelButtonWidget(0, 0, 90, 20, null);
        tabBar.addChild("btn_general", generalButton);

        PanelButtonWidget keybindingsButton = new PanelButtonWidget(95, 0, 95, 20, null);
        tabBar.addChild("btn_keybindings", keybindingsButton);

        generalButton.onActive = () -> {
            generalPanel.setVisible(true);
            generalPanel.setEnabled(true);
            keybindingsPanel.setVisible(false);
            keybindingsPanel.setEnabled(false);
            generalButton.<BlendQuadWidget>getChildUnSafe("back").alpha = 0.6f;
            keybindingsButton.<BlendQuadWidget>getChildUnSafe("back").alpha = 0.3f;
        };
        keybindingsButton.onActive = () -> {
            generalPanel.setVisible(false);
            generalPanel.setEnabled(false);
            keybindingsPanel.setVisible(true);
            keybindingsPanel.setEnabled(true);
            generalButton.<BlendQuadWidget>getChildUnSafe("back").alpha = 0.3f;
            keybindingsButton.<BlendQuadWidget>getChildUnSafe("back").alpha = 0.6f;
        };

        BlendQuadWidget generalBack = new BlendQuadWidget(0, 0, generalButton.getWidth(), generalButton.getHeight());
        generalBack.alpha = 0.6f;
        generalBack.drawLine = false;
        generalButton.addChild("back", generalBack);
        LabelWidget generalLabel = new LabelWidget("General", 0, 0);
        generalLabel.setX((generalButton.getWidth() - generalLabel.getWidth()) / 2);
        generalLabel.setY((generalButton.getHeight() - generalLabel.getHeight()) / 2);
        generalButton.addChild("text", generalLabel);

        BlendQuadWidget keybindingsBack = new BlendQuadWidget(0, 0, keybindingsButton.getWidth(), keybindingsButton.getHeight());
        keybindingsBack.alpha = 0.3f;
        keybindingsBack.drawLine = false;
        keybindingsButton.addChild("back", keybindingsBack);
        LabelWidget keybindingsLabel = new LabelWidget("Keybindings", 0, 0);
        keybindingsLabel.setX((keybindingsButton.getWidth() - keybindingsLabel.getWidth()) / 2);
        keybindingsLabel.setY((keybindingsButton.getHeight() - keybindingsLabel.getHeight()) / 2);
        keybindingsButton.addChild("text", keybindingsLabel);

        generalPanel.setVisible(true);
        generalPanel.setEnabled(true);

        return appArea;
    }

    private static void createKeySelectionPanel(AbstractContainerWidget parent) {
        keySelectionPanel = new PanelWidget(0, 0, 180, 190);
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

        PanelButtonWidget closeButton = new PanelButtonWidget(keySelectionPanel.getWidth() - 25, 5, 20, 15, () -> {
            setBackgroundControlsEnabled(true);
            keySelectionPanel.setVisible(false);
            keySelectionPanel.setEnabled(false);
        });
        LabelWidget closeLabel = new LabelWidget("X", 0, 4);
        closeLabel.setX((closeButton.getWidth() - closeLabel.getWidth()) / 2f);
        closeButton.addChild("text", closeLabel);
        BlendQuadWidget closeBack = new BlendQuadWidget(0, 0, 20, 15);
        closeBack.drawLine = false;
        closeBack.alpha = 0.5f;
        closeButton.addChild("back", closeBack);
        keySelectionPanel.addChild("close_btn", closeButton);
        closeButton.setZ(closeButton.getZ() + 1);

        LabelWidget title = new LabelWidget("Select Key", 5, 5);
        keySelectionPanel.addChild("title", title);

        SmoothScrollPanelWidget keysContainer = new SmoothScrollPanelWidget(5, 25, 170, 135);
        keySelectionPanel.addChild("keys_container", keysContainer);

        VerticalScrollBarWidget scrollBar = new VerticalScrollBarWidget(keysContainer, keysContainer.getX() + keysContainer.getWidth() - 5, keysContainer.getY(), 5, keysContainer.getHeight());
        keySelectionPanel.addChild("scroll_bar", scrollBar);
        scrollBar.setThumbColor(0x50AAAAAA);
        scrollBar.setTrackColor(0x70202020);
        scrollBar.setZ(scrollBar.getZ() + 1);

        PanelButtonWidget listenBtn = new PanelButtonWidget(5, 165, 170, 20, () -> {
            isListeningForKey = true;
            setBackgroundControlsEnabled(true);
            keySelectionPanel.setVisible(false);
            keySelectionPanel.setEnabled(false);
            keySelectionButton.<LabelWidget>getChildUnSafe("text").value = "> ... <";
        });
        BlendQuadWidget listenBtnBack = new BlendQuadWidget(0, 0, 170, 20);
        listenBtnBack.alpha = 0.5f;
        listenBtnBack.drawLine = false;
        listenBtn.addChild("back", listenBtnBack);
        LabelWidget listenLabel = new LabelWidget("Listen for Input", 0, 6);
        listenLabel.setX((listenBtn.getWidth() - listenLabel.getWidth()) / 2f);
        listenBtn.addChild("text", listenLabel);
        keySelectionPanel.addChild("listen_btn", listenBtn);
        listenBtn.setZ(listenBtn.getZ() + 1);

        populateKeySelectionContainer(keysContainer);
    }

    private static void populateKeySelectionContainer(SmoothScrollPanelWidget container) {
        float yOffset = 5f;
        container.addChild("keyboard_label", new LabelWidget("Keyboard", 5, yOffset));
        yOffset += 15;

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

        yOffset = placeKeyRows(container, yOffset, keyboardLayout, InputSystem.InputType.KEYBOARD);

        yOffset += 10;
        container.addChild("mouse_label", new LabelWidget("Mouse", 5, yOffset));
        yOffset += 15;

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

        placeKeyRows(container, yOffset, mouseLayout, InputSystem.InputType.MOUSE);
    }

    private static float placeKeyRows(SmoothScrollPanelWidget container, float y, List<List<Key>> layout, InputSystem.InputType type) {
        final float BASE_UNIT_WIDTH = 9f;
        final float BUTTON_HEIGHT = 15f;
        final float H_SPACING = 2f;
        final float V_SPACING = 3f;
        final float X_START = 5f;

        float currentY = y;

        for (List<Key> row : layout) {
            float currentX = X_START;
            for (Key key : row) {
                float btnWidth = key.widthMultiplier * BASE_UNIT_WIDTH;

                if (key.code != -1) {
                    PanelButtonWidget keyBtn = createSmallKeyButton(key.name, currentX, currentY, btnWidth, BUTTON_HEIGHT, () -> {
                        handleRebind(type, new LinkedHashSet<>(Set.of(key.code)));
                        setBackgroundControlsEnabled(true);
                        keySelectionPanel.setVisible(false);
                        keySelectionPanel.setEnabled(false);
                    });
                    container.addChild("btn_" + type.name().toLowerCase() + "_" + key.name, keyBtn);
                    keyBtn.setZ(keyBtn.getZ() + 1);
                }
                currentX += btnWidth + H_SPACING;
            }
            currentY += BUTTON_HEIGHT + V_SPACING;
        }

        return currentY;
    }

    private static PanelButtonWidget createSmallKeyButton(String text, float x, float y, float w, float h, Runnable onPress) {
        PanelButtonWidget button = new PanelButtonWidget(x, y, w, h, onPress);
        BlendQuadWidget back = new BlendQuadWidget(0, 0, w, h);
        back.drawLine = false;
        back.alpha = 0.5f;
        button.addChild("back", back);

        LabelWidget label = new LabelWidget(text, 0, 0);
        label.scale = 0.75f;
        label.setX((w - label.getWidth() * label.scale) / 2f);
        label.setY((h - label.getHeight() * label.scale) / 2f);
        button.addChild("text", label);
        return button;
    }

    private static void createKeybindingControlPanel(AbstractContainerWidget parentPanel) {
        keybindingControlPanel = new PanelWidget(5, 0, 180, 85);
        keybindingControlPanel.setVisible(false);
        keybindingControlPanel.setEnabled(false);
        parentPanel.addChild("control_panel", keybindingControlPanel);

        BlendQuadWidget controlBack = new BlendQuadWidget(0, 0, 180, 85);
        controlBack.alpha = 0.2f;
        controlBack.drawLine = false;
        keybindingControlPanel.addChild("back", controlBack);

        LabelWidget typeLabel = new LabelWidget("Type:", 5, 8);
        keybindingControlPanel.addChild("type_label", typeLabel);

        PanelWidget inputTypePanel = new PanelWidget(50, 5, 125, 20);
        keybindingControlPanel.addChild("input_type", inputTypePanel);
        inputTypePanel.setZ(inputTypePanel.getZ() + 1);

        keyboardBtn = new PanelButtonWidget(0, 0, 60, 15, null);
        keyboardBtn.addChild("label", new LabelWidget("Keyboard", 8, 4));
        BlendQuadWidget keyboardBtnBack = new BlendQuadWidget(0, 0, 60, 15);
        keyboardBtnBack.drawLine = false;
        keyboardBtnBack.alpha = 0.4f;
        keyboardBtn.addChild("back", keyboardBtnBack);
        inputTypePanel.addChild("keyboard", keyboardBtn);

        mouseBtn = new PanelButtonWidget(65, 0, 60, 15, null);
        mouseBtn.addChild("label", new LabelWidget("Mouse", 15, 4));
        BlendQuadWidget mouseBtnBack = new BlendQuadWidget(0, 0, 60, 15);
        mouseBtnBack.drawLine = false;
        mouseBtnBack.alpha = 0.4f;
        mouseBtn.addChild("back", mouseBtnBack);
        inputTypePanel.addChild("mouse", mouseBtn);

        keyboardBtn.onActive = () -> {
            updateModifierButtonState(keyboardBtn, true);
            updateModifierButtonState(mouseBtn, false);
            updateBindingFromControls(true);
        };
        mouseBtn.onActive = () -> {
            updateModifierButtonState(keyboardBtn, false);
            updateModifierButtonState(mouseBtn, true);
            updateBindingFromControls(true);
        };

        LabelWidget keyLabel = new LabelWidget("Key:", 5, 33);
        keybindingControlPanel.addChild("key_label", keyLabel);
        keySelectionButton = new PanelButtonWidget(50, 30, 125, 20, () -> {
            setBackgroundControlsEnabled(false);
            keySelectionPanel.setVisible(true);
            keySelectionPanel.setEnabled(true);
        });
        keySelectionButton.addChild("text", new LabelWidget("Set Key", 45, 6));
        keybindingControlPanel.addChild("key_select", keySelectionButton);

        LabelWidget modLabel = new LabelWidget("Mods:", 5, 63);
        keybindingControlPanel.addChild("mod_label", modLabel);
        PanelWidget modsPanel = new PanelWidget(50, 60, 125, 20);
        keybindingControlPanel.addChild("mods_panel", modsPanel);
        modsPanel.setZ(modsPanel.getZ() + 1);

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
    }

    private static void toggleModifierButton(PanelButtonWidget button) {
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

    private static PanelButtonWidget createModifierButton(String text, float x, Runnable onPress) {
        PanelButtonWidget button = new PanelButtonWidget(x, 0, 35, 15, onPress);
        BlendQuadWidget back = new BlendQuadWidget(0, 0, 35, 15);
        back.drawLine = false;
        button.addChild("back", back);
        LabelWidget label = new LabelWidget(text, 0, 4);
        label.setX((35 - label.getWidth()) / 2);
        button.addChild("label", label);
        return button;
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

        LabelWidget keyText = keySelectionButton.getChildUnSafe("text");
        keyText.value = formatKey(binding);
        keyText.setX((keySelectionButton.getWidth() - keyText.getWidth()) / 2);

        Set<Integer> mods = binding.keyInfo.modifiers;
        updateModifierButton(shiftModBtn, mods.contains(GLFW.GLFW_MOD_SHIFT));
        updateModifierButton(ctrlModBtn, mods.contains(GLFW.GLFW_MOD_CONTROL));
        updateModifierButton(altModBtn, mods.contains(GLFW.GLFW_MOD_ALT));
    }

    private static void updateModifierButtonState(PanelButtonWidget button, boolean active) {
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

        LabelWidget label = new LabelWidget(displayName, 0, 5);
        panel.addChild("label", label);

        PanelButtonWidget button = new PanelButtonWidget(100, 0, 70, 20, null);
        button.onActive = () -> toggleKeybindingControls(panel, keyName);
        panel.addChild("button", button);

        BlendQuadWidget buttonBack = new BlendQuadWidget(0, 0, button.getWidth(), button.getHeight());
        buttonBack.alpha = 0.3f;
        buttonBack.drawLine = false;
        button.addChild("back", buttonBack);

        InputSystem.InputPair binding = getKeyBindingFromInputSystem(keyName);
        LabelWidget keyLabel = new LabelWidget(formatKeybinding(binding), 0, 0);
        keyLabel.setX((button.getWidth() - keyLabel.getWidth()) / 2);
        keyLabel.setY((button.getHeight() - keyLabel.getHeight()) / 2);
        button.addChild("text", keyLabel);

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
        if (pair == null || pair.keyInfo == null || pair.keyInfo.inputs.isEmpty() || pair.keyInfo.inputs.contains(-1)) return "NONE";
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
        }
    }

    private static void updateDisplayForActiveBinding() {
        if (activeKeybindingEntry != null && activeKeyName != null) {
            InputSystem.InputPair binding = getKeyBindingFromInputSystem(activeKeyName);
            PanelButtonWidget button = activeKeybindingEntry.getChildUnSafe("button");
            LabelWidget text = button.getChildUnSafe("text");
            text.value = formatKeybinding(binding);
            text.setX((button.getWidth() - text.getWidth()) / 2);
            updateControlPanelContents();
        }
    }

    private static void updateModifierButton(PanelButtonWidget button, boolean active) {
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

    record Key(String name, int code, float widthMultiplier) {
    }
}