package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Widget;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A container that acts as a logical controller for a group of ImageRadioButtonWidgets,
 * ensuring that only one can be selected at a time.
 * This widget does NOT perform any visual layout on its children. If you need automatic
 * arrangement, add a layout container (e.g., a PanelWidget or LinearLayoutContainer)
 * as a child of this widget, and then add the buttons to that layout container.
 */
public class RadioGroupWidget extends AbstractContainerWidget {
    @Nullable
    private ImageRadioButtonWidget selectedButton = null;
    @Nullable
    private Consumer<ImageRadioButtonWidget> onSelectionChanged = null;
    private boolean allowReselect = false;

    public RadioGroupWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    public void selectButton(@Nullable ImageRadioButtonWidget buttonToSelect) {
        if (!this.allowReselect && Objects.equals(buttonToSelect, this.selectedButton)) {
            return;
        }

        this.internalSelect(buttonToSelect, true);
    }

    private void internalSelect(@Nullable ImageRadioButtonWidget buttonToSelect, boolean triggerCallback) {
        if (buttonToSelect != null && !buttonToSelect.isEnabled()) {
            return;
        }

        var selectionChanged = !Objects.equals(this.selectedButton, buttonToSelect);

        if (this.selectedButton != null) {
            this.selectedButton.setSelected(false);
        }

        this.selectedButton = buttonToSelect;
        if (this.selectedButton != null) {
            this.selectedButton.setSelected(true);
        }

        if (triggerCallback && (selectionChanged || this.allowReselect) && this.onSelectionChanged != null) {
            this.onSelectionChanged.accept(this.getSelectedButton());
        }
    }

    @Nullable
    public ImageRadioButtonWidget getSelectedButton() {
        return this.selectedButton;
    }

    @Nullable
    public String getSelectedButtonName() {
        if (this.selectedButton == null) {
            return null;
        }
        for (var entry : this.getChildren().entrySet()) {
            if (entry.getValue() == this.selectedButton) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public void addChild(String name, Widget child) {
        super.addChild(name, child);
        if (child instanceof ImageRadioButtonWidget radioButton) {
            radioButton.setRadioGroup(this);
        }
    }

    @Override
    public void removeChild(String name) {
        var removedWidget = this.getChildren().get(name);
        if (removedWidget == this.selectedButton) {
            this.internalSelect(null, true);
        }

        super.removeChild(name);

        if (removedWidget instanceof ImageRadioButtonWidget removedRadio) {
            removedRadio.setRadioGroup(null);
        }
    }

    @Override
    public void clearChildren() {
        for (var child : this.getChildren().values()) {
            if (child instanceof ImageRadioButtonWidget radioButton) {
                radioButton.setRadioGroup(null);
            }
        }
        super.clearChildren();
        if (this.selectedButton != null) {
            this.internalSelect(null, true);
        }
    }

    public void setOnSelectionChanged(@Nullable Consumer<ImageRadioButtonWidget> onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    public void setAllowReselect(boolean allowReselect) {
        this.allowReselect = allowReselect;
    }
}