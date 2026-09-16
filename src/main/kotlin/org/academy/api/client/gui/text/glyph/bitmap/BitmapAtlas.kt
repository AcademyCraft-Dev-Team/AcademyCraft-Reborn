package org.academy.api.client.gui.text.glyph.bitmap

import com.mojang.blaze3d.GpuFormat
import org.academy.api.client.gui.text.atlas.AtlasPage
import org.academy.api.client.gui.text.atlas.AtlasRect
import org.academy.api.client.gui.text.atlas.AtlasSpec
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock

class BitmapAtlas private constructor(private val pageSize: Int) {
    data class Reservation(val page: AtlasPage, val rect: AtlasRect)

    private val pages = ArrayList<AtlasPage>()
    private val atlasLock = ReentrantLock()

    fun reserve(width: Int, height: Int): Reservation? {
        atlasLock.lock()
        try {
            for (candidate in pages) {
                val rect = candidate.reserve(width, height)
                if (rect != null) return Reservation(candidate, rect)
            }

            val newPage = AtlasPage(pageSize, GpuFormat.R8_UNORM, "bitmap_atlas_page_" + pages.size)
            val newRect = newPage.reserve(width, height)
            if (newRect == null) {
                newPage.close()
                return null
            }
            pages.add(newPage)
            return Reservation(newPage, newRect)
        } finally {
            atlasLock.unlock()
        }
    }

    fun upload(reservation: Reservation, buffer: ByteBuffer) {
        reservation.page.upload(reservation.rect, buffer)
    }

    fun getPages(): List<AtlasPage> {
        atlasLock.lock()
        try {
            return pages.toList()
        } finally {
            atlasLock.unlock()
        }
    }

    fun pageCount(): Int {
        atlasLock.lock()
        try {
            return pages.size
        } finally {
            atlasLock.unlock()
        }
    }

    fun close() {
        atlasLock.lock()
        try {
            pages.forEach { it.close() }
            pages.clear()
        } finally {
            atlasLock.unlock()
        }
    }

    companion object {
        fun create(): BitmapAtlas = BitmapAtlas(AtlasSpec.BITMAP_ATLAS_SIZE)
    }
}
