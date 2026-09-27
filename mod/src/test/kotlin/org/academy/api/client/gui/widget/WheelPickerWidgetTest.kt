package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WheelPickerWidgetTest {
    @Test
    fun relayoutDoesNotCancelQueuedCyclicSelection() {
        val picker = WheelPickerWidget().apply {
            setCyclic(true)
            setItemHeight(15f)
            repeat(4) { index -> addChild("item_$index", FrameLayoutWidget()) }
        }

        picker.scrollByItems(1)
        picker.scrollByItems(1)
        assertEquals(2, picker.targetSelectedPosition)
        assertEquals(0, picker.selectedPosition)

        picker.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 104f),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 105f)
        )
        picker.layout(0f, 0f, 104f, 105f)

        assertEquals(2, picker.targetSelectedPosition)
        assertEquals(0, picker.selectedPosition)
    }

    @Test
    fun itemScalesAroundContainerCenterSoDriftIsWidthIndependent() {
        val picker = ProbePicker().apply {
            setItemHeight(15f)
            measure(
                MeasureSpec(MeasureSpec.Mode.EXACTLY, 120f),
                MeasureSpec(MeasureSpec.Mode.EXACTLY, 45f)
            )
            layout(0f, 0f, 120f, 45f)
        }
        val child = FrameLayoutWidget()
        child.layoutParams = WidgetContainer.LayoutParams()

        child.layoutParams.gravity = Gravity.TOP_LEFT
        assertEquals(0f, picker.alignXFor(child, 80f))

        child.layoutParams.gravity = Gravity.TOP or Gravity.RIGHT
        assertEquals(40f, picker.alignXFor(child, 80f))

        child.layoutParams.gravity = Gravity.CENTER
        assertEquals(20f, picker.alignXFor(child, 80f))

        child.layoutParams.gravity = Gravity.NO_GRAVITY
        picker.itemAlign = WheelPickerWidget.ItemAlign.RIGHT
        assertEquals(40f, picker.alignXFor(child, 80f))

        assertEquals(60f, picker.pivotXFor())
    }

    private class ProbePicker : WheelPickerWidget() {
        fun alignXFor(child: Widget, childWidth: Float): Float = computeItemAlignX(child, childWidth)

        fun pivotXFor(): Float = computeItemPivotX()
    }
}
