package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.framework.Widget;

public class LinearLayoutContainer extends AbstractContainerWidget {
    protected final Orientation orientation;
    protected float spacing = 0f;

    public LinearLayoutContainer(float x, float y, float width, float height, Orientation orientation) {
        super(x, y, width, height);
        this.orientation = orientation;
    }

    public void doLayout() {
        var currentPos = 0f;
        for (var child : getChildren().values()) {
            if (orientation == Orientation.HORIZONTAL) {
                child.setX(currentPos);
                child.setY((getHeight() - child.getHeight()) / 2f);
                currentPos += child.getWidth() + spacing;
            } else {
                child.setX((getWidth() - child.getWidth()) / 2f);
                child.setY(currentPos);
                currentPos += child.getHeight() + spacing;
            }
        }

        if (orientation == Orientation.HORIZONTAL) {
            setWidth(Math.max(0, currentPos - spacing));
        } else {
            setHeight(Math.max(0, currentPos - spacing));
        }
    }

    @Override
    public void addChild(String name, Widget child) {
        super.addChild(name, child);
        doLayout();
    }

    @Override
    public void removeChild(String name) {
        super.removeChild(name);
        doLayout();
    }

    public void setSpacing(float spacing) {
        this.spacing = spacing;
        doLayout();
    }

    public float getSpacing() {
        return spacing;
    }
}