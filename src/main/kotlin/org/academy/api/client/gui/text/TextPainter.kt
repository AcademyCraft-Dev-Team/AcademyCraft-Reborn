package org.academy.api.client.gui.text

import org.academy.api.client.gui.command.TextBlobDrawCommand
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.render.Canvas

/**
 * 可复用的文本块绘制器：在控件局部坐标 [originX]/[originY] 处向 [Canvas] 记录一条
 * 设备无关的 [TextBlobDrawCommand]，并统一计算 gravity + padding 对齐。
 *
 * 每个使用方持有一个实例（内部复用同一条命令），[TextWidget] 与 [TextInputWidget] 共用。
 */
class TextPainter {
    private val blobCommand = TextBlobDrawCommand()

    /** 按显式字号与颜色分量绘制；[shaping] 携带影响布局的样式参数。 */
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
        context.pose().pushPose()
        context.pose().translate(originX, originY)
        blobCommand.text = text.toString()
        blobCommand.fontSize = textSize
        blobCommand.shapingOptions = shaping
        blobCommand.thickness = 0f
        blobCommand.red = red
        blobCommand.green = green
        blobCommand.blue = blue
        blobCommand.alpha = alpha
        blobCommand.contentScale = contentScale
        blobCommand.revealCodeUnits = revealCodeUnits
        blobCommand.fadeViewportLeft = fadeViewportLeft
        blobCommand.fadeViewportWidth = fadeViewportWidth
        blobCommand.fadeLength = fadeLength
        blobCommand.fadeLeftStrength = fadeLeftStrength
        blobCommand.fadeRightStrength = fadeRightStrength
        context.submit(blobCommand)
        context.pose().popPose()
    }

    companion object {
        /** 依据 gravity 在可用区域内居中/贴边，并叠加 [paddingLeft]/[paddingTop]。 */
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