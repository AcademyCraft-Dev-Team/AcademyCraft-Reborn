package org.academy.api.client.gui.texture

import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView

class GpuTextureViewSource(
    private val view: GpuTextureView,
    private val preferredSampler: GpuSampler? = null
) : TextureSource {
    override fun getTextureView(): GpuTextureView? =
        if (!view.isClosed) view else null

    override fun getPreferredSampler(): GpuSampler? = preferredSampler

    override fun toString(): String = "GpuTextureViewSource($view)"
}
