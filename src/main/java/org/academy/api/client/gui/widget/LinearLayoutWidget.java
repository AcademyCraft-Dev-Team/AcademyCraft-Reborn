package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.SizeMode;

public class LinearLayoutWidget extends AbstractWidgetContainer {
    protected Orientation orientation = Orientation.VERTICAL;
    protected float spacing = 0;
    protected float weightSum = -1.0f;
    protected int gravity = Gravity.START | Gravity.TOP;
    private float totalLength;

    @Override
    public AbstractWidgetContainer.LayoutParams generateDefaultLayoutParams() {
        if (orientation == Orientation.HORIZONTAL) {
            return new LayoutParams().sizeMode(SizeMode.WRAP_CONTENT, SizeMode.WRAP_CONTENT);
        } else if (orientation == Orientation.VERTICAL) {
            return new LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT);
        }
        return new LayoutParams();
    }

    @Override
    public LayoutParams generateLayoutParams(AbstractWidgetContainer.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public boolean checkLayoutParams(AbstractWidgetContainer.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        if (orientation == Orientation.VERTICAL) {
            measureVertical(widthMeasureSpec, heightMeasureSpec);
        } else {
            measureHorizontal(widthMeasureSpec, heightMeasureSpec);
        }
    }

    void measureVertical(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        totalLength = 0;
        var maxWidth = 0.0f;
        var totalWeight = 0.0f;
        var visibleChildCount = 0;
        var hasMatchParentWidth = false;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            visibleChildCount++;
            var lp = (LayoutParams) child.getLayoutParams();
            totalWeight += lp.weight;
            if (lp.widthMode == SizeMode.MATCH_PARENT) {
                hasMatchParentWidth = true;
            }
        }

        var containerLp = getLayoutParams();
        var widthMode = widthMeasureSpec.getMode();
        var heightMode = heightMeasureSpec.getMode();
        var allFillParent = true;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            var lp = (LayoutParams) child.getLayoutParams();
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            totalLength += child.getMeasuredHeight() + lp.marginTop + lp.marginBottom;
            maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.marginLeft + lp.marginRight);
            allFillParent &= lp.heightMode == SizeMode.MATCH_PARENT;
        }

        if (visibleChildCount > 0) {
            totalLength += (visibleChildCount - 1) * spacing;
        }
        totalLength += containerLp.paddingTop + containerLp.paddingBottom;
        maxWidth += containerLp.paddingLeft + containerLp.paddingRight;

        var finalHeight = resolveSize(totalLength, heightMeasureSpec);
        var remainingSpace = finalHeight - totalLength;

        if (remainingSpace != 0 && totalWeight > 0) {
            var actualWeightSum = weightSum > 0 ? weightSum : totalWeight;
            allFillParent = true;

            for (var child : children.values()) {
                if (!child.isVisible()) continue;
                var lp = (LayoutParams) child.getLayoutParams();
                if (lp.weight > 0) {
                    var share = remainingSpace * lp.weight / actualWeightSum;
                    var childHeight = child.getMeasuredHeight() + share;
                    var childHeightSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, Math.max(0, childHeight));
                    var childWidthSpec = getChildMeasureSpec(widthMeasureSpec,
                            containerLp.paddingLeft + containerLp.paddingRight + lp.marginLeft + lp.marginRight,
                            lp.width, lp.widthMode);
                    child.measure(childWidthSpec, childHeightSpec);
                }
                allFillParent &= lp.heightMode == SizeMode.MATCH_PARENT;
            }

            totalLength = 0;
            maxWidth = 0;
            var finalVisibleChildCount = 0;
            for (var child : children.values()) {
                if (!child.isVisible()) continue;
                var lp = (LayoutParams) child.getLayoutParams();
                totalLength += child.getMeasuredHeight() + lp.marginTop + lp.marginBottom;
                maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.marginLeft + lp.marginRight);
                finalVisibleChildCount++;
            }

            if (finalVisibleChildCount > 0) {
                totalLength += (finalVisibleChildCount - 1) * spacing;
            }
            totalLength += containerLp.paddingTop + containerLp.paddingBottom;
            maxWidth += containerLp.paddingLeft + containerLp.paddingRight;
        }

        var finalWidth = resolveSize(maxWidth, widthMeasureSpec);
        if (hasMatchParentWidth && widthMode != MeasureSpec.Mode.UNSPECIFIED) {
            var innerWidth = finalWidth - containerLp.paddingLeft - containerLp.paddingRight;
            for (var child : children.values()) {
                if (!child.isVisible()) continue;
                var lp = (LayoutParams) child.getLayoutParams();
                if (lp.widthMode == SizeMode.MATCH_PARENT) {
                    var childTargetWidth = innerWidth - lp.marginLeft - lp.marginRight;
                    var childWidthSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, Math.max(0, childTargetWidth));
                    var childHeightSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, child.getMeasuredHeight());
                    child.measure(childWidthSpec, childHeightSpec);
                }
            }
        }

        setMeasuredDimension(finalWidth, finalHeight);
    }

    void measureHorizontal(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        totalLength = 0;
        var maxHeight = 0.0f;
        var totalWeight = 0.0f;
        var visibleChildCount = 0;
        var hasMatchParentHeight = false;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            visibleChildCount++;
            var lp = (LayoutParams) child.getLayoutParams();
            totalWeight += lp.weight;
            if (lp.heightMode == SizeMode.MATCH_PARENT) {
                hasMatchParentHeight = true;
            }
        }

        var containerLp = getLayoutParams();
        var widthMode = widthMeasureSpec.getMode();
        var heightMode = heightMeasureSpec.getMode();
        var allFillParent = true;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;

            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            var lp = (LayoutParams) child.getLayoutParams();
            totalLength += child.getMeasuredWidth() + lp.marginLeft + lp.marginRight;
            maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.marginTop + lp.marginBottom);
            allFillParent &= lp.widthMode == SizeMode.MATCH_PARENT;
        }

        if (visibleChildCount > 0) {
            totalLength += (visibleChildCount - 1) * spacing;
        }
        totalLength += containerLp.paddingLeft + containerLp.paddingRight;
        maxHeight += containerLp.paddingTop + containerLp.paddingBottom;

        var finalWidth = resolveSize(totalLength, widthMeasureSpec);
        var remainingSpace = finalWidth - totalLength;

        if (remainingSpace != 0 && totalWeight > 0) {
            var actualWeightSum = weightSum > 0 ? weightSum : totalWeight;
            allFillParent = true;

            for (var child : children.values()) {
                if (!child.isVisible()) continue;
                var lp = (LayoutParams) child.getLayoutParams();
                if (lp.weight > 0) {
                    var share = remainingSpace * lp.weight / actualWeightSum;
                    var childWidth = child.getMeasuredWidth() + share;
                    var childWidthSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, Math.max(0, childWidth));
                    var childHeightSpec = getChildMeasureSpec(heightMeasureSpec,
                            containerLp.paddingTop + containerLp.paddingBottom + lp.marginTop + lp.marginBottom,
                            lp.height, lp.heightMode);
                    child.measure(childWidthSpec, childHeightSpec);
                }
                allFillParent &= lp.widthMode == SizeMode.MATCH_PARENT;
            }

            totalLength = 0;
            maxHeight = 0;
            var finalVisibleChildCount = 0;
            for (var child : children.values()) {
                if (!child.isVisible()) continue;
                var lp = (LayoutParams) child.getLayoutParams();
                totalLength += child.getMeasuredWidth() + lp.marginLeft + lp.marginRight;
                maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.marginTop + lp.marginBottom);
                finalVisibleChildCount++;
            }

            if (finalVisibleChildCount > 0) {
                totalLength += (finalVisibleChildCount - 1) * spacing;
            }
            totalLength += containerLp.paddingLeft + containerLp.paddingRight;
            maxHeight += containerLp.paddingTop + containerLp.paddingBottom;
        }

        var finalHeight = resolveSize(maxHeight, heightMeasureSpec);
        if (hasMatchParentHeight && heightMode != MeasureSpec.Mode.UNSPECIFIED) {
            var innerHeight = finalHeight - containerLp.paddingTop - containerLp.paddingBottom;
            for (var child : children.values()) {
                if (!child.isVisible()) continue;
                var lp = (LayoutParams) child.getLayoutParams();
                if (lp.heightMode == SizeMode.MATCH_PARENT) {
                    var childTargetHeight = innerHeight - lp.marginTop - lp.marginBottom;
                    var childHeightSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, Math.max(0, childTargetHeight));
                    var childWidthSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, child.getMeasuredWidth());
                    child.measure(childWidthSpec, childHeightSpec);
                }
            }
        }

        setMeasuredDimension(finalWidth, finalHeight);
    }

    @Override
    protected void onLayout() {
        if (orientation == Orientation.VERTICAL) {
            layoutVertical();
        } else {
            layoutHorizontal();
        }
    }

    void layoutVertical() {
        var containerLp = getLayoutParams();
        var paddingLeft = containerLp.paddingLeft;
        var paddingTop = containerLp.paddingTop;
        var paddingBottom = containerLp.paddingBottom;
        var paddingRight = containerLp.paddingRight;

        var currentY = paddingTop;

        var majorGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (majorGravity == Gravity.BOTTOM) {
            currentY += getHeight() - paddingTop - paddingBottom - totalLength;
        } else if (majorGravity == Gravity.CENTER_VERTICAL) {
            currentY += (getHeight() - paddingTop - paddingBottom - totalLength) / 2.0f;
        }

        var availableWidth = getWidth() - paddingLeft - paddingRight;
        var first = true;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;

            var childLp = (LayoutParams) child.getLayoutParams();
            var childWidth = child.getMeasuredWidth();
            var childHeight = child.getMeasuredHeight();

            var childGravity = childLp.gravity;
            if (childGravity < 0) {
                childGravity = gravity;
            }

            var horizontalGravity = childGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            var childLeft = paddingLeft + childLp.marginLeft;
            if (horizontalGravity == Gravity.CENTER_HORIZONTAL) {
                childLeft += (availableWidth - childWidth - childLp.marginLeft - childLp.marginRight) / 2.0f;
            } else if (horizontalGravity == Gravity.RIGHT) {
                childLeft = getWidth() - paddingRight - childWidth - childLp.marginRight;
            }

            if (!first) currentY += spacing;
            var childTop = currentY + childLp.marginTop;
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

            currentY += childHeight + childLp.marginTop + childLp.marginBottom;
            first = false;
        }
    }

    void layoutHorizontal() {
        var containerLp = getLayoutParams();
        var paddingLeft = containerLp.paddingLeft;
        var paddingTop = containerLp.paddingTop;
        var paddingBottom = containerLp.paddingBottom;
        var paddingRight = containerLp.paddingRight;

        var currentX = paddingLeft;

        var majorGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (majorGravity == Gravity.RIGHT) {
            currentX += getWidth() - paddingLeft - paddingRight - totalLength;
        } else if (majorGravity == Gravity.CENTER_HORIZONTAL) {
            currentX += (getWidth() - paddingLeft - paddingRight - totalLength) / 2.0f;
        }

        var availableHeight = getHeight() - paddingTop - paddingBottom;
        var first = true;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            if (!first) currentX += spacing;

            var childLp = (LayoutParams) child.getLayoutParams();
            var childWidth = child.getMeasuredWidth();
            var childHeight = child.getMeasuredHeight();

            var childGravity = childLp.gravity;
            if (childGravity < 0) {
                childGravity = gravity;
            }

            var verticalGravity = childGravity & Gravity.VERTICAL_GRAVITY_MASK;
            var childTop = paddingTop + childLp.marginTop;
            if (verticalGravity == Gravity.CENTER_VERTICAL) {
                childTop += (availableHeight - childHeight - childLp.marginTop - childLp.marginBottom) / 2.0f;
            } else if (verticalGravity == Gravity.BOTTOM) {
                childTop = getHeight() - paddingBottom - childHeight - childLp.marginBottom;
            }

            var childLeft = currentX + childLp.marginLeft;
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

            currentX += childWidth + childLp.marginLeft + childLp.marginRight;
            first = false;
        }
    }

    public LinearLayoutWidget setOrientation(Orientation orientation) {
        if (this.orientation != orientation) {
            this.orientation = orientation;
            requestLayout();
        }
        return this;
    }

    public LinearLayoutWidget setSpacing(float spacing) {
        if (this.spacing != spacing) {
            this.spacing = spacing;
            requestLayout();
        }
        return this;
    }

    public LinearLayoutWidget setGravity(int gravity) {
        if (this.gravity != gravity) {
            this.gravity = gravity;
            requestLayout();
        }
        return this;
    }

    public LinearLayoutWidget setWeightSum(float weightSum) {
        if (this.weightSum != weightSum) {
            this.weightSum = weightSum;
            requestLayout();
        }
        return this;
    }

    public static class LayoutParams extends AbstractWidgetContainer.LayoutParams {
        public float weight = 0;

        public LayoutParams() {
            super();
        }

        public LayoutParams(AbstractWidgetContainer.LayoutParams source) {
            super(source);
            if (source instanceof LayoutParams linearLp) {
                this.weight = linearLp.weight;
            }
        }

        public LayoutParams weight(float weight) {
            this.weight = weight;
            return this;
        }
    }
}