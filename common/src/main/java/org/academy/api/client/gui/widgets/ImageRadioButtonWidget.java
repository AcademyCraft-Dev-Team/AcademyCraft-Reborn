package org.academy.api.client.gui.widgets;

import net.minecraft.client.renderer.RenderType;

public class ImageRadioButtonWidget extends ImageButtonWidget {
    private boolean selected = false;
    private RadioGroupWidget radioGroup = null;

    public float selectedAlpha = 1.0f;
    public float unselectedAlpha = 0.7f;
    public float hoverAlpha = 1.0f;
    public float disabledAlpha = 0.5f;
    private int id = -1;

    public ImageRadioButtonWidget(float x, float y, float width, float height,
                                  RenderType renderType, Runnable onPress) {
        super(x, y, width, height, renderType, onPress);
        updateVisualState();
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        if (result) {
            if (radioGroup != null) {
                radioGroup.selectButton(this);
            }
        }
        return result;
    }

    protected void setRadioGroup(RadioGroupWidget group) {
        this.radioGroup = group;
    }

    public boolean isSelected() {
        return selected;
    }

    /**
     * Sets the selection state of this radio button.
     * Should typically only be called by the RadioGroupWidget.
     * @param selected True if this button should be selected, false otherwise.
     */
    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            updateVisualState();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        updateVisualState();
    }

    @Override
    public void setHovered(boolean hovered) {
        super.setHovered(hovered);
        updateVisualState();
    }

    /**
     * Updates the alpha based on the current state (enabled, selected, hovered).
     */
    private void updateVisualState() {
        if (!this.enabled) {
            this.alpha = disabledAlpha;
        } else if (this.hovered) {
            this.alpha = hoverAlpha; // Hover overrides selection dimming for visual feedback
        } else if (this.selected) {
            this.alpha = selectedAlpha;
        } else {
            this.alpha = unselectedAlpha;
        }
    }
}