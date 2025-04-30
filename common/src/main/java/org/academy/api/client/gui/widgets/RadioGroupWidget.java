package org.academy.api.client.gui.widgets;

import org.academy.api.client.gui.framework.Widget;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class RadioGroupWidget extends PanelWidget {
    private ImageRadioButtonWidget selectedButton = null;
    private Consumer<ImageRadioButtonWidget> onSelectionChanged = null;
    private boolean allowReselect = false;
    public int idCounter = 0;

    public RadioGroupWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    /**
     * Sets a callback to be executed when the selected radio button changes.
     * The callback receives the newly selected button (or null if deselected, though typical radio groups don't deselect all).
     * @param onSelectionChanged Consumer accepting the selected ImageRadioButtonWidget.
     */
    public void setOnSelectionChanged(Consumer<ImageRadioButtonWidget> onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    /**
     * Sets whether clicking the already selected button should trigger the
     * onSelectionChanged callback again. Default is false.
     * @param allowReselect True to allow re-triggering the callback.
     */
    public void setAllowReselect(boolean allowReselect) {
        this.allowReselect = allowReselect;
    }

    @Override
    public void addChild(String name, Widget child) {
        super.addChild(name, child);

        if (child instanceof ImageRadioButtonWidget radioButton) {
            radioButton.setRadioGroup(this);
            radioButton.setId(idCounter++);
        }
    }

    /**
     * Programmatically selects a radio button within this group.
     * @param buttonToSelect The radio button to select. Must be a child of this group.
     */
    public void selectButton(@Nullable ImageRadioButtonWidget buttonToSelect) {
        if (buttonToSelect != null && !this.children.containsValue(buttonToSelect)) {
            System.err.println("Attempted to select a radio button not belonging to this group.");
            return;
        }

        if (!allowReselect && Objects.equals(buttonToSelect, this.selectedButton)) {
            return;
        }

        internalSelect(buttonToSelect, true);
    }

    /**
     * Selects a radio button within the group by its name.
     * @param name The name of the radio button widget to select.
     */
    public void selectButtonByName(String name) {
        Widget widget = getChildren().get(name);
        if (widget instanceof ImageRadioButtonWidget radioButton) {
            selectButton(radioButton);
        } else {
            System.err.println("No ImageRadioButtonWidget found with name: " + name);
        }
    }


    /**
     * Internal selection logic.
     * @param buttonToSelect The button to select.
     * @param triggerCallback If true, triggers the onSelectionChanged callback.
     */
    private void internalSelect(@Nullable ImageRadioButtonWidget buttonToSelect, boolean triggerCallback) {
        if (buttonToSelect != null && !buttonToSelect.isEnabled()) {
            return;
        }

        boolean selectionChanged = !Objects.equals(selectedButton, buttonToSelect);

        if (selectedButton != null) {
            selectedButton.setSelected(false);
        }

        selectedButton = buttonToSelect;
        if (selectedButton != null) {
            selectedButton.setSelected(true);
        }

        if (triggerCallback && (selectionChanged || allowReselect) && onSelectionChanged != null) {
            onSelectionChanged.accept(getSelectedButton());
        }
    }


    @Nullable
    public ImageRadioButtonWidget getSelectedButton() {
        return selectedButton;
    }

    @Nullable
    public String getSelectedButtonName() {
        if (selectedButton == null) {
            return null;
        }
        for (Map.Entry<String, Widget> entry : children.entrySet()) {
            if (entry.getValue() == selectedButton) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public void removeChild(String name) {
        Widget removed = children.get(name);
        super.removeChild(name);
        if (removed == selectedButton) {
            internalSelect(null, true);
        }
        if (removed instanceof ImageRadioButtonWidget removedRadio) {
            removedRadio.setRadioGroup(null);
        }
    }

    @Override
    public void clearChildren() {
        for (Widget child : children.values()) {
            if (child instanceof ImageRadioButtonWidget radioButton) {
                radioButton.setRadioGroup(null);
            }
        }
        super.clearChildren();
        if (selectedButton != null) {
            internalSelect(null, true); // Clear selection and trigger callback
        }
    }
}