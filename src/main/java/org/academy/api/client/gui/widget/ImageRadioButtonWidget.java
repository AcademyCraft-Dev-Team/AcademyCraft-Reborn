package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.event.MouseEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A specialized ImageButton that can be part of a RadioGroup. Only one button
 * within a group can be selected at a time. Its visual appearance (alpha)
 * changes based on its selection, hover, and enabled states.
 */
public class ImageRadioButtonWidget extends ImageButtonWidget {
    protected boolean selected = false;
    protected RadioGroupWidget radioGroup = null;

    protected float selectedAlpha = 1.0f;
    protected float unselectedAlpha = 0.7f;
    protected float hoverAlpha = 1.0f;
    protected float disabledAlpha = 0.5f;
    protected int id = -1;

    public ImageRadioButtonWidget(float x, float y, float width, float height,
                                  RenderType renderType, Runnable onPress) {
        super(x, y, width, height, renderType, onPress);
        this.defaultHoverEffect = false;
        this.updateVisualState();
    }

    public int getId() {
        return this.id;
    }

    public boolean isSelected() {
        return this.selected;
    }

    @Override
    protected void onMousePressed(@NotNull MouseEvent event) {
        super.onMousePressed(event);
        if (event.isConsumed() && this.radioGroup != null) {
            this.radioGroup.selectButton(this);
        }
    }

    @Override
    public void setHovered(boolean hovered) {
        super.setHovered(hovered);
        this.updateVisualState();
    }

    @Override
    public @NotNull ImageButtonWidget setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.updateVisualState();
        return this;
    }

    public ImageRadioButtonWidget setId(int id) {
        this.id = id;
        return this;
    }

    protected ImageRadioButtonWidget setRadioGroup(RadioGroupWidget radioGroup) {
        this.radioGroup = radioGroup;
        return this;
    }

    public ImageRadioButtonWidget setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            this.updateVisualState();
        }
        return this;
    }

    public ImageRadioButtonWidget setVisualAlphas(float selected, float unselected, float hover, float disabled) {
        this.selectedAlpha = selected;
        this.unselectedAlpha = unselected;
        this.hoverAlpha = hover;
        this.disabledAlpha = disabled;
        this.updateVisualState();
        return this;
    }

    /**
     * Updates the widget's alpha based on its current state hierarchy:
     * disabled -> hovered -> selected -> unselected.
     */
    public void updateVisualState() {
        if (!this.isEnabled()) {
            this.setAlpha(this.disabledAlpha);
        } else if (this.isHovered()) {
            this.setAlpha(this.hoverAlpha);
        } else if (this.isSelected()) {
            this.setAlpha(this.selectedAlpha);
        } else {
            this.setAlpha(this.unselectedAlpha);
        }
    }
}