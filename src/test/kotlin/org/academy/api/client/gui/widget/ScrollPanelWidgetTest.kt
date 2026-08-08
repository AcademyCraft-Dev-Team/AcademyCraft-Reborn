package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScrollPanelWidgetTest {
    @Test
    fun `dispatches absolute pointer coordinates to scrolled content`() {
        val panel = ScrollPanelWidget()
        val content = FrameLayoutWidget()
        val probe = PressProbe()
        content.addChild("probe", probe)
        panel.setContent(content)

        panel.layout(10f, 20f, 110f, 120f)
        content.layout(0f, 0f, 100f, 200f)
        probe.layout(5f, 80f, 25f, 100f)
        panel.scrollTo(0f, 50f)

        panel.dispatchEvent(MouseEvent.createPressEvent(20.0, 55.0, 0))

        assertEquals(1, probe.presses)
    }

    private class PressProbe : AbstractWidget() {
        var presses = 0

        override fun onMousePressed(event: MouseEvent) {
            if (event.button == 0 && isMouseOver(event.x, event.y)) {
                presses++
                event.consume()
            }
        }
    }
}
