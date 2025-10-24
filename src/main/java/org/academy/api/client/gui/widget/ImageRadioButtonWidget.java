package org.academy.api.client.gui.widget;

import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.gui.event.MouseEvent;
import org.jetbrains.annotations.Nullable;

public class ImageRadioButtonWidget extends ImageButtonWidget {
    protected boolean selected = false;
    @Nullable
    protected RadioGroupWidget radioGroup = null;

    protected float selectedAlpha = 1.0f;
    protected float unselectedAlpha = 0.7f;
    protected float hoverAlpha = 1.0f;
    protected float disabledAlpha = 0.5f;
    protected int id = -1;

    public ImageRadioButtonWidget(ResourceLocation texture) {
        this(texture, () -> {
        });
    }

    public ImageRadioButtonWidget(ResourceLocation texture, Runnable onPress) {
        super(texture, onPress);
        updateVisualState();
    }

    public int getId() {
        return id;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    protected void onMousePressed(MouseEvent event) {
        super.onMousePressed(event);
        if (event.isConsumed() && radioGroup != null) {
            radioGroup.selectButton(this);
        }
    }

    @Override
    public void setHovered(boolean hovered) {
        super.setHovered(hovered);
        updateVisualState();
    }

    @Override
    public ImageButtonWidget setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        updateVisualState();
        return this;
    }

    public ImageRadioButtonWidget setId(int id) {
        this.id = id;
        return this;
    }

    protected ImageRadioButtonWidget setRadioGroup(@Nullable RadioGroupWidget radioGroup) {
        this.radioGroup = radioGroup;
        return this;
    }

    public ImageRadioButtonWidget setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            updateVisualState();
        }
        return this;
    }

    public ImageRadioButtonWidget setVisualAlphas(float selected, float unselected, float hover, float disabled) {
        selectedAlpha = selected;
        unselectedAlpha = unselected;
        hoverAlpha = hover;
        disabledAlpha = disabled;
        updateVisualState();
        return this;
    }

    public void updateVisualState() {
        if (!isEnabled()) {
            setAlpha(disabledAlpha);
        } else if (isHovered()) {
            setAlpha(hoverAlpha);
        } else if (isSelected()) {
            setAlpha(selectedAlpha);
        } else {
            setAlpha(unselectedAlpha);
        }
    }
}