package org.academy.api.client.gui.widget;

import net.minecraft.util.Mth;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.ScrollEvent;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.render.ScissorRect;
import org.academy.api.client.util.ClientUtil;
import org.jspecify.annotations.Nullable;

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

    public void setContent(@Nullable Widget content) {
        if (this.content == content) return;

        clearChildren();

        if (content != null) addChild("content", content);
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
        if (content != null && content.getName().equals(name)) clearChildren();
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
            content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());

            var maxScrollX = Math.max(0, content.getWidth() - getWidth());
            var maxScrollY = Math.max(0, content.getHeight() - getHeight());

            var currentScrollX = getScrollX();
            var currentScrollY = getScrollY();

            var needsClamping = false;
            if (currentScrollX > maxScrollX) {
                currentScrollX = maxScrollX;
                needsClamping = true;
            }
            if (currentScrollY > maxScrollY) {
                currentScrollY = maxScrollY;
                needsClamping = true;
            }

            if (needsClamping) scrollTo(currentScrollX, currentScrollY);
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
                if (event.getType() == EventType.MOUSE_RELEASED) gestureTarget = null;
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
        if (event.isConsumed() && event.getType() == EventType.MOUSE_PRESSED) setFocusedChild(this);
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
    public void render(RenderContext context) {
        if (!isVisible()) return;

        var currentScrollY = getScrollY();
        var newScrollY = Mth.lerp(ClientUtil.animationFactor(Mth.PI / 1.5f), currentScrollY, scrollTarget);
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
                    renderChildren(context);
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
            var max = getMaxScroll();
            scrollTarget = Mth.clamp(scrollTarget, (float) 0, max);
        }
    }

    public ScrollPanelWidget setScrollTarget(float scrollTarget) {
        var max = getMaxScroll();
        this.scrollTarget = Mth.clamp(scrollTarget, (float) 0, max);
        return this;
    }

    public ScrollPanelWidget setScrollSpeed(float scrollSpeed) {
        this.scrollSpeed = scrollSpeed;
        return this;
    }

    @Override
    public void scrollTo(float x, float y) {
        if (content == null) {
            super.scrollTo(x, y);
            return;
        }

        var maxScrollX = Math.max(0, content.getWidth() - getWidth());
        var maxScrollY = Math.max(0, content.getHeight() - getHeight());

        var finalX = Math.max(0, Math.min(x, maxScrollX));
        var finalY = Math.max(0, Math.min(y, maxScrollY));

        super.scrollTo(finalX, finalY);
    }

    @Override
    public void scrollBy(float dx, float dy) {
        scrollTo(getScrollX() + dx, getScrollY() + dy);
    }
}