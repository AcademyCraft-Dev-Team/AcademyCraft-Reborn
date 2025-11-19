package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.drawable.ColorDrawable;
import org.academy.api.client.gui.render.RenderContext;

public class FillWidget extends AbstractWidget {
    public FillWidget(int color) {
        setBackground(new ColorDrawable(color));
    }

    @Override
    public void render(RenderContext context) {
        if (!isVisible()) return;
        super.render(context);
    }

    public int getColor() {
        if (getBackground() instanceof ColorDrawable colorDrawable) {
            return colorDrawable.getColor();
        }
        return 0;
    }

    /**
     * @deprecated Use {@code setBackground(new ColorDrawable(color))} or {@code if (getBackground() instanceof ColorDrawable cd) cd.setColor(color);} instead.
     */
    @Deprecated
    public FillWidget setColor(int color) {
        if (getBackground() instanceof ColorDrawable colorDrawable) {
            colorDrawable.setColor(color);
        } else {
            setBackground(new ColorDrawable(color));
        }
        return this;
    }
}