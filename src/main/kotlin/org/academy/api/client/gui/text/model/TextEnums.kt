package org.academy.api.client.gui.text.model

enum class Ellipsize {
    NONE,
    START,
    MIDDLE,
    END,
    MARQUEE
}

enum class FontStyle {
    NORMAL,
    BOLD,
    ITALIC,
    BOLD_ITALIC;

    val isBold: Boolean get() = this == BOLD || this == BOLD_ITALIC
    val isItalic: Boolean get() = this == ITALIC || this == BOLD_ITALIC
}
