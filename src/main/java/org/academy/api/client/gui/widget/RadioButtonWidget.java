package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.MouseEvent;
import org.jspecify.annotations.Nullable;

/**
 * A universal radio button that can contain any widget as its content.
 * It extends ButtonWidget, inheriting all button behaviors, and adds selection state management.
 * It is designed to be used within a {@link RadioGroupWidget}.
 */
public class RadioButtonWidget extends ButtonWidget {
    protected boolean selected = false;
    @Nullable
    protected RadioGroupWidget radioGroup = null;
    protected int id = -1;

    /**
     * Creates an empty radio button.
     */
    public RadioButtonWidget() {
    }

    /**
     * Creates a radio button with the given widget as its content.
     * @param content The widget to be displayed inside the radio button.
     */
    public RadioButtonWidget(Widget content) {
        super(content);
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public int getId() {
        return id;
    }

    public RadioButtonWidget setId(int id) {
        this.id = id;
        return this;
    }

    @Override
    protected void onMousePressed(MouseEvent event) {
        super.onMousePressed(event);
        if (event.isConsumed() && radioGroup != null) {
            radioGroup.selectButton(this);
        }
    }

    protected RadioButtonWidget setRadioGroup(@Nullable RadioGroupWidget radioGroup) {
        this.radioGroup = radioGroup;
        return this;
    }
}