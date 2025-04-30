package org.academy.api.client.gui.widgets;

import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Widget;

public class PanelWidget extends AbstractContainerWidget {
    public enum HorizontalGravity {
        LEFT, CENTER, RIGHT
    }

    public enum VerticalGravity {
        TOP, CENTER, BOTTOM
    }

    private HorizontalGravity horizontalGravity = HorizontalGravity.LEFT;
    private VerticalGravity verticalGravity = VerticalGravity.TOP;

    public boolean shouldFocus = false;

    public PanelWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    public void setHorizontalGravity(HorizontalGravity horizontalGravity) {
        this.horizontalGravity = horizontalGravity;
    }

    public void setVerticalGravity(VerticalGravity verticalGravity) {
        this.verticalGravity = verticalGravity;
    }

    public HorizontalGravity getHorizontalGravity() {
        return horizontalGravity;
    }

    public VerticalGravity getVerticalGravity() {
        return verticalGravity;
    }

    @Override
    public boolean shouldFocus() {
        return shouldFocus;
    }

    @Override
    public void addChild(String name, Widget child) {
        super.addChild(name, child);

        float panelCenterX = this.getWidth() / 2;
        float panelCenterY = this.getHeight() / 2;

        float childX, childY;

        childX = switch (horizontalGravity) {
            case LEFT -> child.getX();
            case CENTER -> child.getX() + panelCenterX - child.getWidth() / 2;
            case RIGHT -> child.getX() + this.getWidth() - child.getWidth();
        };

        childY = switch (verticalGravity) {
            case TOP -> child.getY();
            case CENTER -> child.getY() + panelCenterY - child.getHeight() / 2;
            case BOTTOM -> child.getY() + this.getHeight() - child.getHeight();
        };

        child.setX(childX);
        child.setY(childY);
    }
}