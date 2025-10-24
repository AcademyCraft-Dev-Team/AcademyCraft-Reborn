package org.academy.api.client.gui.widget;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A container that acts as both a logical controller and a visual layout
 * for a group of ImageRadioButtonWidgets, ensuring only one can be selected at a time.
 * It inherits from LinearLayoutWidget to automatically arrange its children.
 */
public class RadioGroupWidget extends LinearLayoutWidget {
    @Nullable
    protected ImageRadioButtonWidget selectedButton = null;
    @Nullable
    protected Consumer<ImageRadioButtonWidget> onSelectionChanged = null;
    protected boolean allowReselect = false ;
    protected int idCounter = 0;

    public void selectButton(@Nullable ImageRadioButtonWidget buttonToSelect) {
        if (!allowReselect && Objects.equals(buttonToSelect, selectedButton)) {
            return;
        }

        internalSelect(buttonToSelect, true);
    }

    private void internalSelect(@Nullable ImageRadioButtonWidget buttonToSelect, boolean triggerCallback) {
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
    public ImageRadioButtonWidget getSelectedButton() {
        return selectedButton;
    }

    @Override
    public void addChild(String name, Widget child) {
        if (child instanceof ImageRadioButtonWidget radioButton) {
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

        if (removedWidget instanceof ImageRadioButtonWidget removedRadio) {
            removedRadio.setRadioGroup(null);
        }

        super.removeChild(name);
    }

    @Override
    public void clearChildren() {
        for (var child : children.values()) {
            if (child instanceof ImageRadioButtonWidget radioButton) {
                radioButton.setRadioGroup(null);
            }
        }
        if (selectedButton != null) {
            internalSelect(null, true);
        }
        super.clearChildren();
    }

    public void setOnSelectionChanged(@Nullable Consumer<ImageRadioButtonWidget> onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    public void setAllowReselect(boolean allowReselect) {
        this.allowReselect = allowReselect;
    }
}