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

    public int getId() {
        return id;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public ImageRadioButtonWidget setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        updateVisualState();
        return this;
    }

    @Override
    public ImageRadioButtonWidget setHovered(boolean hovered) {
        super.setHovered(hovered);
        updateVisualState();
        return this;
    }

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        var result = super.mousePressed(mouseX, mouseY, button);
        if (result) {
            if (radioGroup != null) {
                radioGroup.selectButton(this);
            }
        }
        return result;
    }

    public ImageRadioButtonWidget setId(int newId) {
        id = newId;
        return this;
    }

    protected ImageRadioButtonWidget setRadioGroup(RadioGroupWidget newRadioGroup) {
        radioGroup = newRadioGroup;
        return this;
    }

    public ImageRadioButtonWidget setSelected(boolean newSelected) {
        if (selected != newSelected) {
            selected = newSelected;
            updateVisualState();
        }
        return this;
    }

    public void updateVisualState() {
        if (!enabled) {
            setAlpha(disabledAlpha);
        } else if (hovered) {
            setAlpha(hoverAlpha);
        } else if (selected) {
            setAlpha(selectedAlpha);
        } else {
            setAlpha(unselectedAlpha);
        }
    }
}