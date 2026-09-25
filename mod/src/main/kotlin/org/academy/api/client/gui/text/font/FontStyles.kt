package org.academy.api.client.gui.text.font

import org.academy.api.client.gui.text.model.FontStyle
import org.lwjgl.util.freetype.FreeType

object FontStyles {
    fun fromFreeType(styleFlags: Int): FontStyle {
        val bold = styleFlags and FreeType.FT_STYLE_FLAG_BOLD != 0
        val italic = styleFlags and FreeType.FT_STYLE_FLAG_ITALIC != 0
        return when {
            bold && italic -> FontStyle.BOLD_ITALIC
            bold -> FontStyle.BOLD
            italic -> FontStyle.ITALIC
            else -> FontStyle.NORMAL
        }
    }
}
