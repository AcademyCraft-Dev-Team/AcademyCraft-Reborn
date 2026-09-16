package org.academy.api.client.gui.text.model

import net.minecraft.resources.Identifier

class GlyphRun(
    val fontId: Identifier,
    val glyphIndices: IntArray,
    val positionsX: FloatArray,
    val advances: FloatArray,
    val charIndices: IntArray
)

class TextLine(
    val charStart: Int,
    val charEnd: Int,
    val baselineY: Float,
    val ascent: Float,
    val descent: Float,
    val leading: Float
)

class TextBlob(
    val text: String,
    val runs: List<GlyphRun>,
    val lines: List<TextLine>,
    val width: Float,
    val height: Float
)
