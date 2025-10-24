package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.ScrollEvent;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.render.ScissorRect;
import org.academy.api.client.gui.render.WidgetRenderContext;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.Nullable;

public class ScrollPanelWidget extends AbstractWidgetContainer {
    protected float scrollTarget;
    protected float scrollSpeed = 24f;
    protected final Orientation orientation;

    @Nullable
    private Widget content;

    public ScrollPanelWidget(Orientation orientation) {
        this.orientation = orientation;
        scrollTarget = 0f;
    }

    public ScrollPanelWidget() {
        this(Orientation.VERTICAL);
    }

    @Override
    public LayoutParams generateDefaultLayoutParams() {
        return new FrameLayoutWidget.LayoutParams();
    }

    @Override
    public LayoutParams generateLayoutParams(WidgetContainer.LayoutParams p) {
        return new FrameLayoutWidget.LayoutParams(p);
    }

    @Override
    public boolean checkLayoutParams(WidgetContainer.LayoutParams p) {
        return p instanceof FrameLayoutWidget.LayoutParams;
    }

    public void setContent(@Nullable Widget newContent) {
        if (content == newContent) return;

        clearChildren();

        if (newContent != null) {
            content = newContent;
            addChild(newContent.getName(), newContent);
        }
    }

    @Override
    public void addChild(String name, Widget child) {
        if (content != null) {
            throw new IllegalStateException("ScrollPanelWidget can host only one direct child. Use a container like LinearLayoutWidget as the single child.");
        }
        child.setName(name);
        content = child;
        super.addChild(name, child);
    }

    @Override
    public void removeChild(String name) {
        if (content != null && content.getName().equals(name)) {
            clearChildren();
        }
    }

    @Override
    public void clearChildren() {
        if (content != null) {
            super.removeChild(content.getName());
            content = null;
        }
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var lp = getLayoutParams();
        var desiredWidth = lp.paddingLeft + lp.paddingRight;
        var desiredHeight = lp.paddingTop + lp.paddingBottom;

        if (content != null && content.isVisible()) {
            var contentWidthSpec = widthMeasureSpec;
            var contentHeightSpec = heightMeasureSpec;

            if (orientation == Orientation.VERTICAL) {
                var heightMode = heightMeasureSpec.getMode();
                if (heightMode == MeasureSpec.Mode.EXACTLY || heightMode == MeasureSpec.Mode.AT_MOST) {
                    contentHeightSpec = new MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0);
                }
            } else {
                var widthMode = widthMeasureSpec.getMode();
                if (widthMode == MeasureSpec.Mode.EXACTLY || widthMode == MeasureSpec.Mode.AT_MOST) {
                    contentWidthSpec = new MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0);
                }
            }

            measureChild(content, contentWidthSpec, contentHeightSpec);

            var contentLp = content.getLayoutParams();
            desiredWidth += content.getMeasuredWidth() + contentLp.marginLeft + contentLp.marginRight;
            desiredHeight += content.getMeasuredHeight() + contentLp.marginTop + contentLp.marginBottom;
        }

        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onLayout() {
        if (content != null && content.isVisible()) {
            var lp = getLayoutParams();
            var contentLp = content.getLayoutParams();
            var left = lp.paddingLeft + contentLp.marginLeft;
            var top = lp.paddingTop + contentLp.marginTop;
            var right = left + content.getMeasuredWidth();
            var bottom = top + content.getMeasuredHeight();
            content.layout(left, top, right, bottom);
        }
    }

    @Override
    public void dispatchEvent(InputEvent event) {
        if (!isAbsoluteEnabled() || !isVisible()) return;

        if (event instanceof MouseEvent me && !isMouseOver(me.getX(), me.getY())) {
            if (hoveredWidget != null) {
                hoveredWidget.setHovered(false);
                hoveredWidget = null;
            }

            if (gestureTarget != null) {
                gestureTarget.dispatchEvent(event);
                if (event.getType() == EventType.MOUSE_RELEASED)
                    gestureTarget = null;
            }
            return;
        }

        var transformedEvent = transformEvent(event);

        if (content != null && content.isVisible() && content.isAbsoluteEnabled()) {
            content.dispatchEvent(transformedEvent);
            if (transformedEvent.isConsumed()) {
                event.consume();
                if (transformedEvent.getType() == EventType.MOUSE_PRESSED) {
                    gestureTarget = content;
                    setFocusedChild(content.canFocus() ? content : this);
                }
                return;
            }
        }

        super.dispatchEvent(event);
        if (event.isConsumed() && event.getType() == EventType.MOUSE_PRESSED) {
            setFocusedChild(this);
        }
    }

    private InputEvent transformEvent(InputEvent event) {
        if (event instanceof MouseEvent mouseEvent) {
            var transformedX = mouseEvent.getX() - getAbsoluteX() + getScrollX();
            var transformedY = mouseEvent.getY() - getAbsoluteY() + getScrollY();

            return switch (mouseEvent.getType()) {
                case MOUSE_PRESSED -> MouseEvent.createPressEvent(transformedX, transformedY, mouseEvent.getButton());
                case MOUSE_RELEASED -> MouseEvent.createReleaseEvent(transformedX, transformedY, mouseEvent.getButton());
                case MOUSE_MOVED -> MouseEvent.createMoveEvent(transformedX, transformedY);
                case MOUSE_DRAGGED -> MouseEvent.createDragEvent(transformedX, transformedY, mouseEvent.getButton(), mouseEvent.getDragX(), mouseEvent.getDragY());
                default -> mouseEvent;
            };
        }

        if (event instanceof ScrollEvent scrollEvent) {
            var transformedX = scrollEvent.getX() - getAbsoluteX() + getScrollX();
            var transformedY = scrollEvent.getY() - getAbsoluteY() + getScrollY();
            return new ScrollEvent(transformedX, transformedY, scrollEvent.getDelta());
        }

        return event;
    }

    public float getMaxScroll() {
        if (content == null) return 0;

        var lp = getLayoutParams();
        var contentLp = content.getLayoutParams();

        if (orientation == Orientation.VERTICAL) {
            var contentHeight = content.getMeasuredHeight() + contentLp.marginTop + contentLp.marginBottom;
            var viewHeight = getHeight() - lp.paddingTop - lp.paddingBottom;
            return Math.max(0, contentHeight - viewHeight);
        } else {
            var contentWidth = content.getMeasuredWidth() + contentLp.marginLeft + contentLp.marginRight;
            var viewWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
            return Math.max(0, contentWidth - viewWidth);
        }
    }

    public void scrollToEnd() {
        requestLayout();
        setScrollTarget(getMaxScroll());
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var currentScrollY = getScrollY();
        var newScrollY = MathUtil.lerpStartEndFactor(currentScrollY, scrollTarget, ClientUtil.animationFactor(MathUtil.PI / 1.5f));
        scrollTo(getScrollX(), newScrollY);

        var alpha1 = getAlpha();
        context.alpha().push(alpha1);
        var scissor = new ScissorRect(getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight());
        context.enableScissor(scissor);
        {
            context.pose().pushPose();
            {
                context.pose().translate(-getScrollX(), -getScrollY(), 0);
                if (content != null && content.isVisible()) {
                    renderChildren(context, mouseX, mouseY, partialTick);
                }
            }
            context.pose().popPose();
        }
        context.disableScissor();
        context.alpha().pop();
    }

    @Override
    protected void onMouseScrolled(ScrollEvent event) {
        if (isMouseOver(event.getX(), event.getY())) {
            event.consume();
            scrollTarget -= (float) (event.getDelta() * scrollSpeed);
            scrollTarget = MathUtil.clamp(scrollTarget, 0, getMaxScroll());
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