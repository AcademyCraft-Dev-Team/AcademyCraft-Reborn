package org.academy.api.client.gui.text

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.widget.TextWidget

data class TextShapingOptions(
    val letterSpacing: Float = 0f,
    val textScaleX: Float = 1f,
    val lineSpacingMultiplier: Float = 1f,
    val lineSpacingExtra: Float = 0f,
    val includeFontPadding: Boolean = true,
    val preferredFont: Identifier? = null,
    val textStyle: TextStyle = TextStyle.NORMAL
) {
    companion object {
        val DEFAULT: TextShapingOptions = TextShapingOptions()
    }
}