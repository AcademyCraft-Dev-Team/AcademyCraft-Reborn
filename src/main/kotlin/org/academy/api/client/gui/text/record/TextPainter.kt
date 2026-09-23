package org.academy.api.client.gui.text.record

import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.text.model.TextShapingOptions

class TextPainter {
    private var blobCommand: TextBlobRecord? = null

    fun draw(
        context: Canvas,
        text: CharSequence,
        textSize: Float,
        red: Float,
        green: Float,
        blue: Float,
        shaping: TextShapingOptions,
        originX: Float,
        originY: Float,
        contentScale: Float,
        alpha: Float,
        revealCodeUnits: Int = Int.MAX_VALUE,
        fadeViewportLeft: Float = 0f,
        fadeViewportWidth: Float = 0f,
        fadeLength: Float = 0f,
        fadeLeftStrength: Float = 0f,
        fadeRightStrength: Float = 0f
    ) {
        val content = text.toString()
        // Canvas retains command references until expansion and may cache them across frames.
        // Reuse an unchanged record, but never overwrite a record already submitted for drawing.
        val command = blobCommand?.takeIf {
            it.text == content && it.fontSize == textSize && it.shapingOptions == shaping &&
                    it.red == red && it.green == green && it.blue == blue && it.alpha == alpha &&
                    it.contentScale == contentScale && it.revealCodeUnits == revealCodeUnits &&
                    it.fadeViewportLeft == fadeViewportLeft && it.fadeViewportWidth == fadeViewportWidth &&
                    it.fadeLength == fadeLength && it.fadeLeftStrength == fadeLeftStrength &&
                    it.fadeRightStrength == fadeRightStrength
        } ?: TextBlobRecord().apply {
            this.text = content
            fontSize = textSize
            shapingOptions = shaping
            this.red = red
            this.green = green
            this.blue = blue
            this.alpha = alpha
            this.contentScale = contentScale
            this.revealCodeUnits = revealCodeUnits
            this.fadeViewportLeft = fadeViewportLeft
            this.fadeViewportWidth = fadeViewportWidth
            this.fadeLength = fadeLength
            this.fadeLeftStrength = fadeLeftStrength
            this.fadeRightStrength = fadeRightStrength
        }.also { blobCommand = it }
        context.pose().pushPose()
        context.pose().translate(originX, originY)
        context.submit(command)
        context.pose().popPose()
    }

    companion object {
        fun blockOrigin(
            availableWidth: Float,
            availableHeight: Float,
            blockWidth: Float,
            blockHeight: Float,
            gravity: Int,
            paddingLeft: Float,
            paddingTop: Float
        ): Pair<Float, Float> {
            var offsetX = 0f
            val horizontalGravity = (gravity shr Gravity.AXIS_X_SHIFT) and 0x7
            if (horizontalGravity == Gravity.AXIS_SPECIFIED) offsetX = (availableWidth - blockWidth) / 2.0f
            else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) offsetX = availableWidth - blockWidth

            var offsetY = 0f
            val verticalGravity = (gravity shr Gravity.AXIS_Y_SHIFT) and 0x7
            if (verticalGravity == Gravity.AXIS_SPECIFIED) offsetY = (availableHeight - blockHeight) / 2.0f
            else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) offsetY = availableHeight - blockHeight

            return Pair(paddingLeft + offsetX, paddingTop + offsetY)
        }
    }
}
