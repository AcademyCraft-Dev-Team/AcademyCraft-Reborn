package org.academy.api.client.gui.text

/** 省略号位置（对标 Android `TextUtils.TruncateAt`）。 */
enum class Ellipsize {
    NONE,
    START,
    MIDDLE,
    END,
    MARQUEE
}

/** 字重/字形（对标 Android `Typeface` style）。 */
enum class TextStyle {
    NORMAL,
    BOLD,
    ITALIC,
    BOLD_ITALIC
}