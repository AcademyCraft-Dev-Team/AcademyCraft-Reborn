package org.academy.api.client.gui.render

import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScissorRectTest {
    @Test
    fun `fromLocal applies translation`() {
        val matrix = Matrix4f().translate(10f, 20f, 0f)
        assertEquals(
            ScissorRect(10f, 20f, 30f, 40f),
            ScissorRect.fromLocal(0f, 0f, 30f, 40f, matrix)
        )
    }

    @Test
    fun `fromLocal applies non-uniform scale`() {
        val matrix = Matrix4f().scale(2f, 3f, 1f)
        assertEquals(
            ScissorRect(0f, 0f, 60f, 120f),
            ScissorRect.fromLocal(0f, 0f, 30f, 40f, matrix)
        )
    }

    @Test
    fun `fromLocal applies scale around pivot`() {
        val matrix = Matrix4f()
            .translate(10f, 10f, 0f)
            .scale(2f, 2f, 1f)
            .translate(-10f, -10f, 0f)
        assertEquals(
            ScissorRect(-10f, -10f, 40f, 40f),
            ScissorRect.fromLocal(0f, 0f, 20f, 20f, matrix)
        )
    }

    @Test
    fun `empty rects are reported as empty`() {
        assertEquals(true, ScissorRect.empty().isEmpty)
        assertEquals(true, ScissorRect(5f, 5f, 0f, 10f).isEmpty)
        assertEquals(false, ScissorRect(5f, 5f, 1f, 1f).isEmpty)
    }
}
