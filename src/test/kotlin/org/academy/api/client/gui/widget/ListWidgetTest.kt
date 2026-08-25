package org.academy.api.client.gui.widget

import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListWidgetTest {

    private fun buildList(count: Int, itemH: Float = 20f): Pair<ScrollPanelWidget, ListWidget<Int>> {
        val panel = ScrollPanelWidget(Orientation.VERTICAL)
        val list = ListWidget<Int>()
        list.items = (0 until count).toList()
        list.itemHeight = { _, _ -> itemH }
        list.overscan = 3
        list.createItem = { _ -> LinearLayoutWidget() }
        list.bindItem = { view, item, _ ->
            view.clearChildren()
            view.fill(0xFF0000.toInt()) {
                height = itemH
                matchWidth()
            }
        }
        panel.setContent(list)
        panel.measure(
            org.academy.api.client.gui.layout.MeasureSpec(org.academy.api.client.gui.layout.MeasureSpec.Mode.EXACTLY, 100f),
            org.academy.api.client.gui.layout.MeasureSpec(org.academy.api.client.gui.layout.MeasureSpec.Mode.EXACTLY, 200f)
        )
        panel.layout(0f, 0f, 100f, 200f)
        return panel to list
    }

    @Test
    fun `content height equals sum of item heights`() {
        val (_, list) = buildList(1000, 20f)
        assertEquals(20000f, list.measuredHeight)
    }

    @Test
    fun `only visible window is mounted`() {
        val (_, list) = buildList(1000, 20f)
        val mounted = list.children.size
        assertTrue(mounted in 10..20, "expected ~13 mounted, got $mounted")
    }

    @Test
    fun `mounted item children are measured and laid out with real sizes`() {
        val panel = ScrollPanelWidget(Orientation.VERTICAL)
        val list = ListWidget<Int>()
        list.items = (0 until 5).toList()
        list.overscan = 3
        list.createItem = { _ -> LinearLayoutWidget() }
        list.bindItem = { view, item, _ ->
            view.clearChildren()
            view.lp { sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT); padding(2f) }
            view.fill(0xFF334455.toInt(), "bar") {
                matchWidth()
                height = 18f
            }
        }
        panel.setContent(list)
        panel.measure(
            org.academy.api.client.gui.layout.MeasureSpec(
                org.academy.api.client.gui.layout.MeasureSpec.Mode.EXACTLY, 200f
            ),
            org.academy.api.client.gui.layout.MeasureSpec(
                org.academy.api.client.gui.layout.MeasureSpec.Mode.EXACTLY, 100f
            )
        )
        panel.layout(0f, 0f, 200f, 100f)

        assertTrue(list.children.isNotEmpty(), "list should have mounted items")
        val first = list.children.values.first() as LinearLayoutWidget
        val bar = first.children["bar"]
        assertTrue(bar != null, "item should contain 'bar'")
        assertTrue(bar!!.height > 0f, "mounted item child height should be > 0, got ${bar.height}")
        assertTrue(bar.width > 0f, "mounted item child width should be > 0, got ${bar.width}")
    }

    @Test
    fun `scrolling mounts the new window and recycles the old`() {
        val (panel, list) = buildList(1000, 20f)
        panel.scrollTo(0f, 1000f)
        list.tick()

        val mounted = list.children.keys.map { it.removePrefix("item_").toInt() }
        assertTrue(mounted.isNotEmpty())
        assertTrue(mounted.all { it in 35..70 }, "mounted positions $mounted")
        assertTrue(list.children.size in 10..25)
    }

    @Test
    fun `scrolling back to top remounts the first window`() {
        val (panel, list) = buildList(1000, 20f)
        panel.scrollTo(0f, 1000f)
        list.tick()
        panel.scrollTo(0f, 0f)
        list.tick()

        val mounted = list.children.keys.map { it.removePrefix("item_").toInt() }
        assertTrue(mounted.all { it in 0..15 }, "mounted positions $mounted")
    }
}
