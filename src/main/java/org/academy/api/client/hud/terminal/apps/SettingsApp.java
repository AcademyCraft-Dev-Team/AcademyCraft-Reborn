/*
package org.academy.api.client.hud.terminal.apps;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.widget.Widget;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.hud.terminal.App;
import org.academy.api.client.hud.terminal.Config;
import org.academy.api.client.hud.terminal.HUDController;
import org.academy.api.client.hud.terminal.UIManager;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.util.ClientUtil;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SettingsApp implements App {
    public static final App INSTANCE = new SettingsApp();

    private SettingsApp() {
    }

    @Override
    public ResourceLocation getIcon() {
        return Resource.Textures.ICON_SETTINGS;
    }

    @Override
    public @NotNull String getName() {
        return "Settings";
    }

    @Override
    public AbstractContainerWidget createUI(UIManager uiManager) {
        return new SettingsPanel(uiManager);
    }

    private static class SettingsPanel extends PanelWidget {
        private final UIManager uiManager;
        private final Config config;

        private PanelWidget keybindingControlPanel;
        private PanelWidget activeKeybindingEntry;
        private String activeKeyName;
        private PanelWidget keySelectionButton;
        private boolean isListeningForKey = false;
        private PanelWidget keyboardBtn, mouseBtn;
        private PanelWidget shiftModBtn, ctrlModBtn, altModBtn;
        private PanelWidget keySelectionPanel;
        private final Map<Integer, PanelWidget> keyboardButtonMap = new HashMap<>();
        private final Map<Integer, PanelWidget> mouseButtonMap = new HashMap<>();

        public SettingsPanel(UIManager uiManager) {
            super(0, 0, 200, 225);
            this.uiManager = uiManager;
            config = AcademyCraftClient.Config.INSTANCE.getConfig(HUDController.CONFIG_KEY);
            buildUI();
        }

        @Override
        public Widget setVisible(boolean visible) {
            if (visible) {
                NeoForge.EVENT_BUS.register(this);
            } else {
                NeoForge.EVENT_BUS.unregister(this);
            }
            return super.setVisible(visible);
        }

        private void buildUI() {
            var main = new PanelWidget(0, 0, getWidth(), getHeight());
            addChild("main", main);

            var generalPanel = createGeneralPanel();
            main.addChild("panel_general", generalPanel);

            var keybindingsPanel = createKeybindingsPanel(this);
            main.addChild("panel_keybindings", keybindingsPanel);

            createTabBar(main, generalPanel, keybindingsPanel);
        }

        private void createTabBar(AbstractContainerWidget parent, Widget generalPanel, Widget keybindingsPanel) {
            var tabBar = new PanelWidget(5, 5, 190, 20);
            parent.addChild("tab_bar", tabBar);

            var generalButton = createTabButton("General", 0, 90);
            var keybindingsButton = createTabButton("Keybindings", 95, 95);

            var showGeneral = new Runnable() {
                @Override
                public void run() {
                    generalPanel.setVisible(true);
                    keybindingsPanel.setVisible(false);
                    generalButton.<FillWidget>getChildUnSafe("back").setAlpha(0.6f);
                    keybindingsButton.<FillWidget>getChildUnSafe("back").setAlpha(0.3f);
                }
            };
            var showKeybindings = new Runnable() {
                @Override
                public void run() {
                generalPanel.setVisible(false);
                keybindingsPanel.setVisible(true);
                generalButton.<FillWidget>getChildUnSafe("back").setAlpha(0.3f);
                keybindingsButton.<FillWidget>getChildUnSafe("back").setAlpha(0.6f);
                }
            };

            generalButton.<ImageButtonWidget>getChildUnSafe("button_logic").onPress = showGeneral;
            keybindingsButton.<ImageButtonWidget>getChildUnSafe("button_logic").onPress = showKeybindings;

            tabBar.addChild("btn_general", generalButton);
            tabBar.addChild("btn_keybindings", keybindingsButton);

            showGeneral.run();
        }

        private PanelWidget createTabButton(String text, float x, float width) {
            var panel = new PanelWidget(x, 0, width, 20);
            panel.addChild("button_logic", new ImageButtonWidget(0, 0, width, 20, (ResourceLocation) null, () -> {
            }));
            var back = new FillWidget(0, 0, width, 20, 0xFF000000);
            back.setAlpha(0.25f);
            panel.addChild("back", back);
            var layered = new PanelWidget(0, 0, width, 20);
            layered.setEnabled(false);
            panel.addChild("layered", layered);
            var label = new AutoScaleLabelWidget(text, 0, 0, width);
            label.setY((20 - label.getHeight()) / 2f);
            layered.addChild("text", label);
            return panel;
        }

        private ScrollPanelWidget createGeneralPanel() {
            var panel = new ScrollPanelWidget(5, 30, 190, 190);
            var currentY = 10f;

            panel.addChild("label_blur", new AutoScaleLabelWidget("Blur Radius", 0, currentY, 190));
            currentY += 20;

            Consumer<Float> blurRadiusUpdater = val -> {
                config.blurRadius = val;
                AcademyCraftClient.Config.INSTANCE.save();
            };
            panel.addChild("container_blur", createSliderWithLabel(currentY, config.blurRadius, 0.0f, 20.0f, blurRadiusUpdater));
            currentY += 20;

            panel.addChild("label_sensitivity", new AutoScaleLabelWidget("Mouse Sensitivity", 0, currentY, 190));
            currentY += 20;

            Consumer<Float> sensitivityUpdater = val -> {
                config.mouseSensitivity = val;
                AcademyCraftClient.Config.INSTANCE.save();
            };
            panel.addChild("container_sensitivity", createSliderWithLabel(currentY, config.mouseSensitivity, 0.1f, 2.0f, sensitivityUpdater));
            currentY += 20;

            panel.addChild("label_blur_toggle_title", new AutoScaleLabelWidget("Background Blur", 0, currentY, 190));
            currentY += 20;

            Supplier<Boolean> blurStateSupplier = () -> config.enableBlur;
            var blurToggleAction = new Runnable() {
                @Override
                public void run() {
                config.enableBlur = !config.enableBlur;
                AcademyCraftClient.Config.INSTANCE.save();
                }
            };
            panel.addChild("blur_toggle_button", createToggleButton(currentY, blurStateSupplier, blurToggleAction));

            return panel;
        }

        private PanelWidget createSliderWithLabel(float y, float initialValue, float min, float max, Consumer<Float> onValueChanged) {
            var container = new PanelWidget(5f, y, 180f, 8f);
            var slider = new SliderWidget(0, 0, 180f - 50, 8f, Orientation.HORIZONTAL, min, max, initialValue);
            container.addChild("slider", slider);

            var valueLabel = new AutoScaleLabelWidget(String.format("%.2f", initialValue), slider.getWidth() + 5, 0, 45);
            valueLabel.setScale(0.75f);
            valueLabel.setY((8f - valueLabel.getHeight() * valueLabel.getScale()) / 2f);
            container.addChild("label_value", valueLabel);

            slider.setOnValueChanged(val -> {
                onValueChanged.accept(val);
                valueLabel.setText(String.format("%.2f", val));
            });
            return container;
        }

        private PanelWidget createToggleButton(float y, Supplier<Boolean> stateSupplier, Runnable onPress) {
            var buttonPanel = new PanelWidget(5f, y, 180f, 20f);
            var text = new AutoScaleLabelWidget(stateSupplier.get() ? "On" : "Off", 0, 0, 180f);
            var toggleAction = new Runnable() {
                @Override
                public void run() {
                    onPress.run();
                    text.setText(stateSupplier.get() ? "On" : "Off");
                }
            };
            var logic = new ImageButtonWidget(0, 0, 180f, 20f, (ResourceLocation) null, toggleAction);
            buttonPanel.addChild("button_logic", logic);
            var back = new FillWidget(0, 0, 180f, 20f, 0xFF000000);
            back.setAlpha(0.3f);
            buttonPanel.addChild("back", back);
            var layered = new PanelWidget(0, 0, 180f, 20f);
            layered.setEnabled(false);
            buttonPanel.addChild("layered", layered);
            text.setY((20f - text.getHeight() * text.getScale()) / 2f);
            layered.addChild("text", text);
            return buttonPanel;
        }

        private ScrollPanelWidget createKeybindingsPanel(AbstractContainerWidget parent) {
            var keybindingsPanel = new ScrollPanelWidget(5, 30, 190, 190);
            keybindingsPanel.setVisible(false);
            keybindingsPanel.setEnabled(false);

            createKeySelectionPanel(parent);

            keybindingControlPanel = createKeybindingControlPanel();
            keybindingsPanel.addChild("control_panel", keybindingControlPanel);

            var allKeyNames = new ArrayList<>(InputSystem.KEY_BINDINGS.keySet());
            Collections.sort(allKeyNames);

            for (var keyName : allKeyNames) {
                var keybindingPanel = createKeybindingWidget(keyName, keyName);
                keybindingsPanel.addChild("keybinding_" + keyName, keybindingPanel);
            }
            updateKeybindingLayout(keybindingsPanel);
            return keybindingsPanel;
        }

        private void createKeySelectionPanel(AbstractContainerWidget parent) {
            keySelectionPanel = new PanelWidget(0, 0, 180, 190);
            keySelectionPanel.setZ(keySelectionPanel.getZ() + 10);
            keySelectionPanel.setX((parent.getWidth() - keySelectionPanel.getWidth()) / 2f);
            keySelectionPanel.setY((parent.getHeight() - keySelectionPanel.getHeight()) / 2f);
            keySelectionPanel.setVisible(false);
            keySelectionPanel.setEnabled(false);
            parent.addChild("key_selection_panel", keySelectionPanel);

            var back = new FillWidget(0, 0, keySelectionPanel.getWidth(), keySelectionPanel.getHeight(), 0xFF000000);
            back.setAlpha(0.4f);
            keySelectionPanel.addChild("back", back);
            var layered = new PanelWidget(0, 0, keySelectionPanel.getWidth(), keySelectionPanel.getHeight());
            layered.setEnabled(false);
            keySelectionPanel.addChild("key_selection_panel_layered", layered);
            var closeLabel = new AutoScaleLabelWidget("X", keySelectionPanel.getWidth() - 25, 10, 20);
            layered.addChild("text", closeLabel);
            var title = new AutoScaleLabelWidget("Select Key", 5, 5, keySelectionPanel.getWidth() - 10);
            layered.addChild("title", title);

            var closeButtonPanel = new PanelWidget(keySelectionPanel.getWidth() - 25, 5, 20, 15);
            var closeAction = new Runnable() {
                @Override
                public void run() {
                setBackgroundControlsEnabled(true);
                keySelectionPanel.setVisible(false);
                keySelectionPanel.setEnabled(false);
                }
            };
            var closeButtonLogic = new ImageButtonWidget(0, 0, 20, 15, (ResourceLocation) null, closeAction);
            closeButtonPanel.addChild("button_logic", closeButtonLogic);
            var closeBack = new FillWidget(0, 0, 20, 15, 0xFF000000);
            closeBack.setAlpha(0.5f);
            closeButtonPanel.addChild("back", closeBack);
            keySelectionPanel.addChild("close_btn", closeButtonPanel);

            var keysContainer = new ScrollPanelWidget(5, 25, 170, 135);
            keySelectionPanel.addChild("keys_container", keysContainer);

            var scrollBar = new ScrollBarWidget(keysContainer, keysContainer.getX() + keysContainer.getWidth() - 5, keysContainer.getY(), 5, keysContainer.getHeight(), Orientation.VERTICAL);
            keySelectionPanel.addChild("scroll_bar", scrollBar);
            scrollBar.setThumbColor(0x50AAAAAA);
            scrollBar.setTrackColor(0x70202020);
            scrollBar.setZ(scrollBar.getZ() + 1);

            var listenBtnPanel = new PanelWidget(5, 165, 170, 20);
            var listenAction = new Runnable() {
                @Override
                public void run() {
                isListeningForKey = true;
                setBackgroundControlsEnabled(true);
                keySelectionPanel.setVisible(false);
                keySelectionPanel.setEnabled(false);
                keySelectionButton.<PanelWidget>getChildUnSafe("layered").<AutoScaleLabelWidget>getChildUnSafe("text").setText("> ... <");
                }
            };
            var listenButtonLogic = new ImageButtonWidget(0, 0, 170, 20, (ResourceLocation) null, listenAction);
            listenBtnPanel.addChild("button_logic", listenButtonLogic);
            var listenBtnBack = new FillWidget(0, 0, 170, 20, 0xFF000000);
            listenBtnBack.setAlpha(0.5f);
            listenBtnPanel.addChild("back", listenBtnBack);
            var listenLabel = new AutoScaleLabelWidget("Listen for Input", 0, 6, listenBtnPanel.getWidth());
            listenLabel.setZ(listenLabel.getZ() + 1);
            listenBtnPanel.addChild("text", listenLabel);
            keySelectionPanel.addChild("listen_btn", listenBtnPanel);

            populateKeySelectionContainer(keysContainer);
        }

        private void populateKeySelectionContainer(ScrollPanelWidget container) {
            keyboardButtonMap.clear();
            mouseButtonMap.clear();

            var currentY = 5f;

            var keyboardLabel = new AutoScaleLabelWidget("Keyboard", 5, currentY, container.getWidth() - 10);
            keyboardLabel.setZ(keyboardLabel.getZ() + 1);
            container.addChild("keyboard_label", keyboardLabel);
            currentY += 15;

            var keyboardPanel = new PanelWidget(0, currentY, container.getWidth() - 10, 0);
            var keyboardBack = new FillWidget(0, 0, keyboardPanel.getWidth(), 0, 0xFF000000);
            keyboardBack.setAlpha(0.2f);
            keyboardPanel.addChild("back", keyboardBack);
            container.addChild("keyboard_panel", keyboardPanel);

            final var KEY_HEIGHT = 15f;
            final var H_SPACING = 2f;
            final var V_SPACING = 3f;
            final var KEY_WIDTH_UNIT = (keyboardPanel.getWidth() - 14 * H_SPACING) / 15f;

            var mainKeysPanel = new PanelWidget(5, 5, keyboardPanel.getWidth() - 10, 0);
            keyboardPanel.addChild("main_keys_panel", mainKeysPanel);
            float mainKeysY = 0;

            var rowX = 0f;
            var keyWidth = KEY_WIDTH_UNIT;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "`", GLFW.GLFW_KEY_GRAVE_ACCENT, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            for (var i = 1; i <= 9; i++) {
                keyWidth = KEY_WIDTH_UNIT;
                addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, String.valueOf(i), GLFW.GLFW_KEY_1 + i - 1, rowX, mainKeysY, keyWidth);
                rowX += keyWidth + H_SPACING;
            }
            keyWidth = KEY_WIDTH_UNIT;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "0", GLFW.GLFW_KEY_0, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            keyWidth = KEY_WIDTH_UNIT;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "-", GLFW.GLFW_KEY_MINUS, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            keyWidth = KEY_WIDTH_UNIT;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "=", GLFW.GLFW_KEY_EQUAL, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            keyWidth = mainKeysPanel.getWidth() - rowX;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "BACK", GLFW.GLFW_KEY_BACKSPACE, rowX, mainKeysY, keyWidth);
            mainKeysY += KEY_HEIGHT + V_SPACING;

            rowX = 0f;
            keyWidth = KEY_WIDTH_UNIT * 1.5f;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "TAB", GLFW.GLFW_KEY_TAB, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            var row3keys = new String[]{"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]"};
            var row3codes = new int[]{GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET};
            for (var i = 0; i < row3keys.length; i++) {
                keyWidth = KEY_WIDTH_UNIT;
                addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, row3keys[i], row3codes[i], rowX, mainKeysY, keyWidth);
                rowX += keyWidth + H_SPACING;
            }
            keyWidth = mainKeysPanel.getWidth() - rowX;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "\\", GLFW.GLFW_KEY_BACKSLASH, rowX, mainKeysY, keyWidth);
            mainKeysY += KEY_HEIGHT + V_SPACING;

            rowX = 0f;
            keyWidth = KEY_WIDTH_UNIT * 1.75f;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "CAPS", GLFW.GLFW_KEY_CAPS_LOCK, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            var row4keys = new String[]{"A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'"};
            var row4codes = new int[]{GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE};
            for (var i = 0; i < row4keys.length; i++) {
                keyWidth = KEY_WIDTH_UNIT;
                addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, row4keys[i], row4codes[i], rowX, mainKeysY, keyWidth);
                rowX += keyWidth + H_SPACING;
            }
            keyWidth = mainKeysPanel.getWidth() - rowX;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "ENTER", GLFW.GLFW_KEY_ENTER, rowX, mainKeysY, keyWidth);
            mainKeysY += KEY_HEIGHT + V_SPACING;

            rowX = 0f;
            keyWidth = KEY_WIDTH_UNIT * 2.25f;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "LSHIFT", GLFW.GLFW_KEY_LEFT_SHIFT, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            var row5keys = new String[]{"Z", "X", "C", "V", "B", "N", "M", ",", ".", "/"};
            var row5codes = new int[]{GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH};
            for (var i = 0; i < row5keys.length; i++) {
                keyWidth = KEY_WIDTH_UNIT;
                addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, row5keys[i], row5codes[i], rowX, mainKeysY, keyWidth);
                rowX += keyWidth + H_SPACING;
            }
            keyWidth = mainKeysPanel.getWidth() - rowX;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "RSHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT, rowX, mainKeysY, keyWidth);
            mainKeysY += KEY_HEIGHT + V_SPACING;

            rowX = 0f;
            keyWidth = KEY_WIDTH_UNIT * 1.5f;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "LCTRL", GLFW.GLFW_KEY_LEFT_CONTROL, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            keyWidth = KEY_WIDTH_UNIT * 1.25f;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "LALT", GLFW.GLFW_KEY_LEFT_ALT, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            keyWidth = keyboardPanel.getWidth() - (KEY_WIDTH_UNIT * 1.5f + KEY_WIDTH_UNIT * 1.25f + KEY_WIDTH_UNIT * 1.25f + KEY_WIDTH_UNIT * 1.5f) - H_SPACING * 4;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "SPACE", GLFW.GLFW_KEY_SPACE, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            keyWidth = KEY_WIDTH_UNIT * 1.25f;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "RALT", GLFW.GLFW_KEY_RIGHT_ALT, rowX, mainKeysY, keyWidth);
            rowX += keyWidth + H_SPACING;
            keyWidth = KEY_WIDTH_UNIT * 1.5f;
            addKey(mainKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "RCTRL", GLFW.GLFW_KEY_RIGHT_CONTROL, rowX, mainKeysY, keyWidth);
            mainKeysY += KEY_HEIGHT + 5f;

            mainKeysPanel.setHeight(mainKeysY);

            var sideKeysPanel = new PanelWidget(5, mainKeysY + 20, keyboardPanel.getWidth() - 10, 0);
            keyboardPanel.addChild("side_keys_panel", sideKeysPanel);
            float sideKeysY = 0;

            final var KEY_WIDTH_SIDE = 20f;
            addKey(sideKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "UP", GLFW.GLFW_KEY_UP, KEY_WIDTH_SIDE + H_SPACING, sideKeysY - KEY_HEIGHT - V_SPACING, KEY_WIDTH_SIDE);
            addKey(sideKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "LEFT", GLFW.GLFW_KEY_LEFT, 0, sideKeysY, KEY_WIDTH_SIDE);
            addKey(sideKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "DOWN", GLFW.GLFW_KEY_DOWN, KEY_WIDTH_SIDE + H_SPACING, sideKeysY, KEY_WIDTH_SIDE);
            addKey(sideKeysPanel, keyboardButtonMap, InputSystem.InputType.KEYBOARD, "RIGHT", GLFW.GLFW_KEY_RIGHT, 2 * (KEY_WIDTH_SIDE + H_SPACING), sideKeysY, KEY_WIDTH_SIDE);
            sideKeysY += KEY_HEIGHT + 20f;

            sideKeysPanel.setHeight(sideKeysY);

            var keyboardContentHeight = mainKeysY + sideKeysY + 10;
            keyboardPanel.setHeight(keyboardContentHeight);
            keyboardBack.setHeight(keyboardContentHeight);
            currentY += keyboardContentHeight + 10;

            var mouseLabel = new AutoScaleLabelWidget("Mouse", 5, currentY, container.getWidth() - 10);
            mouseLabel.setZ(mouseLabel.getZ() + 1);
            container.addChild("mouse_label", mouseLabel);
            currentY += 15;

            var mousePanel = new PanelWidget(5, currentY, container.getWidth() - 15, 0);
            var mouseBack = new FillWidget(0, 0, mousePanel.getWidth(), 0, 0xFF000000);
            mouseBack.setAlpha(0.2f);
            mousePanel.setZ(mousePanel.getZ() + 1);
            mousePanel.addChild("back", mouseBack);
            container.addChild("mouse_panel", mousePanel);

            var rowY = 5f;
            rowX = 0f;
            keyWidth = (mousePanel.getWidth() - 2 * H_SPACING) / 3f;
            addKey(mousePanel, mouseButtonMap, InputSystem.InputType.MOUSE, "Left", GLFW.GLFW_MOUSE_BUTTON_LEFT, rowX, rowY, keyWidth);
            rowX += keyWidth + H_SPACING;
            addKey(mousePanel, mouseButtonMap, InputSystem.InputType.MOUSE, "Right", GLFW.GLFW_MOUSE_BUTTON_RIGHT, rowX, rowY, keyWidth);
            rowX += keyWidth + H_SPACING;
            addKey(mousePanel, mouseButtonMap, InputSystem.InputType.MOUSE, "Middle", GLFW.GLFW_MOUSE_BUTTON_MIDDLE, rowX, rowY, keyWidth);
            rowY += KEY_HEIGHT + V_SPACING;

            rowX = 0f;
            keyWidth = (mousePanel.getWidth() - 4 * H_SPACING) / 5f;
            for (var i = 4; i <= 8; i++) {
                addKey(mousePanel, mouseButtonMap, InputSystem.InputType.MOUSE, "M" + i, GLFW.GLFW_MOUSE_BUTTON_4 + i - 4, rowX, rowY, keyWidth);
                rowX += keyWidth + H_SPACING;
            }
            rowY += KEY_HEIGHT + 5f;

            var mouseContentHeight = rowY;
            mousePanel.setHeight(mouseContentHeight);
            mouseBack.setHeight(mouseContentHeight);
        }

        private void addKey(PanelWidget container, Map<Integer, PanelWidget> buttonMap, InputSystem.InputType type,
                            String name, int code, float x, float y, float w) {
            var keyBtn = createSmallKeyButton(name, x, y, w, () -> {
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

        private PanelWidget createKeybindingControlPanel() {
            var controlPanel = new PanelWidget(5, 0, 180, 85);
            controlPanel.setVisible(false);
            controlPanel.setEnabled(false);

            var controlBack = new FillWidget(0, 0, 180, 85, 0xFF000000);
            controlBack.setAlpha(0.2f);
            controlPanel.addChild("back", controlBack);

            var controlPanelLayered = new PanelWidget(0, 0, 180, 85);
            controlPanel.addChild("control_panel_layered", controlPanelLayered);
            var typeLabel = new AutoScaleLabelWidget("Type:", 5, 8, 40);
            controlPanelLayered.addChild("type_label", typeLabel);
            var keyLabel = new AutoScaleLabelWidget("Key:", 5, 33, 40);
            controlPanelLayered.addChild("key_label", keyLabel);
            var modLabel = new AutoScaleLabelWidget("Mods:", 5, 63, 40);
            controlPanelLayered.addChild("mod_label", modLabel);

            var inputTypePanel = new PanelWidget(50, 5, 125, 20);
            controlPanel.addChild("input_type", inputTypePanel);

            var keyboardAction = new Runnable() {
                @Override
                public void run() {
                updateModifierButtonState(keyboardBtn, true);
                updateModifierButtonState(mouseBtn, false);
                updateBindingFromControls(true);
                }
            };
            keyboardBtn = new PanelWidget(0, 0, 60, 15);
            var keyboardLogic = new ImageButtonWidget(0, 0, 60, 15, (ResourceLocation) null, keyboardAction);
            keyboardBtn.addChild("button_logic", keyboardLogic);
            var keyboardBtnBack = new FillWidget(0, 0, 60, 15, 0xFF000000);
            keyboardBtnBack.setAlpha(0.4f);
            keyboardBtn.addChild("back", keyboardBtnBack);
            var keyboardBtnLayered = new PanelWidget(0, 0, 60, 15);
            keyboardBtnLayered.setEnabled(false);
            keyboardBtn.addChild("keyboard_btn_layered", keyboardBtnLayered);
            var keyboardLabel = new AutoScaleLabelWidget("Keyboard", 0, 4, keyboardBtn.getWidth());
            keyboardBtnLayered.addChild("label", keyboardLabel);
            inputTypePanel.addChild("keyboard", keyboardBtn);

            var mouseAction = new Runnable() {
                @Override
                public void run() {
                updateModifierButtonState(keyboardBtn, false);
                updateModifierButtonState(mouseBtn, true);
                updateBindingFromControls(true);
                }
            };
            mouseBtn = new PanelWidget(65, 0, 60, 15);
            var mouseLogic = new ImageButtonWidget(0, 0, 60, 15, (ResourceLocation) null, mouseAction);
            mouseBtn.addChild("button_logic", mouseLogic);
            var mouseBtnBack = new FillWidget(0, 0, 60, 15, 0xFF000000);
            mouseBtnBack.setAlpha(0.4f);
            mouseBtn.addChild("back", mouseBtnBack);
            var mouseBtnLayered = new PanelWidget(0, 0, 60, 15);
            mouseBtnLayered.setEnabled(false);
            mouseBtn.addChild("mouse_btn_layered", mouseBtnLayered);
            var mouseLabel = new AutoScaleLabelWidget("Mouse", 0, 4, mouseBtn.getWidth());
            mouseBtnLayered.addChild("label", mouseLabel);
            inputTypePanel.addChild("mouse", mouseBtn);

            keySelectionButton = new PanelWidget(50, 30, 125, 20);
            controlPanel.addChild("key_select", keySelectionButton);
            var keySelectAction = new Runnable() {
                @Override
                public void run() {
                    setBackgroundControlsEnabled(false);
                    keySelectionPanel.setVisible(true);
                    keySelectionPanel.setEnabled(true);
                    updateKeySelectionHighlights();
                }
            };
            var keySelectionLogic = new ImageButtonWidget(0, 0, 125, 20, (ResourceLocation) null, keySelectAction);
            keySelectionButton.addChild("button_logic", keySelectionLogic);
            var keySelectionButtonBack = new FillWidget(0, 0, 125, 20, 0xFF000000);
            keySelectionButtonBack.setAlpha(0.3f);
            keySelectionButton.addChild("back", keySelectionButtonBack);
            var keySelectionButtonLayered = new PanelWidget(0, 0, 125, 20);
            keySelectionButtonLayered.setEnabled(false);
            keySelectionButton.addChild("layered", keySelectionButtonLayered);
            var keySelectionButtonLabel = new AutoScaleLabelWidget("Set Key", 0, 0, keySelectionButton.getWidth());
            keySelectionButtonLabel.setY((keySelectionButton.getHeight() - keySelectionButtonLabel.getHeight()) / 2f);
            keySelectionButtonLayered.addChild("text", keySelectionButtonLabel);

            var modsPanel = new PanelWidget(50, 60, 125, 20);
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

        private PanelWidget createModifierButton(String text, float x, Runnable onPress) {
            var panel = new PanelWidget(x, 0, 35, 15);
            var buttonLogic = new ImageButtonWidget(0, 0, 35, 15, (ResourceLocation) null, onPress);
            panel.addChild("button_logic", buttonLogic);
            var back = new FillWidget(0, 0, 35, 15, 0xFF000000);
            panel.addChild("back", back);
            var layered = new PanelWidget(0, 0, 35, 15);
            layered.setEnabled(false);
            panel.addChild("layered", layered);
            var label = new AutoScaleLabelWidget(text, 0, 0, 35);
            label.setY((15 - label.getHeight()) / 2);
            layered.addChild("label", label);
            return panel;
        }

        private void toggleKeybindingControls(PanelWidget entry, String keyName) {
            var list = (AbstractContainerWidget) entry.getParent();
            if (list == null) return;

            var wasActive = activeKeybindingEntry == entry;

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

        private void updateKeybindingLayout(AbstractContainerWidget list) {
            var currentY = 10f;
            var entries = new ArrayList<Widget>();
            for (var entry : list.getChildren().entrySet()) {
                if (!entry.getKey().equals("control_panel")) {
                    entries.add(entry.getValue());
                }
            }

            for (var child : entries) {
                child.setY(currentY);
                currentY += child.getHeight() + 5;

                if (child == activeKeybindingEntry) {
                    keybindingControlPanel.setY(currentY);
                    currentY += keybindingControlPanel.getHeight() + 5;
                }
            }
        }

        private void updateControlPanelContents() {
            if (activeKeyName == null) return;
            var binding = getKeyBindingFromInputSystem(activeKeyName);
            if (binding == null) return;

            isListeningForKey = false;

            var isKeyboard = binding.inputType == InputSystem.InputType.KEYBOARD;
            updateModifierButtonState(keyboardBtn, isKeyboard);
            updateModifierButtonState(mouseBtn, !isKeyboard);

            var keyText = keySelectionButton.<PanelWidget>getChildUnSafe("layered").<AutoScaleLabelWidget>getChildUnSafe("text");
            keyText.setText(formatKey(binding));

            var mods = binding.keyInfo.modifiers;
            updateModifierButton(shiftModBtn, mods.contains(GLFW.GLFW_MOD_SHIFT));
            updateModifierButton(ctrlModBtn, mods.contains(GLFW.GLFW_MOD_CONTROL));
            updateModifierButton(altModBtn, mods.contains(GLFW.GLFW_MOD_ALT));
        }

        private void updateModifierButtonState(PanelWidget button, boolean active) {
            var back = button.<FillWidget>getChildUnSafe("back");
            back.setAlpha(active ? 0.6f : 0.3f);
        }

        private void updateBindingFromControls(boolean keyShouldBeReset) {
            if (activeKeyName == null) return;
            var binding = InputSystem.KEY_BINDINGS.get(activeKeyName);
            if (binding == null) return;

            binding.inputPair.inputType = keyboardBtn.<FillWidget>getChildUnSafe("back").getAlpha() == 0.6f ? InputSystem.InputType.KEYBOARD : InputSystem.InputType.MOUSE;

            var newMods = new LinkedHashSet<Integer>();
            if (shiftModBtn.<FillWidget>getChildUnSafe("back").getAlpha() == 0.6f) newMods.add(GLFW.GLFW_MOD_SHIFT);
            if (ctrlModBtn.<FillWidget>getChildUnSafe("back").getAlpha() == 0.6f) newMods.add(GLFW.GLFW_MOD_CONTROL);
            if (altModBtn.<FillWidget>getChildUnSafe("back").getAlpha() == 0.6f) newMods.add(GLFW.GLFW_MOD_ALT);
            binding.inputPair.keyInfo.modifiers = newMods;

            if (keyShouldBeReset) {
                binding.inputPair.keyInfo.inputs.clear();
                binding.inputPair.keyInfo.inputs.add(-1);
            }

            AcademyCraftClient.Config.INSTANCE.save();
            updateDisplayForActiveBinding();
        }

        private PanelWidget createKeybindingWidget(String displayName, String keyName) {
            var panel = new PanelWidget(0, 0, 180, 20);

            var label = new HoverLabelWidget(displayName, 5, 5, 95);
            panel.addChild("label", label);

            var buttonPanel = new PanelWidget(105, 0, 75, 20);
            var buttonLogic = new ImageButtonWidget(0, 0, 75, 20, (ResourceLocation) null, () -> toggleKeybindingControls(panel, keyName));
            buttonPanel.addChild("button_logic", buttonLogic);
            panel.addChild("button", buttonPanel);

            var buttonBack = new FillWidget(0, 0, buttonPanel.getWidth(), buttonPanel.getHeight(), 0xFF000000);
            buttonBack.setAlpha(0.3f);
            buttonPanel.addChild("back", buttonBack);

            var binding = getKeyBindingFromInputSystem(keyName);

            var layered = new PanelWidget(0, 0, 180, 20);
            layered.setEnabled(false);
            buttonPanel.addChild("layered", layered);
            var keyLabel = new AutoScaleLabelWidget(formatKeybinding(binding), 3, 0, buttonPanel.getWidth() - 6);
            keyLabel.setY((buttonPanel.getHeight() - keyLabel.getHeight()) / 2);
            layered.addChild("text", keyLabel);

            return panel;
        }

        private InputSystem.InputPair getKeyBindingFromInputSystem(String keyName) {
            var kb = InputSystem.KEY_BINDINGS.get(keyName);
            return kb != null ? kb.inputPair : null;
        }

        private String modifierBitToString(int mod) {
            if (mod == GLFW.GLFW_MOD_SHIFT) return "SHIFT";
            if (mod == GLFW.GLFW_MOD_CONTROL) return "CTRL";
            if (mod == GLFW.GLFW_MOD_ALT) return "ALT";
            return "MOD" + mod;
        }

        private String formatKeybinding(InputSystem.InputPair pair) {
            if (pair == null) return "None";
            var sb = new StringBuilder();
            pair.keyInfo.modifiers.forEach(mod -> sb.append(modifierBitToString(mod)).append("+"));
            sb.append(formatKey(pair));
            return sb.toString();
        }

        private String formatKey(InputSystem.InputPair pair) {
            if (pair == null || pair.keyInfo == null || pair.keyInfo.inputs.isEmpty() || pair.keyInfo.inputs.contains(-1))
                return "NONE";
            var key = pair.keyInfo.inputs.getFirst();
            String name;
            if (pair.inputType == InputSystem.InputType.KEYBOARD) {
                name = GLFW.glfwGetKeyName(key, GLFW.glfwGetKeyScancode(key));
                if (name == null) name = "Key" + key;
            } else {
                name = "Mouse" + key;
            }
            return name.toUpperCase();
        }

        private void handleRebind(InputSystem.InputType newType, LinkedHashSet<Integer> newKeys) {
            var binding = InputSystem.KEY_BINDINGS.get(activeKeyName);
            if (binding == null) {
                resetRebindStateVisuals();
                return;
            }

            binding.inputPair.inputType = newType;
            binding.inputPair.keyInfo.inputs = newKeys;

            isListeningForKey = false;
            AcademyCraftClient.Config.INSTANCE.save();
            ClientUtil.playDownSound();
            updateDisplayForActiveBinding();
        }

        private void resetRebindStateVisuals() {
            if (activeKeyName != null) {
                updateControlPanelContents();
            } else {
                if (keySelectionButton != null) {
                    keySelectionButton.<PanelWidget>getChildUnSafe("layered").<AutoScaleLabelWidget>getChildUnSafe("text").setText("Set Key");
                }
            }
        }

        private void updateDisplayForActiveBinding() {
            if (activeKeybindingEntry != null && activeKeyName != null) {
                var binding = getKeyBindingFromInputSystem(activeKeyName);
                var buttonPanel = activeKeybindingEntry.<PanelWidget>getChildUnSafe("button");
                var text = buttonPanel.<PanelWidget>getChildUnSafe("layered").<AutoScaleLabelWidget>getChildUnSafe("text");
                text.setText(formatKeybinding(binding));
                updateControlPanelContents();
            }
        }

        private void updateModifierButton(PanelWidget button, boolean active) {
            var back = button.<FillWidget>getChildUnSafe("back");
            back.setAlpha(active ? 0.6f : 0.3f);
        }

        private void toggleModifierButton(PanelWidget button) {
            var back = button.<FillWidget>getChildUnSafe("back");
            var nowSelected = back.getAlpha() != 0.6f;
            back.setAlpha(nowSelected ? 0.6f : 0.3f);
        }

        private PanelWidget createSmallKeyButton(String text, float x, float y, float w, Runnable onPress) {
            var panel = new PanelWidget(x, y, w, 15);
            var buttonLogic = new ImageButtonWidget(0, 0, w, 15, (ResourceLocation) null, onPress);
            panel.addChild("button_logic", buttonLogic);

            var back = new FillWidget(0, 0, w, 15, 0xFF000000);
            back.setAlpha(0.5f);
            panel.addChild("back", back);

            var layered = new PanelWidget(0, 0, w, 15);
            layered.setEnabled(false);
            panel.addChild("layered", layered);
            var label = new AutoScaleLabelWidget(text, 0, 0, w * 0.5f);
            label.setY((15f - label.getHeight() * label.getScale()) / 2f);
            label.setX((w - label.getWidth()) / 2f);
            layered.addChild("label", label);
            return panel;
        }

        private void setBackgroundControlsEnabled(boolean enabled) {
            if (keySelectionPanel == null || keySelectionPanel.getParent() == null) return;
            var appArea = (AbstractContainerWidget) keySelectionPanel.getParent();
            for (var child : appArea.getChildren().values()) {
                if (child != keySelectionPanel) {
                    child.setEnabled(enabled);
                }
            }
        }

        private void setKeyButtonSelected(PanelWidget btn, boolean selected) {
            if (btn == null) return;
            var back = btn.<FillWidget>getChildUnSafe("back");
            if (selected) {
                back.setAlpha(0.8f);
            } else {
                back.setAlpha(0.5f);
            }
        }

        private void updateKeySelectionHighlights() {
            keyboardButtonMap.values().forEach(btn -> setKeyButtonSelected(btn, false));
            mouseButtonMap.values().forEach(btn -> setKeyButtonSelected(btn, false));

            if (activeKeyName == null) return;
            var binding = getKeyBindingFromInputSystem(activeKeyName);
            if (binding == null || binding.keyInfo.inputs.isEmpty() || binding.keyInfo.inputs.contains(-1)) return;

            var targetMap = binding.inputType == InputSystem.InputType.KEYBOARD ? keyboardButtonMap : mouseButtonMap;

            for (var keyCode : binding.keyInfo.inputs) {
                setKeyButtonSelected(targetMap.get(keyCode), true);
            }
        }
    }
}*/
