package org.academy.api.client.gui.widget;

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

    public PanelWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    public HorizontalGravity getHorizontalGravity() {
        return horizontalGravity;
    }

    public VerticalGravity getVerticalGravity() {
        return verticalGravity;
    }

    public void setHorizontalGravity(HorizontalGravity newHorizontalGravity) {
        horizontalGravity = newHorizontalGravity;
    }

    public void setVerticalGravity(VerticalGravity newVerticalGravity) {
        verticalGravity = newVerticalGravity;
    }

    @Override
    public void addChild(String name, Widget child) {
        super.addChild(name, child);

        var panelCenterX = getWidth() / 2;
        var panelCenterY = getHeight() / 2;

        float childX, childY;

        childX = switch (horizontalGravity) {
            case LEFT -> child.getX();
            case CENTER -> child.getX() + panelCenterX - child.getWidth() / 2;
            case RIGHT -> child.getX() + getWidth() - child.getWidth();
        };

        childY = switch (verticalGravity) {
            case TOP -> child.getY();
            case CENTER -> child.getY() + panelCenterY - child.getHeight() / 2;
            case BOTTOM -> child.getY() + getHeight() - child.getHeight();
        };

        child.setX(childX);
        child.setY(childY);
    }
}