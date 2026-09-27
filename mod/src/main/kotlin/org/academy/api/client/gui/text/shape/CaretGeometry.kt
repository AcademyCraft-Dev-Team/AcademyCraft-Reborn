package org.academy.api.client.gui.text.shape

import org.academy.api.client.gui.text.model.TextBlob
import org.academy.api.client.gui.text.model.TextLine

object CaretGeometry {
    fun lineFor(blob: TextBlob, unit: Int): TextLine? =
        blob.lines.firstOrNull { unit < it.charEnd } ?: blob.lines.lastOrNull()

    fun caretX(blob: TextBlob, line: TextLine, unit: Int): Float {
        var prevEnd = 0f
        var nextPos = -1f
        var have = false
        for (run in blob.runs) {
            for (g in run.charIndices.indices) {
                val ci = run.charIndices[g]
                if (ci < line.charStart || ci >= line.charEnd) continue
                if (ci == unit) return run.positionsX[g]
                if (ci > unit && nextPos < 0f) nextPos = run.positionsX[g]
                prevEnd = run.positionsX[g] + run.advances[g]
                have = true
            }
        }
        return if (nextPos >= 0f) nextPos else if (have) prevEnd else 0f
    }

    fun hitTest(blob: TextBlob, line: TextLine, localX: Float): Int {
        var best = line.charStart
        var bestDist = Float.MAX_VALUE
        var prevEnd = 0f
        var have = false
        for (run in blob.runs) {
            for (g in run.charIndices.indices) {
                val ci = run.charIndices[g]
                if (ci < line.charStart || ci >= line.charEnd) continue
                val pos = run.positionsX[g]
                val gapMid = if (have) (prevEnd + pos) / 2f else pos
                val dist = kotlin.math.abs(localX - gapMid)
                if (dist < bestDist) {
                    bestDist = dist
                    best = ci
                }
                prevEnd = pos + run.advances[g]
                have = true
            }
        }
        if (have && kotlin.math.abs(localX - prevEnd) < bestDist) {
            best = line.charEnd
        }
        return best
    }
}
