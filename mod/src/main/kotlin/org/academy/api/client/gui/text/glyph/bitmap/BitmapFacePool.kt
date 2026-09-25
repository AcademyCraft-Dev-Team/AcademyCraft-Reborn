package org.academy.api.client.gui.text.glyph.bitmap

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.text.font.FontFace
import org.academy.api.client.gui.text.font.FontRepository
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

object BitmapFacePool {
    private val faces = ConcurrentHashMap<Identifier, FT_Face>()
    private val locks = ConcurrentHashMap<Identifier, ReentrantLock>()

    fun face(font: FontFace): FT_Face =
        faces.computeIfAbsent(font.descriptor.identifier) { FontRepository.newFace(it) }

    fun lock(font: FontFace): ReentrantLock =
        locks.computeIfAbsent(font.descriptor.identifier) { ReentrantLock() }

    fun clear() {
        faces.values.forEach(FreeType::FT_Done_Face)
        faces.clear()
        locks.clear()
    }
}
