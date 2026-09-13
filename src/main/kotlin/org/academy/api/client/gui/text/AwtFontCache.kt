package org.academy.api.client.gui.text

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.environment.UiEnvironment
import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

object AwtFontCache {
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
        val stream = UiEnvironment.get().openResource(fontId.namespace, fontId.path) ?: return null
        return try {
            stream.use {
                Font.createFont(Font.TRUETYPE_FONT, ByteArrayInputStream(it.readAllBytes()))
                    .also { f -> baseFonts[fontId] = f }
            }
        } catch (e: Exception) {
            failed.add(fontId)
            org.academy.AcademyCraft.getLogger().error("Failed to load AWT font {}", fontId, e)
            null
        }
    }

    fun loadedFontIds(): Set<Identifier> = baseFonts.keys.toSet()
}
