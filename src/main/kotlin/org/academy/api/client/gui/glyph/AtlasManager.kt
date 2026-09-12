package org.academy.api.client.gui.glyph

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.glyph.bitmap.BitmapAtlas
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * 字形图集统一入口（对标 Skia `AtlasManager`）。
 *
 * - 位图：单块共享 R8 图集（所有光栅尺寸共用）。
 * - MSDF：按字体各一块。
 */
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

    /** 已创建的共享位图图集；不存在时返回 null，不会按需创建。 */
    fun bitmapIfPresent(): BitmapAtlas? = bitmapAtlas

    /** 已创建的 MSDF 图集快照（按字体 ID），供调试导出遍历。 */
    fun msdfAtlases(): Map<Identifier, MsdfAtlas> = HashMap(msdfAtlases)

    fun msdf(fontId: Identifier, executor: Executor): MsdfAtlas =
        msdfAtlases.computeIfAbsent(fontId) {
            MsdfAtlas(
                it,
                Constants.DEFAULT_ATLAS_SIZE,
                Constants.DEFAULT_GLYPH_SIZE,
                Constants.DEFAULT_PX_RANGE,
                executor
            )
        }

    fun closeAll() {
        synchronized(this) {
            bitmapAtlas?.close()
            bitmapAtlas = null
        }
        msdfAtlases.values.forEach(MsdfAtlas::close)
        msdfAtlases.clear()
    }
}
