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
        return switch (styleFlags) {
            case FreeType.FT_STYLE_FLAG_BOLD -> BOLD;
            case FreeType.FT_STYLE_FLAG_ITALIC -> ITALIC;
            case FreeType.FT_STYLE_FLAG_BOLD | FreeType.FT_STYLE_FLAG_ITALIC -> BOLD_ITALIC;
            default -> NORMAL;
        };
    }
}