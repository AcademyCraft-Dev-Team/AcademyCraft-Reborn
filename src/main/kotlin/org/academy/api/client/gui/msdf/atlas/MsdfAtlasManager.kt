package org.academy.api.client.gui.msdf.atlas

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.msdf.core.Constants
import java.util.concurrent.ConcurrentHashMap

object MsdfAtlasManager {
    private val atlases: MutableMap<Identifier, MsdfAtlas> = ConcurrentHashMap<Identifier, MsdfAtlas>()

    fun getAtlas(descriptor: Identifier): MsdfAtlas {
        return atlases.computeIfAbsent(descriptor) { _ ->
            MsdfAtlas(
                Constants.DEFAULT_ATLAS_SIZE,
                Constants.DEFAULT_GLYPH_SIZE,
                Constants.DEFAULT_PX_RANGE
            )
        }
    }

    fun closeAll() {
        atlases.values.forEach { it.close() }
        atlases.clear()
    }
}