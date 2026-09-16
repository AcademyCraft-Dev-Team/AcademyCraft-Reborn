package org.academy.api.client.gui.text.font

import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private val LOGGER = AcademyCraft.getLogger()

object FontRepository {
    val DEFAULT_FONT_ID: Identifier = AcademyCraft.academy("fonts/source-sans-3-regular.otf")

    val glyphExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AcademyCraft-MSDF-Generator").apply { isDaemon = true }
    }

    private val loadedFonts = ConcurrentHashMap<Identifier, FontFace>()
    private val library: Long
    private val fontSearchOrder = CopyOnWriteArrayList<Identifier>()
    private val charToFontCache = ConcurrentHashMap<Int, Identifier>()
    private val resolutionCount = AtomicLong()
    private val fallbackCount = AtomicLong()

    @Volatile
    private var fontsReady: Boolean = false

    init {
        MemoryStack.stackPush().use { stack ->
            val pp = stack.mallocPointer(1)
            if (FreeType.FT_Init_FreeType(pp) != 0) throw RuntimeException("Failed to initialize FreeType")
            library = pp.get(0)
        }
        LOGGER.info("[FontRepository] FreeType initialized (library=0x{})", java.lang.Long.toHexString(library))
    }

    fun isFontsReady(): Boolean = fontsReady

    fun setFontSearchOrder(availableFonts: List<Identifier>) {
        val previous = fontSearchOrder.toList()
        fontSearchOrder.clear()
        fontSearchOrder.add(DEFAULT_FONT_ID)
        for (id in availableFonts) {
            if (id != DEFAULT_FONT_ID) fontSearchOrder.add(id)
        }
        charToFontCache.clear()
        val missing = fontSearchOrder.filter { it != DEFAULT_FONT_ID && !loadedFonts.containsKey(it) }
        fontsReady = missing.isEmpty()
        LOGGER.info(
            "[FontRepository] search order updated: prev={} new={} loaded={} missing={} fontsReady={}",
            previous, fontSearchOrder.toList(), loadedFonts.keys.toList(), missing, fontsReady
        )
    }

    fun getFont(identifier: Identifier): FontFace =
        loadedFonts[identifier] ?: loadFont(identifier)

    fun loadFont(identifier: Identifier): FontFace {
        val font = FontFace(identifier, newFace(identifier))
        val previous = loadedFonts.put(identifier, font)
        previous?.close()
        if (charToFontCache.isNotEmpty()) {
            LOGGER.info(
                "[FontRepository] font {} loaded, invalidating {} cached codepoint resolution(s) (replaced={})",
                identifier, charToFontCache.size, previous != null
            )
            charToFontCache.clear()
        } else {
            LOGGER.info("[FontRepository] font {} loaded (replaced={})", identifier, previous != null)
        }
        return font
    }

    fun newFace(identifier: Identifier): FT_Face {
        val buffer: ByteBuffer = FontBytes.buffer(identifier)
            ?: throw RuntimeException("Font resource not found: $identifier")
        MemoryStack.stackPush().use { stack ->
            val pp = stack.mallocPointer(1)
            if (FreeType.FT_New_Memory_Face(library, buffer, 0, pp) != FreeType.FT_Err_Ok) {
                throw RuntimeException("Failed to load font face: $identifier")
            }
            return FT_Face.create(pp.get(0))
        }
    }

    fun markFontsReady() {
        val cleared = charToFontCache.size
        charToFontCache.clear()
        fontsReady = true
        LOGGER.info(
            "[FontRepository] fonts READY: order={} loaded={} clearedFallbacks={}",
            fontSearchOrder.toList(), loadedFonts.keys.toList(), cleared
        )
    }

    fun getFont(c: Int): FontFace = resolveFontForChar(c)

    fun isFont(location: Identifier): Boolean {
        val path = location.path
        return path.endsWith(".ttf") || path.endsWith(".otf") || path.endsWith(".ttc")
    }

    fun availableFontIds(): Set<Identifier> = loadedFonts.keys.toSet()

    fun snapshot(): String {
        return buildString {
            append("fontsReady=").append(fontsReady)
            append(", order=").append(fontSearchOrder.toList())
            append(", loaded=").append(loadedFonts.keys.toList())
            append(", cachedCodepoints=").append(charToFontCache.size)
            append(", resolutions=").append(resolutionCount.get())
            append(", fallbacks=").append(fallbackCount.get())
        }
    }

    fun cachedResolutions(limit: Int = 64): List<Pair<String, Identifier>> =
        charToFontCache.entries.take(limit).map { String.format("U+%04X", it.key) to it.value }

    private fun resolveFontForChar(c: Int): FontFace {
        if (!fontsReady) {
            resolutionCount.incrementAndGet()
            val id = findFontIdForChar(c)
            LOGGER.debug(
                "[FontRepository] resolve U+{} -> {} (NOT READY, uncached; order={}, loaded={})",
                String.format("%04X", c), id, fontSearchOrder.toList(), loadedFonts.keys.toList()
            )
            return getFont(id)
        }
        val fontId = charToFontCache.computeIfAbsent(c, ::findFontIdForChar)
        return getFont(fontId)
    }

    private fun findFontIdForChar(c: Int): Identifier {
        for (id in fontSearchOrder) {
            val font = loadedFonts[id]
            if (font != null && font.hasGlyph(c)) return id
        }
        fallbackCount.incrementAndGet()
        if (!fontsReady) {
            val missing = fontSearchOrder.filter { it != DEFAULT_FONT_ID && loadedFonts[it] == null }
            LOGGER.warn(
                "[FontRepository] U+{} fell back to DEFAULT while fonts NOT READY (missing faces={}, order={}). " +
                        "This resolution is temporary and will be recomputed once fonts are ready.",
                String.format("%04X", c), missing, fontSearchOrder.toList()
            )
        } else {
            LOGGER.debug(
                "[FontRepository] U+{} has no glyph in any loaded font, falling back to DEFAULT",
                String.format("%04X", c)
            )
        }
        return DEFAULT_FONT_ID
    }

    fun close() {
        LOGGER.info("[FontRepository] closing: {}", snapshot())
        loadedFonts.values.forEach(FontFace::close)
        loadedFonts.clear()
        charToFontCache.clear()
        fontSearchOrder.clear()
        fontsReady = false
        FontBytes.clear()
        FreeType.FT_Done_FreeType(library)
    }
}
