package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.Widget;
import org.jetbrains.annotations.Nullable;

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

    public void setOnSelectionChanged(Consumer<ImageRadioButtonWidget> newOnSelectionChanged) {
        onSelectionChanged = newOnSelectionChanged;
    }

    public void setAllowReselect(boolean newAllowReselect) {
        allowReselect = newAllowReselect;
    }

    @Override
    public void addChild(String name, Widget child) {
        super.addChild(name, child);

        if (child instanceof ImageRadioButtonWidget radioButton) {
            radioButton.setRadioGroup(this);
            radioButton.setId(idCounter++);
        }
    }

    public void selectButton(@Nullable ImageRadioButtonWidget buttonToSelect) {
        if (!allowReselect && Objects.equals(buttonToSelect, selectedButton)) {
            return;
        }

        internalSelect(buttonToSelect, true);
    }

    public void selectButtonByName(String name) {
        var widget = getChildren().get(name);
        if (widget instanceof ImageRadioButtonWidget radioButton) {
            selectButton(radioButton);
        }
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
        for (var entry : getChildren().entrySet()) {
            if (entry.getValue() == selectedButton) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public void removeChild(String name) {
        var removed = children.get(name);
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
        for (var child : getChildren().values()) {
            if (child instanceof ImageRadioButtonWidget radioButton) {
                radioButton.setRadioGroup(null);
            }
        }
        super.clearChildren();
        if (selectedButton != null) {
            internalSelect(null, true);
        }
    }
}