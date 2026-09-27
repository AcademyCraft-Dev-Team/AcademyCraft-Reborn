package org.academy.api.client.gui.text.font

import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

private val LOGGER = AcademyCraft.getLogger()

object AwtFaceCache {
    val renderContext: FontRenderContext = FontRenderContext(null, true, true)

    private data class SizedKey(val fontId: Identifier, val millis: Int, val awtStyle: Int)

    private val baseFonts = ConcurrentHashMap<Identifier, Font>()
    private val sizedFonts = ConcurrentHashMap<SizedKey, Font>()

    private val failed = ConcurrentHashMap.newKeySet<Identifier>()

    fun font(fontId: Identifier, size: Float, awtStyle: Int = Font.PLAIN): Font? {
        if (fontId in failed) return null
        val base = baseFonts[fontId] ?: loadBase(fontId) ?: return null
        val key = SizedKey(fontId, (size * 1000f).toInt(), awtStyle)
        return sizedFonts.getOrPut(key) {
            if (awtStyle == Font.PLAIN) base.deriveFont(size) else base.deriveFont(awtStyle, size)
        }
    }

    private fun loadBase(fontId: Identifier): Font? {
        val data = FontBytes.bytes(fontId) ?: return null
        return try {
            Font.createFont(Font.TRUETYPE_FONT, ByteArrayInputStream(data))
                .also { f -> baseFonts[fontId] = f }
        } catch (e: Exception) {
            failed.add(fontId)
            LOGGER.error("Failed to load AWT font {}", fontId, e)
            null
        }
    }

    fun clear() {
        baseFonts.clear()
        sizedFonts.clear()
        failed.clear()
    }
}
