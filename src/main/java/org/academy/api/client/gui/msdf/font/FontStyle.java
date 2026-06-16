package org.academy.api.client.gui.msdf.font;

import org.lwjgl.util.freetype.FreeType;

public enum FontStyle {
    NORMAL,
    BOLD,
    ITALIC,
    BOLD_ITALIC;

    public boolean isBold() {
        return this == BOLD || this == BOLD_ITALIC;
    }

    public boolean isItalic() {
        return this == ITALIC || this == BOLD_ITALIC;
    }

    public static FontStyle of(int styleFlags) {
        if (styleFlags == FreeType.FT_STYLE_FLAG_BOLD) return BOLD;
        if (styleFlags == FreeType.FT_STYLE_FLAG_ITALIC) return ITALIC;
        if (styleFlags == (FreeType.FT_STYLE_FLAG_BOLD | FreeType.FT_STYLE_FLAG_ITALIC)) return BOLD_ITALIC;
        return NORMAL;
    }
}
