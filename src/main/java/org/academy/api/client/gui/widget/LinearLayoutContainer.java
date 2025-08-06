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
        float currentPos = 0;
        for (Widget child : this.getChildren().values()) {
            if (orientation == Orientation.HORIZONTAL) {
                child.setX(currentPos);
                child.setY((this.getHeight() - child.getHeight()) / 2f);
                currentPos += child.getWidth() + this.spacing;
            } else {
                child.setX((this.getWidth() - child.getWidth()) / 2f);
                child.setY(currentPos);
                currentPos += child.getHeight() + this.spacing;
            }
        }

        if (orientation == Orientation.HORIZONTAL) {
            this.setWidth(Math.max(0, currentPos - this.spacing));
        } else {
            this.setHeight(Math.max(0, currentPos - this.spacing));
        }
    }

    @Override
    public void addChild(String name, Widget child) {
        super.addChild(name, child);
        this.doLayout();
    }

    @Override
    public void removeChild(String name) {
        super.removeChild(name);
        this.doLayout();
    }

    public void setSpacing(float spacing) {
        this.spacing = spacing;
        this.doLayout();
    }

    public float getSpacing() {
        return this.spacing;
    }
}