package org.academy.api.client.gui.msdf.font

import org.lwjgl.system.MemoryStack
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType

object MsdfKerningManager {
    fun getKerning(face: FT_Face, left: Long, right: Long): Long {
        if (left == 0L || right == 0L) return 0

        if (!FreeType.FT_HAS_KERNING(face)) return 0

        val leftIndex = FreeType.FT_Get_Char_Index(face, left)
        val rightIndex = FreeType.FT_Get_Char_Index(face, right)

        if (leftIndex == 0 || rightIndex == 0) return 0

        MemoryStack.stackPush().use { stack ->
            val kerning = FT_Vector.malloc(stack)
            if (FreeType.FT_Get_Kerning(
                    face, leftIndex, rightIndex, FreeType.FT_KERNING_UNSCALED, kerning
                ) == 0
            ) return kerning.x()
        }
        return 0
    }
}