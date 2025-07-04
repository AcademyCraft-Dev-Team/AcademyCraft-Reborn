package org.academy.api.client.gui.widget;

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
        defaultHoverEffect = false;
        updateVisualState();
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        boolean result = super.mousePressed(mouseX, mouseY, button);
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

    public void updateVisualState() {
        if (!this.enabled) {
            setAlpha(disabledAlpha);
        } else if (this.hovered) {
            setAlpha(hoverAlpha);
        } else if (this.selected) {
            setAlpha(selectedAlpha);
        } else {
            setAlpha(unselectedAlpha);
        }
    }
}