package org.academy.api.client.gui.msdf.font

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft.academy
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.io.IOException
import java.lang.AutoCloseable
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.jvm.optionals.getOrNull

object MsdfFontService : AutoCloseable {
    val defaultFontId = academy("fonts/source-sans-3-regular.ttf")

    private val library: Long
    val loadedFonts: MutableMap<Identifier, MsdfFont> = ConcurrentHashMap<Identifier, MsdfFont>()
    private val fontBuffers: MutableMap<Identifier, ByteBuffer> = ConcurrentHashMap<Identifier, ByteBuffer>()

    private val fontSearchOrder: MutableList<Identifier> = CopyOnWriteArrayList()
    private val charToFontCache: MutableMap<Int, Identifier> = ConcurrentHashMap<Int, Identifier>()

    init {
        MemoryStack.stackPush().use { stack ->
            val pp = stack.mallocPointer(1)
            if (FreeType.FT_Init_FreeType(pp) != 0) throw RuntimeException("Failed to initialize FreeType")
            library = pp.get(0)
        }
    }

    fun setFontSearchOrder(availableFonts: MutableList<Identifier>) {
        fontSearchOrder.clear()
        fontSearchOrder.add(defaultFontId)
        for (id in availableFonts) if (id != defaultFontId) fontSearchOrder.add(id)
        charToFontCache.clear()
    }

    private fun resolveFontForChar(c: Int): MsdfFont {
        val fontId = charToFontCache.computeIfAbsent(c) { findFontIdForChar(it) }
        return getFont(fontId)
    }

    private fun findFontIdForChar(c: Int): Identifier {
        for (id in fontSearchOrder) {
            val font = loadedFonts[id]
            if (font != null) if (FreeType.FT_Get_Char_Index(font.face, c.toLong()) != 0) return id
        }
        return defaultFontId
    }

    fun getFont(identifier: Identifier): MsdfFont {
        return loadedFonts[identifier] ?: loadFont(identifier)
    }

    fun loadFont(identifier: Identifier): MsdfFont {
        val buffer = fontBuffers.computeIfAbsent(identifier) { identifier -> loadResourceToBuffer(identifier) }
        MemoryStack.stackPush().use {
            val pp = it.mallocPointer(1)
            if (FreeType.FT_New_Memory_Face(library, buffer, 0, pp) != 0) {
                throw RuntimeException("Failed to load font face: $identifier")
            }
            val font = MsdfFont(identifier, FT_Face.create(pp.get(0)))
            loadedFonts[identifier] = font
            return font
        }
    }

    private fun loadResourceToBuffer(identifier: Identifier): ByteBuffer {
        try {
            val resource = Minecraft.getInstance().resourceManager.getResource(identifier)
            (resource.getOrNull() ?: throw IOException("Resource not found: $identifier")).open().use {
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

    override fun close() {
        loadedFonts.values.forEach { it.close() }
        loadedFonts.clear()
        fontBuffers.values.forEach { MemoryUtil.memFree(it) }
        fontBuffers.clear()
        FreeType.FT_Done_FreeType(library)
    }

    val defaultFont: MsdfFont
        get() = getFont(defaultFontId)

    fun getFont(c: Int): MsdfFont {
        return resolveFontForChar(c)
    }

    fun isFont(location: Identifier): Boolean {
        val path = location.path
        return path.endsWith(".ttf") || path.endsWith(".otf")
    }

    fun genDefaultGlyph() {
        val font: MsdfFont = defaultFont
        val atlas = font.atlas
        val face = font.face
        var c = '!'
        while (c <= '~') {
            atlas.getOrGenerate(face, c.code)
            c++
        }
    }
}