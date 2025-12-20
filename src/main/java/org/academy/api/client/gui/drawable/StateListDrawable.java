package org.academy.api.client.gui.drawable;

import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.widget.Widget;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StateListDrawable implements Drawable {
    private record StatePair(int mask, @Nullable Drawable drawable) {
    }

    private final List<StatePair> stateList = new ArrayList<>();
    @Nullable
    private Drawable defaultDrawable;

    @Override
    public void draw(RenderContext context, Widget widget) {
        var currentState = widget.getWidgetState();

        for (var pair : stateList) {
            if ((currentState & pair.mask) == pair.mask) {
                if (pair.drawable != null) {
                    pair.drawable.draw(context, widget);
                }
                return;
            }
        }

        if (defaultDrawable != null) {
            defaultDrawable.draw(context, widget);
        }
    }

    public void addState(int stateMask, @Nullable Drawable drawable) {
        stateList.add(new StatePair(stateMask, drawable));
    }

    public void setDefault(@Nullable Drawable drawable) {
        defaultDrawable = drawable;
    }
}