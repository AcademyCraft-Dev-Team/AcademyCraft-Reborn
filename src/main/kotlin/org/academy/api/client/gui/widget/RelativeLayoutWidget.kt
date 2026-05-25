package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import java.util.*
import kotlin.math.max
import kotlin.math.min

class RelativeLayoutWidget : AbstractWidgetContainer() {
    private val graph = DependencyGraph()
    override var isLayoutDirty = true
    private var sortedHorizontalChildren: List<Widget> = emptyList()
    private var sortedVerticalChildren: List<Widget> = emptyList()

    init {
        layoutParams = LayoutParams()
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams()
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is LayoutParams
    }

    private fun sortChildren() {
        val visibleChildren = children.values.filter { it.isVisible() }
        graph.clear()
        visibleChildren.forEach { graph.add(it) }

        val horizontalList = mutableListOf<Widget>()
        graph.getSortedViews(RULES_HORIZONTAL, horizontalList)
        sortedHorizontalChildren = horizontalList

        val verticalList = mutableListOf<Widget>()
        graph.getSortedViews(RULES_VERTICAL, verticalList)
        sortedVerticalChildren = verticalList
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        if (isLayoutDirty) {
            isLayoutDirty = false
            sortChildren()
        }

        var myWidth = -1.0f
        var myHeight = -1.0f
        var width = 0.0f
        var height = 0.0f

        val widthMode = widthMeasureSpec.mode
        val heightMode = heightMeasureSpec.mode
        val widthSize = widthMeasureSpec.size
        val heightSize = heightMeasureSpec.size

        if (widthMode != MeasureSpec.Mode.UNSPECIFIED) myWidth = widthSize
        if (heightMode != MeasureSpec.Mode.UNSPECIFIED) myHeight = heightSize
        if (widthMode == MeasureSpec.Mode.EXACTLY) width = myWidth
        if (heightMode == MeasureSpec.Mode.EXACTLY) height = myHeight

        val isWrapContentWidth = widthMode != MeasureSpec.Mode.EXACTLY
        val isWrapContentHeight = heightMode != MeasureSpec.Mode.EXACTLY
        var offsetHorizontalAxis = false
        var offsetVerticalAxis = false

        for (child in sortedHorizontalChildren) {
            val params = child.layoutParams as LayoutParams
            applyHorizontalSizeRules(params, myWidth)
            measureChildHorizontal(child, params, myWidth, myHeight)
            if (positionChildHorizontal(child, params, myWidth, isWrapContentWidth)) {
                offsetHorizontalAxis = true
            }
        }

        for (child in sortedVerticalChildren) {
            val params = child.layoutParams as LayoutParams
            applyVerticalSizeRules(params, myHeight)
            measureChild(child, params, myWidth, myHeight)
            if (positionChildVertical(child, params, myHeight, isWrapContentHeight)) {
                offsetVerticalAxis = true
            }
            if (isWrapContentWidth) width = max(width, params.right)
            if (isWrapContentHeight) height = max(height, params.bottom)
        }

        val lp = layoutParams
        if (isWrapContentWidth) {
            width += lp.paddingRight
            if (layoutParams.width >= 0) width = max(width, layoutParams.width)
            width = resolveSize(width, widthMeasureSpec)

            if (offsetHorizontalAxis) {
                for (child in children.values) {
                    if (child.isVisible()) {
                        val params = child.layoutParams as LayoutParams
                        if (params.hasBooleanRule(CENTER_IN_PARENT) || params.hasBooleanRule(CENTER_HORIZONTAL)) {
                            centerHorizontal(child, params, width)
                        } else if (params.hasBooleanRule(ALIGN_PARENT_RIGHT)) {
                            val childWidth = child.measuredWidth
                            params.left = width - lp.paddingRight - childWidth
                            params.right = params.left + childWidth
                        }
                    }
                }
            }
        }

        if (isWrapContentHeight) {
            height += lp.paddingBottom
            if (layoutParams.height >= 0) height = max(height, layoutParams.height)
            height = resolveSize(height, heightMeasureSpec)

            if (offsetVerticalAxis) {
                for (child in children.values) {
                    if (child.isVisible()) {
                        val params = child.layoutParams as LayoutParams
                        if (params.hasBooleanRule(CENTER_IN_PARENT) || params.hasBooleanRule(CENTER_VERTICAL)) {
                            centerVertical(child, params, height)
                        } else if (params.hasBooleanRule(ALIGN_PARENT_BOTTOM)) {
                            val childHeight = child.measuredHeight
                            params.top = height - lp.paddingBottom - childHeight
                            params.bottom = params.top + childHeight
                        }
                    }
                }
            }
        }

        setMeasuredDimension(width, height)
    }

    private fun applyHorizontalSizeRules(childParams: LayoutParams, myWidth: Float) {
        childParams.left = VALUE_NOT_SET
        childParams.right = VALUE_NOT_SET

        var subject = childParams.getRule(LEFT_OF)
        if (subject is Widget) {
            val anchorParams = subject.layoutParams as LayoutParams
            childParams.right = anchorParams.left - (anchorParams.marginLeft + childParams.marginRight)
        }

        subject = childParams.getRule(RIGHT_OF)
        if (subject is Widget) {
            val anchorParams = subject.layoutParams as LayoutParams
            childParams.left = anchorParams.right + (anchorParams.marginRight + childParams.marginLeft)
        }

        subject = childParams.getRule(ALIGN_LEFT)
        if (subject is Widget) {
            val anchorParams = subject.layoutParams as LayoutParams
            childParams.left = anchorParams.left + childParams.marginLeft
        }

        subject = childParams.getRule(ALIGN_RIGHT)
        if (subject is Widget) {
            val anchorParams = subject.layoutParams as LayoutParams
            childParams.right = anchorParams.right - childParams.marginRight
        }

        if (childParams.hasBooleanRule(ALIGN_PARENT_LEFT)) {
            childParams.left = layoutParams.paddingLeft + childParams.marginLeft
        }

        if (childParams.hasBooleanRule(ALIGN_PARENT_RIGHT) && myWidth >= 0) {
            childParams.right = myWidth - layoutParams.paddingRight - childParams.marginRight
        }
    }

    private fun applyVerticalSizeRules(childParams: LayoutParams, myHeight: Float) {
        childParams.top = VALUE_NOT_SET
        childParams.bottom = VALUE_NOT_SET

        var subject = childParams.getRule(ABOVE)
        if (subject is Widget) {
            val anchorParams = subject.layoutParams as LayoutParams
            childParams.bottom = anchorParams.top - (anchorParams.marginTop + childParams.marginBottom)
        }

        subject = childParams.getRule(BELOW)
        if (subject is Widget) {
            val anchorParams = subject.layoutParams as LayoutParams
            childParams.top = anchorParams.bottom + (anchorParams.marginBottom + childParams.marginTop)
        }

        subject = childParams.getRule(ALIGN_TOP)
        if (subject is Widget) {
            val anchorParams = subject.layoutParams as LayoutParams
            childParams.top = anchorParams.top + childParams.marginTop
        }

        subject = childParams.getRule(ALIGN_BOTTOM)
        if (subject is Widget) {
            val anchorParams = subject.layoutParams as LayoutParams
            childParams.bottom = anchorParams.bottom - childParams.marginBottom
        }

        if (childParams.hasBooleanRule(ALIGN_PARENT_TOP)) {
            childParams.top = layoutParams.paddingTop + childParams.marginTop
        }

        if (childParams.hasBooleanRule(ALIGN_PARENT_BOTTOM) && myHeight >= 0) {
            childParams.bottom = myHeight - layoutParams.paddingBottom - childParams.marginBottom
        }
    }

    private fun measureChild(child: Widget, params: LayoutParams, myWidth: Float, myHeight: Float) {
        val lp = layoutParams
        val childWidthSpec = getChildMeasureSpec(
            params.left,
            params.right,
            params.width,
            params.marginLeft,
            params.marginRight,
            lp.paddingLeft,
            lp.paddingRight,
            myWidth
        )
        val childHeightSpec = getChildMeasureSpec(
            params.top,
            params.bottom,
            params.height,
            params.marginTop,
            params.marginBottom,
            lp.paddingTop,
            lp.paddingBottom,
            myHeight
        )
        child.measure(childWidthSpec, childHeightSpec)
    }

    private fun measureChildHorizontal(child: Widget, params: LayoutParams, myWidth: Float, myHeight: Float) {
        val lp = layoutParams
        val childWidthSpec = getChildMeasureSpec(
            params.left,
            params.right,
            params.width,
            params.marginLeft,
            params.marginRight,
            lp.paddingLeft,
            lp.paddingRight,
            myWidth
        )

        var childHeightSpec: MeasureSpec?
        if (params.heightMode == SizeMode.FIXED) {
            childHeightSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, params.height)
        } else {
            val maxHeight =
                max(0f, myHeight - lp.paddingTop - lp.paddingBottom - params.marginTop - params.marginBottom)
            val heightMode =
                if (params.heightMode == SizeMode.MATCH_PARENT) MeasureSpec.Mode.EXACTLY else MeasureSpec.Mode.AT_MOST
            childHeightSpec = MeasureSpec(heightMode, maxHeight)
        }
        child.measure(childWidthSpec, childHeightSpec)
    }

    private fun getChildMeasureSpec(
        childStart: Float,
        childEnd: Float,
        childSize: Float,
        startMargin: Float,
        endMargin: Float,
        startPadding: Float,
        endPadding: Float,
        mySize: Float
    ): MeasureSpec {
        val isUnspecified = mySize < 0
        if (isUnspecified) {
            if (childStart != VALUE_NOT_SET && childEnd != VALUE_NOT_SET) {
                return MeasureSpec(MeasureSpec.Mode.EXACTLY, max(0f, childEnd - childStart))
            }
            if (childSize >= 0) {
                return MeasureSpec(MeasureSpec.Mode.EXACTLY, childSize)
            }
            return MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
        }

        val tempStart = if (childStart == VALUE_NOT_SET) startPadding + startMargin else childStart
        val tempEnd = if (childEnd == VALUE_NOT_SET) mySize - endPadding - endMargin else childEnd
        val maxAvailable = tempEnd - tempStart

        if (childStart != VALUE_NOT_SET && childEnd != VALUE_NOT_SET) {
            return MeasureSpec(MeasureSpec.Mode.EXACTLY, max(0f, maxAvailable))
        }

        if (childSize >= 0) {
            return MeasureSpec(MeasureSpec.Mode.EXACTLY, min(maxAvailable, childSize))
        }
        return if (childSize == -1f) {
            MeasureSpec(MeasureSpec.Mode.EXACTLY, max(0f, maxAvailable))
        } else {
            MeasureSpec(MeasureSpec.Mode.AT_MOST, maxAvailable)
        }
    }

    private fun positionChildHorizontal(
        child: Widget,
        params: LayoutParams,
        myWidth: Float,
        wrapContent: Boolean
    ): Boolean {
        if (params.left == VALUE_NOT_SET && params.right != VALUE_NOT_SET) {
            params.left = params.right - child.measuredWidth
        } else if (params.left != VALUE_NOT_SET && params.right == VALUE_NOT_SET) {
            params.right = params.left + child.measuredWidth
        } else if (params.left == VALUE_NOT_SET) {
            if (params.hasBooleanRule(CENTER_IN_PARENT) || params.hasBooleanRule(CENTER_HORIZONTAL)) {
                if (!wrapContent) {
                    centerHorizontal(child, params, myWidth)
                } else {
                    val lp = layoutParams
                    params.left = lp.paddingLeft + params.marginLeft
                    params.right = params.left + child.measuredWidth
                }
                return true
            } else {
                val lp = layoutParams
                params.left = lp.paddingLeft + params.marginLeft
                params.right = params.left + child.measuredWidth
            }
        }
        return params.hasBooleanRule(ALIGN_PARENT_RIGHT)
    }

    private fun positionChildVertical(
        child: Widget,
        params: LayoutParams,
        myHeight: Float,
        wrapContent: Boolean
    ): Boolean {
        if (params.top == VALUE_NOT_SET && params.bottom != VALUE_NOT_SET) {
            params.top = params.bottom - child.measuredHeight
        } else if (params.top != VALUE_NOT_SET && params.bottom == VALUE_NOT_SET) {
            params.bottom = params.top + child.measuredHeight
        } else if (params.top == VALUE_NOT_SET) {
            if (params.hasBooleanRule(CENTER_IN_PARENT) || params.hasBooleanRule(CENTER_VERTICAL)) {
                if (!wrapContent) {
                    centerVertical(child, params, myHeight)
                } else {
                    val lp = layoutParams
                    params.top = lp.paddingTop + params.marginTop
                    params.bottom = params.top + child.measuredHeight
                }
                return true
            } else {
                val lp = layoutParams
                params.top = lp.paddingTop + params.marginTop
                params.bottom = params.top + child.measuredHeight
            }
        }
        return params.hasBooleanRule(ALIGN_PARENT_BOTTOM)
    }

    override fun onLayout() {
        for (child in children.values) {
            if (child.isVisible()) {
                val st = child.layoutParams as LayoutParams
                child.layout(st.left, st.top, st.right, st.bottom)
            }
        }
    }

    class LayoutParams : WidgetContainer.LayoutParams {
        private val rules: MutableMap<Int, Any> = HashMap<Int, Any>()
        var left = 0f
        var top = 0f
        var right = 0f
        var bottom = 0f

        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source) {
            if (source is LayoutParams) {
                rules.putAll(source.rules)
            }
        }

        fun addRule(verb: Int): LayoutParams {
            rules[verb] = true
            return this
        }

        fun addRule(verb: Int, subject: Widget): LayoutParams {
            rules[verb] = subject
            return this
        }

        fun removeRule(verb: Int) {
            rules.remove(verb)
        }

        fun getRule(verb: Int): Any? {
            return rules[verb]
        }

        fun hasBooleanRule(verb: Int): Boolean {
            return true == rules[verb]
        }
    }

    private class DependencyGraph {
        private val nodes = ArrayList<Node>()
        private val keyNodes: MutableMap<Widget, Node> = HashMap<Widget, Node>()
        private val roots = ArrayDeque<Node>()

        fun clear() {
            nodes.clear()
            keyNodes.clear()
            roots.clear()
        }

        fun add(view: Widget) {
            val node = Node(view)
            keyNodes[view] = node
            nodes.add(node)
        }

        fun getSortedViews(rules: IntArray, sorted: MutableList<Widget>) {
            val roots = findRoots(rules)
            var node: Node?
            while (roots.pollLast().also { node = it } != null) {
                sorted.add(node!!.view)

                for (dependent in node.dependents.keys) {
                    dependent.dependencies.remove(node.view)
                    if (dependent.dependencies.isEmpty()) {
                        roots.add(dependent)
                    }
                }
            }
            check(sorted.size == nodes.size) {
                "Circular dependencies cannot exist in RelativeLayout"
            }
        }

        fun findRoots(rulesFilter: IntArray): ArrayDeque<Node> {
            for (node in nodes) {
                node.dependents.clear()
                node.dependencies.clear()
            }

            for (node in nodes) {
                val layoutParams = node.view.layoutParams as LayoutParams
                for (verb in rulesFilter) {
                    val subject = layoutParams.getRule(verb)
                    if (subject is Widget) {
                        val dependency = keyNodes[subject]
                        if (dependency == null || dependency === node) {
                            continue
                        }
                        dependency.dependents[node] = this
                        node.dependencies[subject] = dependency
                    }
                }
            }

            roots.clear()
            for (node in nodes) {
                if (node.dependencies.isEmpty()) {
                    roots.addLast(node)
                }
            }
            return roots
        }

        private class Node(val view: Widget) {
            val dependents: MutableMap<Node, DependencyGraph> = HashMap<Node, DependencyGraph>()
            val dependencies: MutableMap<Widget, Node> = HashMap<Widget, Node>()
        }
    }

    companion object {
        const val LEFT_OF: Int = 0
        const val RIGHT_OF: Int = 1
        const val ABOVE: Int = 2
        const val BELOW: Int = 3
        const val ALIGN_BASELINE: Int = 4
        const val ALIGN_LEFT: Int = 5
        const val ALIGN_TOP: Int = 6
        const val ALIGN_RIGHT: Int = 7
        const val ALIGN_BOTTOM: Int = 8
        const val ALIGN_PARENT_LEFT: Int = 9
        const val ALIGN_PARENT_TOP: Int = 10
        const val ALIGN_PARENT_RIGHT: Int = 11
        const val ALIGN_PARENT_BOTTOM: Int = 12
        const val CENTER_IN_PARENT: Int = 13
        const val CENTER_HORIZONTAL: Int = 14
        const val CENTER_VERTICAL: Int = 15

        private const val VALUE_NOT_SET = Float.MIN_VALUE

        private val RULES_VERTICAL = intArrayOf(ABOVE, BELOW, ALIGN_BASELINE, ALIGN_TOP, ALIGN_BOTTOM)
        private val RULES_HORIZONTAL = intArrayOf(LEFT_OF, RIGHT_OF, ALIGN_LEFT, ALIGN_RIGHT)

        private fun centerHorizontal(child: Widget, params: LayoutParams, myWidth: Float) {
            val childWidth = child.measuredWidth
            val left = (myWidth - childWidth) / 2.0f
            params.left = left
            params.right = left + childWidth
        }

        private fun centerVertical(child: Widget, params: LayoutParams, myHeight: Float) {
            val childHeight = child.measuredHeight
            val top = (myHeight - childHeight) / 2.0f
            params.top = top
            params.bottom = top + childHeight
        }
    }
}