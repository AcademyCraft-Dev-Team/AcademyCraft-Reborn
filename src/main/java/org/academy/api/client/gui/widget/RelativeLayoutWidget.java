package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.SizeMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RelativeLayoutWidget extends AbstractWidgetContainer {
    public static final int LEFT_OF = 0;
    public static final int RIGHT_OF = 1;
    public static final int ABOVE = 2;
    public static final int BELOW = 3;
    public static final int ALIGN_BASELINE = 4;
    public static final int ALIGN_LEFT = 5;
    public static final int ALIGN_TOP = 6;
    public static final int ALIGN_RIGHT = 7;
    public static final int ALIGN_BOTTOM = 8;
    public static final int ALIGN_PARENT_LEFT = 9;
    public static final int ALIGN_PARENT_TOP = 10;
    public static final int ALIGN_PARENT_RIGHT = 11;
    public static final int ALIGN_PARENT_BOTTOM = 12;
    public static final int CENTER_IN_PARENT = 13;
    public static final int CENTER_HORIZONTAL = 14;
    public static final int CENTER_VERTICAL = 15;

    private static final Boolean TRUE = Boolean.TRUE;
    private static final float VALUE_NOT_SET = Float.MIN_VALUE;

    private static final int[] RULES_VERTICAL = {ABOVE, BELOW, ALIGN_BASELINE, ALIGN_TOP, ALIGN_BOTTOM};
    private static final int[] RULES_HORIZONTAL = {LEFT_OF, RIGHT_OF, ALIGN_LEFT, ALIGN_RIGHT};

    private final DependencyGraph graph = new DependencyGraph();
    private boolean isLayoutDirty = true;
    private Widget[] sortedHorizontalChildren = new Widget[]{};
    private Widget[] sortedVerticalChildren = new Widget[]{};

    public RelativeLayoutWidget() {
        setLayoutParams(new LayoutParams());
    }

    @Override
    public LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    public LayoutParams generateLayoutParams(WidgetContainer.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public boolean checkLayoutParams(WidgetContainer.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    private void sortChildren() {
        var visibleChildren = children.values().stream().filter(Widget::isVisible).toList();
        var count = visibleChildren.size();
        if (sortedVerticalChildren.length != count) {
            sortedVerticalChildren = new Widget[count];
        }
        if (sortedHorizontalChildren.length != count) {
            sortedHorizontalChildren = new Widget[count];
        }

        graph.clear();
        for (var child : visibleChildren) {
            graph.add(child);
        }

        graph.getSortedViews(sortedHorizontalChildren, RULES_HORIZONTAL);
        graph.getSortedViews(sortedVerticalChildren, RULES_VERTICAL);
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        if (isLayoutDirty) {
            isLayoutDirty = false;
            sortChildren();
        }

        var myWidth = -1.0f;
        var myHeight = -1.0f;
        var width = 0.0f;
        var height = 0.0f;

        var widthMode = widthMeasureSpec.getMode();
        var heightMode = heightMeasureSpec.getMode();
        var widthSize = widthMeasureSpec.getSize();
        var heightSize = heightMeasureSpec.getSize();

        if (widthMode != MeasureSpec.Mode.UNSPECIFIED) myWidth = widthSize;
        if (heightMode != MeasureSpec.Mode.UNSPECIFIED) myHeight = heightSize;
        if (widthMode == MeasureSpec.Mode.EXACTLY) width = myWidth;
        if (heightMode == MeasureSpec.Mode.EXACTLY) height = myHeight;

        var isWrapContentWidth = widthMode != MeasureSpec.Mode.EXACTLY;
        var isWrapContentHeight = heightMode != MeasureSpec.Mode.EXACTLY;
        var offsetHorizontalAxis = false;
        var offsetVerticalAxis = false;

        var views = sortedHorizontalChildren;
        for (var child : views) {
            var params = (LayoutParams) child.getLayoutParams();
            applyHorizontalSizeRules(params, myWidth);
            measureChildHorizontal(child, params, myWidth, myHeight);
            if (positionChildHorizontal(child, params, myWidth, isWrapContentWidth)) {
                offsetHorizontalAxis = true;
            }
        }

        views = sortedVerticalChildren;
        for (var child : views) {
            var params = (LayoutParams) child.getLayoutParams();
            applyVerticalSizeRules(params, myHeight);
            measureChild(child, params, myWidth, myHeight);
            if (positionChildVertical(child, params, myHeight, isWrapContentHeight)) {
                offsetVerticalAxis = true;
            }

            if (isWrapContentWidth) width = Math.max(width, params.right);
            if (isWrapContentHeight) height = Math.max(height, params.bottom);
        }

        var lp = getLayoutParams();
        if (isWrapContentWidth) {
            width += lp.paddingRight;
            if (layoutParams.width >= 0) width = Math.max(width, layoutParams.width);

            width = resolveSize(width, widthMeasureSpec);

            if (offsetHorizontalAxis) {
                for (var child : children.values()) {
                    if (child.isVisible()) {
                        var params = (LayoutParams) child.getLayoutParams();
                        if (params.hasBooleanRule(CENTER_IN_PARENT) || params.hasBooleanRule(CENTER_HORIZONTAL)) {
                            centerHorizontal(child, params, width);
                        } else if (params.hasBooleanRule(ALIGN_PARENT_RIGHT)) {
                            var childWidth = child.getMeasuredWidth();
                            params.left = width - lp.paddingRight - childWidth;
                            params.right = params.left + childWidth;
                        }
                    }
                }
            }
        }

        if (isWrapContentHeight) {
            height += lp.paddingBottom;
            if (layoutParams.height >= 0) height = Math.max(height, layoutParams.height);

            height = resolveSize(height, heightMeasureSpec);

            if (offsetVerticalAxis) {
                for (var child : children.values()) {
                    if (child.isVisible()) {
                        var params = (LayoutParams) child.getLayoutParams();
                        if (params.hasBooleanRule(CENTER_IN_PARENT) || params.hasBooleanRule(CENTER_VERTICAL)) {
                            centerVertical(child, params, height);
                        } else if (params.hasBooleanRule(ALIGN_PARENT_BOTTOM)) {
                            var childHeight = child.getMeasuredHeight();
                            params.top = height - lp.paddingBottom - childHeight;
                            params.bottom = params.top + childHeight;
                        }
                    }
                }
            }
        }

        setMeasuredDimension(width, height);
    }

    private void applyHorizontalSizeRules(LayoutParams childParams, float myWidth) {
        childParams.left = VALUE_NOT_SET;
        childParams.right = VALUE_NOT_SET;

        var subject = childParams.getRule(LEFT_OF);
        if (subject instanceof Widget anchorWidget) {
            var anchorParams = (LayoutParams) anchorWidget.getLayoutParams();
            childParams.right = anchorParams.left - (anchorParams.marginLeft + childParams.marginRight);
        }

        subject = childParams.getRule(RIGHT_OF);
        if (subject instanceof Widget anchorWidget) {
            var anchorParams = (LayoutParams) anchorWidget.getLayoutParams();
            childParams.left = anchorParams.right + (anchorParams.marginRight + childParams.marginLeft);
        }

        subject = childParams.getRule(ALIGN_LEFT);
        if (subject instanceof Widget anchorWidget) {
            var anchorParams = (LayoutParams) anchorWidget.getLayoutParams();
            childParams.left = anchorParams.left + childParams.marginLeft;
        }

        subject = childParams.getRule(ALIGN_RIGHT);
        if (subject instanceof Widget anchorWidget) {
            var anchorParams = (LayoutParams) anchorWidget.getLayoutParams();
            childParams.right = anchorParams.right - childParams.marginRight;
        }

        if (childParams.hasBooleanRule(ALIGN_PARENT_LEFT)) {
            childParams.left = getLayoutParams().paddingLeft + childParams.marginLeft;
        }

        if (childParams.hasBooleanRule(ALIGN_PARENT_RIGHT) && myWidth >= 0) {
            childParams.right = myWidth - getLayoutParams().paddingRight - childParams.marginRight;
        }
    }

    private void applyVerticalSizeRules(LayoutParams childParams, float myHeight) {
        childParams.top = VALUE_NOT_SET;
        childParams.bottom = VALUE_NOT_SET;

        var subject = childParams.getRule(ABOVE);
        if (subject instanceof Widget anchorWidget) {
            var anchorParams = (LayoutParams) anchorWidget.getLayoutParams();
            childParams.bottom = anchorParams.top - (anchorParams.marginTop + childParams.marginBottom);
        }

        subject = childParams.getRule(BELOW);
        if (subject instanceof Widget anchorWidget) {
            var anchorParams = (LayoutParams) anchorWidget.getLayoutParams();
            childParams.top = anchorParams.bottom + (anchorParams.marginBottom + childParams.marginTop);
        }

        subject = childParams.getRule(ALIGN_TOP);
        if (subject instanceof Widget anchorWidget) {
            var anchorParams = (LayoutParams) anchorWidget.getLayoutParams();
            childParams.top = anchorParams.top + childParams.marginTop;
        }

        subject = childParams.getRule(ALIGN_BOTTOM);
        if (subject instanceof Widget anchorWidget) {
            var anchorParams = (LayoutParams) anchorWidget.getLayoutParams();
            childParams.bottom = anchorParams.bottom - childParams.marginBottom;
        }

        if (childParams.hasBooleanRule(ALIGN_PARENT_TOP)) {
            childParams.top = getLayoutParams().paddingTop + childParams.marginTop;
        }

        if (childParams.hasBooleanRule(ALIGN_PARENT_BOTTOM) && myHeight >= 0) {
            childParams.bottom = myHeight - getLayoutParams().paddingBottom - childParams.marginBottom;
        }
    }

    private void measureChild(Widget child, LayoutParams params, float myWidth, float myHeight) {
        var lp = getLayoutParams();
        var childWidthSpec = getChildMeasureSpec(params.left, params.right, params.width, params.marginLeft, params.marginRight, lp.paddingLeft, lp.paddingRight, myWidth);
        var childHeightSpec = getChildMeasureSpec(params.top, params.bottom, params.height, params.marginTop, params.marginBottom, lp.paddingTop, lp.paddingBottom, myHeight);
        child.measure(childWidthSpec, childHeightSpec);
    }

    private void measureChildHorizontal(Widget child, LayoutParams params, float myWidth, float myHeight) {
        var lp = getLayoutParams();
        var childWidthSpec = getChildMeasureSpec(params.left, params.right, params.width, params.marginLeft, params.marginRight, lp.paddingLeft, lp.paddingRight, myWidth);

        var childHeightSpec = (MeasureSpec) null;
        if (params.heightMode == SizeMode.FIXED) {
            childHeightSpec = new MeasureSpec(MeasureSpec.Mode.EXACTLY, params.height);
        } else {
            var maxHeight = Math.max(0, myHeight - lp.paddingTop - lp.paddingBottom - params.marginTop - params.marginBottom);
            var heightMode = params.heightMode == SizeMode.MATCH_PARENT ? MeasureSpec.Mode.EXACTLY : MeasureSpec.Mode.AT_MOST;
            childHeightSpec = new MeasureSpec(heightMode, maxHeight);
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    private MeasureSpec getChildMeasureSpec(float childStart, float childEnd, float childSize, float startMargin, float endMargin, float startPadding, float endPadding, float mySize) {
        var isUnspecified = mySize < 0;
        if (isUnspecified) {
            if (childStart != VALUE_NOT_SET && childEnd != VALUE_NOT_SET) {
                return new MeasureSpec(MeasureSpec.Mode.EXACTLY, Math.max(0, childEnd - childStart));
            }
            if (childSize >= 0) {
                return new MeasureSpec(MeasureSpec.Mode.EXACTLY, childSize);
            }
            return new MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0);
        }

        var tempStart = childStart == VALUE_NOT_SET ? startPadding + startMargin : childStart;
        var tempEnd = childEnd == VALUE_NOT_SET ? mySize - endPadding - endMargin : childEnd;
        var maxAvailable = tempEnd - tempStart;

        if (childStart != VALUE_NOT_SET && childEnd != VALUE_NOT_SET) {
            return new MeasureSpec(MeasureSpec.Mode.EXACTLY, Math.max(0, maxAvailable));
        }

        if (childSize >= 0) {
            return new MeasureSpec(MeasureSpec.Mode.EXACTLY, Math.min(maxAvailable, childSize));
        }
        if (childSize == -1) {
            return new MeasureSpec(MeasureSpec.Mode.EXACTLY, Math.max(0, maxAvailable));
        } else {
            return new MeasureSpec(MeasureSpec.Mode.AT_MOST, maxAvailable);
        }
    }

    private boolean positionChildHorizontal(Widget child, LayoutParams params, float myWidth, boolean wrapContent) {
        if (params.left == VALUE_NOT_SET && params.right != VALUE_NOT_SET) {
            params.left = params.right - child.getMeasuredWidth();
        } else if (params.left != VALUE_NOT_SET && params.right == VALUE_NOT_SET) {
            params.right = params.left + child.getMeasuredWidth();
        } else if (params.left == VALUE_NOT_SET) {
            if (params.hasBooleanRule(CENTER_IN_PARENT) || params.hasBooleanRule(CENTER_HORIZONTAL)) {
                if (!wrapContent) {
                    centerHorizontal(child, params, myWidth);
                } else {
                    var lp = getLayoutParams();
                    params.left = lp.paddingLeft + params.marginLeft;
                    params.right = params.left + child.getMeasuredWidth();
                }
                return true;
            } else {
                var lp = getLayoutParams();
                params.left = lp.paddingLeft + params.marginLeft;
                params.right = params.left + child.getMeasuredWidth();
            }
        }
        return params.hasBooleanRule(ALIGN_PARENT_RIGHT);
    }

    private boolean positionChildVertical(Widget child, LayoutParams params, float myHeight, boolean wrapContent) {
        if (params.top == VALUE_NOT_SET && params.bottom != VALUE_NOT_SET) {
            params.top = params.bottom - child.getMeasuredHeight();
        } else if (params.top != VALUE_NOT_SET && params.bottom == VALUE_NOT_SET) {
            params.bottom = params.top + child.getMeasuredHeight();
        } else if (params.top == VALUE_NOT_SET) {
            if (params.hasBooleanRule(CENTER_IN_PARENT) || params.hasBooleanRule(CENTER_VERTICAL)) {
                if (!wrapContent) {
                    centerVertical(child, params, myHeight);
                } else {
                    var lp = getLayoutParams();
                    params.top = lp.paddingTop + params.marginTop;
                    params.bottom = params.top + child.getMeasuredHeight();
                }
                return true;
            } else {
                var lp = getLayoutParams();
                params.top = lp.paddingTop + params.marginTop;
                params.bottom = params.top + child.getMeasuredHeight();
            }
        }
        return params.hasBooleanRule(ALIGN_PARENT_BOTTOM);
    }

    private static void centerHorizontal(Widget child, LayoutParams params, float myWidth) {
        var childWidth = child.getMeasuredWidth();
        var left = (myWidth - childWidth) / 2.0f;
        params.left = left;
        params.right = left + childWidth;
    }

    private static void centerVertical(Widget child, LayoutParams params, float myHeight) {
        var childHeight = child.getMeasuredHeight();
        var top = (myHeight - childHeight) / 2.0f;
        params.top = top;
        params.bottom = top + childHeight;
    }

    @Override
    protected void onLayout() {
        for (var child : children.values()) {
            if (child.isVisible()) {
                var st = (LayoutParams) child.getLayoutParams();
                child.layout(st.left, st.top, st.right, st.bottom);
            }
        }
    }

    public static class LayoutParams extends WidgetContainer.LayoutParams {
        private final Map<Integer, Object> rules = new HashMap<>();
        private float left, top, right, bottom;

        public LayoutParams() {
        }

        public LayoutParams(WidgetContainer.LayoutParams source) {
            super(source);
            if (source instanceof LayoutParams relativeLp) {
                rules.putAll(relativeLp.rules);
            }
        }

        public LayoutParams addRule(int verb) {
            rules.put(verb, TRUE);
            return this;
        }

        public LayoutParams addRule(int verb, Widget subject) {
            rules.put(verb, subject);
            return this;
        }

        public void removeRule(int verb) {
            rules.remove(verb);
        }

        @Nullable
        public Object getRule(int verb) {
            return rules.get(verb);
        }

        public boolean hasBooleanRule(int verb) {
            return TRUE.equals(rules.get(verb));
        }
    }

    private static class DependencyGraph {
        private final ArrayList<Node> nodes = new ArrayList<>();
        private final Map<Widget, Node> keyNodes = new HashMap<>();
        private final ArrayDeque<Node> roots = new ArrayDeque<>();

        void clear() {
            nodes.clear();
            keyNodes.clear();
            roots.clear();
        }

        void add(Widget view) {
            var node = new Node(view);
            keyNodes.put(view, node);
            nodes.add(node);
        }

        void getSortedViews(Widget[] sorted, int... rules) {
            var roots = findRoots(rules);
            var index = 0;
            Node node;
            while ((node = roots.pollLast()) != null) {
                sorted[index++] = node.view;

                for (var dependent : node.dependents.keySet()) {
                    dependent.dependencies.remove(node.view);
                    if (dependent.dependencies.isEmpty()) {
                        roots.add(dependent);
                    }
                }
            }
            if (index < sorted.length) {
                throw new IllegalStateException("Circular dependencies cannot exist in RelativeLayout");
            }
        }

        private ArrayDeque<Node> findRoots(int[] rulesFilter) {
            for (var node : nodes) {
                node.dependents.clear();
                node.dependencies.clear();
            }

            for (var node : nodes) {
                var layoutParams = (LayoutParams) node.view.getLayoutParams();
                for (var verb : rulesFilter) {
                    var subject = layoutParams.getRule(verb);
                    if (subject instanceof Widget anchorWidget) {
                        var dependency = keyNodes.get(anchorWidget);
                        if (dependency == null || dependency == node) {
                            continue;
                        }
                        dependency.dependents.put(node, this);
                        node.dependencies.put(anchorWidget, dependency);
                    }
                }
            }

            roots.clear();
            for (var node : nodes) {
                if (node.dependencies.isEmpty()) {
                    roots.addLast(node);
                }
            }
            return roots;
        }

        private static class Node {
            final Widget view;
            final Map<Node, DependencyGraph> dependents = new HashMap<>();
            final Map<Widget, Node> dependencies = new HashMap<>();

            Node(Widget view) {
                this.view = view;
            }
        }
    }
}