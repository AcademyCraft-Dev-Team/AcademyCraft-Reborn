package org.academy.api.client.gui.text.font

import org.lwjgl.util.freetype.FreeType

enum class FontStyle {
    NORMAL,
    BOLD,
    ITALIC,
    BOLD_ITALIC;

    val isBold: Boolean get() = this == BOLD || this == BOLD_ITALIC
    val isItalic: Boolean get() = this == ITALIC || this == BOLD_ITALIC

    companion object {
        fun of(styleFlags: Int): FontStyle {
            if (styleFlags == FreeType.FT_STYLE_FLAG_BOLD) return BOLD
            if (styleFlags == FreeType.FT_STYLE_FLAG_ITALIC) return ITALIC
            if (styleFlags == (FreeType.FT_STYLE_FLAG_BOLD or FreeType.FT_STYLE_FLAG_ITALIC)) return BOLD_ITALIC
            return NORMAL
        }
    }
}
