package org.academy.api.client.gui.text.glyph.bitmap

import com.mojang.renderpearl.api.GpuFormat
import org.academy.api.client.gui.text.atlas.AtlasPage
import org.academy.api.client.gui.text.atlas.AtlasRect
import org.academy.api.client.gui.text.atlas.AtlasSpec
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock

class BitmapAtlas private constructor(private val pageSize: Int, private val padding: Int) {
    data class Reservation(val page: AtlasPage, val rect: AtlasRect, val inkRect: AtlasRect)

    private val pages = ArrayList<AtlasPage>()
    private val atlasLock = ReentrantLock()

    fun reserve(width: Int, height: Int): Reservation? {
        val slotWidth = width + padding * 2
        val slotHeight = height + padding * 2
        atlasLock.lock()
        try {
            for (candidate in pages) {
                val slot = candidate.reserve(slotWidth, slotHeight)
                if (slot != null) return reservation(candidate, slot, width, height)
            }

            val newPage = AtlasPage(pageSize, GpuFormat.R8_UNORM, "bitmap_atlas_page_" + pages.size)
            val newSlot = newPage.reserve(slotWidth, slotHeight)
            if (newSlot == null) {
                newPage.close()
                return null
            }
            pages.add(newPage)
            return reservation(newPage, newSlot, width, height)
        } finally {
            atlasLock.unlock()
        }
    }

    private fun reservation(page: AtlasPage, slot: AtlasRect, width: Int, height: Int): Reservation =
        Reservation(page, slot, AtlasRect(slot.x + padding, slot.y + padding, width, height))

    fun upload(reservation: Reservation, buffer: ByteBuffer) {
        val ink = reservation.inkRect
        if (padding <= 0) {
            reservation.page.upload(ink, buffer)
            return
        }

        val slot = reservation.rect
        val slotPixels = MemoryUtil.memAlloc(slot.width * slot.height)
        try {
            val inkWidth = ink.width
            val inkHeight = ink.height
            for (y in 0 until slot.height) {
                val sourceY = (y - padding).coerceIn(0, inkHeight - 1)
                val rowBase = sourceY * inkWidth
                for (x in 0 until slot.width) {
                    val sourceX = (x - padding).coerceIn(0, inkWidth - 1)
                    slotPixels.put(buffer.get(rowBase + sourceX))
                }
            }
            slotPixels.flip()
            reservation.page.upload(slot, slotPixels)
        } finally {
            MemoryUtil.memFree(slotPixels)
        }
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
        fun create(padding: Int = AtlasSpec.DEFAULT_ATLAS_PADDING): BitmapAtlas =
            BitmapAtlas(AtlasSpec.BITMAP_ATLAS_SIZE, padding)
    }
}
