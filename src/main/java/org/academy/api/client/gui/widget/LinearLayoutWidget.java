package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.framework.layout.MeasureSpec;
import org.academy.api.client.gui.framework.layout.SizeMode;

public class LinearLayoutWidget extends AbstractContainerWidget {
    protected Orientation orientation = Orientation.VERTICAL;
    protected float spacing = 0;

    public LinearLayoutWidget(float width, float height) {
        super(width, height);
    }

    public LinearLayoutWidget() {
        super(0, 0);
        setLayoutParams(new org.academy.api.client.gui.framework.layout.LayoutParams()
                .widthMode(SizeMode.WRAP_CONTENT)
                .heightMode(SizeMode.WRAP_CONTENT));
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        if (orientation == Orientation.VERTICAL) {
            measureVertical(widthMeasureSpec, heightMeasureSpec);
        } else {
            measureHorizontal(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void measureVertical(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var totalHeight = 0.0f;
        var maxWidth = 0.0f;
        var totalWeight = 0.0f;
        var visibleChildCount = 0;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            var lp = child.getLayoutParams();
            totalWeight += lp.weight;
            totalHeight += child.getMeasuredHeight() + lp.marginTop + lp.marginBottom;
            maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.marginLeft + lp.marginRight);
            visibleChildCount++;
        }

        if (visibleChildCount > 0) {
            totalHeight += (visibleChildCount - 1) * spacing;
        }

        var lp = getLayoutParams();
        totalHeight += lp.paddingTop + lp.paddingBottom;
        maxWidth += lp.paddingLeft + lp.paddingRight;

        var finalHeight = resolveSize(totalHeight, heightMeasureSpec);

        if (totalWeight > 0 && heightMeasureSpec.getMode() == MeasureSpec.Mode.EXACTLY) {
            var remainingSpace = finalHeight - totalHeight;
            if (remainingSpace > 0) {
                remeasureWithWeight(remainingSpace, widthMeasureSpec, heightMeasureSpec, true);

                totalHeight = 0;
                visibleChildCount = 0;
                for (var child : children.values()) {
                    if (!child.isVisible()) continue;
                    var childLp = child.getLayoutParams();
                    totalHeight += child.getMeasuredHeight() + childLp.marginTop + childLp.marginBottom;
                    visibleChildCount++;
                }
                if (visibleChildCount > 0) {
                    totalHeight += (visibleChildCount - 1) * spacing;
                }
                totalHeight += lp.paddingTop + lp.paddingBottom;
                finalHeight = resolveSize(totalHeight, heightMeasureSpec);
            }
        }

        var finalWidth = resolveSize(maxWidth, widthMeasureSpec);
        setMeasuredDimension(finalWidth, finalHeight);
    }

    private void measureHorizontal(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var totalWidth = 0.0f;
        var maxHeight = 0.0f;
        var totalWeight = 0.0f;
        var visibleChildCount = 0;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            var lp = child.getLayoutParams();
            totalWeight += lp.weight;
            totalWidth += child.getMeasuredWidth() + lp.marginLeft + lp.marginRight;
            maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.marginTop + lp.marginBottom);
            visibleChildCount++;
        }

        if (visibleChildCount > 0) {
            totalWidth += (visibleChildCount - 1) * spacing;
        }

        var lp = getLayoutParams();
        totalWidth += lp.paddingLeft + lp.paddingRight;
        maxHeight += lp.paddingTop + lp.paddingBottom;

        var finalWidth = resolveSize(totalWidth, widthMeasureSpec);

        if (totalWeight > 0 && widthMeasureSpec.getMode() == MeasureSpec.Mode.EXACTLY) {
            var remainingSpace = finalWidth - totalWidth;
            if (remainingSpace > 0) {
                remeasureWithWeight(remainingSpace, widthMeasureSpec, heightMeasureSpec, false);

                totalWidth = 0;
                visibleChildCount = 0;
                for (var child : children.values()) {
                    if (!child.isVisible()) continue;
                    var childLp = child.getLayoutParams();
                    totalWidth += child.getMeasuredWidth() + childLp.marginLeft + childLp.marginRight;
                    visibleChildCount++;
                }
                if (visibleChildCount > 0) {
                    totalWidth += (visibleChildCount - 1) * spacing;
                }
                totalWidth += lp.paddingLeft + lp.paddingRight;
                finalWidth = resolveSize(totalWidth, widthMeasureSpec);
            }
        }
        var finalHeight = resolveSize(maxHeight, heightMeasureSpec);
        setMeasuredDimension(finalWidth, finalHeight);
    }

    private void remeasureWithWeight(float remainingSpace, MeasureSpec widthSpec, MeasureSpec heightSpec, boolean isVertical) {
        var totalWeight = 0.0f;
        for (var child : children.values()) {
            if (child.isVisible()) {
                totalWeight += child.getLayoutParams().weight;
            }
        }
        if (totalWeight <= 0) return;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            var lp = child.getLayoutParams();
            if (lp.weight <= 0) continue;

            var share = remainingSpace * lp.weight / totalWeight;
            if (isVertical) {
                var childHeightSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, child.getMeasuredHeight() + share);
                var childWidthSpec = getChildMeasureSpec(widthSpec, getLayoutParams().paddingLeft + getLayoutParams().paddingRight + lp.marginLeft + lp.marginRight, lp.width, lp.widthMode);
                child.measure(childWidthSpec, childHeightSpec);
            } else {
                var childWidthSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, child.getMeasuredWidth() + share);
                var childHeightSpec = getChildMeasureSpec(heightSpec, getLayoutParams().paddingTop + getLayoutParams().paddingBottom + lp.marginTop + lp.marginBottom, lp.height, lp.heightMode);
                child.measure(childWidthSpec, childHeightSpec);
            }
        }
    }

    @Override
    protected void onLayout() {
        if (orientation == Orientation.VERTICAL) {
            layoutVertical();
        } else {
            layoutHorizontal();
        }
    }

    private void layoutVertical() {
        var lp = getLayoutParams();
        var currentY = lp.paddingTop;
        var parentLeft = lp.paddingLeft;
        var availableWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var first = true;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            if (!first) currentY += spacing;

            var childLp = child.getLayoutParams();
            var childWidth = child.getMeasuredWidth();
            var childHeight = child.getMeasuredHeight();
            var childLeft = parentLeft + childLp.marginLeft;

            switch (childLp.alignment) {
                case CENTER -> childLeft += (availableWidth - childWidth - childLp.marginLeft - childLp.marginRight) / 2.0f;
                case END -> childLeft = getWidth() - lp.paddingRight - childWidth - childLp.marginRight;
            }

            var childTop = currentY + childLp.marginTop;
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

            currentY += childHeight + childLp.marginTop + childLp.marginBottom;
            first = false;
        }
    }

    private void layoutHorizontal() {
        var lp = getLayoutParams();
        var currentX = lp.paddingLeft;
        var parentTop = lp.paddingTop;
        var availableHeight = getHeight() - lp.paddingTop - lp.paddingBottom;
        var first = true;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            if (!first) currentX += spacing;

            var childLp = child.getLayoutParams();
            var childWidth = child.getMeasuredWidth();
            var childHeight = child.getMeasuredHeight();
            var childTop = parentTop + childLp.marginTop;

            switch (childLp.alignment) {
                case CENTER -> childTop += (availableHeight - childHeight - childLp.marginTop - childLp.marginBottom) / 2.0f;
                case END -> childTop = getHeight() - lp.paddingBottom - childHeight - childLp.marginBottom;
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
}