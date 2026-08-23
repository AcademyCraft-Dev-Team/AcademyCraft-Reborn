package org.academy.api.client.gui.widget

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
}
