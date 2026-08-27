package org.academy.desktop.grapheditor.canvas

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Camera2DTest {

    @Test
    fun snapRoundsToGrid() {
        val camera = Camera2D()
        assertEquals(10f, camera.snap(10.4f))
        assertEquals(20f, camera.snap(19.6f))
        assertEquals(-10f, camera.snap(-9.6f))
        assertEquals(0f, camera.snap(0f))
    }

    @Test
    fun snapZeroGridReturnsInput() {
        val camera = Camera2D()
        assertEquals(123f, camera.snap(123f, 0f))
    }

    @Test
    fun frameToBoundsCentersAndFits() {
        val camera = Camera2D()
        camera.frameToBounds(0f, 0f, 200f, 100f, 0f, 0f, 1000f, 500f, maxZoom = 2f)
        assertEquals(2f, camera.zoom)
        // 图范围中心 (100,50) 应对齐窗口中心 (500,250)
        assertEquals(500f, camera.graphToScreenX(100f), 0.001f)
        assertEquals(250f, camera.graphToScreenY(50f), 0.001f)
    }

    @Test
    fun frameToBoundsClampsToMaxZoom() {
        val camera = Camera2D()
        camera.frameToBounds(0f, 0f, 100f, 100f, 0f, 0f, 2000f, 2000f, maxZoom = 1f)
        assertEquals(1f, camera.zoom)
    }

    @Test
    fun frameToBoundsDegenerateRange() {
        val camera = Camera2D()
        // 单点/空范围不崩溃，回退最小尺寸
        camera.frameToBounds(5f, 5f, 5f, 5f, 0f, 0f, 1000f, 800f, maxZoom = 2f)
        assertTrue(camera.zoom > 0f)
    }
}
