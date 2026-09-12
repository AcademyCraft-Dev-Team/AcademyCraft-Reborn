package org.academy.api.client.gui.text.font

import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.glyph.bitmap.BitmapStrikeCache
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object MsdfFontService {
    val DEFAULT_FONT_ID: Identifier = AcademyCraft.academy("fonts/source-sans-3-regular.otf")

    private val GLYPH_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AcademyCraft-MSDF-Generator").apply { isDaemon = true }
    }

    private val loadedFonts = ConcurrentHashMap<Identifier, MsdfFont>()
    private val library: Long
    private val fontBuffers = ConcurrentHashMap<Identifier, ByteBuffer>()
    private val fontSearchOrder = CopyOnWriteArrayList<Identifier>()
    private val charToFontCache = ConcurrentHashMap<Int, Identifier>()

    init {
        MemoryStack.stackPush().use { stack ->
            val pp = stack.mallocPointer(1)
            if (FreeType.FT_Init_FreeType(pp) != 0) throw RuntimeException("Failed to initialize FreeType")
            library = pp.get(0)
        }
    }

    fun setFontSearchOrder(availableFonts: List<Identifier>) {
        fontSearchOrder.clear()
        fontSearchOrder.add(DEFAULT_FONT_ID)
        for (id in availableFonts) {
            if (id != DEFAULT_FONT_ID) fontSearchOrder.add(id)
        }
        charToFontCache.clear()
    }

    fun getFont(identifier: Identifier): MsdfFont =
        loadedFonts[identifier] ?: loadFont(identifier)

    fun loadFont(identifier: Identifier): MsdfFont {
        val buffer = fontBuffers.computeIfAbsent(identifier, ::loadResourceToBuffer)
        MemoryStack.stackPush().use { stack ->
            val pp = stack.mallocPointer(1)
            if (FreeType.FT_New_Memory_Face(library, buffer, 0, pp) != FreeType.FT_Err_Ok) {
                throw RuntimeException("Failed to load font face: $identifier")
            }
            val font = MsdfFont(identifier, FT_Face.create(pp.get(0)), GLYPH_EXECUTOR)
            // 资源包重载时关闭被替换的旧字体（FT face / bitmap face），避免泄漏。
            loadedFonts.put(identifier, font)?.close()
            return font
        }
    }

    fun createBitmapFace(identifier: Identifier): FT_Face =
        createBitmapFaceInternal(identifier)

    private fun createBitmapFaceInternal(identifier: Identifier): FT_Face {
        val buffer = fontBuffers.computeIfAbsent(identifier, ::loadResourceToBuffer)
        MemoryStack.stackPush().use { stack ->
            val pp = stack.mallocPointer(1)
            if (FreeType.FT_New_Memory_Face(library, buffer, 0, pp) != FreeType.FT_Err_Ok) {
                throw RuntimeException("Failed to load bitmap font face: $identifier")
            }
            return FT_Face.create(pp.get(0))
        }
    }

    private fun loadResourceToBuffer(identifier: Identifier): ByteBuffer {
        try {
            val stream = UiEnvironment.get()
                .openResource(identifier.namespace, identifier.path)
                ?: throw IOException("Resource not found: $identifier")
            stream.use {
                val bytes = it.readAllBytes()
                val buffer = MemoryUtil.memAlloc(bytes.size)
                buffer.put(bytes)
                buffer.flip()
                return buffer
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun getFont(c: Int): MsdfFont = resolveFontForChar(c)

    fun isFont(location: Identifier): Boolean {
        val path = location.path
        return path.endsWith(".ttf") || path.endsWith(".otf") || path.endsWith(".ttc")
    }

    fun genDefaultGlyph() {
        val lang = Minecraft.getInstance().languageManager.selected
        GLYPH_EXECUTOR.execute {
            try {
                var c = ' '.code
                while (c <= '~'.code) {
                    getFont(c).getGlyph(c)
                    c++
                }
                genLanguageGlyphs(lang)
            } catch (e: Exception) {
                AcademyCraft.getLogger().error("Failed to pre-generate default glyphs", e)
            }
        }
    }

    private fun genLanguageGlyphs(lang: String) {
        val id = AcademyCraft.academy("lang/$lang.json")
        val stream = UiEnvironment.get().openResource(id.namespace, id.path) ?: return
        try {
            stream.use {
                val root = JsonParser.parseReader(
                    InputStreamReader(it, StandardCharsets.UTF_8)
                ).asJsonObject
                for (entry in root.entrySet()) {
                    entry.value.asString.codePoints().forEach { ch -> getFont(ch).getGlyph(ch) }
                }
            }
        } catch (e: Exception) {
            AcademyCraft.getLogger().warn("Failed to pre-generate glyphs for language {}", lang, e)
        }
    }

    private fun resolveFontForChar(c: Int): MsdfFont {
        val fontId = charToFontCache.computeIfAbsent(c, ::findFontIdForChar)
        return getFont(fontId)
    }

    private fun findFontIdForChar(c: Int): Identifier {
        for (id in fontSearchOrder) {
            val font = loadedFonts[id]
            if (font != null && font.hasGlyph(c)) return id
        }
        return DEFAULT_FONT_ID
    }

    fun close() {
        loadedFonts.values.forEach(MsdfFont::close)
        loadedFonts.clear()
        fontBuffers.values.forEach(MemoryUtil::memFree)
        fontBuffers.clear()
        BitmapStrikeCache.clear()
        FreeType.FT_Done_FreeType(library)
    }
}
