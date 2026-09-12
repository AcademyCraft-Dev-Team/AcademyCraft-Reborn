package org.academy.api.client.gui.text

import net.minecraft.resources.Identifier
import java.awt.font.TextLayout

/**
 * 一段共享同一字体的字形序列，设备无关。
 *
 * [glyphIndices] 是字体 glyph index（AWT 选定的字形，与 FreeType 编号一致）；
 * [positionsX] 是每个字形 origin 的 x（AWT user units，即按字号缩放的 points）；
 * [charIndices] 是每个字形对应的原始文本 UTF-16 起始偏移。
 */
class GlyphRun(
    val fontId: Identifier,
    val glyphIndices: IntArray,
    val positionsX: FloatArray,
    val advances: FloatArray,
    val charIndices: IntArray
)

/** 一行：原始文本的 char 区间与度量（AWT user units）。 */
class TextLine(
    val charStart: Int,
    val charEnd: Int,
    val baselineY: Float,
    val ascent: Float,
    val descent: Float,
    val leading: Float
)

/** 一次 shaping 的完整结果（对标 Skia `TextBlob`）。 */
class TextBlob(
    val text: String,
    val runs: List<GlyphRun>,
    val lines: List<TextLine>,
    /** 与 [lines] 一一对应的 AWT 行布局（空行/null），供 caret/hit-test 使用 JDK 实现。 */
    val layouts: List<TextLayout?>,
    val width: Float,
    val height: Float
)
