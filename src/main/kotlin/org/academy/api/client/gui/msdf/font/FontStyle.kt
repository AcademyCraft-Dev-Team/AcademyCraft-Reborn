package org.academy.api.client.gui.msdf.font

import org.lwjgl.util.freetype.FreeType

enum class FontStyle {
    NORMAL,
    BOLD,
    ITALIC,
    BOLD_ITALIC;

    val isBold: Boolean
        get() = this == BOLD || this == BOLD_ITALIC

    val isItalic: Boolean
        get() = this == ITALIC || this == BOLD_ITALIC

    companion object {
        fun of(styleFlags: Int): FontStyle {
            return when (styleFlags) {
                FreeType.FT_STYLE_FLAG_BOLD -> BOLD
                FreeType.FT_STYLE_FLAG_ITALIC -> ITALIC
                FreeType.FT_STYLE_FLAG_BOLD or FreeType.FT_STYLE_FLAG_ITALIC -> BOLD_ITALIC
                else -> NORMAL
            }
        }
    }
}