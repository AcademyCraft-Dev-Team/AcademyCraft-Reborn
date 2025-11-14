package org.academy.api.client.gui.widget;

import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.gui.drawable.StateListDrawable;
import org.academy.api.client.gui.drawable.TextureDrawable;
import org.academy.api.client.gui.drawable.WidgetState;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.OnClickListener;
import org.jetbrains.annotations.Nullable;

public class ImageRadioButtonWidget extends ImageButtonWidget {
    protected boolean selected = false;
    @Nullable
    protected RadioGroupWidget radioGroup = null;
    protected int id = -1;

    public ImageRadioButtonWidget(ResourceLocation texture) {
        this(texture, null);
    }

    public ImageRadioButtonWidget(ResourceLocation texture, @Nullable OnClickListener listener) {
        super(texture, listener);

        var defaultDrawable = new TextureDrawable(texture);
        defaultDrawable.setTintColor(0xB3FFFFFF);
        var hoveredDrawable = new TextureDrawable(texture);
        hoveredDrawable.setTintColor(0xFFFFFFFF);
        var selectedDrawable = new TextureDrawable(texture);
        selectedDrawable.setTintColor(0xFFFFFFFF);
        var disabledDrawable = new TextureDrawable(texture);
        disabledDrawable.setTintColor(0x80FFFFFF);

        var sld = new StateListDrawable();
        sld.addState(WidgetState.DISABLED, disabledDrawable);
        sld.addState(WidgetState.SELECTED, selectedDrawable);
        sld.addState(WidgetState.HOVERED, hoveredDrawable);
        sld.addState(WidgetState.DEFAULT, defaultDrawable);

        setBackground(sld);
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

    @Override
    protected void onMousePressed(MouseEvent event) {
        super.onMousePressed(event);
        if (event.isConsumed() && radioGroup != null) {
            radioGroup.selectButton(this);
        }
    }

    public ImageRadioButtonWidget setId(int id) {
        this.id = id;
        return this;
    }

    protected ImageRadioButtonWidget setRadioGroup(@Nullable RadioGroupWidget radioGroup) {
        this.radioGroup = radioGroup;
        return this;
    }

    /**
     * @deprecated Visual state is now controlled by the background {@link StateListDrawable}.
     */
    @Deprecated
    public ImageRadioButtonWidget setVisualAlphas(float selected, float unselected, float hover, float disabled) {
        return this;
    }

    /**
     * @deprecated Visual state is now controlled by the background {@link StateListDrawable}.
     */
    @Deprecated
    public void updateVisualState() {
    }
}