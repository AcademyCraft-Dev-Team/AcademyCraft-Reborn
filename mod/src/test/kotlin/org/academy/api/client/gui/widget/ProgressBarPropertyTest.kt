package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.Orientation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 验证 ProgressBar/SeekBar 属性化后的钳制与回调语义. */
class ProgressBarPropertyTest {

    @Test
    fun `progress clamps to min max range`() {
        val bar = ProgressBarWidget()
        bar.setMax(100f)
        bar.setMin(0f)
        bar.setProgress(250f)
        assertEquals(100f, bar.progress)
        bar.setProgress(-10f)
        assertEquals(0f, bar.progress)
    }

    @Test
    fun `lowering max clamps the current progress`() {
        val bar = ProgressBarWidget()
        bar.setMin(0f)
        bar.setMax(100f)
        bar.setProgress(80f)
        bar.setMax(50f)
        assertEquals(50f, bar.progress)
        assertEquals(50f, bar.max)
    }

    @Test
    fun `min and max stay consistent when crossing`() {
        val bar = ProgressBarWidget()
        bar.setMin(20f)
        bar.setMax(10f)
        assertEquals(20f, bar.min)
        assertEquals(20f, bar.max)
    }

    @Test
    fun `orientation and colors are assignable fields`() {
        val bar = ProgressBarWidget()
        bar.setOrientation(Orientation.VERTICAL)
        assertEquals(Orientation.VERTICAL, bar.orientation)
        bar.setBackgroundColor(0xFF112233.toInt())
        bar.setProgressColor(0xFF445566.toInt())
        assertEquals(0xFF112233.toInt(), bar.backgroundColor)
        assertEquals(0xFF445566.toInt(), bar.progressColor)
    }

    @Test
    fun `seek bar notifies listener on property progress change`() {
        val bar = SeekBarWidget()
        var notified = false
        bar.setMin(0f)
        bar.setMax(10f)
        bar.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                notified = true
            }

            override fun onStartTrackingTouch(seekBar: SeekBarWidget) {}

            override fun onStopTrackingTouch(seekBar: SeekBarWidget) {}
        })
        bar.setProgress(5f)
        assertEquals(true, notified)
    }
}
