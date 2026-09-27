package org.academy.api.client.gui.text.device

object FadeMask {
    fun fadeFactor(
        u: Float,
        viewportLeft: Float,
        viewportWidth: Float,
        fadeLen: Float,
        leftStrength: Float,
        rightStrength: Float
    ): Float {
        if (fadeLen <= 0f || viewportWidth <= 0f) return 1f
        val local = u - viewportLeft
        val inFromLeft = (local / fadeLen).coerceIn(0f, 1f)
        val inFromRight = ((viewportWidth - local) / fadeLen).coerceIn(0f, 1f)
        val left = 1f - leftStrength * (1f - inFromLeft)
        val right = 1f - rightStrength * (1f - inFromRight)
        return (left * right).coerceIn(0f, 1f)
    }
}
