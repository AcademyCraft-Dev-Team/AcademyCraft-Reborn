package org.academy.api.client.gui.text.font

/** 字体 em 单位度量（用于把字体单位换算到布局/设备尺寸）。 */
data class MsdfFontMetrics(
    val unitsPerEm: Short,
    val ascender: Short,
    val descender: Short,
    val lineHeight: Short
)
