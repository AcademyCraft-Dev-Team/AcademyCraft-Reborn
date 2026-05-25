package org.academy.api.client.gui.msdf.font

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.msdf.atlas.MsdfAtlas
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasManager
import org.academy.api.client.gui.msdf.atlas.MsdfGlyph
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.lang.AutoCloseable

class MsdfFont(identifier: Identifier, val face: FT_Face) : AutoCloseable {
    val descriptor: FontDescriptor = FontDescriptor(
        identifier, FontStyle.of(
            face.style_flags().toInt()
        )
    )

    val atlas: MsdfAtlas = MsdfAtlasManager.getAtlas(identifier)

    val metrics: MsdfFontMetrics = MsdfFontMetrics(
        face.units_per_EM(),
        face.ascender(),
        face.descender(),
        face.height()
    )

    fun getGlyph(character: Int): MsdfGlyph? {
        return atlas.getOrGenerate(face, character)
    }

    override fun close() {
        FreeType.FT_Done_Face(face)
    }
}