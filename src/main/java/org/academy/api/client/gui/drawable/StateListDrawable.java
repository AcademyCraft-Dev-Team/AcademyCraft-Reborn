package org.academy.api.client.gui.drawable;

import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.widget.Widget;

import java.util.EnumMap;
import java.util.Map;

public class StateListDrawable implements Drawable {
    private final Map<WidgetState, Drawable> stateMap = new EnumMap<>(WidgetState.class);

    @Override
    public void draw(RenderContext context, Widget widget) {
        var drawable = getDrawableForState(widget);
        if (drawable != null) {
            drawable.draw(context, widget);
        }
    }

    public void addState(WidgetState state, Drawable drawable) {
        stateMap.put(state, drawable);
    }

    private Drawable getDrawableForState(Widget widget) {
        if (!widget.isEnabled()) return stateMap.get(WidgetState.DISABLED);
        if (widget.isPressed()) return stateMap.get(WidgetState.PRESSED);
        if (widget.isSelected()) return stateMap.get(WidgetState.SELECTED);
        if (widget.isHovered()) return stateMap.get(WidgetState.HOVERED);
        if (widget.isFocused()) return stateMap.get(WidgetState.FOCUSED);

        return stateMap.get(WidgetState.DEFAULT);
    }
}