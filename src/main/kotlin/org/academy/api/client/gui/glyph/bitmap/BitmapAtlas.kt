package org.academy.api.client.gui.glyph.bitmap

import com.mojang.blaze3d.GpuFormat
import org.academy.api.client.gui.glyph.AtlasPage
import org.academy.api.client.gui.glyph.Constants
import org.academy.api.client.gui.glyph.allocator.Rect
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock

/**
 * 共享灰度字形图集：所有光栅尺寸共用，R8_UNORM 页 + Skyline 分配，按需追加新页。
 */
class BitmapAtlas private constructor(private val pageSize: Int) {
    data class Reservation(val page: AtlasPage, val rect: Rect)

    private val pages = ArrayList<AtlasPage>()
    private val atlasLock = ReentrantLock()

    /** 为字形保留槽位；必须在渲染线程调用。 */
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

    /** 上传紧凑的 8-bit alpha 行；必须在渲染线程调用。 */
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
        fun create(): BitmapAtlas = BitmapAtlas(Constants.BITMAP_ATLAS_SIZE)
    }
}
