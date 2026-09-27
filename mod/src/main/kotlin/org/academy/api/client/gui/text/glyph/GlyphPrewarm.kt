package org.academy.api.client.gui.text.glyph

import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import org.academy.AcademyCraft
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.text.font.FontRepository
import org.academy.api.client.gui.text.glyph.msdf.MsdfGlyphCache
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

private val LOGGER = AcademyCraft.getLogger()

object GlyphPrewarm {
    private val runs = AtomicLong()

    fun onFontsReady() {
        val run = runs.incrementAndGet()
        LOGGER.info(
            "[GlyphPrewarm] scheduling prewarm run #{} (fontsReady={}, order={})",
            run, FontRepository.isFontsReady(), FontRepository.snapshot()
        )
        FontRepository.glyphExecutor.execute {
            val start = System.nanoTime()
            try {
                prewarmAscii()
                prewarmLanguage()
                LOGGER.info(
                    "[GlyphPrewarm] run #{} finished in {} ms",
                    run, (System.nanoTime() - start) / 1_000_000
                )
            } catch (e: Exception) {
                LOGGER.error("Failed to pre-generate default glyphs", e)
            }
        }
    }

    private fun prewarmAscii() {
        if (!FontRepository.isFontsReady()) {
            LOGGER.warn("[GlyphPrewarm] prewarmAscii called before fonts ready; skipping to avoid poisoning resolution cache")
            return
        }
        var c = ' '.code
        var generated = 0
        var skipped = 0
        while (c <= '~'.code) {
            if (prewarm(c)) generated++ else skipped++
            c++
        }
        LOGGER.info("[GlyphPrewarm] ASCII prewarm done: generated={} skipped={}", generated, skipped)
    }

    private fun prewarmLanguage() {
        if (!FontRepository.isFontsReady()) {
            LOGGER.warn("[GlyphPrewarm] prewarmLanguage called before fonts ready; skipping")
            return
        }
        val lang = Minecraft.getInstance().languageManager.selected
        val id = AcademyCraft.academy("lang/$lang.json")
        val stream = UiEnvironment.get().openResource(id.namespace, id.path)
        if (stream == null) {
            LOGGER.warn("[GlyphPrewarm] language resource not found: {}; skipping language prewarm", id)
            return
        }
        var generated = 0
        var skipped = 0
        try {
            stream.use {
                val root = JsonParser.parseReader(
                    InputStreamReader(it, StandardCharsets.UTF_8)
                ).asJsonObject
                LOGGER.info("[GlyphPrewarm] prewarming {} language entries for '{}'", root.size(), lang)
                for ((_, value) in root.entrySet()) {
                    value.asString.codePoints().forEach { ch ->
                        if (prewarm(ch)) generated++ else skipped++
                    }
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to pre-generate glyphs for language {}", lang, e)
        }
        LOGGER.info("[GlyphPrewarm] language '{}' prewarm done: generated={} skipped={}", lang, generated, skipped)
    }

    private fun prewarm(codepoint: Int): Boolean {
        if (Character.isISOControl(codepoint) || Character.getType(codepoint) == Character.FORMAT.toInt()) {
            return false
        }
        val font = FontRepository.getFont(codepoint)
        if (font.glyphIndex(codepoint) == 0) {
            LOGGER.debug(
                "[GlyphPrewarm] skip U+{}: font '{}' has no glyph (glyphIndex=0)",
                String.format("%04X", codepoint), font.descriptor.identifier
            )
            return false
        }
        MsdfGlyphCache.of(font).getGlyph(codepoint)
        return true
    }
}
