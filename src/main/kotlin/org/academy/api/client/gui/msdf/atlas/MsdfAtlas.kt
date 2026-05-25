package org.academy.api.client.gui.msdf.atlas

import net.minecraft.util.Mth
import org.academy.api.client.gui.msdf.atlas.allocator.Rect
import org.academy.api.client.gui.msdf.core.*
import org.academy.api.client.gui.msdf.util.FreeTypeShapeConverter.loadShapeFromFtOutline
import org.academy.api.client.gui.msdf.util.MsdfTextureUtil
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.lang.AutoCloseable

class MsdfAtlas(private val pageSize: Int, private val glyphSize: Int, private val pxRange: Double) : AutoCloseable {
    val pages: MutableList<AtlasPage> = ArrayList()
    private val glyphCache: MutableMap<Int, MsdfGlyph> = HashMap()
    private val padding: Int = Mth.ceil(pxRange) + 2

    fun getOrGenerate(face: FT_Face, character: Int): MsdfGlyph? {
        if (glyphCache.containsKey(character)) return glyphCache[character]

        if (FreeType.FT_Load_Char(
                face,
                character.toLong(),
                FreeType.FT_LOAD_NO_SCALE or FreeType.FT_LOAD_NO_HINTING or FreeType.FT_LOAD_NO_BITMAP
            ) != 0
        ) return null

        val slot = face.glyph() ?: return null

        val metrics = slot.metrics()
        val advance = metrics.horiAdvance()
        val bearingX = metrics.horiBearingX()
        val bearingY = metrics.horiBearingY()

        val shape: Shape
        MemoryStack.stackPush().use { stack ->
            shape = loadShapeFromFtOutline(slot.outline(), stack)
        }
        if (shape.contours.isEmpty()) {
            val whitespaceGlyph = MsdfGlyph(
                null, 0f, 0f, 0f, 0f, advance, bearingX, bearingY, 0.0, 0.0, 0.0, 0.0
            )
            glyphCache[character] = whitespaceGlyph
            return whitespaceGlyph
        }

        var processedShape = shape.normalize().orient()
        processedShape = EdgeColoring.colorShape(processedShape, 3.0, 0)

        val bounds = processedShape.getBounds()
        val scale = glyphSize.toDouble() / face.units_per_EM()

        val slotSize = glyphSize + Mth.ceil(pxRange * 2)
        val slotSizeWithPadding = slotSize + padding

        val texWidth = Mth.ceil((bounds.r - bounds.l) * scale + pxRange * 2)
        val texHeight = Mth.ceil((bounds.t - bounds.b) * scale + pxRange * 2)

        val (page, rect) = pages.firstNotNullOfOrNull { p ->
            p.reserve(slotSizeWithPadding, slotSizeWithPadding).map { p to it }.orElse(null)
        } ?: run {
            val newPage = AtlasPage(pageSize, "msdf_atlas_page_" + pages.size)
            pages.add(newPage)
            val newRect = newPage.reserve(slotSizeWithPadding, slotSizeWithPadding)
                .orElseThrow {
                    IllegalStateException(
                        "Glyph is too large (" + slotSizeWithPadding + "x" + slotSizeWithPadding +
                                ") for atlas page size (" + pageSize + "x" + pageSize + ")"
                    )
                }
            newPage to newRect
        }

        val tx = -bounds.l + (pxRange / scale)
        val ty = -bounds.b + (pxRange / scale)

        val projection = Projection(Vec2(scale, scale), Vec2(tx, ty))
        val distanceMapping = DistanceMapping(Range(pxRange / scale))

        val config = GeneratorConfig(
            width = texWidth,
            height = texHeight,
            pxRange = pxRange / scale,
            overlapSupport = true,
            projection = projection,
            distanceMapping = distanceMapping
        )

        val bitmap = MsdfGenerator.generate(processedShape, config)

        MsdfTextureUtil.convertToNativeImage(bitmap.toRef()).use { nativeImage ->
            page.upload(Rect(rect.x, rect.y, texWidth, texHeight), nativeImage)
        }
        val u0 = rect.x.toFloat() / pageSize
        val v0 = rect.y.toFloat() / pageSize
        val u1 = (rect.x + texWidth).toFloat() / pageSize
        val v1 = (rect.y + texHeight).toFloat() / pageSize

        val pLeft = bounds.l - (pxRange / scale)
        val pBottom = bounds.b - (pxRange / scale)
        val pRight = bounds.r + (pxRange / scale)
        val pTop = bounds.t + (pxRange / scale)

        val glyph = MsdfGlyph(
            page, u0, v0, u1, v1,
            advance, bearingX, bearingY,
            pLeft, pBottom, pRight, pTop
        )

        glyphCache[character] = glyph
        return glyph
    }

    override fun close() {
        pages.forEach { obj -> obj.close() }
        pages.clear()
        glyphCache.clear()
    }
}