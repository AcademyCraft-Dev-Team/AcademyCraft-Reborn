package org.academy.api.client.gui.text

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.environment.UiEnvironment
import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * `java.awt.Font` 缓存：从与 FreeType 相同的字体字节加载，仅用于**布局与度量**。
 *
 * 光栅化仍由 FreeType/jmsdfgen 负责。布局与光栅共用同一字体、同一 glyph index，因此
 * advance/kerning/换行/caret（AWT）与轮廓（FT）在构造上一致。
 */
object AwtFontCache {
    /**
     * AA + fractional metrics、无变换、不 hint：advance 为未 hint 的分数度量，匹配
     * 亚像素定位；hinting 只作用于 FT 的 ink，不回流 pen。
     */
    val renderContext: FontRenderContext = FontRenderContext(null, true, true)

    private data class SizedKey(val fontId: Identifier, val millis: Int, val awtStyle: Int)

    private val baseFonts = ConcurrentHashMap<Identifier, Font>()
    private val sizedFonts = ConcurrentHashMap<SizedKey, Font>()

    /** 加载失败的字体（避免反复尝试）。 */
    private val failed = ConcurrentHashMap.newKeySet<Identifier>()

    /**
     * 返回大小为 [size]（逻辑单位）、[awtStyle]（Font.PLAIN/BOLD/ITALIC 组合）的 AWT 字体；
     * 字体字节不可加载时返回 null。
     */
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

    /** 供 [MsdfFontService] 预热的字体 id 集合。 */
    fun loadedFontIds(): Set<Identifier> = baseFonts.keys.toSet()
}
