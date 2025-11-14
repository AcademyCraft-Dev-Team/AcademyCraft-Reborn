package org.academy.api.client.gui.drawable;

import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.widget.Widget;

public interface Drawable {
    void draw(RenderContext context, Widget widget);
}