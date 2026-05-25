package org.academy.api.client.gui.widget

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.layout.Orientation

open class SpriteSheetWidget(
    texture: Identifier,
    protected val orientation: Orientation,
    protected val sheetWidth: Int,
    protected val sheetHeight: Int,
    protected val frameWidth: Int,
    protected val frameHeight: Int,
    protected val frameCount: Int
) : ImageWidget(texture) {
    protected var currentFrame: Int = 0

    fun nextFrame() {
        this.frameIndex = (currentFrame + 1) % frameCount
    }

    fun previousFrame() {
        this.frameIndex = (currentFrame - 1 + frameCount) % frameCount
    }

    var frameIndex: Int
        get() = currentFrame
        set(index) {
            require(index in 0..<frameCount) {
                "Frame index $index is out of bounds for frame count $frameCount"
            }
            currentFrame = index
            computeUV(index)
        }

    protected fun computeUV(index: Int) {
        val newU0: Float
        val newU1: Float
        val newV0: Float
        val newV1: Float

        if (orientation == Orientation.HORIZONTAL) {
            val x = index * frameWidth
            newU0 = x.toFloat() / sheetWidth
            newU1 = (x + frameWidth).toFloat() / sheetWidth
            newV0 = 0f
            newV1 = frameHeight.toFloat() / sheetHeight
        } else {
            val y = index * frameHeight
            newU0 = 0f
            newU1 = frameWidth.toFloat() / sheetWidth
            newV0 = y.toFloat() / sheetHeight
            newV1 = (y + frameHeight).toFloat() / sheetHeight
        }

        setUv(newU0, newV0, newU1, newV1)
    }
}