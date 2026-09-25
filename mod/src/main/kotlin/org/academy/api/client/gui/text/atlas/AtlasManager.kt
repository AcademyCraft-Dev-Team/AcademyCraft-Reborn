package org.academy.api.client.gui.text.atlas

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.text.glyph.bitmap.BitmapAtlas
import org.academy.api.client.gui.text.glyph.msdf.MsdfAtlas
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

object AtlasManager {
    @Volatile
    private var bitmapAtlas: BitmapAtlas? = null
    private val msdfAtlases = ConcurrentHashMap<Identifier, MsdfAtlas>()

    fun bitmap(): BitmapAtlas {
        val existing = bitmapAtlas
        if (existing != null) return existing
        synchronized(this) {
            val recheck = bitmapAtlas
            if (recheck != null) return recheck
            val created = BitmapAtlas.create()
            bitmapAtlas = created
            return created
        }
    }

    fun bitmapIfPresent(): BitmapAtlas? = bitmapAtlas

    fun msdfAtlases(): Map<Identifier, MsdfAtlas> = HashMap(msdfAtlases)

    fun msdf(fontId: Identifier, executor: Executor): MsdfAtlas =
        msdfAtlases.computeIfAbsent(fontId) {
            MsdfAtlas(
                it,
                AtlasSpec.DEFAULT_ATLAS_SIZE,
                AtlasSpec.DEFAULT_GLYPH_SIZE,
                AtlasSpec.DEFAULT_PX_RANGE,
                executor
            )
        }

    fun msdfPageCount(): Int = msdfAtlases.values.sumOf { it.pageCount() }

    fun closeAll() {
        synchronized(this) {
            bitmapAtlas?.close()
            bitmapAtlas = null
        }
        msdfAtlases.values.forEach(MsdfAtlas::close)
        msdfAtlases.clear()
    }
}
