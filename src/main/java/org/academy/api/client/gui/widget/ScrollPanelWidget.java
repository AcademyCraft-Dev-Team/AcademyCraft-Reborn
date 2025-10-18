package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.ScrollEvent;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.framework.ScissorRect;
import org.academy.api.client.gui.framework.WidgetRenderContext;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;

import java.util.ArrayList;
import java.util.Collections;

public class ScrollPanelWidget extends AbstractContainerWidget {
    protected float scrollTarget;
    protected float scrollSpeed = 24f;
    protected final Orientation orientation;

    public ScrollPanelWidget(float x, float y, float width, float height, Orientation orientation) {
        super(x, y, width, height);
        this.orientation = orientation;
        scrollTarget = 0f;
    }

    public ScrollPanelWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
        orientation = Orientation.VERTICAL;
        scrollTarget = 0f;
    }

    @Override
    public void dispatchEvent(InputEvent event) {
        if (event instanceof MouseEvent me && !isMouseOver(me.getX(), me.getY())) {
            if (hoveredWidget != null) {
                hoveredWidget.setHovered(false);
            }

            hoveredWidget = null;

            if (gestureTarget != null) {
                gestureTarget.dispatchEvent(event);
                if (event.getType() == EventType.MOUSE_RELEASED)
                    gestureTarget = null;
            }
            return;
        }
        super.dispatchEvent(event);
    }

    public float getMaxScroll() {
        if (orientation == Orientation.VERTICAL) {
            var contentHeight = 0f;
            for (var child : getChildren().values()) {
                contentHeight = Math.max(contentHeight, child.getY() + child.getHeight());
            }
            return Math.max(0, contentHeight - getHeight());
        } else {
            var contentWidth = 0f;
            for (var child : getChildren().values()) {
                contentWidth = Math.max(contentWidth, child.getX() + child.getWidth());
            }
            return Math.max(0, contentWidth - getWidth());
        }
    }

    public void scrollToEnd() {
        setScrollTarget(getMaxScroll());
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var currentScroll = (orientation == Orientation.VERTICAL) ? getScrollY() : getScrollX();
        var newScroll = MathUtil.lerpStartEndFactor(currentScroll, scrollTarget, ClientUtil.animationFactor(MathUtil.PI / 1.5f));

        if (orientation == Orientation.VERTICAL) {
            scrollTo(getScrollX(), newScroll);
        } else {
            scrollTo(newScroll, getScrollY());
        }

        context.pose().pushPose();
        context.pose().translate(getX(), getY(), getZ());
        context.pushAlpha(getAlpha());

        var scissor = new ScissorRect(getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight());
        context.enableScissor(scissor);

        context.pose().pushPose();
        context.pose().translate(-getScrollX(), -getScrollY(), 0);
        renderChildren(context, mouseX, mouseY, partialTick);
        context.pose().popPose();

        context.disableScissor();
        context.popAlpha();
        context.pose().popPose();
    }

    @Override
    protected void onMouseScrolled(ScrollEvent event) {
        if (isMouseOver(event.getX(), event.getY())) {
            var childrenList = new ArrayList<>(children.values());
            Collections.reverse(childrenList);
            for (var child : childrenList) {
                child.dispatchEvent(event);
                if (event.isConsumed())
                    return;
            }

            scrollTarget -= (float) (event.getDelta() * scrollSpeed);
            scrollTarget = MathUtil.clamp(scrollTarget, 0, getMaxScroll());
            event.consume();
        }
    }

    public ScrollPanelWidget setScrollTarget(float scrollTarget) {
        this.scrollTarget = MathUtil.clamp(scrollTarget, 0, getMaxScroll());
        return this;
    }

    public ScrollPanelWidget setScrollSpeed(float scrollSpeed) {
        this.scrollSpeed = scrollSpeed;
        return this;
    }
}