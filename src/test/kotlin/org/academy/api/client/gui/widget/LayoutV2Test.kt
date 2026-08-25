package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayoutV2Test {

    private fun WidgetContainer.measureAndLayout(width: Float, height: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, width),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, height)
        )
        layout(0f, 0f, width, height)
    }

    private fun fill(sizeW: Float, sizeH: Float): FillWidget {
        return FillWidget(0xFF0000.toInt()).apply {
            layoutParams = WidgetContainer.LayoutParams().size(sizeW, sizeH)
        }
    }

    // ============ AnchorLayout ============

    @Test
    fun `anchor center centers child`() {
        val layout = AnchorLayoutWidget()
        layout.addChild(
            "child",
            fill(20f, 10f).apply {
                layoutParams = AnchorLayoutWidget.LayoutParams().size(20f, 10f).anchors(0.5f, 0.5f)
            }
        )
        layout.measureAndLayout(100f, 50f)
        val child = layout.children["child"]!!
        assertEquals(40f, child.x)
        assertEquals(20f, child.y)
        assertEquals(20f, child.width)
        assertEquals(10f, child.height)
    }

    @Test
    fun `anchor bottom right aligns to corner`() {
        val layout = AnchorLayoutWidget()
        layout.addChild(
            "child",
            fill(20f, 10f).apply {
                layoutParams = AnchorLayoutWidget.LayoutParams().size(20f, 10f).anchors(1f, 1f)
            }
        )
        layout.measureAndLayout(100f, 50f)
        val child = layout.children["child"]!!
        assertEquals(80f, child.x)
        assertEquals(40f, child.y)
    }

    @Test
    fun `stretch without second anchor fills the axis`() {
        val layout = AnchorLayoutWidget()
        layout.addChild(
            "child",
            fill(20f, 10f).apply {
                layoutParams = AnchorLayoutWidget.LayoutParams().stretch(true, false).height(10f)
            }
        )
        layout.measureAndLayout(100f, 50f)
        val child = layout.children["child"]!!
        assertEquals(0f, child.x)
        assertEquals(100f, child.width)
    }

    @Test
    fun `stretch interval between two anchors`() {
        val layout = AnchorLayoutWidget()
        layout.addChild(
            "child",
            fill(20f, 10f).apply {
                layoutParams = AnchorLayoutWidget.LayoutParams()
                    .stretch(true, false).anchors(0.25f, 0f).anchors2(0.75f, -1f).height(10f)
            }
        )
        layout.measureAndLayout(100f, 50f)
        val child = layout.children["child"]!!
        assertEquals(25f, child.x)
        assertEquals(50f, child.width)
    }

    @Test
    fun `offset shifts the anchored position`() {
        val layout = AnchorLayoutWidget()
        layout.addChild(
            "child",
            fill(20f, 10f).apply {
                layoutParams = AnchorLayoutWidget.LayoutParams()
                    .size(20f, 10f).anchors(0f, 0f).offset(5f, 8f)
            }
        )
        layout.measureAndLayout(100f, 50f)
        val child = layout.children["child"]!!
        assertEquals(5f, child.x)
        assertEquals(8f, child.y)
    }

    @Test
    fun `percent size resolves against parent content area`() {
        val layout = AnchorLayoutWidget()
        layout.addChild(
            "child",
            fill(20f, 10f).apply {
                layoutParams = AnchorLayoutWidget.LayoutParams()
                    .widthPercent(50f).anchors(0.5f, 0f).height(10f)
            }
        )
        layout.measureAndLayout(100f, 50f)
        val child = layout.children["child"]!!
        assertEquals(50f, child.width)
        assertEquals(25f, child.x)
    }

    // ============ GridLayout ============

    @Test
    fun `grid fills equal cells`() {
        val grid = GridLayoutWidget().apply { columns = 2 }
        grid.addChild("a", fill(10f, 10f))
        grid.addChild("b", fill(10f, 10f))
        grid.addChild("c", fill(10f, 10f))
        grid.addChild("d", fill(10f, 10f))
        grid.measureAndLayout(100f, 50f)

        val a = grid.children["a"]!!
        val b = grid.children["b"]!!
        val c = grid.children["c"]!!
        val d = grid.children["d"]!!
        assertEquals(0f, a.x); assertEquals(0f, a.y); assertEquals(50f, a.width); assertEquals(25f, a.height)
        assertEquals(50f, b.x); assertEquals(0f, b.y)
        assertEquals(0f, c.x); assertEquals(25f, c.y)
        assertEquals(50f, d.x); assertEquals(25f, d.y)
    }

    // ============ WrapLayout ============

    @Test
    fun `wrap breaks to a new row when content overflows`() {
        val wrap = WrapLayoutWidget().apply {
            horizontalSpacing = 2f
            verticalSpacing = 2f
        }
        wrap.addChild("a", fill(30f, 10f))
        wrap.addChild("b", fill(30f, 10f))
        wrap.addChild("c", fill(30f, 10f))
        wrap.measureAndLayout(70f, 100f)

        val a = wrap.children["a"]!!
        val b = wrap.children["b"]!!
        val c = wrap.children["c"]!!
        assertEquals(0f, a.x); assertEquals(0f, a.y)
        assertEquals(32f, b.x); assertEquals(0f, b.y)
        assertEquals(0f, c.x); assertEquals(12f, c.y)
    }

    // ============ DockLayout ============

    @Test
    fun `dock places top, start and fill edges`() {
        val dock = DockLayoutWidget()
        dock.addChild(
            "header",
            fill(10f, 10f).apply {
                val lp = DockLayoutWidget.LayoutParams()
                lp.size(10f, 10f)
                lp.dock(DockLayoutWidget.Dock.TOP)
                layoutParams = lp
            }
        )
        dock.addChild(
            "sidebar",
            fill(10f, 10f).apply {
                val lp = DockLayoutWidget.LayoutParams()
                lp.size(10f, 10f)
                lp.dock(DockLayoutWidget.Dock.START)
                layoutParams = lp
            }
        )
        dock.addChild("content", fill(10f, 10f))
        dock.measureAndLayout(100f, 50f)

        val header = dock.children["header"]!!
        val sidebar = dock.children["sidebar"]!!
        val content = dock.children["content"]!!
        assertEquals(0f, header.x); assertEquals(0f, header.y); assertEquals(100f, header.width); assertEquals(10f, header.height)
        assertEquals(0f, sidebar.x); assertEquals(10f, sidebar.y); assertEquals(10f, sidebar.width); assertEquals(40f, sidebar.height)
        assertEquals(10f, content.x); assertEquals(10f, content.y)
        assertEquals(90f, content.width); assertEquals(40f, content.height)
    }

    // ============ StackLayout ============

    @Test
    fun `stack orders children by z index`() {
        val stack = StackLayoutWidget()
        stack.addChild(
            "back",
            fill(10f, 10f).apply { layoutParams = StackLayoutWidget.LayoutParams().zIndex(0) }
        )
        stack.addChild(
            "front",
            fill(10f, 10f).apply { layoutParams = StackLayoutWidget.LayoutParams().zIndex(5) }
        )
        assertEquals(listOf("back", "front"), stack.children.keys.toList())

        stack.bringToFront("back")
        assertEquals(listOf("front", "back"), stack.children.keys.toList())
    }

    // ============ AspectLayout ============

    @Test
    fun `aspect constrains the content ratio`() {
        val aspect = AspectLayoutWidget(2f)
        aspect.addChild("content", fill(100f, 100f))
        aspect.measureAndLayout(50f, 100f)

        val content = aspect.children["content"]!!
        assertEquals(50f, content.width)
        assertEquals(25f, content.height)
        assertEquals(0f, content.x)
        assertEquals(37.5f, content.y)
    }

    // ============ Layout lifecycle ============

    @Test
    fun `postLayout runs after the layout pass`() {
        val root = FrameLayoutWidget()
        var ran = false
        root.postLayout { ran = true }
        assertFalse(ran)

        root.measureAndLayout(100f, 100f)
        assertTrue(ran)
    }

    @Test
    fun `onLayoutComplete reports resolved rect`() {
        val root = FrameLayoutWidget()
        var info: Widget.WidgetLayoutInfo? = null
        val child = fill(20f, 10f).apply {
            onLayoutComplete = { info = it }
        }
        root.addChild("child", child)
        root.measureAndLayout(100f, 100f)

        assertNotNull(info)
        assertEquals(child.width, info!!.width)
        assertEquals(child.x, info!!.x)
    }

    // ============ FrameLayout gravity/size regression (BUG-4) ============

    @Test
    fun `wrap content column with center gravity is centered`() {
        val root = FrameLayoutWidget()
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.WRAP_CONTENT)
                .gravity(Gravity.CENTER)
        }
        column.addChild("child", fill(40f, 20f))
        root.addChild("column", column)
        root.measureAndLayout(200f, 200f)

        val laid = root.children["column"]!!
        assertEquals(40f, laid.width)
        assertEquals(20f, laid.height)
        assertEquals(80f, laid.x, "wrap_content + CENTER must center horizontally")
        assertEquals(90f, laid.y, "wrap_content + CENTER must center vertically")
    }

    @Test
    fun `match parent column with center gravity fills the parent`() {
        // 回归根因: FrameLayoutWidget.LayoutParams 默认 MATCH_PARENT,
        // 只设 gravity(CENTER) 会撑满全屏, 内容顶部对齐 (旧 cover 表现).
        val root = FrameLayoutWidget()
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            layoutParams = FrameLayoutWidget.LayoutParams().gravity(Gravity.CENTER)
        }
        root.addChild("column", column)
        root.measureAndLayout(200f, 200f)

        val laid = root.children["column"]!!
        assertEquals(200f, laid.width)
        assertEquals(200f, laid.height)
        assertEquals(0f, laid.x)
        assertEquals(0f, laid.y)
    }
}
