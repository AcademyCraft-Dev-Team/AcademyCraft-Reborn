package org.academy.api.client.gui.widget;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A container that acts as both a logical controller and a visual layout
 * for a group of {@link RadioButtonWidget}s, ensuring only one can be selected at a time.
 * It inherits from LinearLayoutWidget to automatically arrange its children.
 */
public class RadioGroupWidget extends LinearLayoutWidget {
    @Nullable
    protected RadioButtonWidget selectedButton = null;
    @Nullable
    protected Consumer<RadioButtonWidget> onSelectionChanged = null;
    protected boolean allowReselect = false;
    protected int idCounter = 0;

    public void selectButton(@Nullable RadioButtonWidget buttonToSelect) {
        if (!allowReselect && Objects.equals(buttonToSelect, selectedButton)) {
            return;
        }

        internalSelect(buttonToSelect, true);
    }

    private void internalSelect(@Nullable RadioButtonWidget buttonToSelect, boolean triggerCallback) {
        if (buttonToSelect != null && !buttonToSelect.isEnabled()) {
            return;
        }

        var selectionChanged = !Objects.equals(selectedButton, buttonToSelect);

        if (selectedButton != null) {
            selectedButton.setSelected(false);
        }

        selectedButton = buttonToSelect;
        if (selectedButton != null) {
            selectedButton.setSelected(true);
        }

        if (triggerCallback && (selectionChanged || allowReselect) && onSelectionChanged != null && getSelectedButton() != null) {
            onSelectionChanged.accept(getSelectedButton());
        }
    }

    @Nullable
    public RadioButtonWidget getSelectedButton() {
        return selectedButton;
    }

    @Override
    public void addChild(String name, Widget child) {
        if (child instanceof RadioButtonWidget radioButton) {
            radioButton.setRadioGroup(this);
            radioButton.setId(idCounter++);
        }
        super.addChild(name, child);
    }

    @Override
    public void removeChild(String name) {
        var removedWidget = children.get(name);
        if (removedWidget == selectedButton) {
            internalSelect(null, true);
        }

        if (removedWidget instanceof RadioButtonWidget removedRadio) {
            removedRadio.setRadioGroup(null);
        }

        super.removeChild(name);
    }

    @Override
    public void clearChildren() {
        for (var child : children.values()) {
            if (child instanceof RadioButtonWidget radioButton) {
                radioButton.setRadioGroup(null);
            }
        }
        if (selectedButton != null) {
            internalSelect(null, true);
        }
        super.clearChildren();
    }

    public void setOnSelectionChanged(@Nullable Consumer<RadioButtonWidget> onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    public void setAllowReselect(boolean allowReselect) {
        this.allowReselect = allowReselect;
    }
}