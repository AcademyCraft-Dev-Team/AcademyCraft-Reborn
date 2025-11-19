package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.SizeMode;

import java.util.ArrayList;
import java.util.List;

public class FrameLayoutWidget extends AbstractWidgetContainer {
    private static final int DEFAULT_CHILD_GRAVITY = Gravity.TOP | Gravity.START;

    private boolean measureAllChildren = false;
    private final List<Widget> matchParentChildren = new ArrayList<>(1);

    @Override
    public LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT);
    }

    @Override
    public LayoutParams generateLayoutParams(WidgetContainer.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public boolean checkLayoutParams(WidgetContainer.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected void renderChildren(RenderContext context) {
        context.drawOrder().push();
        {
            for (var child : children.values()) {
                if (child.isVisible()) {
                    context.pose().pushPose();
                    {
                        context.pose().translate(child.getX(), child.getY(), child.getZ());
                        context.pose().translate(child.getTranslationX(), child.getTranslationY(), 0);
                        child.render(context);
                    }
                    context.pose().popPose();
                    context.drawOrder().advance();
                }
            }
        }
        context.drawOrder().pop();
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var measureMatchParentChildren =
                widthMeasureSpec.getMode() != MeasureSpec.Mode.EXACTLY ||
                        heightMeasureSpec.getMode() != MeasureSpec.Mode.EXACTLY;
        matchParentChildren.clear();

        var maxHeight = 0.0f;
        var maxWidth = 0.0f;

        for (var child : children.values()) {
            if (measureAllChildren || child.isVisible()) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                var lp = (LayoutParams) child.getLayoutParams();
                maxWidth = Math.max(maxWidth,
                        child.getMeasuredWidth() + lp.marginLeft + lp.marginRight);
                maxHeight = Math.max(maxHeight,
                        child.getMeasuredHeight() + lp.marginTop + lp.marginBottom);
                if (measureMatchParentChildren) {
                    if (lp.widthMode == SizeMode.MATCH_PARENT ||
                            lp.heightMode == SizeMode.MATCH_PARENT) {
                        matchParentChildren.add(child);
                    }
                }
            }
        }

        var containerLp = getLayoutParams();
        maxWidth += containerLp.paddingLeft + containerLp.paddingRight;
        maxHeight += containerLp.paddingTop + containerLp.paddingBottom;

        maxHeight = Math.max(maxHeight, 0);
        maxWidth = Math.max(maxWidth, 0);

        setMeasuredDimension(resolveSize(maxWidth, widthMeasureSpec),
                resolveSize(maxHeight, heightMeasureSpec));

        var matchParentCount = matchParentChildren.size();
        if (matchParentCount > 0) {
            for (var child : matchParentChildren) {
                var lp = (LayoutParams) child.getLayoutParams();

                MeasureSpec childWidthMeasureSpec;
                if (lp.widthMode == SizeMode.MATCH_PARENT) {
                    var width = Math.max(0, getMeasuredWidth()
                            - containerLp.paddingLeft - containerLp.paddingRight
                            - lp.marginLeft - lp.marginRight);
                    childWidthMeasureSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, width);
                } else {
                    childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                            containerLp.paddingLeft + containerLp.paddingRight +
                                    lp.marginLeft + lp.marginRight,
                            lp.width, lp.widthMode);
                }

                MeasureSpec childHeightMeasureSpec;
                if (lp.heightMode == SizeMode.MATCH_PARENT) {
                    var height = Math.max(0, getMeasuredHeight()
                            - containerLp.paddingTop - containerLp.paddingBottom
                            - lp.marginTop - lp.marginBottom);
                    childHeightMeasureSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, height);
                } else {
                    childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                            containerLp.paddingTop + containerLp.paddingBottom +
                                    lp.marginTop + lp.marginBottom,
                            lp.height, lp.heightMode);
                }

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
    }

    @Override
    protected void onLayout() {
        var containerLp = getLayoutParams();
        var parentLeft = containerLp.paddingLeft;
        var parentRight = getWidth() - containerLp.paddingRight;
        var availableWidth = parentRight - parentLeft;

        var parentTop = containerLp.paddingTop;
        var parentBottom = getHeight() - containerLp.paddingBottom;
        var availableHeight = parentBottom - parentTop;

        for (var child : children.values()) {
            if (child.isVisible()) {
                var lp = (LayoutParams) child.getLayoutParams();

                var width = child.getMeasuredWidth();
                var height = child.getMeasuredHeight();

                float childLeft;
                float childTop;

                var gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = DEFAULT_CHILD_GRAVITY;
                }

                var horizontalGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                var verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                childLeft = parentLeft + lp.marginLeft;
                if (horizontalGravity == Gravity.CENTER_HORIZONTAL) {
                    childLeft += (availableWidth - width - lp.marginLeft - lp.marginRight) / 2.0f;
                } else if (horizontalGravity == Gravity.RIGHT) {
                    childLeft = parentRight - width - lp.marginRight;
                }

                childTop = parentTop + lp.marginTop;
                if (verticalGravity == Gravity.CENTER_VERTICAL) {
                    childTop += (availableHeight - height - lp.marginTop - lp.marginBottom) / 2.0f;
                } else if (verticalGravity == Gravity.BOTTOM) {
                    childTop = parentBottom - height - lp.marginBottom;
                }

                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }
        }
    }

    public void setMeasureAllChildren(boolean measureAll) {
        measureAllChildren = measureAll;
    }

    public boolean getMeasureAllChildren() {
        return measureAllChildren;
    }

    public static class LayoutParams extends WidgetContainer.LayoutParams {
        public static final int UNSPECIFIED_GRAVITY = -1;
        public int gravity = UNSPECIFIED_GRAVITY;

        public LayoutParams() {
        }

        public LayoutParams(SizeMode widthMode, SizeMode heightMode) {
            sizeMode(widthMode, heightMode);
        }

        public LayoutParams(WidgetContainer.LayoutParams source) {
            super(source);
            if (source instanceof LayoutParams frameLp) {
                gravity = frameLp.gravity;
            }
        }

        @Override
        public LayoutParams gravity(int gravity) {
            this.gravity = gravity;
            return this;
        }
    }
}